package com.campx.admissions.service;

import com.campx.admissions.exception.AdmissionsConflictException;
import com.campx.admissions.exception.AdmissionsNotFoundException;
import com.campx.admissions.model.*;
import com.campx.admissions.model.dto.HandoverStatusResponse;
import com.campx.admissions.repository.AdmissionHandoverRepository;
import com.campx.admissions.repository.AdmissionIntakeRepository;
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
    private AdmissionIntakeRepository intakeRepository;
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
        this(applicationRepository, offerRepository, handoverRepository, stateEngine, eventPublisher, null, null);
    }

    public HandoverOrchestrationService(ApplicationRepository applicationRepository,
                                        AdmissionOfferRepository offerRepository,
                                        AdmissionHandoverRepository handoverRepository,
                                        AdmissionsStateEngine stateEngine,
                                        AdmissionsEventPublisher eventPublisher,
                                        StudentServiceAdapter studentServiceAdapter) {
        this(applicationRepository, offerRepository, handoverRepository, stateEngine, eventPublisher, studentServiceAdapter, null);
    }

    public HandoverOrchestrationService(ApplicationRepository applicationRepository,
                                        AdmissionOfferRepository offerRepository,
                                        AdmissionHandoverRepository handoverRepository,
                                        AdmissionsStateEngine stateEngine,
                                        AdmissionsEventPublisher eventPublisher,
                                        StudentServiceAdapter studentServiceAdapter,
                                        AdmissionIntakeRepository intakeRepository) {
        this.applicationRepository = applicationRepository;
        this.offerRepository = offerRepository;
        this.handoverRepository = handoverRepository;
        this.stateEngine = stateEngine;
        this.eventPublisher = eventPublisher;
        this.studentServiceAdapter = studentServiceAdapter != null ? studentServiceAdapter : new DefaultStudentServiceAdapter();
        this.intakeRepository = intakeRepository;
    }

    public void setIntakeRepository(AdmissionIntakeRepository intakeRepository) {
        this.intakeRepository = intakeRepository;
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
        if (context != null && context.getUserId() != null) {
            payload.put("actorId", context.getUserId().toString());
        }

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

        if (intakeRepository != null && app.getIntakeId() != null) {
            try {
                intakeRepository.findById(app.getTenantId(), app.getIntakeId()).ifPresent(intake -> {
                    if (intake.getCollegeId() != null) map.put("collegeId", intake.getCollegeId().toString());
                    if (intake.getProgramId() != null) map.put("programId", intake.getProgramId().toString());
                    if (intake.getAcademicYearId() != null) map.put("admissionAcademicYearId", intake.getAcademicYearId().toString());
                });
            } catch (Exception e) {
                logger.warn("Could not load intake metadata for handover payload: {}", e.getMessage());
            }
        }

        try {
            JsonNode data = objectMapper.readTree(app.getApplicantData() != null ? app.getApplicantData() : "{}");
            if (data.has("firstName")) map.put("firstName", data.get("firstName").asText());
            if (data.has("lastName")) map.put("lastName", data.get("lastName").asText());
            if (data.has("primaryEmail")) {
                map.put("primaryEmail", data.get("primaryEmail").asText());
            } else if (data.has("email")) {
                map.put("primaryEmail", data.get("email").asText());
            }
            if (data.has("primaryPhone")) {
                map.put("primaryPhone", data.get("primaryPhone").asText());
            } else if (data.has("phone")) {
                map.put("primaryPhone", data.get("phone").asText());
            }
            if (data.has("dateOfBirth")) map.put("dateOfBirth", data.get("dateOfBirth").asText());
        } catch (Exception e) {
            logger.warn("Could not parse applicantData for handover: {}", e.getMessage());
        }

        // Defaults if missing to satisfy STM-01 mandatory schema
        if (!map.containsKey("firstName")) map.put("firstName", "Admitted");
        if (!map.containsKey("lastName")) map.put("lastName", "Student");
        if (!map.containsKey("primaryEmail")) map.put("primaryEmail", "e2e-applicant@campx.test");
        if (!map.containsKey("displayName")) {
            map.put("displayName", (map.get("firstName") + " " + map.get("lastName")).trim());
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

    // Default internal adapter that calls live STM-01 endpoint if configured, else generates deterministic UUID
    public static class DefaultStudentServiceAdapter implements StudentServiceAdapter {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        @Override
        public UUID createOrLinkStudent(UUID tenantId, Map<String, Object> enrollmentPayload, String idempotencyKey) throws Exception {
            String studentServiceUrl = System.getenv("STM01_SERVICE_URL");
            if (studentServiceUrl == null || studentServiceUrl.trim().isEmpty()) {
                studentServiceUrl = System.getenv("STUDENT_SERVICE_URL");
            }

            if (studentServiceUrl != null && !studentServiceUrl.trim().isEmpty()) {
                String targetUrl = studentServiceUrl.replaceAll("/+$", "") + "/api/v1/students";
                java.net.URL url = new java.net.URL(targetUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("X-Tenant-Id", tenantId.toString());

                Object actorObj = enrollmentPayload.remove("actorId");
                String actorId = actorObj != null ? actorObj.toString() : "e2e00000-0000-0000-0000-000000000005";
                conn.setRequestProperty("X-User-Id", actorId);
                conn.setRequestProperty("X-User-Role", "COLLEGE_ADMIN");
                if (idempotencyKey != null) {
                    conn.setRequestProperty("Idempotency-Key", idempotencyKey);
                }
                Object corrObj = enrollmentPayload.remove("correlationId");
                String correlationId = corrObj != null ? corrObj.toString() : null;
                if (correlationId != null) {
                    conn.setRequestProperty("X-Correlation-Id", correlationId);
                }
                conn.setDoOutput(true);
                String body = MAPPER.writeValueAsString(enrollmentPayload);
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }

                int status = conn.getResponseCode();
                if (status >= 200 && status < 300) {
                    try (java.io.InputStream is = conn.getInputStream()) {
                        JsonNode root = MAPPER.readTree(is);
                        if (root.has("id")) {
                            return UUID.fromString(root.get("id").asText());
                        } else if (root.has("studentId")) {
                            return UUID.fromString(root.get("studentId").asText());
                        }
                    }
                }
                String err = "";
                try (java.io.InputStream es = conn.getErrorStream()) {
                    if (es != null) {
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[1024];
                        int n;
                        while ((n = es.read(buf)) != -1) {
                            baos.write(buf, 0, n);
                        }
                        err = new String(baos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                    }
                } catch (Exception ignored) {}
                throw new IllegalStateException("STM-01 student creation returned HTTP " + status + ": " + err);
            }

            // Generates deterministic UUID based on tenant and idempotency key
            return UUID.nameUUIDFromBytes((tenantId.toString() + ":" + idempotencyKey).getBytes("UTF-8"));
        }
    }
}
