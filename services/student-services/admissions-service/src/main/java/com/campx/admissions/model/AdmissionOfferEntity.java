package com.campx.admissions.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AdmissionOfferEntity {

    private UUID tenantId;
    private UUID id;
    private UUID applicationId;
    private UUID decisionId;
    private String offerCode;
    private String offerTermsVersion = "1.0.0";
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private OfferStatus status = OfferStatus.ISSUED;
    private OffsetDateTime acceptedAt;
    private OffsetDateTime declinedAt;
    private String declineReason;
    private boolean extensionGranted = false;
    private UUID extensionGrantedBy;
    private OffsetDateTime extendedValidTo;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private int rowVersion = 1;

    public AdmissionOfferEntity() {
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

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public UUID getDecisionId() {
        return decisionId;
    }

    public void setDecisionId(UUID decisionId) {
        this.decisionId = decisionId;
    }

    public String getOfferCode() {
        return offerCode;
    }

    public void setOfferCode(String offerCode) {
        this.offerCode = offerCode;
    }

    public String getOfferTermsVersion() {
        return offerTermsVersion;
    }

    public void setOfferTermsVersion(String offerTermsVersion) {
        this.offerTermsVersion = offerTermsVersion;
    }

    public OffsetDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(OffsetDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public OffsetDateTime getValidTo() {
        return validTo;
    }

    public void setValidTo(OffsetDateTime validTo) {
        this.validTo = validTo;
    }

    public OfferStatus getStatus() {
        return status;
    }

    public void setStatus(OfferStatus status) {
        this.status = status;
    }

    public OffsetDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(OffsetDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public OffsetDateTime getDeclinedAt() {
        return declinedAt;
    }

    public void setDeclinedAt(OffsetDateTime declinedAt) {
        this.declinedAt = declinedAt;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public void setDeclineReason(String declineReason) {
        this.declineReason = declineReason;
    }

    public boolean isExtensionGranted() {
        return extensionGranted;
    }

    public void setExtensionGranted(boolean extensionGranted) {
        this.extensionGranted = extensionGranted;
    }

    public UUID getExtensionGrantedBy() {
        return extensionGrantedBy;
    }

    public void setExtensionGrantedBy(UUID extensionGrantedBy) {
        this.extensionGrantedBy = extensionGrantedBy;
    }

    public OffsetDateTime getExtendedValidTo() {
        return extendedValidTo;
    }

    public void setExtendedValidTo(OffsetDateTime extendedValidTo) {
        this.extendedValidTo = extendedValidTo;
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

    public int getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(int rowVersion) {
        this.rowVersion = rowVersion;
    }

    public boolean isExpired(OffsetDateTime now) {
        if (status == OfferStatus.EXPIRED)
            return true;
        if (status != OfferStatus.ISSUED)
            return false;
        OffsetDateTime effectiveExpiry = extendedValidTo != null ? extendedValidTo : validTo;
        return effectiveExpiry != null && now.isAfter(effectiveExpiry);
    }
}
