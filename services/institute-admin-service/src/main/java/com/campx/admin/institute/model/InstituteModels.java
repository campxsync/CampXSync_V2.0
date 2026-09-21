package com.campx.admin.institute.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Domain models for ADM-01: Institute Admin Service (Platform Tier).
 */
public final class InstituteModels {

    public static class Institute {
        private String id;
        private String instituteCode;
        private String legalName;
        private String displayName;
        private String timezone;
        private String locale;
        private String defaultCurrency;
        private String status = "ACTIVE";
        private int version = 1;
        private long createdAt;
        private long updatedAt;

        public Institute() {
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = this.createdAt;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getInstituteCode() { return instituteCode; }
        public void setInstituteCode(String instituteCode) { this.instituteCode = instituteCode; }
        public String getLegalName() { return legalName; }
        public void setLegalName(String legalName) { this.legalName = legalName; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getTimezone() { return timezone; }
        public void setTimezone(String timezone) { this.timezone = timezone; }
        public String getLocale() { return locale; }
        public void setLocale(String locale) { this.locale = locale; }
        public String getDefaultCurrency() { return defaultCurrency; }
        public void setDefaultCurrency(String defaultCurrency) { this.defaultCurrency = defaultCurrency; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class College {
        private String id;
        private String collegeCode;
        private String name;
        private String instituteId;
        private List<String> campusIds = new ArrayList<>();
        private String status = "ACTIVE";
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCollegeCode() { return collegeCode; }
        public void setCollegeCode(String collegeCode) { this.collegeCode = collegeCode; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getInstituteId() { return instituteId; }
        public void setInstituteId(String instituteId) { this.instituteId = instituteId; }
        public List<String> getCampusIds() { return campusIds; }
        public void setCampusIds(List<String> campusIds) { this.campusIds = campusIds; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getCreatedAt() { return createdAt; }
    }

    public static class TenantProvisioning {
        private String provisioningId;
        private String tenantId;
        private String targetScope;
        private String planId;
        private String provisioningStatus = "REQUESTED"; // REQUESTED -> VALIDATED -> PROVISIONING -> COMPLETED
        private String requestedBy;
        private String idempotencyKey;
        private long requestedAt = System.currentTimeMillis();
        private long completedAt;

        public String getProvisioningId() { return provisioningId; }
        public void setProvisioningId(String provisioningId) { this.provisioningId = provisioningId; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getTargetScope() { return targetScope; }
        public void setTargetScope(String targetScope) { this.targetScope = targetScope; }
        public String getPlanId() { return planId; }
        public void setPlanId(String planId) { this.planId = planId; }
        public String getProvisioningStatus() { return provisioningStatus; }
        public void setProvisioningStatus(String provisioningStatus) { this.provisioningStatus = provisioningStatus; }
        public String getRequestedBy() { return requestedBy; }
        public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
        public long getRequestedAt() { return requestedAt; }
        public long getCompletedAt() { return completedAt; }
        public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }
    }

    public static class GlobalSetting {
        private String key;
        private String value;
        private String dataType;
        private String scope;
        private boolean isSecret;
        private int version = 1;
        private long effectiveFrom;
        private long effectiveTo;

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
        public boolean isSecret() { return isSecret; }
        public void setSecret(boolean secret) { isSecret = secret; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public long getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(long effectiveFrom) { this.effectiveFrom = effectiveFrom; }
        public long getEffectiveTo() { return effectiveTo; }
        public void setEffectiveTo(long effectiveTo) { this.effectiveTo = effectiveTo; }
    }

    public static class CommercialPlan {
        private String planCode;
        private String name;
        private String billingCycle; // MONTHLY, ANNUALLY
        private double price;
        private String currency;
        private List<String> entitlements = new ArrayList<>();
        private boolean published = true;
        private int version = 1;

        public String getPlanCode() { return planCode; }
        public void setPlanCode(String planCode) { this.planCode = planCode; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getBillingCycle() { return billingCycle; }
        public void setBillingCycle(String billingCycle) { this.billingCycle = billingCycle; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public List<String> getEntitlements() { return entitlements; }
        public void setEntitlements(List<String> entitlements) { this.entitlements = entitlements; }
        public boolean isPublished() { return published; }
        public void setPublished(boolean published) { this.published = published; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
    }
}
