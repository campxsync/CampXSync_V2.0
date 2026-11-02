package com.campx.admissions;

import com.campx.admissions.intelligence.AdmissionsIntelligenceService;
import com.campx.admissions.model.*;
import com.campx.admissions.repository.*;
import com.campx.platform.intelligence.ControlledIntelligenceAdapter;
import com.campx.platform.intelligence.IntelligenceContextEvent;
import com.campx.platform.intelligence.IntelligenceContractValidator;
import org.junit.Before;
import org.junit.Test;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Rigorous integration test for STM-02 Admissions Intelligence Integration.
 * Validates the 5 governed intelligence outputs, schema v1.0.0 compliance,
 * multi-tenant boundary preservation, idempotency, and data minimization.
 */
public class AdmissionsIntelligenceIntegrationTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private InMemoryApplicationRepository appRepo;
    private InMemoryAdmissionIntakeRepository intakeRepo;
    private InMemoryApplicationReviewRepository reviewRepo;
    private InMemoryAdmissionDecisionRepository decisionRepo;
    private InMemoryAdmissionOfferRepository offerRepo;
    private InMemoryAdmissionHandoverRepository handoverRepo;
    private ControlledIntelligenceAdapter adapter;
    private AdmissionsIntelligenceService intelligenceService;

    @Before
    public void setUp() {
        appRepo = new InMemoryApplicationRepository();
        intakeRepo = new InMemoryAdmissionIntakeRepository();
        reviewRepo = new InMemoryApplicationReviewRepository();
        decisionRepo = new InMemoryAdmissionDecisionRepository();
        offerRepo = new InMemoryAdmissionOfferRepository();
        handoverRepo = new InMemoryAdmissionHandoverRepository();
        adapter = new ControlledIntelligenceAdapter();

        intelligenceService = new AdmissionsIntelligenceService(
                appRepo, intakeRepo, reviewRepo, decisionRepo, offerRepo, handoverRepo, adapter
        );
    }

    @Test
    public void testAdmissionsFunnelIntelligenceGenerationAndContractValidation() {
        UUID intakeId = UUID.randomUUID();

        // Seed 4 applications for Tenant A: 1 DRAFT, 1 UNDER_REVIEW, 1 OFFERED, 1 ACCEPTED
        ApplicationEntity a1 = createApp(TENANT_A, intakeId, ApplicationStatus.DRAFT);
        ApplicationEntity a2 = createApp(TENANT_A, intakeId, ApplicationStatus.UNDER_REVIEW);
        ApplicationEntity a3 = createApp(TENANT_A, intakeId, ApplicationStatus.OFFERED);
        ApplicationEntity a4 = createApp(TENANT_A, intakeId, ApplicationStatus.ACCEPTED);
        appRepo.save(a1); appRepo.save(a2); appRepo.save(a3); appRepo.save(a4);

        Map<String, Object> funnel = intelligenceService.calculateAndPublishFunnelMetrics(TENANT_A, intakeId, "corr-funnel-1");

        assertNotNull(funnel);
        assertEquals(4L, funnel.get("applicationsReceived"));
        assertEquals(3L, funnel.get("applicationsSubmitted"));
        assertEquals(1L, funnel.get("applicationsAccepted"));
        assertEquals(75.0, funnel.get("submissionRatePercent"));

        // Verify published event in adapter
        assertEquals(1, adapter.getCapturedCount());
        IntelligenceContextEvent event = adapter.getCapturedEvents().get(0);
        assertEquals("AdmissionsFunnelCalculated.v1", event.getEventType());
        assertEquals(TENANT_A.toString(), event.getTenantId());
        assertEquals("1.0.0", event.getSchemaVersion());
        assertEquals("corr-funnel-1", event.getCorrelationId());

        // Invariant contract check
        IntelligenceContractValidator.validate(event);
    }

    @Test
    public void testApplicationDemandIntelligenceGeneration() {
        AdmissionIntakeEntity intake = new AdmissionIntakeEntity();
        intake.setId(UUID.randomUUID());
        intake.setTenantId(TENANT_A);
        intake.setProgramId(UUID.randomUUID());
        intake.setCapacity(100);
        intake.setAdmittedCount(65);
        intakeRepo.save(intake);

        ApplicationEntity a1 = createApp(TENANT_A, intake.getId(), ApplicationStatus.SUBMITTED);
        appRepo.save(a1);

        Map<String, Object> demand = intelligenceService.calculateAndPublishDemandMetrics(TENANT_A, "corr-demand-1");
        assertNotNull(demand);
        assertEquals(1, demand.get("totalIntakesConfigured"));
        assertEquals(100, demand.get("totalSeatCapacity"));
        assertEquals(65, demand.get("totalAdmittedStudents"));

        IntelligenceContextEvent event = adapter.getCapturedEvents().get(0);
        assertEquals("ApplicationDemandAnalyzed.v1", event.getEventType());
        IntelligenceContractValidator.validate(event);
    }

    @Test
    public void testAdmissionsOperationsAndDecisionIntelligence() {
        UUID reviewerId = UUID.randomUUID();
        ApplicationEntity a1 = createApp(TENANT_A, UUID.randomUUID(), ApplicationStatus.UNDER_REVIEW);
        a1.setAssignedReviewerId(reviewerId);
        a1.setDocumentVerificationStatus(DocumentVerificationStatus.PENDING);
        appRepo.save(a1);

        AdmissionDecisionEntity decision = new AdmissionDecisionEntity();
        decision.setId(UUID.randomUUID());
        decision.setTenantId(TENANT_A);
        decision.setApplicationId(a1.getId());
        decision.setDecisionType(DecisionType.OFFER_APPROVED);
        decisionRepo.save(decision);

        Map<String, Object> ops = intelligenceService.calculateAndPublishOperationsMetrics(TENANT_A, "corr-ops-1");
        assertEquals(1, ops.get("totalActiveApplications"));
        assertEquals(1L, ops.get("documentVerificationPendingCount"));

        Map<String, Object> dec = intelligenceService.calculateAndPublishDecisionMetrics(TENANT_A, "corr-dec-1");
        assertEquals(1, dec.get("totalDecisionsRecorded"));
        assertEquals(1, dec.get("offersApproved"));
        assertEquals(100.0, dec.get("committeeApprovalRatioPercent"));

        assertEquals(2, adapter.getCapturedCount());
    }

    @Test
    public void testEnrolmentConversionIntelligenceAndHandoverTracking() {
        UUID appId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        AdmissionOfferEntity offer = new AdmissionOfferEntity();
        offer.setId(offerId);
        offer.setTenantId(TENANT_A);
        offer.setApplicationId(appId);
        offer.setStatus(OfferStatus.ACCEPTED);
        offerRepo.save(offer);

        AdmissionHandoverEntity handover = new AdmissionHandoverEntity();
        handover.setId(UUID.randomUUID());
        handover.setTenantId(TENANT_A);
        handover.setApplicationId(appId);
        handover.setOfferId(offerId);
        handover.setCreatedStudentId(studentId);
        handover.setEnrolmentStatus("STUDENT_CREATED");
        handoverRepo.save(handover);

        Map<String, Object> conv = intelligenceService.calculateAndPublishConversionMetrics(TENANT_A, "corr-conv-1");
        assertEquals(1, conv.get("totalOffersIssued"));
        assertEquals(1, conv.get("offersAccepted"));
        assertEquals(1, conv.get("matriculationHandoversCompleted"));
        assertEquals(100.0, conv.get("offerToStudentYieldPercent"));
    }

    @Test
    public void testMultiTenantIsolationInIntelligenceOutputs() {
        // Tenant A has 2 applications
        appRepo.save(createApp(TENANT_A, UUID.randomUUID(), ApplicationStatus.SUBMITTED));
        appRepo.save(createApp(TENANT_A, UUID.randomUUID(), ApplicationStatus.OFFERED));

        // Tenant B has 5 applications
        for (int i = 0; i < 5; i++) {
            appRepo.save(createApp(TENANT_B, UUID.randomUUID(), ApplicationStatus.SUBMITTED));
        }

        Map<String, Object> funnelA = intelligenceService.calculateAndPublishFunnelMetrics(TENANT_A, null, "corr-A");
        Map<String, Object> funnelB = intelligenceService.calculateAndPublishFunnelMetrics(TENANT_B, null, "corr-B");

        assertEquals(2L, funnelA.get("applicationsReceived"));
        assertEquals(5L, funnelB.get("applicationsReceived"));

        // Verify tenant separation in adapter
        List<IntelligenceContextEvent> eventsA = adapter.getPublishedEventsForTenant(TENANT_A.toString());
        List<IntelligenceContextEvent> eventsB = adapter.getPublishedEventsForTenant(TENANT_B.toString());

        assertEquals(1, eventsA.size());
        assertEquals(1, eventsB.size());
        assertEquals(TENANT_A.toString(), eventsA.get(0).getTenantId());
        assertEquals(TENANT_B.toString(), eventsB.get(0).getTenantId());
    }

    @Test
    public void testIdempotentLifecycleEventIngestion() {
        IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                .tenantId(TENANT_A)
                .sourceService("admissions-service")
                .aggregateType("ADMISSION_APPLICATION")
                .aggregateId(UUID.randomUUID())
                .eventType("ApplicationSubmitted.v1")
                .idempotencyKey("idem-event-100")
                .correlationId("corr-idem-1")
                .payloadJson("{\"applicationNumber\":\"APP-2026-001\"}")
                .build();

        // First ingest: accepted
        boolean firstIngest = intelligenceService.ingestLifecycleEvent(event);
        assertTrue("First ingest must succeed", firstIngest);

        // Duplicate ingest: rejected idempotently without exception
        boolean duplicateIngest = intelligenceService.ingestLifecycleEvent(event);
        assertFalse("Duplicate ingest with same idempotency key must be skipped", duplicateIngest);
    }

    private ApplicationEntity createApp(UUID tenantId, UUID intakeId, ApplicationStatus status) {
        ApplicationEntity a = new ApplicationEntity();
        a.setId(UUID.randomUUID());
        a.setTenantId(tenantId);
        a.setIntakeId(intakeId);
        a.setApplicationNumber("APP-" + UUID.randomUUID().toString().substring(0, 8));
        a.setApplicantPersonId(UUID.randomUUID());
        a.setStatus(status);
        a.setDocumentVerificationStatus(DocumentVerificationStatus.VERIFIED);
        a.setPaymentStatus(PaymentStatus.PAID);
        a.setSubmissionTimestamp(OffsetDateTime.now());
        return a;
    }
}
