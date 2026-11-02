package com.campx.admissions.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AdmissionHandoverEntity {

    private UUID tenantId;
    private UUID id;
    private UUID applicationId;
    private UUID offerId;
    private String idempotencyKey;
    private UUID createdStudentId;
    private String enrolmentStatus = "PENDING"; // PENDING, STUDENT_CREATED, ENROLMENT_COMPLETED, FAILED
    private int retryCount = 0;
    private String lastError;
    private OffsetDateTime handoverCompletedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private int rowVersion = 1;

    public AdmissionHandoverEntity() {
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

    public UUID getOfferId() {
        return offerId;
    }

    public void setOfferId(UUID offerId) {
        this.offerId = offerId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getCreatedStudentId() {
        return createdStudentId;
    }

    public void setCreatedStudentId(UUID createdStudentId) {
        this.createdStudentId = createdStudentId;
    }

    public String getEnrolmentStatus() {
        return enrolmentStatus;
    }

    public void setEnrolmentStatus(String enrolmentStatus) {
        this.enrolmentStatus = enrolmentStatus;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public OffsetDateTime getHandoverCompletedAt() {
        return handoverCompletedAt;
    }

    public void setHandoverCompletedAt(OffsetDateTime handoverCompletedAt) {
        this.handoverCompletedAt = handoverCompletedAt;
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

    public int getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(int rowVersion) {
        this.rowVersion = rowVersion;
    }
}
