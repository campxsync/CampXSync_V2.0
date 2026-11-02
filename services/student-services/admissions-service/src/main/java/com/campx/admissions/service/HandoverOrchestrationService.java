package com.campx.admissions.service;

import com.campx.admissions.exception.AdmissionsConflictException;
import com.campx.admissions.exception.AdmissionsNotFoundException;
import com.campx.admissions.model.*;
import com.campx.admissions.model.dto.HandoverStatusResponse;
import com.campx.admissions.repository.AdmissionHandoverRepository;
import com.campx.admissions.repository.AdmissionOfferRepository;
import com.campx.admissions.repository.ApplicationRepository;
import com.campx.admissions.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Coordinates idempotent student creation in STM-01 and enrolment handover to STM-03 (EN-009, EN-010, US-021, US-022, US-033 / ADR D-09).
 */
public class HandoverOrchestrationService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(HandoverOrchestrationService.class);

    private final ApplicationRepository applicationRepository;
    private final AdmissionOfferRepository offerRepository;
    private final AdmissionHandoverRepository handoverRepository;
    private final AdmissionsStateEngine stateEngine;
    private final AdmissionsEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Simulated/configurable downstream STM-01 client adapter for tests
    private StudentServiceAdapter studentServiceAdapter;

    public interface StudentServiceAdapter {
        UUID createOrLinkStudent(UUID tenantId, Map<String, Object> enrollmentPayload, String idempotencyKey) throws Exception;
    }

    public HandoverOrchestrationService(ApplicationRepository applicationRepository,
                                        AdmissionOfferRepository offerRepository,
                                        AdmissionHandoverRepository handoverRepository,
                                        AdmissionsStateEngine stateEngine,
                                        AdmissionsEventPublisher eventPublisher) {
        this(applicationRepository, offerRepository, handoverRepository, stateEngine, eventPublisher, null);
    }

    public HandoverOrchestrationService(ApplicationRepository applicationRepository,
                                        AdmissionOfferRepository offerRepository,
                                        AdmissionHandoverRepository handoverRepository,
                                        AdmissionsStateEngine stateEngine,
                                        AdmissionsEventPublisher eventPublisher,
                                        StudentServiceAdapter studentServiceAdapter) {
        this.applicationRepository = applicationRepository;
        this.offerRepository = offerRepository;
        this.handoverRepository = handoverRepository;
        this.stateEngine = stateEngine;
        this.eventPublisher = eventPublisher;
        this.studentServiceAdapter = studentServiceAdapter != null ? studentServiceAdapter : new DefaultStudentServiceAdapter();
    }

    public void setStudentServiceAdapter(StudentServiceAdapter adapter) {
        this.studentServiceAdapter = adapter;
    }

    public HandoverStatusResponse triggerHandover(UserSecurityContext context, UUID applicationId, String callerIdempotencyKey) {
        ApplicationEntity application = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));

        if (application.getStatus() != ApplicationStatus.ACCEPTED
                && application.getStatus() != ApplicationStatus.HANDOVER_PENDING
                && application.getStatus() != ApplicationStatus.HANDED_OVER) {
            throw new AdmissionsConflictException("Application must be in ACCEPTED status to trigger handover: current status is " + application.getStatus());
        }

        AdmissionOfferEntity offer = offerRepository.findByApplication(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Accepted offer not found for application: " + applicationId));

        String idempotencyKey = callerIdempotencyKey != null ? callerIdempotencyKey : "stm02-handover-offer-" + offer.getId();

        // Check if handover already exists
        Optional<AdmissionHandoverEntity> existingHandover = handoverRepository.findByIdempotencyKey(context.getTenantId(), idempotencyKey);
        AdmissionHandoverEntity handover = existingHandover.orElseGet(() -> {
            AdmissionHandoverEntity h = new AdmissionHandoverEntity();
            h.setTenantId(context.getTenantId());
            h.setApplicationId(applicationId);
            h.setOfferId(offer.getId());
            h.setIdempotencyKey(idempotencyKey);
            h.setEnrolmentStatus("PENDING");
            h.setCreatedAt(OffsetDateTime.now());
            h.setUpdatedAt(OffsetDateTime.now());
            return handoverRepository.save(h);
        });

        if ("ENROLMENT_COMPLETED".equals(handover.getEnrolmentStatus()) || "STUDENT_CREATED".equals(handover.getEnrolmentStatus())) {
            return toResponse(handover);
        }

        // Map payload for STM-01 enrollStudent contract
        Map<String, Object> payload = buildStudentEnrollmentPayload(application);

        try {
            UUID studentId = studentServiceAdapter.createOrLinkStudent(context.getTenantId(), payload, idempotencyKey);
            handover.setCreatedStudentId(studentId);
            handover.setEnrolmentStatus("STUDENT_CREATED");
            handover.setHandoverCompletedAt(OffsetDateTime.now());
            handover.setUpdatedAt(OffsetDateTime.now());
            handoverRepository.save(handover);

            // Transition application to HANDED_OVER
            stateEngine.transition(context, application, ApplicationStatus.HANDED_OVER, "HANDOVER_CONFIRMED",
                    "Student created: " + studentId, null);
            applicationRepository.save(application);

            // Emit downstream enrolment handover event for STM-03
            if (eventPublisher != null) {
                Map<String, Object> stm03Payload = new HashMap<>();
                stm03Payload.put("applicationId", applicationId.toString());
                stm03Payload.put("studentId", studentId.toString());
                stm03Payload.put("offerId", offer.getId().toString());
                stm03Payload.put("intakeId", application.getIntakeId().toString());

                eventPublisher.publishEvent(null, context.getTenantId(), applicationId,
                        "admissions.enrolment.handover_requested.v1", stm03Payload, context.getRole(), null);
            }

            logger.info("Application {} successfully handed over to STM-01 student {}", applicationId, studentId);

        } catch (Exception e) {
            logger.error("Handover failed for application {}: {}", applicationId, e.getMessage());
            handover.setEnrolmentStatus("FAILED");
            handover.setLastError(e.getMessage());
            handover.setRetryCount(handover.getRetryCount() + 1);
            handover.setUpdatedAt(OffsetDateTime.now());
            handoverRepository.save(handover);
        }

        return toResponse(handover);
    }

    public int reconcileHandovers(UUID tenantId) {
        List<AdmissionHandoverEntity> pending = handoverRepository.findPendingOrFailed(tenantId);
        int successCount = 0;
        UserSecurityContext systemContext = new UserSecurityContext(UUID.fromString("00000000-0000-0000-0000-000000000001"), tenantId);

        for (AdmissionHandoverEntity h : pending) {
            try {
                triggerHandover(systemContext, h.getApplicationId(), h.getIdempotencyKey());
                successCount++;
            } catch (Exception e) {
                logger.warn("Reconciliation retry failed for handover {}: {}", h.getId(), e.getMessage());
            }
        }
        return successCount;
    }

    private Map<String, Object> buildStudentEnrollmentPayload(ApplicationEntity app) {
        Map<String, Object> map = new HashMap<>();
        map.put("admissionNo", app.getApplicationNumber());
        map.put("admissionDate", LocalDate.now().toString());
        map.put("personId", app.getApplicantPersonId().toString());

        try {
            JsonNode data = objectMapper.readTree(app.getApplicantData() != null ? app.getApplicantData() : "{}");
            if (data.has("firstName")) map.put("firstName", data.get("firstName").asText());
            if (data.has("lastName")) map.put("lastName", data.get("lastName").asText());
            if (data.has("primaryEmail")) map.put("primaryEmail", data.get("primaryEmail").asText());
            if (data.has("primaryPhone")) map.put("primaryPhone", data.get("primaryPhone").asText());
            if (data.has("dateOfBirth")) map.put("dateOfBirth", data.get("dateOfBirth").asText());
        } catch (Exception e) {
            logger.warn("Could not parse applicantData for handover: {}", e.getMessage());
        }
        return map;
    }

    private HandoverStatusResponse toResponse(AdmissionHandoverEntity h) {
        HandoverStatusResponse resp = new HandoverStatusResponse();
        resp.setApplicationId(h.getApplicationId());
        resp.setOfferId(h.getOfferId());
        resp.setCreatedStudentId(h.getCreatedStudentId());
        resp.setEnrolmentStatus(h.getEnrolmentStatus());
        resp.setRetryCount(h.getRetryCount());
        resp.setLastError(h.getLastError());
        resp.setHandoverCompletedAt(h.getHandoverCompletedAt());
        return resp;
    }

    // Default internal adapter that generates deterministically or simulates STM-01 response
    public static class DefaultStudentServiceAdapter implements StudentServiceAdapter {
        @Override
        public UUID createOrLinkStudent(UUID tenantId, Map<String, Object> enrollmentPayload, String idempotencyKey) throws Exception {
            // Generates deterministic UUID based on tenant and idempotency key
            return UUID.nameUUIDFromBytes((tenantId.toString() + ":" + idempotencyKey).getBytes("UTF-8"));
        }
    }
}
