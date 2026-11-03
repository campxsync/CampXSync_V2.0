package com.campx.admissions.service;

import com.campx.admissions.exception.*;
import com.campx.admissions.model.*;
import com.campx.admissions.model.dto.*;
import com.campx.admissions.repository.*;
import com.campx.admissions.security.AdmissionsPermissionEvaluator;
import com.campx.admissions.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Master Domain Service orchestrating all 55 user stories and enablers for STM-02.
 */
public class ApplicationDomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(ApplicationDomainService.class);

    private final ApplicationRepository applicationRepository;
    private final AdmissionIntakeRepository intakeRepository;
    private final EligibilityRuleRepository eligibilityRuleRepository;
    private final ApplicationDocumentRepository documentRepository;
    private final ApplicationReviewRepository reviewRepository;
    private final AdmissionDecisionRepository decisionRepository;
    private final AdmissionOfferRepository offerRepository;
    private final AdmissionHandoverRepository handoverRepository;
    private final StatusHistoryRepository historyRepository;

    private final AdmissionsStateEngine stateEngine;
    private final EligibilityEvaluationEngine eligibilityEngine;
    private final ReviewAndDecisionService reviewAndDecisionService;
    private final HandoverOrchestrationService handoverService;
    private final AdmissionsEventPublisher eventPublisher;
    private final com.campx.admissions.intelligence.AdmissionsIntelligenceService intelligenceService;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Allowed MIME types for uploaded document references
    private static final Set<String> ALLOWED_DOC_MIMES = new HashSet<>(Arrays.asList(
            "application/pdf", "image/jpeg", "image/png", "image/webp"
    ));
    private static final long MAX_DOC_SIZE_BYTES = 10 * 1024 * 1024; // 10MB

    public ApplicationDomainService(
            ApplicationRepository applicationRepository,
            AdmissionIntakeRepository intakeRepository,
            EligibilityRuleRepository eligibilityRuleRepository,
            ApplicationDocumentRepository documentRepository,
            ApplicationReviewRepository reviewRepository,
            AdmissionDecisionRepository decisionRepository,
            AdmissionOfferRepository offerRepository,
            AdmissionHandoverRepository handoverRepository,
            StatusHistoryRepository historyRepository,
            AdmissionsEventPublisher eventPublisher) {

        this.applicationRepository = applicationRepository;
        this.intakeRepository = intakeRepository;
        this.eligibilityRuleRepository = eligibilityRuleRepository;
        this.documentRepository = documentRepository;
        this.reviewRepository = reviewRepository;
        this.decisionRepository = decisionRepository;
        this.offerRepository = offerRepository;
        this.handoverRepository = handoverRepository;
        this.historyRepository = historyRepository;
        this.eventPublisher = eventPublisher != null ? eventPublisher : new AdmissionsEventPublisher();

        this.stateEngine = new AdmissionsStateEngine(historyRepository, this.eventPublisher);
        this.eligibilityEngine = new EligibilityEvaluationEngine();
        this.reviewAndDecisionService = new ReviewAndDecisionService(
                applicationRepository, intakeRepository, reviewRepository, decisionRepository, offerRepository, this.stateEngine, this.eventPublisher
        );
        this.handoverService = new HandoverOrchestrationService(
                applicationRepository, offerRepository, handoverRepository, this.stateEngine, this.eventPublisher, null, intakeRepository
        );
        this.intelligenceService = new com.campx.admissions.intelligence.AdmissionsIntelligenceService(
                applicationRepository, intakeRepository, reviewRepository, decisionRepository, offerRepository, handoverRepository,
                this.eventPublisher.getIntelligenceAdapter()
        );
        this.rateLimiter = new RateLimiter(50, 10.0); // 50 capacity, 10 tokens/sec
    }

    public com.campx.admissions.intelligence.AdmissionsIntelligenceService getIntelligenceService() {
        return intelligenceService;
    }

    // ========================================================================
    // 1. INTAKE CONFIGURATION (US-001, US-002, US-003, US-036)
    // ========================================================================

    public IntakeResponse configureIntake(UserSecurityContext context, ConfigureIntakeRequest request) {
        AdmissionsPermissionEvaluator.requireIntakeConfigurationPermission(context);

        if (request.getApplicationWindowStart() == null || request.getApplicationWindowEnd() == null) {
            throw new AdmissionsValidationException("Application window start and end dates are required");
        }
        if (request.getApplicationWindowEnd().isBefore(request.getApplicationWindowStart())) {
            throw new AdmissionsValidationException("Application window end date must be after start date");
        }
        if (request.getCapacity() <= 0) {
            throw new AdmissionsValidationException("Intake capacity must be greater than zero");
        }

        // Uniqueness check for intakeCode
        if (intakeRepository.existsByCode(context.getTenantId(), request.getAcademicYearId(), request.getIntakeCode())) {
            throw new AdmissionsConflictException("Intake code already exists for this academic year: " + request.getIntakeCode());
        }

        AdmissionIntakeEntity intake = new AdmissionIntakeEntity();
        intake.setTenantId(context.getTenantId());
        intake.setCollegeId(request.getCollegeId() != null ? request.getCollegeId() : context.getCollegeId());
        intake.setProgramId(request.getProgramId());
        intake.setAcademicYearId(request.getAcademicYearId());
        intake.setIntakeName(request.getIntakeName());
        intake.setIntakeCode(request.getIntakeCode());
        intake.setApplicationWindowStart(request.getApplicationWindowStart());
        intake.setApplicationWindowEnd(request.getApplicationWindowEnd());
        intake.setCapacity(request.getCapacity());
        intake.setFeeRequired(request.isFeeRequired());
        intake.setFeeAmount(request.getFeeAmount() != null ? request.getFeeAmount() : BigDecimal.ZERO);
        intake.setCurrency(request.getCurrency() != null ? request.getCurrency() : "INR");
        intake.setFeeTiming(request.getFeeTiming() != null ? request.getFeeTiming() : FeeTiming.AT_SUBMISSION);
        intake.setScoringRubric(request.getScoringRubric() != null ? request.getScoringRubric() : "{}");
        intake.setCreatedAt(OffsetDateTime.now());
        intake.setUpdatedAt(OffsetDateTime.now());
        intake.setCreatedBy(context.getUserId());

        intakeRepository.save(intake);
        return toIntakeResponse(intake);
    }

    public List<IntakeResponse> listActiveIntakes(UUID tenantId) {
        List<AdmissionIntakeEntity> list = intakeRepository.findActiveIntakes(tenantId);
        List<IntakeResponse> responses = new ArrayList<>();
        for (AdmissionIntakeEntity i : list) {
            responses.add(toIntakeResponse(i));
        }
        return responses;
    }

    public IntakeResponse getIntake(UUID tenantId, UUID intakeId) {
        AdmissionIntakeEntity intake = intakeRepository.findById(tenantId, intakeId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Intake not found: " + intakeId));
        return toIntakeResponse(intake);
    }

    public void retireIntake(UserSecurityContext context, UUID intakeId) {
        AdmissionsPermissionEvaluator.requireIntakeConfigurationPermission(context);
        AdmissionIntakeEntity intake = intakeRepository.findById(context.getTenantId(), intakeId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Intake not found: " + intakeId));
        intake.setActive(false);
        intake.setUpdatedAt(OffsetDateTime.now());
        intakeRepository.save(intake);
    }

    public EligibilityRuleEntity configureEligibilityRules(UserSecurityContext context, UUID intakeId, EligibilityRuleEntity rule) {
        AdmissionsPermissionEvaluator.requireIntakeConfigurationPermission(context);

        rule.setTenantId(context.getTenantId());
        rule.setIntakeId(intakeId);
        rule.setCreatedAt(OffsetDateTime.now());
        rule.setUpdatedAt(OffsetDateTime.now());
        rule.setCreatedBy(context.getUserId());
        return eligibilityRuleRepository.save(rule);
    }

    // ========================================================================
    // 2. APPLICATION DRAFTING & SUBMISSION (US-005, US-006, US-007, US-008, US-026, US-035, US-041)
    // ========================================================================

    public ApplicationResponse createDraftApplication(UserSecurityContext context, CreateApplicationRequest request) {
        String rateKey = context.getTenantId() + ":" + context.getUserId() + ":create_draft";
        if (!rateLimiter.tryAcquire(rateKey)) {
            throw new AdmissionsRateLimitExceededException("Rate limit exceeded for draft application creation; please retry later", 60);
        }

        AdmissionIntakeEntity intake = intakeRepository.findById(context.getTenantId(), request.getIntakeId())
                .orElseThrow(() -> new AdmissionsNotFoundException("Intake not found: " + request.getIntakeId()));

        if (!intake.isWindowOpen(OffsetDateTime.now())) {
            throw new AdmissionsValidationException("Cannot create application: Intake window is currently closed");
        }

        // Duplicate in-flight application check for applicant
        Optional<ApplicationEntity> activeExisting = applicationRepository.findActiveByApplicant(context.getTenantId(), request.getIntakeId(), context.getUserId());
        if (activeExisting.isPresent()) {
            return toApplicationResponse(activeExisting.get());
        }

        ApplicationEntity app = new ApplicationEntity();
        app.setTenantId(context.getTenantId());
        app.setIntakeId(request.getIntakeId());
        app.setApplicantPersonId(context.getUserId());
        app.setApplicationNumber("APP-" + LocalDate.now().getYear() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        app.setStatus(ApplicationStatus.DRAFT);
        app.setDocumentVerificationStatus(DocumentVerificationStatus.PENDING);
        app.setPaymentStatus(intake.isFeeRequired() ? PaymentStatus.PENDING : PaymentStatus.EXEMPT);
        app.setPaymentAmount(intake.getFeeAmount());
        app.setApplicantData(request.getApplicantData() != null ? request.getApplicantData() : "{}");
        app.setCreatedAt(OffsetDateTime.now());
        app.setUpdatedAt(OffsetDateTime.now());
        app.setCreatedBy(context.getUserId());

        applicationRepository.save(app);

        // Emit application created event
        Map<String, Object> payload = new HashMap<>();
        payload.put("applicationId", app.getId().toString());
        payload.put("applicationNumber", app.getApplicationNumber());
        payload.put("intakeId", intake.getId().toString());
        eventPublisher.publishEvent(null, context.getTenantId(), app.getId(), "admissions.application.created.v1", payload, context.getRole(), null);

        return toApplicationResponse(app);
    }

    public ApplicationResponse updateDraftApplication(UserSecurityContext context, UUID applicationId, UpdateApplicationRequest request, Integer expectedVersion) {
        ApplicationEntity app = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));

        AdmissionsPermissionEvaluator.requireApplicationOwnershipOrStaff(context, app.getApplicantPersonId());

        if (app.getStatus() != ApplicationStatus.DRAFT && app.getStatus() != ApplicationStatus.RETURNED_FOR_INFORMATION) {
            throw new AdmissionsLifecycleException("Cannot edit application in status: " + app.getStatus());
        }

        // Optimistic concurrency check
        if (expectedVersion != null && app.getRowVersion() != expectedVersion) {
            throw new AdmissionsConflictException("Optimistic lock conflict: Stale version. Expected " + expectedVersion + ", actual " + app.getRowVersion());
        }

        if (request.getApplicantData() != null) {
            app.setApplicantData(request.getApplicantData());
        }
        app.setUpdatedAt(OffsetDateTime.now());
        app.setRowVersion(app.getRowVersion() + 1);

        applicationRepository.save(app);
        return toApplicationResponse(app);
    }

    public ApplicationResponse submitApplication(UserSecurityContext context, UUID applicationId, SubmitApplicationRequest request) {
        ApplicationEntity app = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));

        AdmissionsPermissionEvaluator.requireApplicationOwnershipOrStaff(context, app.getApplicantPersonId());

        AdmissionIntakeEntity intake = intakeRepository.findById(context.getTenantId(), app.getIntakeId())
                .orElseThrow(() -> new AdmissionsNotFoundException("Intake not found: " + app.getIntakeId()));

        // Window check (unless late submission exception granted)
        if (!intake.isWindowOpen(OffsetDateTime.now()) && !app.isLateSubmissionExceptionGranted()) {
            throw new AdmissionsValidationException("Cannot submit application: Intake application window is closed");
        }

        if (request == null || !request.isDeclarationAccepted() || !request.isPrivacyConsentAccepted()) {
            throw new AdmissionsValidationException("Submission requires explicit acceptance of declarations and privacy consent (US-040)");
        }

        app.setDeclarationAccepted(true);
        app.setPrivacyConsentAccepted(true);
        app.setDeclarationTimestamp(OffsetDateTime.now());
        app.setSubmissionTimestamp(OffsetDateTime.now());

        // Fee policy check: If fee is mandatory AT_SUBMISSION, verify paid
        if (intake.isFeeRequired() && intake.getFeeTiming() == FeeTiming.AT_SUBMISSION) {
            if (app.getPaymentStatus() != PaymentStatus.PAID && app.getPaymentStatus() != PaymentStatus.WAIVED) {
                throw new AdmissionsConflictException("Application fee payment is required prior to submission (US-008, D-06)");
            }
        }

        stateEngine.transition(context, app, ApplicationStatus.SUBMITTED, "SUBMIT_APPLICATION",
                "Applicant submitted formal application", null);

        // Automated eligibility evaluation step (US-010)
        Optional<EligibilityRuleEntity> ruleOpt = eligibilityRuleRepository.findLatestActiveByIntake(context.getTenantId(), app.getIntakeId());
        EligibilityEvaluationEngine.EvaluationResult eval = eligibilityEngine.evaluate(app, ruleOpt.orElse(null));
        app.setEligibilityRuleVersion(eval.ruleVersion);
        app.setEligibilityResult(eval.resultCode);
        app.setEligibilityEvaluationDetail(eval.detailJson);

        if (eval.eligible) {
            stateEngine.transition(context, app, ApplicationStatus.ELIGIBLE, "AUTOMATED_ELIGIBILITY_PASS",
                    "Automated rule check passed", null);
        }

        applicationRepository.save(app);
        return toApplicationResponse(app);
    }

    public ApplicationResponse returnForInformation(UserSecurityContext context, UUID applicationId, String reason) {
        AdmissionsPermissionEvaluator.requireDocumentVerificationPermission(context);

        ApplicationEntity app = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));

        stateEngine.transition(context, app, ApplicationStatus.RETURNED_FOR_INFORMATION, "REQUEST_INFORMATION", reason, null);
        applicationRepository.save(app);
        return toApplicationResponse(app);
    }

    public ApplicationResponse resubmitApplication(UserSecurityContext context, UUID applicationId) {
        ApplicationEntity app = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));

        AdmissionsPermissionEvaluator.requireApplicationOwnershipOrStaff(context, app.getApplicantPersonId());

        stateEngine.transition(context, app, ApplicationStatus.SUBMITTED, "RESUBMIT_APPLICATION",
                "Applicant resubmitted following information request", null);
        applicationRepository.save(app);
        return toApplicationResponse(app);
    }

    public ApplicationResponse withdrawApplication(UserSecurityContext context, UUID applicationId, WithdrawApplicationRequest request) {
        ApplicationEntity app = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));

        AdmissionsPermissionEvaluator.requireApplicationOwnershipOrStaff(context, app.getApplicantPersonId());

        stateEngine.transition(context, app, ApplicationStatus.WITHDRAWN, "WITHDRAW_APPLICATION",
                request != null ? request.getReason() : "Applicant withdrawn", null);
        applicationRepository.save(app);
        return toApplicationResponse(app);
    }

    public ApplicationResponse getApplication(UserSecurityContext context, UUID applicationId) {
        ApplicationEntity app = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));

        AdmissionsPermissionEvaluator.requireApplicationOwnershipOrStaff(context, app.getApplicantPersonId());
        return toApplicationResponse(app);
    }

    public List<ApplicationResponse> listApplications(UserSecurityContext context, UUID intakeId, ApplicationStatus status) {
        List<ApplicationEntity> list;
        if (AdmissionsPermissionEvaluator.isApplicant(context)) {
            list = applicationRepository.findByApplicant(context.getTenantId(), context.getUserId());
        } else if (intakeId != null) {
            list = applicationRepository.findByIntake(context.getTenantId(), intakeId);
            if (status != null) {
                List<ApplicationEntity> filtered = new ArrayList<>();
                for (ApplicationEntity a : list) {
                    if (a.getStatus() == status) {
                        filtered.add(a);
                    }
                }
                list = filtered;
            }
        } else if (status != null) {
            list = applicationRepository.findByStatus(context.getTenantId(), status);
        } else {
            list = applicationRepository.findByTenantId(context.getTenantId());
        }

        List<ApplicationResponse> responses = new ArrayList<>();
        for (ApplicationEntity app : list) {
            responses.add(toApplicationResponse(app));
        }
        return responses;
    }

    public List<ApplicationReviewEntity> getApplicationReviews(UserSecurityContext context, UUID applicationId) {
        ApplicationEntity app = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));
        AdmissionsPermissionEvaluator.requireApplicationOwnershipOrStaff(context, app.getApplicantPersonId());
        return reviewRepository.findByApplication(context.getTenantId(), applicationId);
    }

    public AdmissionDecisionEntity getApplicationDecision(UserSecurityContext context, UUID applicationId) {
        ApplicationEntity app = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));
        AdmissionsPermissionEvaluator.requireApplicationOwnershipOrStaff(context, app.getApplicantPersonId());
        return decisionRepository.findByApplication(context.getTenantId(), applicationId).orElse(null);
    }

    public AdmissionOfferEntity getApplicationOffer(UserSecurityContext context, UUID applicationId) {
        ApplicationEntity app = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));
        AdmissionsPermissionEvaluator.requireApplicationOwnershipOrStaff(context, app.getApplicantPersonId());
        return offerRepository.findByApplication(context.getTenantId(), applicationId).orElse(null);
    }

    public AdmissionHandoverEntity getHandover(UserSecurityContext context, UUID applicationId) {
        ApplicationEntity app = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));
        AdmissionsPermissionEvaluator.requireApplicationOwnershipOrStaff(context, app.getApplicantPersonId());
        return handoverRepository.findByApplication(context.getTenantId(), applicationId).orElse(null);
    }

    public List<ApplicationStatusHistoryEntity> getStatusHistory(UserSecurityContext context, UUID applicationId) {
        ApplicationEntity app = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));
        AdmissionsPermissionEvaluator.requireApplicationOwnershipOrStaff(context, app.getApplicantPersonId());
        return historyRepository.findByApplication(context.getTenantId(), applicationId);
    }

    // ========================================================================
    // 3. APPLICATION DOCUMENTS (US-011, US-012, US-032, US-044)
    // ========================================================================

    public ApplicationDocumentEntity attachDocument(UserSecurityContext context, UUID applicationId, AttachDocumentRequest request) {
        ApplicationEntity app = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));

        AdmissionsPermissionEvaluator.requireApplicationOwnershipOrStaff(context, app.getApplicantPersonId());

        if (request.getMimeType() == null || !ALLOWED_DOC_MIMES.contains(request.getMimeType().toLowerCase())) {
            throw new AdmissionsValidationException("Invalid document MIME type: " + request.getMimeType() + ". Allowed: PDF, JPEG, PNG, WEBP");
        }

        if (request.getFileSizeBytes() > MAX_DOC_SIZE_BYTES) {
            throw new AdmissionsValidationException("File size exceeds 10MB limit: " + request.getFileSizeBytes() + " bytes");
        }

        ApplicationDocumentEntity doc = new ApplicationDocumentEntity();
        doc.setTenantId(context.getTenantId());
        doc.setApplicationId(applicationId);
        doc.setDocumentId(request.getDocumentId() != null ? request.getDocumentId() : UUID.randomUUID());
        doc.setDocumentType(request.getDocumentType());
        doc.setStoragePath(request.getStoragePath());
        doc.setFileName(request.getFileName());
        doc.setMimeType(request.getMimeType());
        doc.setFileSizeBytes(request.getFileSizeBytes());
        doc.setVerificationStatus(DocumentVerificationStatus.PENDING);
        doc.setCreatedAt(OffsetDateTime.now());
        doc.setUpdatedAt(OffsetDateTime.now());

        documentRepository.save(doc);

        app.setDocumentVerificationStatus(DocumentVerificationStatus.IN_PROGRESS);
        applicationRepository.save(app);

        return doc;
    }

    public ApplicationDocumentEntity verifyDocument(UserSecurityContext context, UUID applicationId, UUID documentId, VerifyDocumentRequest request) {
        AdmissionsPermissionEvaluator.requireDocumentVerificationPermission(context);

        ApplicationDocumentEntity doc = documentRepository.findById(context.getTenantId(), documentId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Document reference not found: " + documentId));

        doc.setVerificationStatus(request.getStatus());
        doc.setVerifiedBy(context.getUserId());
        doc.setVerifiedAt(OffsetDateTime.now());
        doc.setRejectionReason(request.getRejectionReason());
        doc.setFraudulent(request.isFraudulent());
        doc.setFraudNotes(request.getFraudNotes());
        doc.setUpdatedAt(OffsetDateTime.now());

        documentRepository.save(doc);

        // Update overall application document verification status
        List<ApplicationDocumentEntity> allDocs = documentRepository.findByApplication(context.getTenantId(), applicationId);
        boolean anyRejected = allDocs.stream().anyMatch(d -> d.getVerificationStatus() == DocumentVerificationStatus.REJECTED);
        boolean allVerified = allDocs.stream().allMatch(d -> d.getVerificationStatus() == DocumentVerificationStatus.VERIFIED || d.getVerificationStatus() == DocumentVerificationStatus.WAIVED);

        applicationRepository.findById(context.getTenantId(), applicationId).ifPresent(app -> {
            if (anyRejected) {
                app.setDocumentVerificationStatus(DocumentVerificationStatus.REJECTED);
            } else if (allVerified && !allDocs.isEmpty()) {
                app.setDocumentVerificationStatus(DocumentVerificationStatus.VERIFIED);
            }
            applicationRepository.save(app);
        });

        return doc;
    }

    public List<ApplicationDocumentEntity> getApplicationDocuments(UserSecurityContext context, UUID applicationId) {
        ApplicationEntity app = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));

        AdmissionsPermissionEvaluator.requireApplicationOwnershipOrStaff(context, app.getApplicantPersonId());
        return documentRepository.findByApplication(context.getTenantId(), applicationId);
    }

    public void deleteDocument(UserSecurityContext context, UUID applicationId, UUID documentId) {
        ApplicationEntity app = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));

        AdmissionsPermissionEvaluator.requireApplicationOwnershipOrStaff(context, app.getApplicantPersonId());

        if (app.getStatus() != ApplicationStatus.DRAFT && app.getStatus() != ApplicationStatus.RETURNED_FOR_INFORMATION) {
            throw new AdmissionsLifecycleException("Documents can only be deleted when application is in DRAFT or RETURNED_FOR_INFORMATION status. Current status: " + app.getStatus());
        }

        ApplicationDocumentEntity doc = documentRepository.findById(context.getTenantId(), documentId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Document reference not found: " + documentId));

        if (!doc.getApplicationId().equals(applicationId)) {
            throw new AdmissionsValidationException("Document does not belong to specified application");
        }

        documentRepository.delete(context.getTenantId(), documentId);
    }

    // ========================================================================
    // 4. ELIGIBILITY OVERRIDES & EXCEPTIONS (US-037, US-043)
    // ========================================================================

    public ApplicationResponse recordEligibilityOverride(UserSecurityContext context, UUID applicationId, String overrideReason) {
        AdmissionsPermissionEvaluator.requireDecisionApprovalPermission(context);

        ApplicationEntity app = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));

        app.setEligibilityOverridden(true);
        app.setEligibilityOverrideReason(overrideReason);
        app.setEligibilityOverrideBy(context.getUserId());
        app.setEligibilityResult("OVERRIDDEN_ELIGIBLE");

        stateEngine.transition(context, app, ApplicationStatus.ELIGIBLE, "RECORD_ELIGIBILITY_OVERRIDE", overrideReason, null);
        applicationRepository.save(app);
        return toApplicationResponse(app);
    }

    public ApplicationResponse grantLateSubmissionException(UserSecurityContext context, UUID applicationId, String reason) {
        AdmissionsPermissionEvaluator.requireDecisionApprovalPermission(context);

        ApplicationEntity app = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));

        app.setLateSubmissionExceptionGranted(true);
        app.setLateSubmissionExceptionBy(context.getUserId());
        applicationRepository.save(app);

        logger.info("Late submission exception granted for application {} by {}", applicationId, context.getUserId());
        return toApplicationResponse(app);
    }

    // ========================================================================
    // 5. REVIEW & DECISION DELEGATES (US-013 to US-018)
    // ========================================================================

    public void assignReviewer(UserSecurityContext context, UUID applicationId, AssignReviewerRequest request) {
        reviewAndDecisionService.assignReviewer(context, applicationId, request);
    }

    public ApplicationReviewEntity submitReview(UserSecurityContext context, UUID applicationId, SubmitReviewRequest request) {
        return reviewAndDecisionService.submitReview(context, applicationId, request);
    }

    public AdmissionDecisionEntity recordDecision(UserSecurityContext context, UUID applicationId, RecordDecisionRequest request) {
        return reviewAndDecisionService.recordDecision(context, applicationId, request);
    }

    public AdmissionOfferEntity issueOffer(UserSecurityContext context, UUID applicationId, IssueOfferRequest request) {
        return reviewAndDecisionService.issueOffer(context, applicationId, request);
    }

    public AdmissionOfferEntity acceptOffer(UserSecurityContext context, UUID applicationId, AcceptOfferRequest request) {
        AdmissionOfferEntity offer = reviewAndDecisionService.acceptOffer(context, applicationId, request);
        // Automatically initiate handover
        try {
            handoverService.triggerHandover(context, applicationId, null);
        } catch (Exception e) {
            logger.warn("Initial synchronous handover deferred to background worker: {}", e.getMessage());
        }
        return offer;
    }

    public AdmissionOfferEntity declineOffer(UserSecurityContext context, UUID applicationId, DeclineOfferRequest request) {
        return reviewAndDecisionService.declineOffer(context, applicationId, request);
    }

    public int expireOffers(UUID tenantId) {
        return reviewAndDecisionService.expireOffers(tenantId);
    }

    public AdmissionOfferEntity extendOffer(UserSecurityContext context, UUID applicationId, OffsetDateTime newValidTo) {
        return reviewAndDecisionService.extendOffer(context, applicationId, newValidTo);
    }

    // ========================================================================
    // 6. PAYMENTS COORDINATION (US-019, US-020 / ADR D-06)
    // ========================================================================

    public PaymentIntentResponse createPaymentIntent(UserSecurityContext context, UUID applicationId) {
        ApplicationEntity app = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));

        AdmissionsPermissionEvaluator.requireApplicationOwnershipOrStaff(context, app.getApplicantPersonId());

        PaymentIntentResponse intent = new PaymentIntentResponse();
        intent.setApplicationId(applicationId);
        intent.setPaymentIntentId("pi_sim_" + UUID.randomUUID().toString().replace("-", ""));
        intent.setAmount(app.getPaymentAmount());
        intent.setCurrency("INR");
        intent.setClientSecret("secret_" + UUID.randomUUID().toString().replace("-", ""));
        intent.setStatus("REQUIRES_PAYMENT");

        app.setPaymentReference(intent.getPaymentIntentId());
        applicationRepository.save(app);

        return intent;
    }

    public ApplicationResponse receivePaymentConfirmation(UserSecurityContext context, PaymentWebhookRequest request) {
        ApplicationEntity app = applicationRepository.findById(context.getTenantId(), request.getApplicationId())
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + request.getApplicationId()));

        if ("PAID".equalsIgnoreCase(request.getStatus())) {
            app.setPaymentStatus(PaymentStatus.PAID);
            app.setPaymentReference(request.getPaymentReference());
            if (request.getAmount() != null) {
                app.setPaymentAmount(request.getAmount());
            }
        } else {
            app.setPaymentStatus(PaymentStatus.FAILED);
        }
        app.setUpdatedAt(OffsetDateTime.now());
        applicationRepository.save(app);

        return toApplicationResponse(app);
    }

    // ========================================================================
    // 7. HANDOVER DELEGATES (US-021, US-022, US-033)
    // ========================================================================

    public HandoverStatusResponse triggerHandover(UserSecurityContext context, UUID applicationId, String idempotencyKey) {
        return handoverService.triggerHandover(context, applicationId, idempotencyKey);
    }

    public int reconcileHandovers(UUID tenantId) {
        return handoverService.reconcileHandovers(tenantId);
    }

    // ========================================================================
    // 8. FUNNEL REPORTING & DATA GOVERNANCE (US-024, US-025, US-034, US-049)
    // ========================================================================

    public AdmissionsFunnelReport getFunnelReport(UserSecurityContext context, UUID intakeId) {
        AdmissionsPermissionEvaluator.requireReportingPermission(context);

        List<ApplicationEntity> apps = applicationRepository.findByIntake(context.getTenantId(), intakeId);

        long drafts = 0, submitted = 0, eligible = 0, reviewed = 0, offered = 0, accepted = 0, declined = 0, expired = 0, rejected = 0, handedOver = 0;

        for (ApplicationEntity a : apps) {
            switch (a.getStatus()) {
                case DRAFT: drafts++; break;
                case SUBMITTED: submitted++; break;
                case ELIGIBLE: eligible++; break;
                case UNDER_REVIEW: reviewed++; break;
                case OFFERED: offered++; break;
                case ACCEPTED: accepted++; break;
                case DECLINED: declined++; break;
                case EXPIRED: expired++; break;
                case REJECTED: rejected++; break;
                case HANDED_OVER: handedOver++; break;
                default: break;
            }
        }

        AdmissionsFunnelReport report = new AdmissionsFunnelReport();
        report.setTenantId(context.getTenantId());
        report.setIntakeId(intakeId);
        report.setTotalDrafts(drafts);
        report.setTotalSubmitted(submitted);
        report.setTotalEligible(eligible);
        report.setTotalReviewed(reviewed);
        report.setTotalOffered(offered);
        report.setTotalAccepted(accepted);
        report.setTotalDeclined(declined);
        report.setTotalExpired(expired);
        report.setTotalRejected(rejected);
        report.setTotalHandedOver(handedOver);

        long totalResolved = accepted + handedOver;
        long totalDecided = totalResolved + declined + expired + rejected;
        double conv = totalDecided > 0 ? ((double) totalResolved / totalDecided) * 100.0 : 0.0;
        report.setConversionRatePercentage(Math.round(conv * 100.0) / 100.0);

        return report;
    }

    public void redactApplicantPersonalData(UserSecurityContext context, UUID applicationId) {
        ApplicationEntity app = applicationRepository.findById(context.getTenantId(), applicationId)
                .orElseThrow(() -> new AdmissionsNotFoundException("Application not found: " + applicationId));

        AdmissionsPermissionEvaluator.requireApplicationOwnershipOrStaff(context, app.getApplicantPersonId());

        if (!app.getStatus().isTerminal()) {
            throw new AdmissionsConflictException("Cannot redact active application; must be terminal status");
        }

        app.setApplicantData("{\"redacted\":true,\"redactedAt\":\"" + OffsetDateTime.now() + "\"}");
        app.setUpdatedAt(OffsetDateTime.now());
        applicationRepository.save(app);

        logger.info("Applicant personal data redacted for application {} under compliance retention rules", applicationId);
    }

    // ========================================================================
    // HELPER MAPPERS
    // ========================================================================

    private ApplicationResponse toApplicationResponse(ApplicationEntity app) {
        ApplicationResponse resp = new ApplicationResponse();
        resp.setId(app.getId());
        resp.setTenantId(app.getTenantId());
        resp.setApplicationNumber(app.getApplicationNumber());
        resp.setIntakeId(app.getIntakeId());
        resp.setApplicantPersonId(app.getApplicantPersonId());
        resp.setStatus(app.getStatus());
        resp.setDocumentVerificationStatus(app.getDocumentVerificationStatus());
        resp.setPaymentStatus(app.getPaymentStatus());
        resp.setPaymentAmount(app.getPaymentAmount());
        resp.setSubmissionTimestamp(app.getSubmissionTimestamp());
        resp.setEligibilityResult(app.getEligibilityResult());
        resp.setDeclarationAccepted(app.isDeclarationAccepted());
        resp.setApplicantData(app.getApplicantData());
        resp.setTimelineStatus(app.getStatus().name());
        resp.setRowVersion(app.getRowVersion());
        resp.setCreatedAt(app.getCreatedAt());
        return resp;
    }

    private IntakeResponse toIntakeResponse(AdmissionIntakeEntity i) {
        IntakeResponse r = new IntakeResponse();
        r.setId(i.getId());
        r.setTenantId(i.getTenantId());
        r.setCollegeId(i.getCollegeId());
        r.setProgramId(i.getProgramId());
        r.setAcademicYearId(i.getAcademicYearId());
        r.setIntakeName(i.getIntakeName());
        r.setIntakeCode(i.getIntakeCode());
        r.setApplicationWindowStart(i.getApplicationWindowStart());
        r.setApplicationWindowEnd(i.getApplicationWindowEnd());
        r.setCapacity(i.getCapacity());
        r.setAdmittedCount(i.getAdmittedCount());
        r.setActive(i.isActive());
        r.setWindowOpen(i.isWindowOpen(OffsetDateTime.now()));
        r.setFeeRequired(i.isFeeRequired());
        r.setFeeAmount(i.getFeeAmount());
        r.setCurrency(i.getCurrency());
        r.setFeeTiming(i.getFeeTiming());
        r.setScoringRubric(i.getScoringRubric());
        return r;
    }

    public ReviewAndDecisionService getReviewAndDecisionService() {
        return reviewAndDecisionService;
    }

    public HandoverOrchestrationService getHandoverService() {
        return handoverService;
    }

    public AdmissionsEventPublisher getEventPublisher() {
        return eventPublisher;
    }
}

