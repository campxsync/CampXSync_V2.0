package com.campx.admissions.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class EligibilityRuleEntity {

    private UUID tenantId;
    private UUID id;
    private UUID intakeId;
    private String version = "1.0.0";
    private BigDecimal minQualificationGrade = BigDecimal.ZERO;
    private int minAge = 16;
    private Integer maxAge;
    private String requiredPrerequisites = "[]";
    private String ruleDefinitions = "{}";
    private boolean active = true;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime effectiveTo;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private int rowVersion = 1;

    public EligibilityRuleEntity() {
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

    public UUID getIntakeId() {
        return intakeId;
    }

    public void setIntakeId(UUID intakeId) {
        this.intakeId = intakeId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public BigDecimal getMinQualificationGrade() {
        return minQualificationGrade;
    }

    public void setMinQualificationGrade(BigDecimal minQualificationGrade) {
        this.minQualificationGrade = minQualificationGrade;
    }

    public int getMinAge() {
        return minAge;
    }

    public void setMinAge(int minAge) {
        this.minAge = minAge;
    }

    public Integer getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(Integer maxAge) {
        this.maxAge = maxAge;
    }

    public String getRequiredPrerequisites() {
        return requiredPrerequisites;
    }

    public void setRequiredPrerequisites(String requiredPrerequisites) {
        this.requiredPrerequisites = requiredPrerequisites;
    }

    public String getRuleDefinitions() {
        return ruleDefinitions;
    }

    public void setRuleDefinitions(String ruleDefinitions) {
        this.ruleDefinitions = ruleDefinitions;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public OffsetDateTime getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(OffsetDateTime effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public OffsetDateTime getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(OffsetDateTime effectiveTo) {
        this.effectiveTo = effectiveTo;
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
}
