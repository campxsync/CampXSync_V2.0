package com.campx.admissions;

import com.campx.admissions.exception.AdmissionsConflictException;
import com.campx.admissions.exception.AdmissionsSecurityViolationException;
import com.campx.admissions.model.*;
import com.campx.admissions.model.dto.*;
import com.campx.admissions.repository.*;
import com.campx.admissions.security.UserSecurityContext;
import com.campx.admissions.service.AdmissionsStateEngine;
import com.campx.admissions.service.ReviewAndDecisionService;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.Assert.*;

public class ReviewAndDecisionServiceTest {

    private ApplicationRepository applicationRepository;
    private AdmissionIntakeRepository intakeRepository;
    private ApplicationReviewRepository reviewRepository;
    private AdmissionDecisionRepository decisionRepository;
    private AdmissionOfferRepository offerRepository;
    private StatusHistoryRepository statusHistoryRepository;
    private AdmissionsStateEngine stateEngine;
    private ReviewAndDecisionService reviewAndDecisionService;

    private UUID tenantId;
    private UUID applicantId;
    private UUID reviewerId;
    private UUID approverId;
    private UUID intakeId;
    private UUID applicationId;

    private UserSecurityContext applicantContext;
    private UserSecurityContext reviewerContext;
    private UserSecurityContext approverContext;

    @Before
    public void setUp() {
        applicationRepository = new InMemoryApplicationRepository();
        intakeRepository = new InMemoryAdmissionIntakeRepository();
        reviewRepository = new InMemoryApplicationReviewRepository();
        decisionRepository = new InMemoryAdmissionDecisionRepository();
        offerRepository = new InMemoryAdmissionOfferRepository();
        statusHistoryRepository = new InMemoryStatusHistoryRepository();
        stateEngine = new AdmissionsStateEngine(statusHistoryRepository, null);

        reviewAndDecisionService = new ReviewAndDecisionService(
                applicationRepository,
                intakeRepository,
                reviewRepository,
                decisionRepository,
                offerRepository,
                stateEngine,
                null
        );

        tenantId = UUID.randomUUID();
        applicantId = UUID.randomUUID();
        reviewerId = UUID.randomUUID();
        approverId = UUID.randomUUID();
        intakeId = UUID.randomUUID();
        applicationId = UUID.randomUUID();

        applicantContext = new UserSecurityContext(applicantId, tenantId, null, null, "APPLICANT", false);
        reviewerContext = new UserSecurityContext(reviewerId, tenantId, null, null, "FACULTY_REVIEWER", false);
        approverContext = new UserSecurityContext(approverId, tenantId, null, null, "ADMISSIONS_APPROVER", false);

        // Seed intake with capacity of 1
        AdmissionIntakeEntity intake = new AdmissionIntakeEntity();
        intake.setTenantId(tenantId);
        intake.setId(intakeId);
        intake.setIntakeCode("CS-2026");
        intake.setIntakeName("Computer Science 2026");
        intake.setCapacity(1);
        intake.setAdmittedCount(0);
        intakeRepository.save(intake);

        // Seed application in SUBMITTED state
        ApplicationEntity app = new ApplicationEntity();
        app.setTenantId(tenantId);
        app.setId(applicationId);
        app.setApplicantPersonId(applicantId);
        app.setIntakeId(intakeId);
        app.setApplicationNumber("APP-CS-001");
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setRowVersion(0);
        applicationRepository.save(app);
    }

    @Test
    public void testReviewAssignmentAndSubmission() {
        // 1. Assign Reviewer
        UserSecurityContext officerContext = new UserSecurityContext(UUID.randomUUID(), tenantId, null, null, "ADMISSIONS_OFFICER", false);
        AssignReviewerRequest assignReq = new AssignReviewerRequest();
        assignReq.setReviewerId(reviewerId);
        reviewAndDecisionService.assignReviewer(officerContext, applicationId, assignReq);

        ApplicationEntity updatedApp = applicationRepository.findById(tenantId, applicationId).get();
        assertEquals(reviewerId, updatedApp.getAssignedReviewerId());
        assertEquals(ApplicationStatus.UNDER_REVIEW, updatedApp.getStatus());

        // 2. Submit Review
        SubmitReviewRequest reviewReq = new SubmitReviewRequest();
        reviewReq.setRubricVersion("1.0.0");
        reviewReq.setRubricScores("{\"academics\":9,\"sop\":8}");
        reviewReq.setTotalScore(new BigDecimal("85.00"));
        reviewReq.setRecommendation(ReviewRecommendation.RECOMMEND_OFFER);
        reviewReq.setConfidentialComments("Strong candidate with research background");
        reviewReq.setApplicantFacingRemarks("Excellent background");

        ApplicationReviewEntity review = reviewAndDecisionService.submitReview(reviewerContext, applicationId, reviewReq);
        assertNotNull(review);
        assertEquals(new BigDecimal("85.00"), review.getTotalScore());
        assertEquals(ReviewRecommendation.RECOMMEND_OFFER, review.getRecommendation());
    }

    @Test(expected = AdmissionsSecurityViolationException.class)
    public void testSegregationOfDutiesApproverCannotBeTheReviewer() {
        // Assign reviewer
        ApplicationEntity app = applicationRepository.findById(tenantId, applicationId).get();
        app.setAssignedReviewerId(reviewerId);
        applicationRepository.save(app);

        // Attempt by reviewer to also approve decision should fail SoD
        RecordDecisionRequest decReq = new RecordDecisionRequest();
        decReq.setDecisionType(DecisionType.OFFER_APPROVED);
        decReq.setDecisionNotes("Approved by reviewer");

        reviewAndDecisionService.recordDecision(reviewerContext, applicationId, decReq);
    }

    @Test
    public void testOfferIssuanceAndCapacityEnforcement() {
        // Assign and decide by dean/approver
        ApplicationEntity app = applicationRepository.findById(tenantId, applicationId).get();
        app.setStatus(ApplicationStatus.UNDER_REVIEW);
        app.setAssignedReviewerId(reviewerId);
        applicationRepository.save(app);

        RecordDecisionRequest decReq = new RecordDecisionRequest();
        decReq.setDecisionType(DecisionType.OFFER_APPROVED);
        decReq.setDecisionNotes("Committee approval ratified");
        reviewAndDecisionService.recordDecision(approverContext, applicationId, decReq);

        // Issue offer (Capacity = 1)
        IssueOfferRequest offerReq = new IssueOfferRequest();
        offerReq.setOfferTermsVersion("1.0.0");
        offerReq.setValidTo(OffsetDateTime.now().plusDays(10));
        AdmissionOfferEntity offer = reviewAndDecisionService.issueOffer(approverContext, applicationId, offerReq);

        assertNotNull(offer);
        assertEquals(OfferStatus.ISSUED, offer.getStatus());
        assertEquals(ApplicationStatus.OFFERED, applicationRepository.findById(tenantId, applicationId).get().getStatus());

        // Accept offer
        AcceptOfferRequest acceptReq = new AcceptOfferRequest();
        acceptReq.setConfirmAcceptance(true);
        AdmissionOfferEntity acceptedOffer = reviewAndDecisionService.acceptOffer(applicantContext, applicationId, acceptReq);
        assertEquals(OfferStatus.ACCEPTED, acceptedOffer.getStatus());

        // Verify intake capacity is now exhausted (1/1)
        AdmissionIntakeEntity updatedIntake = intakeRepository.findById(tenantId, intakeId).get();
        assertEquals(1, (int) updatedIntake.getAdmittedCount());
        assertFalse(updatedIntake.hasAvailableCapacity());

        // Attempting to issue offer to second applicant must fail with AdmissionsConflictException
        UUID secondAppId = UUID.randomUUID();
        ApplicationEntity secondApp = new ApplicationEntity();
        secondApp.setTenantId(tenantId);
        secondApp.setId(secondAppId);
        secondApp.setIntakeId(intakeId);
        secondApp.setStatus(ApplicationStatus.UNDER_REVIEW);
        secondApp.setRowVersion(0);
        applicationRepository.save(secondApp);

        try {
            reviewAndDecisionService.issueOffer(approverContext, secondAppId, offerReq);
            fail("Expected AdmissionsConflictException due to exhausted intake capacity");
        } catch (AdmissionsConflictException e) {
            assertTrue(e.getMessage().contains("capacity exhausted"));
        }
    }
}
