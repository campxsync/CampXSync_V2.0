package com.campx.admissions.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class ApplicationEntity {

    private UUID tenantId;
    private UUID id;
    private String applicationNumber;
    private UUID intakeId;
    private UUID applicantPersonId;
    private UUID assignedDepartmentId;
    private UUID assignedReviewerId;
    private ApplicationStatus status = ApplicationStatus.DRAFT;
    private DocumentVerificationStatus documentVerificationStatus = DocumentVerificationStatus.PENDING;
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;
    private UUID feeObligationId;
    private String paymentReference;
    private BigDecimal paymentAmount = BigDecimal.ZERO;
    private OffsetDateTime submissionTimestamp;
    private String eligibilityRuleVersion;
    private String eligibilityResult;
    private String eligibilityEvaluationDetail = "{}";
    private boolean eligibilityOverridden = false;
    private String eligibilityOverrideReason;
    private UUID eligibilityOverrideBy;
    private boolean lateSubmissionExceptionGranted = false;
    private UUID lateSubmissionExceptionBy;
    private boolean declarationAccepted = false;
    private OffsetDateTime declarationTimestamp;
    private boolean privacyConsentAccepted = false;
    private String applicantData = "{}";
    private String reviewCommentsSummary;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    private OffsetDateTime deletedAt;
    private int rowVersion = 1;

    public ApplicationEntity() {
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getApplicationNumber() {
        return applicationNumber;
    }

    public void setApplicationNumber(String applicationNumber) {
        this.applicationNumber = applicationNumber;
    }

    public UUID getIntakeId() {
        return intakeId;
    }

    public void setIntakeId(UUID intakeId) {
        this.intakeId = intakeId;
    }

    public UUID getApplicantPersonId() {
        return applicantPersonId;
    }

    public void setApplicantPersonId(UUID applicantPersonId) {
        this.applicantPersonId = applicantPersonId;
    }

    public UUID getAssignedDepartmentId() {
        return assignedDepartmentId;
    }

    public void setAssignedDepartmentId(UUID assignedDepartmentId) {
        this.assignedDepartmentId = assignedDepartmentId;
    }

    public UUID getAssignedReviewerId() {
        return assignedReviewerId;
    }

    public void setAssignedReviewerId(UUID assignedReviewerId) {
        this.assignedReviewerId = assignedReviewerId;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public DocumentVerificationStatus getDocumentVerificationStatus() {
        return documentVerificationStatus;
    }

    public void setDocumentVerificationStatus(DocumentVerificationStatus documentVerificationStatus) {
        this.documentVerificationStatus = documentVerificationStatus;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public UUID getFeeObligationId() {
        return feeObligationId;
    }

    public void setFeeObligationId(UUID feeObligationId) {
        this.feeObligationId = feeObligationId;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount(BigDecimal paymentAmount) {
        this.paymentAmount = paymentAmount;
    }

    public OffsetDateTime getSubmissionTimestamp() {
        return submissionTimestamp;
    }

    public void setSubmissionTimestamp(OffsetDateTime submissionTimestamp) {
        this.submissionTimestamp = submissionTimestamp;
    }

    public String getEligibilityRuleVersion() {
        return eligibilityRuleVersion;
    }

    public void setEligibilityRuleVersion(String eligibilityRuleVersion) {
        this.eligibilityRuleVersion = eligibilityRuleVersion;
    }

    public String getEligibilityResult() {
        return eligibilityResult;
    }

    public void setEligibilityResult(String eligibilityResult) {
        this.eligibilityResult = eligibilityResult;
    }

    public String getEligibilityEvaluationDetail() {
        return eligibilityEvaluationDetail;
    }

    public void setEligibilityEvaluationDetail(String eligibilityEvaluationDetail) {
        this.eligibilityEvaluationDetail = eligibilityEvaluationDetail;
    }

    public boolean isEligibilityOverridden() {
        return eligibilityOverridden;
    }

    public void setEligibilityOverridden(boolean eligibilityOverridden) {
        this.eligibilityOverridden = eligibilityOverridden;
    }

    public String getEligibilityOverrideReason() {
        return eligibilityOverrideReason;
    }

    public void setEligibilityOverrideReason(String eligibilityOverrideReason) {
        this.eligibilityOverrideReason = eligibilityOverrideReason;
    }

    public UUID getEligibilityOverrideBy() {
        return eligibilityOverrideBy;
    }

    public void setEligibilityOverrideBy(UUID eligibilityOverrideBy) {
        this.eligibilityOverrideBy = eligibilityOverrideBy;
    }

    public boolean isLateSubmissionExceptionGranted() {
        return lateSubmissionExceptionGranted;
    }

    public void setLateSubmissionExceptionGranted(boolean lateSubmissionExceptionGranted) {
        this.lateSubmissionExceptionGranted = lateSubmissionExceptionGranted;
    }

    public UUID getLateSubmissionExceptionBy() {
        return lateSubmissionExceptionBy;
    }

    public void setLateSubmissionExceptionBy(UUID lateSubmissionExceptionBy) {
        this.lateSubmissionExceptionBy = lateSubmissionExceptionBy;
    }

    public boolean isDeclarationAccepted() {
        return declarationAccepted;
    }

    public void setDeclarationAccepted(boolean declarationAccepted) {
        this.declarationAccepted = declarationAccepted;
    }

    public OffsetDateTime getDeclarationTimestamp() {
        return declarationTimestamp;
    }

    public void setDeclarationTimestamp(OffsetDateTime declarationTimestamp) {
        this.declarationTimestamp = declarationTimestamp;
    }

    public boolean isPrivacyConsentAccepted() {
        return privacyConsentAccepted;
    }

    public void setPrivacyConsentAccepted(boolean privacyConsentAccepted) {
        this.privacyConsentAccepted = privacyConsentAccepted;
    }

    public String getApplicantData() {
        return applicantData;
    }

    public void setApplicantData(String applicantData) {
        this.applicantData = applicantData;
    }

    public String getReviewCommentsSummary() {
        return reviewCommentsSummary;
    }

    public void setReviewCommentsSummary(String reviewCommentsSummary) {
        this.reviewCommentsSummary = reviewCommentsSummary;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public int getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(int rowVersion) {
        this.rowVersion = rowVersion;
    }
}
