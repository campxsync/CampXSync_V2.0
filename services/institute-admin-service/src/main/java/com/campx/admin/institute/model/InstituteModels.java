package com.campx.admin.institute.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Domain entity models and transactional DTOs for ADM-01: Institute Admin Service (Platform Tier).
 * <p>
 * Encompasses core institute hierarchy, tenant provisioning workflows, global settings, commercial billing,
 * RBAC/IAM identity bindings, transactional outbox/inbox reliability records, feature flag toggles,
 * operational telemetry alerts, and data governance policies.
 */
public final class InstituteModels {

    /**
     * Top-level institutional tenant entity representing a multi-campus educational institution.
     */
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

        /**
         * Initializes a default institute instance setting creation and update timestamps.
         */
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

    /**
     * Constituent college entity operating under a parent {@link Institute}.
     */
    public static class College {
        private String id;
        private String collegeCode;
        private String name;
        private String instituteId;
        private String legalName;
        private List<String> campusIds = new ArrayList<>();
        private String status = "ACTIVE";
        private int version = 1;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCollegeCode() { return collegeCode; }
        public void setCollegeCode(String collegeCode) { this.collegeCode = collegeCode; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getLegalName() { return legalName; }
        public void setLegalName(String legalName) { this.legalName = legalName; }
        public String getInstituteId() { return instituteId; }
        public void setInstituteId(String instituteId) { this.instituteId = instituteId; }
        public List<String> getCampusIds() { return campusIds; }
        public void setCampusIds(List<String> campusIds) { this.campusIds = campusIds; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    }

    /**
     * Tenant provisioning orchestration model tracking state from REQUESTED to COMPLETED.
     */
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

    /**
     * Global configuration setting with scope, data type, and encryption/secret flags.
     */
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

    /**
     * Commercial tier and pricing plan defining billing cycles, rates, and entitlement bundles.
     */
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

    // =========================================================================
    // Phase 1: RBAC & Identity Models (User Story Lines 17–21)
    // =========================================================================

    /**
     * ADM01_admin_users — Platform administrative user context.
     * Tracks status and risk state WITHOUT storing any credentials (CSV Line 17).
     */
    public static class AdminUser {
        private String id;
        private String userId;          // IAM identity reference (e.g., Keycloak sub)
        private String displayName;
        private String email;
        private String status = "ACTIVE"; // ACTIVE, SUSPENDED, DEACTIVATED
        private String riskState = "NORMAL"; // NORMAL, ELEVATED, HIGH
        private long lastLoginAt;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getRiskState() { return riskState; }
        public void setRiskState(String riskState) { this.riskState = riskState; }
        public long getLastLoginAt() { return lastLoginAt; }
        public void setLastLoginAt(long lastLoginAt) { this.lastLoginAt = lastLoginAt; }
        public long getCreatedAt() { return createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    }

    /**
     * ADM01_roles — Platform role definitions with permission bundles.
     * Protected system roles cannot be deleted (CSV Line 18).
     */
    public static class PlatformRole {
        private String id;
        private String roleCode;
        private String name;
        private String scope;           // PLATFORM, TENANT, COLLEGE
        private List<String> permissionCodes = new ArrayList<>();
        private boolean protectedSystemRole = false;
        private int version = 1;
        private String status = "ACTIVE"; // ACTIVE, DEPRECATED
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getRoleCode() { return roleCode; }
        public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
        public List<String> getPermissionCodes() { return permissionCodes; }
        public void setPermissionCodes(List<String> permissionCodes) { this.permissionCodes = permissionCodes; }
        public boolean isProtectedSystemRole() { return protectedSystemRole; }
        public void setProtectedSystemRole(boolean protectedSystemRole) { this.protectedSystemRole = protectedSystemRole; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getCreatedAt() { return createdAt; }
    }

    /**
     * ADM01_permissions — Fine-grained permission catalog.
     * Codes become immutable once referenced by a role binding (CSV Line 19).
     */
    public static class Permission {
        private String id;
        private String permissionCode;
        private String resource;
        private String action;
        private String description;
        private int usedByRoles = 0;    // Counter incremented when assigned to roles

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getPermissionCode() { return permissionCode; }
        public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }
        public String getResource() { return resource; }
        public void setResource(String resource) { this.resource = resource; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public int getUsedByRoles() { return usedByRoles; }
        public void setUsedByRoles(int usedByRoles) { this.usedByRoles = usedByRoles; }
    }

    /**
     * ADM01_role_bindings — Scoped, time-bound role grants.
     * Enforces (tenantId, principalId, roleId, scope) uniqueness and privilege escalation detection (CSV Line 20).
     */
    public static class RoleBinding {
        private String id;
        private String tenantId;
        private String principalId;
        private String roleId;
        private String scope;           // PLATFORM, INSTITUTE, COLLEGE
        private long effectiveFrom;
        private long effectiveTo;       // 0 = indefinite
        private String grantedBy;
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getPrincipalId() { return principalId; }
        public void setPrincipalId(String principalId) { this.principalId = principalId; }
        public String getRoleId() { return roleId; }
        public void setRoleId(String roleId) { this.roleId = roleId; }
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
        public long getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(long effectiveFrom) { this.effectiveFrom = effectiveFrom; }
        public long getEffectiveTo() { return effectiveTo; }
        public void setEffectiveTo(long effectiveTo) { this.effectiveTo = effectiveTo; }
        public String getGrantedBy() { return grantedBy; }
        public void setGrantedBy(String grantedBy) { this.grantedBy = grantedBy; }
        public long getCreatedAt() { return createdAt; }
    }

    /**
     * ADM01_access_reviews — Periodic privileged access certification.
     * Prevents self-certification where policy requires separation of duties (CSV Line 21).
     */
    public static class AccessReview {
        private String id;
        private String reviewId;
        private String tenantId;
        private String principalId;     // The user whose access is being reviewed
        private String roleBindingId;
        private String reviewerId;      // The reviewer (must != principalId for sep-of-duties)
        private String decision;        // CERTIFY, REVOKE, PENDING
        private String status = "OPEN"; // OPEN, COMPLETED, OVERDUE
        private long dueAt;
        private long completedAt;
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getReviewId() { return reviewId; }
        public void setReviewId(String reviewId) { this.reviewId = reviewId; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getPrincipalId() { return principalId; }
        public void setPrincipalId(String principalId) { this.principalId = principalId; }
        public String getRoleBindingId() { return roleBindingId; }
        public void setRoleBindingId(String roleBindingId) { this.roleBindingId = roleBindingId; }
        public String getReviewerId() { return reviewerId; }
        public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }
        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getDueAt() { return dueAt; }
        public void setDueAt(long dueAt) { this.dueAt = dueAt; }
        public long getCompletedAt() { return completedAt; }
        public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }
        public long getCreatedAt() { return createdAt; }
    }

    // =========================================================================
    // Phase 2: Transactional Reliability Layer Models (User Story Lines 40–43)
    // =========================================================================

    /**
     * ADM01_idempotency_records — Command deduplication and request hash verification (CSV Line 40).
     */
    public static class IdempotencyRecord {
        private String id;
        private String idempotencyKey;
        private String operation;
        private String requestHash;
        private String responseRef;
        private String status = "PROCESSING"; // PROCESSING, COMPLETED, FAILED
        private long expiresAt;
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }
        public String getRequestHash() { return requestHash; }
        public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
        public String getResponseRef() { return responseRef; }
        public void setResponseRef(String responseRef) { this.responseRef = responseRef; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getExpiresAt() { return expiresAt; }
        public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    /**
     * ADM01_outbox_events — Transactional outbox for guaranteed event delivery (CSV Line 41).
     */
    public static class OutboxEvent {
        private String id;
        private String eventType;
        private String aggregateId;
        private String tenantId;
        private String payload;
        private String status = "PENDING"; // PENDING, PUBLISHED, FAILED
        private int retryCount = 0;
        private long publishedAt;
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getAggregateId() { return aggregateId; }
        public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
        public long getPublishedAt() { return publishedAt; }
        public void setPublishedAt(long publishedAt) { this.publishedAt = publishedAt; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    /**
     * ADM01_inbox_events — Inbound event deduplication for idempotent consumption (CSV Line 42).
     */
    public static class InboxEvent {
        private String id;
        private String eventId;
        private String sourceService;
        private String consumerGroup;
        private String status = "PROCESSED"; // PROCESSED, FAILED
        private long processedAt = System.currentTimeMillis();
        private String error;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }
        public String getSourceService() { return sourceService; }
        public void setSourceService(String sourceService) { this.sourceService = sourceService; }
        public String getConsumerGroup() { return consumerGroup; }
        public void setConsumerGroup(String consumerGroup) { this.consumerGroup = consumerGroup; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getProcessedAt() { return processedAt; }
        public void setProcessedAt(long processedAt) { this.processedAt = processedAt; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    /**
     * ADM01_dead_letter_events — Failure observability and manual/controlled replay (CSV Line 43).
     */
    public static class DeadLetterEvent {
        private String id;
        private String originalEventId;
        private String eventType;
        private String failureCode;
        private int retryCount;
        private String payload;
        private String disposition = "OPEN"; // OPEN, REPLAYED, DISCARDED
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getOriginalEventId() { return originalEventId; }
        public void setOriginalEventId(String originalEventId) { this.originalEventId = originalEventId; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getFailureCode() { return failureCode; }
        public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }
        public String getDisposition() { return disposition; }
        public void setDisposition(String disposition) { this.disposition = disposition; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    // =========================================================================
    // Phase 3: Configuration & Policy Engine Models (User Story Lines 13–15)
    // =========================================================================

    /**
     * ADM01_feature_flags — Platform feature flag definitions and overrides (CSV Line 13).
     */
    public static class FeatureFlag {
        private String id;
        private String flagKey;
        private String defaultValue;
        private String description;
        private List<String> targetingRules = new ArrayList<>();
        private List<TenantOverride> tenantOverrides = new ArrayList<>();
        private String status = "ACTIVE"; // ACTIVE, DISABLED
        private boolean globallyLocked = false;
        private long createdAt = System.currentTimeMillis();

        public static class TenantOverride {
            private String tenantId;
            private String overrideValue;
            private String reason;
            private long effectiveFrom;
            private long effectiveTo;

            public String getTenantId() { return tenantId; }
            public void setTenantId(String tenantId) { this.tenantId = tenantId; }
            public String getOverrideValue() { return overrideValue; }
            public void setOverrideValue(String overrideValue) { this.overrideValue = overrideValue; }
            public String getReason() { return reason; }
            public void setReason(String reason) { this.reason = reason; }
            public long getEffectiveFrom() { return effectiveFrom; }
            public void setEffectiveFrom(long effectiveFrom) { this.effectiveFrom = effectiveFrom; }
            public long getEffectiveTo() { return effectiveTo; }
            public void setEffectiveTo(long effectiveTo) { this.effectiveTo = effectiveTo; }
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getFlagKey() { return flagKey; }
        public void setFlagKey(String flagKey) { this.flagKey = flagKey; }
        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getTargetingRules() { return targetingRules; }
        public void setTargetingRules(List<String> targetingRules) { this.targetingRules = targetingRules; }
        public List<TenantOverride> getTenantOverrides() { return tenantOverrides; }
        public void setTenantOverrides(List<TenantOverride> tenantOverrides) { this.tenantOverrides = tenantOverrides; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public boolean isGloballyLocked() { return globallyLocked; }
        public void setGloballyLocked(boolean globallyLocked) { this.globallyLocked = globallyLocked; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    /**
     * ADM01_global_policies — Immutable governance policies with approval tracking (CSV Line 14).
     */
    public static class GlobalPolicy {
        private String id;
        private String policyCode;
        private String policyType;
        private List<String> rules = new ArrayList<>();
        private int version = 1;
        private String status = "DRAFT"; // DRAFT -> APPROVED -> PUBLISHED
        private String approvedBy;
        private long publishedAt;
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getPolicyCode() { return policyCode; }
        public void setPolicyCode(String policyCode) { this.policyCode = policyCode; }
        public String getPolicyType() { return policyType; }
        public void setPolicyType(String policyType) { this.policyType = policyType; }
        public List<String> getRules() { return rules; }
        public void setRules(List<String> rules) { this.rules = rules; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getApprovedBy() { return approvedBy; }
        public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
        public long getPublishedAt() { return publishedAt; }
        public void setPublishedAt(long publishedAt) { this.publishedAt = publishedAt; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    /**
     * ADM01_configuration_versions — Snapshot and rollback versioning with checksums (CSV Line 15).
     */
    public static class ConfigurationVersion {
        private String id;
        private String scope;           // GLOBAL, TENANT
        private int version;
        private String snapshotJson;
        private String checksum;
        private String status = "ACTIVE"; // ACTIVE, SUPERSEDED
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public String getSnapshotJson() { return snapshotJson; }
        public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
        public String getChecksum() { return checksum; }
        public void setChecksum(String checksum) { this.checksum = checksum; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    // =========================================================================
    // Phase 4: Commercial & Billing Models (User Story Lines 23–27)
    // =========================================================================

    /**
     * ADM01_subscription_plans — Commercial subscription plans defining pricing and billing cycles.
     */
    public static class SubscriptionPlan {
        private String id;
        private String planCode;
        private String name;
        private String billingCycle = "MONTHLY"; // MONTHLY, QUARTERLY, ANNUALLY
        private double price = 0.0;
        private String currencyCode = "INR";
        private boolean published = true;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private int rowVersion = 1;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getPlanCode() { return planCode; }
        public void setPlanCode(String planCode) { this.planCode = planCode; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getBillingCycle() { return billingCycle; }
        public void setBillingCycle(String billingCycle) { this.billingCycle = billingCycle; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
        public boolean isPublished() { return published; }
        public void setPublished(boolean published) { this.published = published; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
    }

    /**
     * ADM01_plan_entitlements — Entitlements and numeric limits for commercial subscription plans (CSV Line 23).
     */
    public static class PlanEntitlement {
        private String id;
        private String planId;
        private String entitlementKey;
        private Long limitValue;
        private boolean enabled = true;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private int rowVersion = 1;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getPlanId() { return planId; }
        public void setPlanId(String planId) { this.planId = planId; }
        public String getEntitlementKey() { return entitlementKey; }
        public void setEntitlementKey(String entitlementKey) { this.entitlementKey = entitlementKey; }
        public Long getLimitValue() { return limitValue; }
        public void setLimitValue(Long limitValue) { this.limitValue = limitValue; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
    }

    /**
     * ADM01_subscriptions — Tenant subscriptions with entitlement snapshots (CSV Line 24).
     */
    public static class Subscription {
        private String id;
        private String tenantId;
        private String planId;
        private String status = "ACTIVE"; // TRIAL, ACTIVE, PAST_DUE, SUSPENDED, CANCELLED
        private int seatCount = 50;
        private List<String> entitlementSnapshot = new ArrayList<>();
        private long startDate = System.currentTimeMillis();
        private long endDate = System.currentTimeMillis() + 31536000000L; // 1 year
        private boolean autoRenew = true;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private int rowVersion = 1;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getPlanId() { return planId; }
        public void setPlanId(String planId) { this.planId = planId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getSeatCount() { return seatCount; }
        public void setSeatCount(int seatCount) { this.seatCount = seatCount; }
        public List<String> getEntitlementSnapshot() { return entitlementSnapshot; }
        public void setEntitlementSnapshot(List<String> entitlementSnapshot) { this.entitlementSnapshot = entitlementSnapshot; }
        public long getStartDate() { return startDate; }
        public void setStartDate(long startDate) { this.startDate = startDate; }
        public long getEndDate() { return endDate; }
        public void setEndDate(long endDate) { this.endDate = endDate; }
        public boolean isAutoRenew() { return autoRenew; }
        public void setAutoRenew(boolean autoRenew) { this.autoRenew = autoRenew; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
    }

    /**
     * ADM01_invoices — Issued invoices with tax computation and immutable states (CSV Line 25).
     */
    public static class Invoice {
        private String id;
        private String invoiceNo;
        private String subscriptionId;
        private String tenantId;
        private String billingPeriod;
        private double subtotal;
        private double tax;
        private double total;
        private String status = "DRAFT"; // DRAFT, ISSUED, PAID, VOID
        private long issuedAt;
        private long paidAt;
        private String currencyCode = "INR";
        private long issuedOn;
        private long dueDate;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private int rowVersion = 1;
        private List<InvoiceLine> lines = new ArrayList<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getInvoiceNo() { return invoiceNo; }
        public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
        public String getSubscriptionId() { return subscriptionId; }
        public void setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getBillingPeriod() { return billingPeriod; }
        public void setBillingPeriod(String billingPeriod) { this.billingPeriod = billingPeriod; }
        public double getSubtotal() { return subtotal; }
        public void setSubtotal(double subtotal) { this.subtotal = subtotal; }
        public double getTax() { return tax; }
        public void setTax(double tax) { this.tax = tax; }
        public double getTotal() { return total; }
        public void setTotal(double total) { this.total = total; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getIssuedAt() { return issuedAt; }
        public void setIssuedAt(long issuedAt) { this.issuedAt = issuedAt; }
        public long getPaidAt() { return paidAt; }
        public void setPaidAt(long paidAt) { this.paidAt = paidAt; }
        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
        public long getIssuedOn() { return issuedOn; }
        public void setIssuedOn(long issuedOn) { this.issuedOn = issuedOn; }
        public long getDueDate() { return dueDate; }
        public void setDueDate(long dueDate) { this.dueDate = dueDate; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
        public List<InvoiceLine> getLines() { return lines; }
        public void setLines(List<InvoiceLine> lines) { this.lines = lines; }
    }

    /**
     * ADM01_invoice_lines — Itemized breakdown lines for issued invoices (CSV Line 25).
     */
    public static class InvoiceLine {
        private String id;
        private String tenantId;
        private String invoiceId;
        private String description;
        private double quantity = 1.0;
        private double unitPrice = 0.0;
        private double amount = 0.0;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private int rowVersion = 1;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getInvoiceId() { return invoiceId; }
        public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public double getQuantity() { return quantity; }
        public void setQuantity(double quantity) { this.quantity = quantity; }
        public double getUnitPrice() { return unitPrice; }
        public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
    }

    /**
     * ADM01_billing_gateway_transactions — Reconciled payment transactions (CSV Line 26).
     */
    public static class BillingGatewayTransaction {
        private String id;
        private String gatewayTransactionId;
        private String invoiceId;
        private String tenantId;
        private double amount;
        private String gateway = "RAZORPAY";
        private String currency = "INR";
        private String rawReference;
        private String status = "PENDING"; // PENDING, SUCCESS, FAILED
        private long reconciledAt;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private int rowVersion = 1;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getGatewayTransactionId() { return gatewayTransactionId; }
        public void setGatewayTransactionId(String gatewayTransactionId) { this.gatewayTransactionId = gatewayTransactionId; }
        public String getInvoiceId() { return invoiceId; }
        public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getGateway() { return gateway; }
        public void setGateway(String gateway) { this.gateway = gateway; }
        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getRawReference() { return rawReference; }
        public void setRawReference(String rawReference) { this.rawReference = rawReference; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getReconciledAt() { return reconciledAt; }
        public void setReconciledAt(long reconciledAt) { this.reconciledAt = reconciledAt; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
    }

    /**
     * ADM01_usage_metrics — Tenant consumption metrics for billing and audits (CSV Line 27).
     */
    public static class UsageMetric {
        private String id;
        private String tenantId;
        private String metricType; // ACTIVE_USERS, STORAGE_MB, API_CALLS
        private String period;     // YYYY-MM
        private long value;
        private long calculatedAt = System.currentTimeMillis();
        private String dimension;
        private String recordedOn;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getMetricType() { return metricType; }
        public void setMetricType(String metricType) { this.metricType = metricType; }
        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }
        public long getValue() { return value; }
        public void setValue(long value) { this.value = value; }
        public long getCalculatedAt() { return calculatedAt; }
        public void setCalculatedAt(long calculatedAt) { this.calculatedAt = calculatedAt; }
        public String getDimension() { return dimension; }
        public void setDimension(String dimension) { this.dimension = dimension; }
        public String getRecordedOn() { return recordedOn; }
        public void setRecordedOn(String recordedOn) { this.recordedOn = recordedOn; }
    }

    // =========================================================================
    // Phase 6: Operations & Workflows Models (User Story Lines 29, 30, 39)
    // =========================================================================

    /**
     * ADM01_platform_health — Synthetic heartbeat and health monitoring snapshot (CSV Line 29).
     */
    public static class PlatformHealth {
        private String id;
        private String component; // AUTH, GATEWAY, DATABASE, BILLING
        private String region = "ap-south-1";
        private String status = "HEALTHY"; // HEALTHY, DEGRADED, UNHEALTHY
        private long latencyMs;
        private double errorRate;
        private List<String> dependencies = new ArrayList<>();
        private long observedAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getComponent() { return component; }
        public void setComponent(String component) { this.component = component; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getLatencyMs() { return latencyMs; }
        public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
        public double getErrorRate() { return errorRate; }
        public void setErrorRate(double errorRate) { this.errorRate = errorRate; }
        public List<String> getDependencies() { return dependencies; }
        public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }
        public long getObservedAt() { return observedAt; }
        public void setObservedAt(long observedAt) { this.observedAt = observedAt; }
    }

    /**
     * ADM01_operational_alerts — System alerts with deduplication and acknowledgment lifecycle (CSV Line 30).
     */
    public static class OperationalAlert {
        private String id;
        private String alertCode;
        private String severity = "INFO"; // INFO, WARNING, ERROR, CRITICAL
        private String source;
        private String scope;
        private String message;
        private String status = "OPEN"; // OPEN, ACKNOWLEDGED, RESOLVED
        private String deduplicationKey;
        private String assignee;
        private long acknowledgedAt;
        private long resolvedAt;
        private String resolutionNotes;
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getAlertCode() { return alertCode; }
        public void setAlertCode(String alertCode) { this.alertCode = alertCode; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getDeduplicationKey() { return deduplicationKey; }
        public void setDeduplicationKey(String deduplicationKey) { this.deduplicationKey = deduplicationKey; }
        public String getAssignee() { return assignee; }
        public void setAssignee(String assignee) { this.assignee = assignee; }
        public long getAcknowledgedAt() { return acknowledgedAt; }
        public void setAcknowledgedAt(long acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
        public long getResolvedAt() { return resolvedAt; }
        public void setResolvedAt(long resolvedAt) { this.resolvedAt = resolvedAt; }
        public String getResolutionNotes() { return resolutionNotes; }
        public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    /**
     * ADM01_workflow_instances — Long-running administrative workflow coordination (CSV Line 39).
     */
    public static class WorkflowInstance {
        private String id;
        private String workflowType; // TENANT_ONBOARDING, OFFBOARDING, POLICY_ROLLOUT
        private String subject;
        private List<String> steps = new ArrayList<>();
        private String currentState = "INITIATED"; // INITIATED, RUNNING, COMPLETED, FAILED, TIMED_OUT
        private String startedBy;
        private long startedAt = System.currentTimeMillis();
        private long completedAt;
        private int timeoutMinutes = 60;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getWorkflowType() { return workflowType; }
        public void setWorkflowType(String workflowType) { this.workflowType = workflowType; }
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public List<String> getSteps() { return steps; }
        public void setSteps(List<String> steps) { this.steps = steps; }
        public String getCurrentState() { return currentState; }
        public void setCurrentState(String currentState) { this.currentState = currentState; }
        public String getStartedBy() { return startedBy; }
        public void setStartedBy(String startedBy) { this.startedBy = startedBy; }
        public long getStartedAt() { return startedAt; }
        public void setStartedAt(long startedAt) { this.startedAt = startedAt; }
        public long getCompletedAt() { return completedAt; }
        public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }
        public int getTimeoutMinutes() { return timeoutMinutes; }
        public void setTimeoutMinutes(int timeoutMinutes) { this.timeoutMinutes = timeoutMinutes; }
    }

    // =========================================================================
    // Phase 7: Data Governance & Audit Enhancement (User Story Lines 32–34)
    // =========================================================================

    /**
     * ADM01_data_retention_policies — Data retention rules with legal minimum enforcement (CSV Line 32).
     * retentionDays must be >= legalMinimumDays to prevent accidental compliance violations.
     */
    public static class DataRetentionPolicy {
        private String id;
        private String policyCode;          // e.g., "RET_STUDENT_PII", "RET_FINANCIAL_LOGS"
        private String entityType = "STUDENT_RECORD";
        private String dataClass;           // Maps to DataClassification.classCode
        private int retentionDays = 365;    // Business-defined retention window
        private String action = "ARCHIVE";  // ARCHIVE, DELETE, ANONYMIZE
        private int archiveAfterDays;       // Move to cold storage after this threshold
        private String legalHoldBehavior;   // SUSPEND_PURGE, EXTEND_RETENTION
        private int legalMinimumDays;       // Floor — retentionDays cannot go below this
        private int version = 1;
        private int rowVersion = 1;
        private String status = "ACTIVE";   // ACTIVE, SUPERSEDED
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private String createdBy;
        private String updatedBy;
        private Long deletedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getPolicyCode() { return policyCode; }
        public void setPolicyCode(String policyCode) { this.policyCode = policyCode; }
        public String getEntityType() { return entityType; }
        public void setEntityType(String entityType) { this.entityType = entityType; }
        public String getDataClass() { return dataClass; }
        public void setDataClass(String dataClass) { this.dataClass = dataClass; }
        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public int getArchiveAfterDays() { return archiveAfterDays; }
        public void setArchiveAfterDays(int archiveAfterDays) { this.archiveAfterDays = archiveAfterDays; }
        public String getLegalHoldBehavior() { return legalHoldBehavior; }
        public void setLegalHoldBehavior(String legalHoldBehavior) { this.legalHoldBehavior = legalHoldBehavior; }
        public int getLegalMinimumDays() { return legalMinimumDays; }
        public void setLegalMinimumDays(int legalMinimumDays) { this.legalMinimumDays = legalMinimumDays; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
        public Long getDeletedAt() { return deletedAt; }
        public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
    }

    /**
     * ADM01_data_classifications — Versioned, audited data classification taxonomy (CSV Line 33).
     * exportRules affects downstream ExportRequest authorization decisions.
     */
    public static class DataClassification {
        private String id;
        private String classCode;           // PUBLIC, INTERNAL, RESTRICTED, HIGHLY_RESTRICTED
        private String classificationCode;  // Maps to cfg.data_classifications.classification_code
        private String sensitivity;         // LOW, MEDIUM, HIGH, CRITICAL
        private String sensitivityLevel = "LOW"; // LOW, MEDIUM, HIGH, RESTRICTED
        private boolean encryptionRequired = false;
        private String handlingRules;       // Encryption requirements, access logging, etc.
        private String exportRules;         // ALLOWED, APPROVAL_REQUIRED, PROHIBITED
        private int version = 1;
        private int rowVersion = 1;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private String createdBy;
        private String updatedBy;
        private Long deletedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getClassCode() { return classCode != null ? classCode : classificationCode; }
        public void setClassCode(String classCode) { this.classCode = classCode; this.classificationCode = classCode; }
        public String getClassificationCode() { return classificationCode != null ? classificationCode : classCode; }
        public void setClassificationCode(String classificationCode) { this.classificationCode = classificationCode; this.classCode = classificationCode; }
        public String getSensitivity() { return sensitivity != null ? sensitivity : sensitivityLevel; }
        public void setSensitivity(String sensitivity) { this.sensitivity = sensitivity; this.sensitivityLevel = sensitivity; }
        public String getSensitivityLevel() { return sensitivityLevel != null ? sensitivityLevel : sensitivity; }
        public void setSensitivityLevel(String sensitivityLevel) { this.sensitivityLevel = sensitivityLevel; this.sensitivity = sensitivityLevel; }
        public boolean isEncryptionRequired() { return encryptionRequired; }
        public void setEncryptionRequired(boolean encryptionRequired) { this.encryptionRequired = encryptionRequired; }
        public String getHandlingRules() { return handlingRules; }
        public void setHandlingRules(String handlingRules) { this.handlingRules = handlingRules; }
        public String getExportRules() { return exportRules; }
        public void setExportRules(String exportRules) { this.exportRules = exportRules; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
        public Long getDeletedAt() { return deletedAt; }
        public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
    }

    /**
     * ADM01_export_requests — Controlled, authorized data export with mandatory expiry (CSV Line 34).
     * Status: PENDING → APPROVED (requires approvedBy) → COMPLETED (sets objectRef + checksum).
     * expiresAt is mandatory — requests without expiry are rejected.
     */
    public static class ExportRequest {
        private String id;
        private String requestedBy;         // Principal who initiated the export
        private String dataScope;           // e.g., "TENANT:T001:STUDENTS", "GLOBAL:AUDIT_LOGS"
        private String dataClass;           // Links to DataClassification for exportRules check
        private String format;              // CSV, JSON, PARQUET
        private String purpose;             // COMPLIANCE_AUDIT, ANALYTICS, DATA_MIGRATION
        private String status = "PENDING";  // PENDING → APPROVED → COMPLETED / REJECTED
        private String approvedBy;          // Required before APPROVED transition
        private long expiresAt;             // Mandatory — 0 is rejected
        private String objectRef;           // S3/GCS ref set on completion
        private String checksum;            // SHA-256 of exported artifact
        private long createdAt = System.currentTimeMillis();
        private long completedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getRequestedBy() { return requestedBy; }
        public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
        public String getDataScope() { return dataScope; }
        public void setDataScope(String dataScope) { this.dataScope = dataScope; }
        public String getDataClass() { return dataClass; }
        public void setDataClass(String dataClass) { this.dataClass = dataClass; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        public String getPurpose() { return purpose; }
        public void setPurpose(String purpose) { this.purpose = purpose; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getApprovedBy() { return approvedBy; }
        public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
        public long getExpiresAt() { return expiresAt; }
        public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
        public String getObjectRef() { return objectRef; }
        public void setObjectRef(String objectRef) { this.objectRef = objectRef; }
        public String getChecksum() { return checksum; }
        public void setChecksum(String checksum) { this.checksum = checksum; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getCompletedAt() { return completedAt; }
        public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }
    }

    /**
     * Platform administrator identity aggregate mapping to plat.platform_admins.
     */
    public static class PlatformAdmin {
        private String id;
        private String userId;
        private String fullName;
        private String roleCode = "SYSTEM_ADMIN";
        private String status = "ACTIVE";
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private String createdBy;
        private String updatedBy;
        private int rowVersion = 1;
        private Long deletedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getRoleCode() { return roleCode; }
        public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
        public Long getDeletedAt() { return deletedAt; }
        public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
    }

    /**
     * API Client aggregate mapping to iam.api_clients.
     */
    public static class ApiClient {
        private String id;
        private String tenantId;
        private String userId;
        private String name;
        private String ownerUserId;
        private String description;
        private String status = "ACTIVE";
        private List<String> allowedIps = new ArrayList<>();
        private Long expiresAt;
        private Long lastUsedAt;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private String createdBy;
        private String updatedBy;
        private int rowVersion = 1;
        private Long deletedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public List<String> getAllowedIps() { return allowedIps; }
        public void setAllowedIps(List<String> allowedIps) { this.allowedIps = allowedIps != null ? allowedIps : new ArrayList<>(); }
        public Long getExpiresAt() { return expiresAt; }
        public void setExpiresAt(Long expiresAt) { this.expiresAt = expiresAt; }
        public Long getLastUsedAt() { return lastUsedAt; }
        public void setLastUsedAt(Long lastUsedAt) { this.lastUsedAt = lastUsedAt; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
        public Long getDeletedAt() { return deletedAt; }
        public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
    }

    /**
     * Scoped grant assignment mapping to iam.api_client_grants.
     */
    public static class ApiClientGrant {
        private String id;
        private String tenantId;
        private String clientId;
        private String permissionId;
        private String collegeId;
        private String departmentId;
        private String resourceScope = "{}";
        private Long validTo;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private String createdBy;
        private String updatedBy;
        private int rowVersion = 1;
        private Long deletedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getPermissionId() { return permissionId; }
        public void setPermissionId(String permissionId) { this.permissionId = permissionId; }
        public String getCollegeId() { return collegeId; }
        public void setCollegeId(String collegeId) { this.collegeId = collegeId; }
        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
        public String getResourceScope() { return resourceScope; }
        public void setResourceScope(String resourceScope) { this.resourceScope = resourceScope; }
        public Long getValidTo() { return validTo; }
        public void setValidTo(Long validTo) { this.validTo = validTo; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
        public Long getDeletedAt() { return deletedAt; }
        public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
    }

    /**
     * ADM01_delegations — Temporary role authority delegation mapping to iam.delegations.
     */
    public static class Delegation {
        private String id;
        private String tenantId;
        private String fromUserId;
        private String toUserId;
        private String roleId;
        private long validFrom = System.currentTimeMillis();
        private long validTo = System.currentTimeMillis() + 86400000L;
        private String reason;
        private String status = "ACTIVE";
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private String createdBy;
        private String updatedBy;
        private int rowVersion = 1;
        private Long deletedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getFromUserId() { return fromUserId; }
        public void setFromUserId(String fromUserId) { this.fromUserId = fromUserId; }
        public String getToUserId() { return toUserId; }
        public void setToUserId(String toUserId) { this.toUserId = toUserId; }
        public String getRoleId() { return roleId; }
        public void setRoleId(String roleId) { this.roleId = roleId; }
        public long getValidFrom() { return validFrom; }
        public void setValidFrom(long validFrom) { this.validFrom = validFrom; }
        public long getValidTo() { return validTo; }
        public void setValidTo(long validTo) { this.validTo = validTo; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
        public Long getDeletedAt() { return deletedAt; }
        public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
    }

    /**
     * ADM01_user_groups — User grouping mapping to iam.user_groups.
     */
    public static class UserGroup {
        private String id;
        private String tenantId;
        private String code;
        private String name;
        private String description;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private String createdBy;
        private String updatedBy;
        private int rowVersion = 1;
        private Long deletedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
        public Long getDeletedAt() { return deletedAt; }
        public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
    }

    /**
     * ADM01_user_group_members — Member user assignment mapping to iam.user_group_members.
     */
    public static class UserGroupMember {
        private String id;
        private String tenantId;
        private String groupId;
        private String userId;
        private long createdAt = System.currentTimeMillis();
        private String createdBy;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    }

    /**
     * ADM01_role_templates — Standard role templates mapping to iam.role_templates.
     */
    public static class RoleTemplate {
        private String id;
        private String code;
        private String name;
        private String catalogueId;
        private String kind;
        private String scopeNote;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private String createdBy;
        private String updatedBy;
        private int rowVersion = 1;
        private Long deletedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCatalogueId() { return catalogueId; }
        public void setCatalogueId(String catalogueId) { this.catalogueId = catalogueId; }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public String getScopeNote() { return scopeNote; }
        public void setScopeNote(String scopeNote) { this.scopeNote = scopeNote; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
        public Long getDeletedAt() { return deletedAt; }
        public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
    }

    /**
     * ADM01_role_template_permissions — Template permission bindings mapping to iam.role_template_permissions.
     */
    public static class RoleTemplatePermission {
        private String id;
        private String roleCode;
        private String permissionCode;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private String createdBy;
        private String updatedBy;
        private int rowVersion = 1;
        private Long deletedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getRoleCode() { return roleCode; }
        public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
        public String getPermissionCode() { return permissionCode; }
        public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
        public Long getDeletedAt() { return deletedAt; }
        public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
    }

    /**
     * ADM01_campuses — Campus facility mapping to core.campuses.
     */
    public static class Campus {
        private String id;
        private String tenantId;
        private String collegeId;
        private String code;
        private String name;
        private String addressId;
        private boolean isPrimary = false;
        private String status = "ACTIVE";
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private String createdBy;
        private String updatedBy;
        private int rowVersion = 1;
        private Long deletedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getCollegeId() { return collegeId; }
        public void setCollegeId(String collegeId) { this.collegeId = collegeId; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getAddressId() { return addressId; }
        public void setAddressId(String addressId) { this.addressId = addressId; }
        public boolean isPrimary() { return isPrimary; }
        public void setPrimary(boolean primary) { isPrimary = primary; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
        public Long getDeletedAt() { return deletedAt; }
        public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
    }

    /**
     * ADM01_academic_years — Academic year definition mapping to core.academic_years.
     */
    public static class AcademicYear {
        private String id;
        private String tenantId;
        private String code;
        private long startDate = System.currentTimeMillis();
        private long endDate = System.currentTimeMillis() + 31536000000L; // +365 days
        private boolean isCurrent = false;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private String createdBy;
        private String updatedBy;
        private int rowVersion = 1;
        private Long deletedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public long getStartDate() { return startDate; }
        public void setStartDate(long startDate) { this.startDate = startDate; }
        public long getEndDate() { return endDate; }
        public void setEndDate(long endDate) { this.endDate = endDate; }
        public boolean isCurrent() { return isCurrent; }
        public void setCurrent(boolean current) { isCurrent = current; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
        public Long getDeletedAt() { return deletedAt; }
        public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
    }

    /**
     * ADM01_calendars — Institutional calendars mapping to core.calendars.
     */
    public static class Calendar {
        private String id;
        private String tenantId;
        private String collegeId;
        private String academicYearId;
        private String code;
        private String name;
        private String calendarType = "ACADEMIC";
        private String status = "DRAFT";
        private Long publishedAt;
        private short weekStartDay = 1;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private String createdBy;
        private String updatedBy;
        private int rowVersion = 1;
        private Long deletedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getCollegeId() { return collegeId; }
        public void setCollegeId(String collegeId) { this.collegeId = collegeId; }
        public String getAcademicYearId() { return academicYearId; }
        public void setAcademicYearId(String academicYearId) { this.academicYearId = academicYearId; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCalendarType() { return calendarType; }
        public void setCalendarType(String calendarType) { this.calendarType = calendarType; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getPublishedAt() { return publishedAt; }
        public void setPublishedAt(Long publishedAt) { this.publishedAt = publishedAt; }
        public short getWeekStartDay() { return weekStartDay; }
        public void setWeekStartDay(short weekStartDay) { this.weekStartDay = weekStartDay; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
        public Long getDeletedAt() { return deletedAt; }
        public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
    }

    /**
     * ADM01_calendar_events — Events associated with a calendar mapping to core.calendar_events.
     */
    public static class CalendarEvent {
        private String id;
        private String tenantId;
        private String calendarId;
        private String eventType = "EVENT";
        private String title;
        private String description;
        private long startDate = System.currentTimeMillis();
        private long endDate = System.currentTimeMillis();
        private boolean isHoliday = false;
        private String appliesTo = "{}";
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private String createdBy;
        private String updatedBy;
        private int rowVersion = 1;
        private Long deletedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getCalendarId() { return calendarId; }
        public void setCalendarId(String calendarId) { this.calendarId = calendarId; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public long getStartDate() { return startDate; }
        public void setStartDate(long startDate) { this.startDate = startDate; }
        public long getEndDate() { return endDate; }
        public void setEndDate(long endDate) { this.endDate = endDate; }
        public boolean isHoliday() { return isHoliday; }
        public void setHoliday(boolean holiday) { isHoliday = holiday; }
        public String getAppliesTo() { return appliesTo; }
        public void setAppliesTo(String appliesTo) { this.appliesTo = appliesTo; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
        public Long getDeletedAt() { return deletedAt; }
        public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
    }

    /**
     * ADM01_calendar_working_days — Working day rules mapping to core.calendar_working_days.
     */
    public static class CalendarWorkingDay {
        private String id;
        private String tenantId;
        private String calendarId;
        private short dayOfWeek = 1; // 0=Sunday..6=Saturday
        private boolean isWorking = true;
        private boolean isHalfDay = false;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private String createdBy;
        private String updatedBy;
        private int rowVersion = 1;
        private Long deletedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getCalendarId() { return calendarId; }
        public void setCalendarId(String calendarId) { this.calendarId = calendarId; }
        public short getDayOfWeek() { return dayOfWeek; }
        public void setDayOfWeek(short dayOfWeek) { this.dayOfWeek = dayOfWeek; }
        public boolean isWorking() { return isWorking; }
        public void setWorking(boolean working) { isWorking = working; }
        public boolean isHalfDay() { return isHalfDay; }
        public void setHalfDay(boolean halfDay) { isHalfDay = halfDay; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
        public Long getDeletedAt() { return deletedAt; }
        public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
    }

    /**
     * ADM01_number_sequences — Configurable sequence generators mapping to core.number_sequences.
     */
    public static class NumberSequence {
        private String id;
        private String tenantId;
        private String scopeKey;
        private String prefix;
        private String suffix;
        private long nextValue = 1;
        private short padding = 6;
        private String resetPolicy = "NEVER"; // NEVER, YEARLY, MONTHLY, ACADEMIC_YEAR
        private Long lastResetOn;
        private String requiredPermission;
        private String collegeId;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private String createdBy;
        private String updatedBy;
        private int rowVersion = 1;
        private Long deletedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getScopeKey() { return scopeKey; }
        public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }
        public String getSuffix() { return suffix; }
        public void setSuffix(String suffix) { this.suffix = suffix; }
        public long getNextValue() { return nextValue; }
        public void setNextValue(long nextValue) { this.nextValue = nextValue; }
        public short getPadding() { return padding; }
        public void setPadding(short padding) { this.padding = padding; }
        public String getResetPolicy() { return resetPolicy; }
        public void setResetPolicy(String resetPolicy) { this.resetPolicy = resetPolicy; }
        public Long getLastResetOn() { return lastResetOn; }
        public void setLastResetOn(Long lastResetOn) { this.lastResetOn = lastResetOn; }
        public String getRequiredPermission() { return requiredPermission; }
        public void setRequiredPermission(String requiredPermission) { this.requiredPermission = requiredPermission; }
        public String getCollegeId() { return collegeId; }
        public void setCollegeId(String collegeId) { this.collegeId = collegeId; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
        public Long getDeletedAt() { return deletedAt; }
        public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
    }

    /**
     * ADM01_lookup_types — Reference domain categories mapping to core.lookup_types.
     */
    public static class LookupType {
        private String id;
        private String tenantId;
        private String code;
        private String name;
        private boolean isSystem = false;
        private String description;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private String createdBy;
        private String updatedBy;
        private int rowVersion = 1;
        private Long deletedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isSystem() { return isSystem; }
        public void setSystem(boolean system) { isSystem = system; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
        public Long getDeletedAt() { return deletedAt; }
        public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
    }

    /**
     * ADM01_lookup_values — Reference lookup items mapping to core.lookup_values.
     */
    public static class LookupValue {
        private String id;
        private String tenantId;
        private String lookupTypeId;
        private String code;
        private String label;
        private int sortOrder = 0;
        private boolean isActive = true;
        private String attrs = "{}";
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private String createdBy;
        private String updatedBy;
        private int rowVersion = 1;
        private Long deletedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getLookupTypeId() { return lookupTypeId; }
        public void setLookupTypeId(String lookupTypeId) { this.lookupTypeId = lookupTypeId; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public int getSortOrder() { return sortOrder; }
        public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
        public boolean isActive() { return isActive; }
        public void setActive(boolean active) { isActive = active; }
        public String getAttrs() { return attrs; }
        public void setAttrs(String attrs) { this.attrs = attrs; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
        public Long getDeletedAt() { return deletedAt; }
        public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
    }

    /**
     * ADM01_access_events — Granular access audit events mapping to audit.access_events.
     */
    public static class AccessEvent {
        private String id;
        private String tenantId;
        private String principalId;
        private String resourceType;
        private String resourceId;
        private String accessType; // READ, EXPORT, PRINT, DOWNLOAD
        private String sensitivity;
        private String ip;
        private String reason;
        private long createdAt = System.currentTimeMillis();
        private String createdBy;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getPrincipalId() { return principalId; }
        public void setPrincipalId(String principalId) { this.principalId = principalId; }
        public String getResourceType() { return resourceType; }
        public void setResourceType(String resourceType) { this.resourceType = resourceType; }
        public String getResourceId() { return resourceId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public String getAccessType() { return accessType; }
        public void setAccessType(String accessType) { this.accessType = accessType; }
        public String getSensitivity() { return sensitivity; }
        public void setSensitivity(String sensitivity) { this.sensitivity = sensitivity; }
        public String getIp() { return ip; }
        public void setIp(String ip) { this.ip = ip; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    }

    /**
     * ADM01_change_log — Row-level change audit log mapping to audit.change_log.
     */
    public static class AuditChangeLog {
        private String id;
        private String tenantId;
        private String tableSchema;
        private String tableName;
        private String recordId;
        private String action; // I, U, D
        private java.util.List<String> changedFields;
        private String oldData;
        private String newData;
        private String actorId;
        private String requestId;
        private long createdAt = System.currentTimeMillis();
        private String createdBy;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getTableSchema() { return tableSchema; }
        public void setTableSchema(String tableSchema) { this.tableSchema = tableSchema; }
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getRecordId() { return recordId; }
        public void setRecordId(String recordId) { this.recordId = recordId; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public java.util.List<String> getChangedFields() { return changedFields; }
        public void setChangedFields(java.util.List<String> changedFields) { this.changedFields = changedFields; }
        public String getOldData() { return oldData; }
        public void setOldData(String oldData) { this.oldData = oldData; }
        public String getNewData() { return newData; }
        public void setNewData(String newData) { this.newData = newData; }
        public String getActorId() { return actorId; }
        public void setActorId(String actorId) { this.actorId = actorId; }
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    }
}





