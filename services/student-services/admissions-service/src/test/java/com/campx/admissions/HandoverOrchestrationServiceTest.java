package com.campx.admissions;

import com.campx.admissions.exception.AdmissionsConflictException;
import com.campx.admissions.model.*;
import com.campx.admissions.model.dto.HandoverStatusResponse;
import com.campx.admissions.repository.*;
import com.campx.admissions.security.UserSecurityContext;
import com.campx.admissions.service.AdmissionsStateEngine;
import com.campx.admissions.service.HandoverOrchestrationService;
import org.junit.Before;
import org.junit.Test;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class HandoverOrchestrationServiceTest {

    private ApplicationRepository applicationRepository;
    private AdmissionOfferRepository offerRepository;
    private AdmissionHandoverRepository handoverRepository;
    private StatusHistoryRepository statusHistoryRepository;
    private AdmissionsStateEngine stateEngine;
    private HandoverOrchestrationService handoverService;

    private UUID tenantId;
    private UUID applicationId;
    private UUID applicantPersonId;
    private UUID offerId;
    private UserSecurityContext staffContext;
    private final AtomicInteger downstreamCalls = new AtomicInteger(0);

    @Before
    public void setUp() {
        applicationRepository = new InMemoryApplicationRepository();
        offerRepository = new InMemoryAdmissionOfferRepository();
        handoverRepository = new InMemoryAdmissionHandoverRepository();
        statusHistoryRepository = new InMemoryStatusHistoryRepository();
        stateEngine = new AdmissionsStateEngine(statusHistoryRepository, null);

        tenantId = UUID.randomUUID();
        applicationId = UUID.randomUUID();
        applicantPersonId = UUID.randomUUID();
        offerId = UUID.randomUUID();
        staffContext = new UserSecurityContext(UUID.randomUUID(), tenantId, null, null, "ADMISSIONS_OFFICER", false);
        downstreamCalls.set(0);

        HandoverOrchestrationService.StudentServiceAdapter mockAdapter = new HandoverOrchestrationService.StudentServiceAdapter() {
            @Override
            public UUID createOrLinkStudent(UUID tId, Map<String, Object> payload, String idempotencyKey) {
                downstreamCalls.incrementAndGet();
                return UUID.randomUUID();
            }
        };

        handoverService = new HandoverOrchestrationService(
                applicationRepository,
                offerRepository,
                handoverRepository,
                stateEngine,
                null,
                mockAdapter
        );

        // Seed application in ACCEPTED status
        ApplicationEntity app = new ApplicationEntity();
        app.setTenantId(tenantId);
        app.setId(applicationId);
        app.setApplicantPersonId(applicantPersonId);
        app.setApplicationNumber("APP-2026-ENG-099");
        app.setIntakeId(UUID.randomUUID());
        app.setStatus(ApplicationStatus.ACCEPTED);
        app.setApplicantData("{\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"email\":\"jane.doe@example.com\"}");
        app.setRowVersion(0);
        applicationRepository.save(app);

        // Seed offer in ACCEPTED status
        AdmissionOfferEntity offer = new AdmissionOfferEntity();
        offer.setTenantId(tenantId);
        offer.setId(offerId);
        offer.setApplicationId(applicationId);
        offer.setStatus(OfferStatus.ACCEPTED);
        offer.setAcceptedAt(OffsetDateTime.now());
        offerRepository.save(offer);
    }

    @Test
    public void testSuccessfulHandoverAndMatriculation() {
        HandoverStatusResponse response = handoverService.triggerHandover(staffContext, applicationId, "idemp-001");

        assertNotNull(response);
        assertEquals("STUDENT_CREATED", response.getEnrolmentStatus());
        assertNotNull(response.getCreatedStudentId());
        assertEquals(1, downstreamCalls.get());

        // Verify application status updated to HANDED_OVER
        ApplicationEntity updatedApp = applicationRepository.findById(tenantId, applicationId).get();
        assertEquals(ApplicationStatus.HANDED_OVER, updatedApp.getStatus());
    }

    @Test
    public void testIdempotentRepeatedHandoverDoesNotReCallDownstream() {
        HandoverStatusResponse firstResponse = handoverService.triggerHandover(staffContext, applicationId, "idemp-002");
        assertEquals(1, downstreamCalls.get());

        // Second call with same idempotency key
        HandoverStatusResponse secondResponse = handoverService.triggerHandover(staffContext, applicationId, "idemp-002");
        assertEquals("Downstream student service should not be called twice for identical key", 1, downstreamCalls.get());
        assertEquals(firstResponse.getCreatedStudentId(), secondResponse.getCreatedStudentId());
        assertEquals(firstResponse.getEnrolmentStatus(), secondResponse.getEnrolmentStatus());
    }

    @Test(expected = AdmissionsConflictException.class)
    public void testHandoverRejectedIfNotInAcceptedStatus() {
        ApplicationEntity app = applicationRepository.findById(tenantId, applicationId).get();
        app.setStatus(ApplicationStatus.SUBMITTED);
        applicationRepository.save(app);

        handoverService.triggerHandover(staffContext, applicationId, "idemp-003");
    }
}
