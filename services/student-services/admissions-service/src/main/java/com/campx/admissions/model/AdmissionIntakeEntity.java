package com.campx.admissions.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class AdmissionIntakeEntity {

    private UUID tenantId;
    private UUID id;
    private UUID collegeId;
    private UUID programId;
    private UUID academicYearId;
    private String intakeName;
    private String intakeCode;
    private OffsetDateTime applicationWindowStart;
    private OffsetDateTime applicationWindowEnd;
    private int capacity;
    private int admittedCount;
    private boolean active = true;
    private boolean feeRequired = false;
    private BigDecimal feeAmount = BigDecimal.ZERO;
    private String currency = "INR";
    private FeeTiming feeTiming = FeeTiming.AT_SUBMISSION;
    private String scoringRubric = "{}";
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    private OffsetDateTime deletedAt;
    private int rowVersion = 1;

    public AdmissionIntakeEntity() {
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

    public UUID getCollegeId() {
        return collegeId;
    }

    public void setCollegeId(UUID collegeId) {
        this.collegeId = collegeId;
    }

    public UUID getProgramId() {
        return programId;
    }

    public void setProgramId(UUID programId) {
        this.programId = programId;
    }

    public UUID getAcademicYearId() {
        return academicYearId;
    }

    public void setAcademicYearId(UUID academicYearId) {
        this.academicYearId = academicYearId;
    }

    public String getIntakeName() {
        return intakeName;
    }

    public void setIntakeName(String intakeName) {
        this.intakeName = intakeName;
    }

    public String getIntakeCode() {
        return intakeCode;
    }

    public void setIntakeCode(String intakeCode) {
        this.intakeCode = intakeCode;
    }

    public OffsetDateTime getApplicationWindowStart() {
        return applicationWindowStart;
    }

    public void setApplicationWindowStart(OffsetDateTime applicationWindowStart) {
        this.applicationWindowStart = applicationWindowStart;
    }

    public OffsetDateTime getApplicationWindowEnd() {
        return applicationWindowEnd;
    }

    public void setApplicationWindowEnd(OffsetDateTime applicationWindowEnd) {
        this.applicationWindowEnd = applicationWindowEnd;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getAdmittedCount() {
        return admittedCount;
    }

    public void setAdmittedCount(int admittedCount) {
        this.admittedCount = admittedCount;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isFeeRequired() {
        return feeRequired;
    }

    public void setFeeRequired(boolean feeRequired) {
        this.feeRequired = feeRequired;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public void setFeeAmount(BigDecimal feeAmount) {
        this.feeAmount = feeAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public FeeTiming getFeeTiming() {
        return feeTiming;
    }

    public void setFeeTiming(FeeTiming feeTiming) {
        this.feeTiming = feeTiming;
    }

    public String getScoringRubric() {
        return scoringRubric;
    }

    public void setScoringRubric(String scoringRubric) {
        this.scoringRubric = scoringRubric;
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

    public boolean isWindowOpen(OffsetDateTime instant) {
        if (!active || deletedAt != null)
            return false;
        if (applicationWindowStart == null || applicationWindowEnd == null)
            return false;
        return !instant.isBefore(applicationWindowStart) && !instant.isAfter(applicationWindowEnd);
    }

    public boolean hasAvailableCapacity() {
        return admittedCount < capacity;
    }
}
