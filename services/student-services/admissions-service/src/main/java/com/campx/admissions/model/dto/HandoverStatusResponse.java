package com.campx.admissions.model.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class HandoverStatusResponse {

    private UUID applicationId;
    private UUID offerId;
    private UUID createdStudentId;
    private String enrolmentStatus;
    private int retryCount;
    private String lastError;
    private OffsetDateTime handoverCompletedAt;

    public HandoverStatusResponse() {
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
}
