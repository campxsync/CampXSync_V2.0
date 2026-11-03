package com.campx.admissions.service;

import com.campx.admissions.exception.AdmissionsConflictException;
import com.campx.admissions.exception.AdmissionsNotFoundException;
import com.campx.admissions.exception.AdmissionsValidationException;
import com.campx.admissions.model.*;
import com.campx.admissions.model.dto.*;
import com.campx.admissions.repository.*;
import com.campx.admissions.security.AdmissionsPermissionEvaluator;
import com.campx.admissions.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Service managing review scores, admission decisions, offer issuance, and offer acceptance lifecycle (US-013 to US-018, US-046, US-047, US-048 / ADR D-07, D-08, D-14).
 */
public class ReviewAndDecisionService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(ReviewAndDecisionService.class);

    private final ApplicationRepository applicationRepository;
    private final AdmissionIntakeRepository intakeRepository;
    private final ApplicationReviewRepository reviewRepository;
    private final AdmissionDecisionRepository decisionRepository;
    private final AdmissionOfferRepository offerRepository;
    private final AdmissionsStateEngine stateEngine;
    private final AdmissionsEventPublisher eventPublisher;

    public ReviewAndDecisionService(ApplicationRepository applicationRepository,
                                    AdmissionIntakeRepository intakeRepository,
                                    ApplicationReviewRepository reviewRepository,
                                    AdmissionDecisionRepository decisionRepository,
                                    AdmissionOfferRepository offerRepository,
                                    AdmissionsStateEngine stateEngine,
                                    AdmissionsEventPublisher eventPublisher) {
        this.applicationRepository = applicationRepository;
        this.intakeRepository = intakeRepository;
        this.reviewRepository = reviewRepository;
        this.decisionRepository = decisionRepository;
        this.offerRepository = offerRepository;
        this.stateEngine = stateEngine;
        this.eventPublisher = eventPublisher;
    }

    public void assignReviewer(UserSecurityContext context, UUID applicationId, AssignReviewerRequest request) {
        AdmissionsPermissionEvaluator.requireIntakeConfigurationPermission(context);

        ApplicationEntity application = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));

        application.setAssignedReviewerId(request.getReviewerId());
        if (request.getDepartmentId() != null) {
            application.setAssignedDepartmentId(request.getDepartmentId());
        }
        application.setUpdatedAt(OffsetDateTime.now());
        application.setRowVersion(application.getRowVersion() + 1);

        if (application.getStatus() == ApplicationStatus.ELIGIBLE || application.getStatus() == ApplicationStatus.SUBMITTED) {
            stateEngine.transition(context, application, ApplicationStatus.UNDER_REVIEW, "ASSIGN_REVIEWER",
                    "Assigned to reviewer: " + request.getReviewerId(), null);
        }

        applicationRepository.save(application);
    }

    public ApplicationReviewEntity submitReview(UserSecurityContext context, UUID applicationId, SubmitReviewRequest request) {
        ApplicationEntity application = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));

        AdmissionsPermissionEvaluator.requireReviewSubmissionPermission(context, application.getAssignedReviewerId(), application.getAssignedDepartmentId());

        ApplicationReviewEntity review = new ApplicationReviewEntity();
        review.setTenantId(context.getTenantId());
        review.setApplicationId(applicationId);
        review.setReviewerId(context.getUserId());
        review.setRubricVersion(request.getRubricVersion() != null ? request.getRubricVersion() : "1.0.0");
        review.setRubricScores(request.getRubricScores() != null ? request.getRubricScores() : "{}");
        review.setTotalScore(request.getTotalScore() != null ? request.getTotalScore() : java.math.BigDecimal.ZERO);
        review.setRecommendation(request.getRecommendation() != null ? request.getRecommendation() : ReviewRecommendation.RECOMMEND_OFFER);
        review.setConfidentialComments(request.getConfidentialComments());
        review.setApplicantFacingRemarks(request.getApplicantFacingRemarks());
        review.setSubmittedAt(OffsetDateTime.now());
        review.setCreatedAt(OffsetDateTime.now());
        review.setUpdatedAt(OffsetDateTime.now());

        reviewRepository.save(review);

        application.setReviewCommentsSummary(request.getApplicantFacingRemarks());
        applicationRepository.save(application);

        // Emit review submitted event
        if (eventPublisher != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("applicationId", applicationId.toString());
            payload.put("reviewerId", context.getUserId().toString());
            payload.put("totalScore", review.getTotalScore());
            payload.put("recommendation", review.getRecommendation().name());
            eventPublisher.publishEvent(null, context.getTenantId(), applicationId, "admissions.application.reviewed.v1", payload, context.getRole(), null);
        }

        return review;
    }

    public AdmissionDecisionEntity recordDecision(UserSecurityContext context, UUID applicationId, RecordDecisionRequest request) {
        AdmissionsPermissionEvaluator.requireDecisionApprovalPermission(context);

        ApplicationEntity application = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));

        // Segregation of Duties: Submitter cannot approve own application
        AdmissionsPermissionEvaluator.validateSegregationOfDutiesOnDecision(context, application.getApplicantPersonId(), application.getAssignedReviewerId());

        AdmissionDecisionEntity decision = new AdmissionDecisionEntity();
        decision.setTenantId(context.getTenantId());
        decision.setApplicationId(applicationId);
        decision.setDecisionType(request.getDecisionType());
        decision.setDecisionReasonCode(request.getDecisionReasonCode() != null ? request.getDecisionReasonCode() : "STANDARD_DECISION");
        decision.setDecisionNotes(request.getDecisionNotes());
        decision.setDecidedBy(context.getUserId());
        decision.setDecidedAt(OffsetDateTime.now());
        decision.setCreatedAt(OffsetDateTime.now());

        decisionRepository.save(decision);

        // Transition application state
        if (request.getDecisionType() == DecisionType.REJECTED) {
            stateEngine.transition(context, application, ApplicationStatus.REJECTED, "RECORD_DECISION", request.getDecisionNotes(), null);
        } else if (request.getDecisionType() == DecisionType.OFFER_APPROVED) {
            // Prepared for offer issuance
            logger.info("Application {} approved for admission offer", applicationId);
        }

        applicationRepository.save(application);
        return decision;
    }

    public AdmissionOfferEntity issueOffer(UserSecurityContext context, UUID applicationId, IssueOfferRequest request) {
        AdmissionsPermissionEvaluator.requireDecisionApprovalPermission(context);

        ApplicationEntity application = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));

        AdmissionIntakeEntity intake = intakeRepository.findById(context.getTenantId(), application.getIntakeId())
                .orElseThrow(() -> new AdmissionsNotFoundException("Intake not found: " + application.getIntakeId()));

        if (!intake.hasAvailableCapacity()) {
            throw new AdmissionsConflictException("Cannot issue offer: Intake capacity exhausted (" + intake.getAdmittedCount() + "/" + intake.getCapacity() + ")");
        }

        // Verify that offer does not already exist
        Optional<AdmissionOfferEntity> existing = offerRepository.findByApplication(context.getTenantId(), applicationId);
        if (existing.isPresent() && (existing.get().getStatus() == OfferStatus.ISSUED || existing.get().getStatus() == OfferStatus.ACCEPTED)) {
            return existing.get();
        }

        UUID decisionId = request.getDecisionId();
        if (decisionId == null) {
            decisionId = decisionRepository.findByApplication(context.getTenantId(), applicationId)
                    .map(AdmissionDecisionEntity::getId)
                    .orElseThrow(() -> new AdmissionsValidationException("Cannot issue offer without prior approved decision"));
        }

        AdmissionOfferEntity offer = new AdmissionOfferEntity();
        offer.setTenantId(context.getTenantId());
        offer.setApplicationId(applicationId);
        offer.setDecisionId(decisionId);
        offer.setOfferCode("OFR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        offer.setOfferTermsVersion(request.getOfferTermsVersion() != null ? request.getOfferTermsVersion() : "1.0.0");
        offer.setValidFrom(OffsetDateTime.now());
        offer.setValidTo(request.getValidTo() != null ? request.getValidTo() : OffsetDateTime.now().plusDays(14));
        offer.setStatus(OfferStatus.ISSUED);
        offer.setCreatedAt(OffsetDateTime.now());
        offer.setUpdatedAt(OffsetDateTime.now());
        offer.setCreatedBy(context.getUserId());

        offerRepository.save(offer);

        // Transition application state to OFFERED
        stateEngine.transition(context, application, ApplicationStatus.OFFERED, "ISSUE_OFFER",
                "Offer issued: " + offer.getOfferCode(), null);
        applicationRepository.save(application);

        return offer;
    }

    public synchronized AdmissionOfferEntity acceptOffer(UserSecurityContext context, UUID applicationId, AcceptOfferRequest request) {
        ApplicationEntity application = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));

        AdmissionsPermissionEvaluator.requireApplicationOwnershipOrStaff(context, application.getApplicantPersonId());

        AdmissionOfferEntity offer = offerRepository.findByApplication(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("No active offer found for application: " + applicationId));

        if (offer.getStatus() == OfferStatus.ACCEPTED) {
            return offer; // Idempotent return
        }

        if (offer.getStatus() != OfferStatus.ISSUED) {
            throw new AdmissionsConflictException("Offer is not in ISSUED status: current status is " + offer.getStatus());
        }

        if (offer.isExpired(OffsetDateTime.now())) {
            offer.setStatus(OfferStatus.EXPIRED);
            offerRepository.save(offer);
            stateEngine.transition(context, application, ApplicationStatus.EXPIRED, "AUTO_EXPIRE", "Offer expired", null);
            applicationRepository.save(application);
            throw new AdmissionsConflictException("Admission offer has expired and can no longer be accepted");
        }

        // Validate Intake capacity before locking in
        AdmissionIntakeEntity intake = intakeRepository.findById(context.getTenantId(), application.getIntakeId())
                .orElseThrow(() -> new AdmissionsNotFoundException("Intake not found: " + application.getIntakeId()));

        if (!intake.hasAvailableCapacity()) {
            throw new AdmissionsConflictException("Intake capacity reached before offer acceptance");
        }

        // Commit acceptance atomically
        offer.setStatus(OfferStatus.ACCEPTED);
        offer.setAcceptedAt(OffsetDateTime.now());
        offer.setUpdatedAt(OffsetDateTime.now());
        offer.setRowVersion(offer.getRowVersion() + 1);
        offerRepository.save(offer);

        intake.setAdmittedCount(intake.getAdmittedCount() + 1);
        intakeRepository.save(intake);

        stateEngine.transition(context, application, ApplicationStatus.ACCEPTED, "ACCEPT_OFFER",
                "Offer accepted by applicant: " + offer.getOfferCode(), null);
        applicationRepository.save(application);

        return offer;
    }

    public AdmissionOfferEntity declineOffer(UserSecurityContext context, UUID applicationId, DeclineOfferRequest request) {
        ApplicationEntity application = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));

        AdmissionsPermissionEvaluator.requireApplicationOwnershipOrStaff(context, application.getApplicantPersonId());

        AdmissionOfferEntity offer = offerRepository.findByApplication(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("No active offer found for application: " + applicationId));

        if (offer.getStatus() != OfferStatus.ISSUED) {
            throw new AdmissionsConflictException("Offer is not in ISSUED status");
        }

        offer.setStatus(OfferStatus.DECLINED);
        offer.setDeclinedAt(OffsetDateTime.now());
        offer.setDeclineReason(request != null ? request.getDeclineReason() : "Declined by applicant");
        offer.setUpdatedAt(OffsetDateTime.now());
        offerRepository.save(offer);

        stateEngine.transition(context, application, ApplicationStatus.DECLINED, "DECLINE_OFFER",
                offer.getDeclineReason(), null);
        applicationRepository.save(application);

        return offer;
    }

    public int expireOffers(UUID tenantId) {
        List<AdmissionOfferEntity> expired = offerRepository.findExpiredOffers(tenantId, OffsetDateTime.now());
        for (AdmissionOfferEntity offer : expired) {
            offer.setStatus(OfferStatus.EXPIRED);
            offer.setUpdatedAt(OffsetDateTime.now());
            offerRepository.save(offer);

            applicationRepository.findById(tenantId, offer.getApplicationId()).ifPresent(app -> {
                if (app.getStatus() == ApplicationStatus.OFFERED) {
                    UserSecurityContext systemContext = new UserSecurityContext(UUID.fromString("00000000-0000-0000-0000-000000000001"), tenantId);
                    stateEngine.transition(systemContext, app, ApplicationStatus.EXPIRED, "EXPIRE_OFFER", "Offer expired automatically", null);
                    applicationRepository.save(app);
                }
            });
        }
        return expired.size();
    }

    public AdmissionOfferEntity extendOffer(UserSecurityContext context, UUID applicationId, OffsetDateTime newValidTo) {
        AdmissionsPermissionEvaluator.requireDecisionApprovalPermission(context);

        AdmissionOfferEntity offer = offerRepository.findByApplication(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Offer not found for application: " + applicationId));

        if (newValidTo.isBefore(OffsetDateTime.now())) {
            throw new AdmissionsValidationException("Extended validity date must be in the future");
        }

        offer.setExtensionGranted(true);
        offer.setExtensionGrantedBy(context.getUserId());
        offer.setExtendedValidTo(newValidTo);
        if (offer.getStatus() == OfferStatus.EXPIRED) {
            offer.setStatus(OfferStatus.ISSUED); // Reopen offer
        }
        offer.setUpdatedAt(OffsetDateTime.now());
        offerRepository.save(offer);

        return offer;
    }
}
