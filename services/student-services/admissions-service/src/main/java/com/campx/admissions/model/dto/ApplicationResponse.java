package com.campx.admissions.model.dto;

import com.campx.admissions.model.ApplicationStatus;
import com.campx.admissions.model.DocumentVerificationStatus;
import com.campx.admissions.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class ApplicationResponse {

    private UUID id;
    private UUID tenantId;
    private String applicationNumber;
    private UUID intakeId;
    private UUID applicantPersonId;
    private ApplicationStatus status;
    private DocumentVerificationStatus documentVerificationStatus;
    private PaymentStatus paymentStatus;
    private BigDecimal paymentAmount;
    private OffsetDateTime submissionTimestamp;
    private String eligibilityResult;
    private boolean declarationAccepted;
    private String applicantData;
    private String timelineStatus;
    private int rowVersion;
    private OffsetDateTime createdAt;

    public ApplicationResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
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

    public String getEligibilityResult() {
        return eligibilityResult;
    }

    public void setEligibilityResult(String eligibilityResult) {
        this.eligibilityResult = eligibilityResult;
    }

    public boolean isDeclarationAccepted() {
        return declarationAccepted;
    }

    public void setDeclarationAccepted(boolean declarationAccepted) {
        this.declarationAccepted = declarationAccepted;
    }

    public String getApplicantData() {
        return applicantData;
    }

    public void setApplicantData(String applicantData) {
        this.applicantData = applicantData;
    }

    public String getTimelineStatus() {
        return timelineStatus;
    }

    public void setTimelineStatus(String timelineStatus) {
        this.timelineStatus = timelineStatus;
    }

    public int getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(int rowVersion) {
        this.rowVersion = rowVersion;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
