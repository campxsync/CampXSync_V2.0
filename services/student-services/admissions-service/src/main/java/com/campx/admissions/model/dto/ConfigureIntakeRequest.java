package com.campx.admissions.model.dto;

import com.campx.admissions.model.FeeTiming;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class ConfigureIntakeRequest {

    private UUID collegeId;
    private UUID programId;
    private UUID academicYearId;
    private String intakeName;
    private String intakeCode;
    private OffsetDateTime applicationWindowStart;
    private OffsetDateTime applicationWindowEnd;
    private int capacity;
    private boolean feeRequired = false;
    private BigDecimal feeAmount = BigDecimal.ZERO;
    private String currency = "INR";
    private FeeTiming feeTiming = FeeTiming.AT_SUBMISSION;
    private String scoringRubric = "{}";

    public ConfigureIntakeRequest() {
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
}
