package com.campx.admissions.intelligence;

import com.campx.admissions.model.*;
import com.campx.admissions.repository.*;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.platform.intelligence.ControlledIntelligenceAdapter;
import com.campx.platform.intelligence.IntelligenceContextEvent;
import com.campx.platform.intelligence.IntelligenceContractValidator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise Intelligence Integration Service for STM-02 Admissions Domain.
 * <p>
 * Aggregates, minimizes, and delivers governed admissions intelligence to the
 * CampXSync Intelligence Layer via {@link ControlledIntelligenceAdapter} and
 * versioned {@link IntelligenceContextEvent} contracts.
 * </p>
 * <p>
 * Emits five governed intelligence streams without leaking applicant PII:
 * <ul>
 *   <li>1. Admissions Funnel Intelligence</li>
 *   <li>2. Application Demand & Completion Intelligence</li>
 *   <li>3. Admissions Operational Workload & Bottleneck Intelligence</li>
 *   <li>4. Admission Decision & Cutoff Intelligence</li>
 *   <li>5. Enrolment & Matriculation Conversion Intelligence</li>
 * </ul>
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class AdmissionsIntelligenceService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AdmissionsIntelligenceService.class);
    private static final String SERVICE_NAME = "admissions-service";
    private static final String SCHEMA_VERSION = "1.0.0";

    private final ApplicationRepository applicationRepository;
    private final AdmissionIntakeRepository intakeRepository;
    private final ApplicationReviewRepository reviewRepository;
    private final AdmissionDecisionRepository decisionRepository;
    private final AdmissionOfferRepository offerRepository;
    private final AdmissionHandoverRepository handoverRepository;
    private final ControlledIntelligenceAdapter intelligenceAdapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Idempotent event deduplication cache: tenantId -> Set<eventId>
    private final Map<UUID, Set<String>> processedEventIds = new ConcurrentHashMap<>();

    public AdmissionsIntelligenceService(
            ApplicationRepository applicationRepository,
            AdmissionIntakeRepository intakeRepository,
            ApplicationReviewRepository reviewRepository,
            AdmissionDecisionRepository decisionRepository,
            AdmissionOfferRepository offerRepository,
            AdmissionHandoverRepository handoverRepository,
            ControlledIntelligenceAdapter intelligenceAdapter) {

        this.applicationRepository = applicationRepository;
        this.intakeRepository = intakeRepository;
        this.reviewRepository = reviewRepository;
        this.decisionRepository = decisionRepository;
        this.offerRepository = offerRepository;
        this.handoverRepository = handoverRepository;
        this.intelligenceAdapter = intelligenceAdapter != null ? intelligenceAdapter : new ControlledIntelligenceAdapter();
    }

    /**
     * 1. Generates and emits Admissions Funnel Intelligence.
     * Computes conversion funnel metrics: Received -> Submitted -> Under Review -> Offered -> Accepted.
     */
    public Map<String, Object> calculateAndPublishFunnelMetrics(UUID tenantId, UUID intakeId, String correlationId) {
        validateTenant(tenantId);
        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();

        List<ApplicationEntity> apps = intakeId != null
                ? applicationRepository.findByIntake(tenantId, intakeId)
                : applicationRepository.findByTenantId(tenantId);

        long received = apps.size();
        long submitted = 0;
        long underReview = 0;
        long offered = 0;
        long accepted = 0;
        long rejected = 0;

        for (ApplicationEntity app : apps) {
            ApplicationStatus s = app.getStatus();
            if (s != ApplicationStatus.DRAFT && s != ApplicationStatus.WITHDRAWN) {
                submitted++;
            }
            if (s == ApplicationStatus.UNDER_REVIEW || s == ApplicationStatus.ELIGIBLE) {
                underReview++;
            }
            if (s == ApplicationStatus.OFFERED || s == ApplicationStatus.ACCEPTED || s == ApplicationStatus.HANDED_OVER) {
                offered++;
            }
            if (s == ApplicationStatus.ACCEPTED || s == ApplicationStatus.HANDED_OVER) {
                accepted++;
            }
            if (s == ApplicationStatus.REJECTED) {
                rejected++;
            }
        }

        double submissionRate = received > 0 ? ((double) submitted / received) * 100.0 : 0.0;
        double offerRate = submitted > 0 ? ((double) offered / submitted) * 100.0 : 0.0;
        double acceptanceRate = offered > 0 ? ((double) accepted / offered) * 100.0 : 0.0;
        double overallConversionRate = received > 0 ? ((double) accepted / received) * 100.0 : 0.0;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("intakeId", intakeId != null ? intakeId.toString() : "ALL");
        payload.put("applicationsReceived", received);
        payload.put("applicationsSubmitted", submitted);
        payload.put("applicationsUnderReview", underReview);
        payload.put("applicationsOffered", offered);
        payload.put("applicationsAccepted", accepted);
        payload.put("applicationsRejected", rejected);
        payload.put("submissionRatePercent", Math.round(submissionRate * 100.0) / 100.0);
        payload.put("offerRatePercent", Math.round(offerRate * 100.0) / 100.0);
        payload.put("offerAcceptanceRatePercent", Math.round(acceptanceRate * 100.0) / 100.0);
        payload.put("overallConversionRatePercent", Math.round(overallConversionRate * 100.0) / 100.0);
        payload.put("computedAt", Instant.now().toString());

        emitIntelligenceEvent(tenantId, "AdmissionsFunnelCalculated.v1", "ADMISSION_FUNNEL",
                intakeId != null ? intakeId.toString() : tenantId.toString(), payload, corrId);

        return payload;
    }

    /**
     * 2. Generates and emits Application Demand Intelligence.
     * Computes program-wise demand, completion rates, and incomplete application drop-off patterns.
     */
    public Map<String, Object> calculateAndPublishDemandMetrics(UUID tenantId, String correlationId) {
        validateTenant(tenantId);
        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();

        List<AdmissionIntakeEntity> intakes = intakeRepository.findByTenantId(tenantId);
        Map<String, Integer> programDemand = new HashMap<>();
        int totalCapacity = 0;
        int totalAdmitted = 0;

        for (AdmissionIntakeEntity intake : intakes) {
            totalCapacity += intake.getCapacity();
            totalAdmitted += intake.getAdmittedCount();
            String progId = intake.getProgramId() != null ? intake.getProgramId().toString() : "UNKNOWN";
            programDemand.put(progId, programDemand.getOrDefault(progId, 0) + intake.getAdmittedCount());
        }

        List<ApplicationEntity> allApps = applicationRepository.findByTenantId(tenantId);
        long draftCount = 0;
        long completedCount = 0;
        for (ApplicationEntity app : allApps) {
            if (app.getStatus() == ApplicationStatus.DRAFT) {
                draftCount++;
            } else {
                completedCount++;
            }
        }
        double completionRate = !allApps.isEmpty() ? ((double) completedCount / allApps.size()) * 100.0 : 0.0;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("totalIntakesConfigured", intakes.size());
        payload.put("totalSeatCapacity", totalCapacity);
        payload.put("totalAdmittedStudents", totalAdmitted);
        payload.put("programWiseDemand", programDemand);
        payload.put("draftApplicationsCount", draftCount);
        payload.put("completedApplicationsCount", completedCount);
        payload.put("applicationCompletionRatePercent", Math.round(completionRate * 100.0) / 100.0);
        payload.put("computedAt", Instant.now().toString());

        emitIntelligenceEvent(tenantId, "ApplicationDemandAnalyzed.v1", "APPLICATION_DEMAND",
                tenantId.toString(), payload, corrId);

        return payload;
    }

    /**
     * 3. Generates and emits Admissions Operations Intelligence.
     * Identifies reviewer workload, bottleneck stages, and application turnaround time.
     */
    public Map<String, Object> calculateAndPublishOperationsMetrics(UUID tenantId, String correlationId) {
        validateTenant(tenantId);
        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();

        List<ApplicationEntity> apps = applicationRepository.findByTenantId(tenantId);
        Map<String, Integer> reviewerWorkload = new HashMap<>();
        long pendingReviewCount = 0;
        long docVerificationPending = 0;

        for (ApplicationEntity app : apps) {
            if (app.getAssignedReviewerId() != null) {
                String reviewerId = app.getAssignedReviewerId().toString();
                reviewerWorkload.put(reviewerId, reviewerWorkload.getOrDefault(reviewerId, 0) + 1);
            }
            if (app.getStatus() == ApplicationStatus.UNDER_REVIEW || app.getStatus() == ApplicationStatus.SUBMITTED) {
                pendingReviewCount++;
            }
            if (app.getDocumentVerificationStatus() == DocumentVerificationStatus.PENDING) {
                docVerificationPending++;
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("totalActiveApplications", apps.size());
        payload.put("pendingReviewCount", pendingReviewCount);
        payload.put("documentVerificationPendingCount", docVerificationPending);
        payload.put("reviewerWorkloadDistribution", reviewerWorkload);
        payload.put("bottleneckStage", docVerificationPending > pendingReviewCount ? "DOCUMENT_VERIFICATION" : "COMMITTEE_REVIEW");
        payload.put("computedAt", Instant.now().toString());

        emitIntelligenceEvent(tenantId, "AdmissionsOperationsAnalyzed.v1", "ADMISSIONS_OPERATIONS",
                tenantId.toString(), payload, corrId);

        return payload;
    }

    /**
     * 4. Generates and emits Decision Intelligence.
     * Summarizes committee approval patterns, rejection reasons, and quota utilization.
     */
    public Map<String, Object> calculateAndPublishDecisionMetrics(UUID tenantId, String correlationId) {
        validateTenant(tenantId);
        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();

        List<AdmissionDecisionEntity> decisions = decisionRepository.findByTenantId(tenantId);
        int offersApproved = 0;
        int rejections = 0;
        int waitlists = 0;

        for (AdmissionDecisionEntity decision : decisions) {
            if (decision.getDecisionType() == DecisionType.OFFER_APPROVED) {
                offersApproved++;
            } else if (decision.getDecisionType() == DecisionType.REJECTED) {
                rejections++;
            } else if (decision.getDecisionType() == DecisionType.WAITLISTED) {
                waitlists++;
            }
        }

        double acceptanceRatio = !decisions.isEmpty() ? ((double) offersApproved / decisions.size()) * 100.0 : 0.0;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("totalDecisionsRecorded", decisions.size());
        payload.put("offersApproved", offersApproved);
        payload.put("rejectionsRecorded", rejections);
        payload.put("waitlistPlacements", waitlists);
        payload.put("committeeApprovalRatioPercent", Math.round(acceptanceRatio * 100.0) / 100.0);
        payload.put("computedAt", Instant.now().toString());

        emitIntelligenceEvent(tenantId, "AdmissionsDecisionAnalyzed.v1", "ADMISSION_DECISIONS",
                tenantId.toString(), payload, corrId);

        return payload;
    }

    /**
     * 5. Generates and emits Enrolment Conversion Intelligence.
     * Tracks offers accepted, declined, expired, and successful STM-01 matriculation handovers.
     */
    public Map<String, Object> calculateAndPublishConversionMetrics(UUID tenantId, String correlationId) {
        validateTenant(tenantId);
        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();

        List<AdmissionOfferEntity> offers = offerRepository.findByTenantId(tenantId);
        int issued = offers.size();
        int accepted = 0;
        int declined = 0;
        int expired = 0;

        for (AdmissionOfferEntity offer : offers) {
            if (offer.getStatus() == OfferStatus.ACCEPTED) {
                accepted++;
            } else if (offer.getStatus() == OfferStatus.DECLINED) {
                declined++;
            } else if (offer.getStatus() == OfferStatus.EXPIRED) {
                expired++;
            }
        }

        List<AdmissionHandoverEntity> handovers = handoverRepository.findByTenantId(tenantId);
        int successfulHandovers = 0;
        int failedHandovers = 0;

        for (AdmissionHandoverEntity handover : handovers) {
            String status = handover.getEnrolmentStatus();
            if ("STUDENT_CREATED".equalsIgnoreCase(status) || "ENROLMENT_COMPLETED".equalsIgnoreCase(status)) {
                successfulHandoverCount(handover);
                successfulHandovers++;
            } else if ("FAILED".equalsIgnoreCase(status)) {
                failedHandovers++;
            }
        }

        double offerToStudentYield = issued > 0 ? ((double) successfulHandovers / issued) * 100.0 : 0.0;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("totalOffersIssued", issued);
        payload.put("offersAccepted", accepted);
        payload.put("offersDeclined", declined);
        payload.put("offersExpired", expired);
        payload.put("matriculationHandoversCompleted", successfulHandovers);
        payload.put("matriculationHandoversFailed", failedHandovers);
        payload.put("offerToStudentYieldPercent", Math.round(offerToStudentYield * 100.0) / 100.0);
        payload.put("computedAt", Instant.now().toString());

        emitIntelligenceEvent(tenantId, "EnrolmentConversionCalculated.v1", "ENROLMENT_CONVERSION",
                tenantId.toString(), payload, corrId);

        return payload;
    }

    private void successfulHandoverCount(AdmissionHandoverEntity handover) {
        // Traceability check
        if (handover.getCreatedStudentId() == null) {
            logger.warn("Handover completed without linked STM-01 student ID for app: {}", handover.getApplicationId());
        }
    }

    /**
     * Ingests an admissions domain event into the intelligence processor idempotently.
     */
    public boolean ingestLifecycleEvent(IntelligenceContextEvent event) {
        IntelligenceContractValidator.validate(event);

        UUID tenantId = UUID.fromString(event.getTenantId());
        Set<String> seenEvents = processedEventIds.computeIfAbsent(tenantId, k -> Collections.synchronizedSet(new HashSet<>()));

        if (seenEvents.contains(event.getIdempotencyKey())) {
            logger.info("[INTELLIGENCE_DEDUPLICATION] Event already processed; skipping duplicate: {}", event.getIdempotencyKey());
            return false;
        }

        seenEvents.add(event.getIdempotencyKey());
        logger.info("[INTELLIGENCE_INGEST] Ingested event [{}] type={} aggregate={}",
                event.getEventId(), event.getEventType(), event.getAggregateId());

        return true;
    }

    private void emitIntelligenceEvent(UUID tenantId, String eventType, String aggregateType,
                                       String aggregateId, Map<String, Object> payload, String correlationId) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String eventId = UUID.randomUUID().toString();

            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .schemaVersion(SCHEMA_VERSION)
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .tenantId(tenantId.toString())
                    .sourceService(SERVICE_NAME)
                    .correlationId(correlationId)
                    .idempotencyKey("intel-" + eventType + "-" + tenantId + "-" + aggregateId + "-" + payload.get("computedAt"))
                    .payloadJson(payloadJson)
                    .build();

            intelligenceAdapter.publish(event);
        } catch (Exception e) {
            logger.error("Failed to emit intelligence event {}: {}", eventType, e.getMessage(), e);
        }
    }

    private void validateTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null for intelligence calculations");
        }
    }

    public ControlledIntelligenceAdapter getIntelligenceAdapter() {
        return intelligenceAdapter;
    }
}
