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
     * ADM01_subscriptions — Tenant subscriptions with entitlement snapshots (CSV Line 24).
     */
    public static class Subscription {
        private String id;
        private String tenantId;
        private String planId;
        private String status = "ACTIVE"; // ACTIVE, SUSPENDED, RENEWED, CANCELLED
        private int seatCount = 50;
        private List<String> entitlementSnapshot = new ArrayList<>();
        private long startDate = System.currentTimeMillis();
        private long endDate = System.currentTimeMillis() + 31536000000L; // 1 year
        private long createdAt = System.currentTimeMillis();

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
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
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
        private long createdAt = System.currentTimeMillis();

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
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
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
        private String currency = "INR";
        private String rawReference;
        private String status = "PENDING"; // PENDING, SUCCESS, FAILED
        private long reconciledAt;
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getGatewayTransactionId() { return gatewayTransactionId; }
        public void setGatewayTransactionId(String gatewayTransactionId) { this.gatewayTransactionId = gatewayTransactionId; }
        public String getInvoiceId() { return invoiceId; }
        public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
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
        private String dataClass;           // Maps to DataClassification.classCode
        private int retentionDays;          // Business-defined retention window
        private int archiveAfterDays;       // Move to cold storage after this threshold
        private String legalHoldBehavior;   // SUSPEND_PURGE, EXTEND_RETENTION
        private int legalMinimumDays;       // Floor — retentionDays cannot go below this
        private int version = 1;
        private String status = "ACTIVE";   // ACTIVE, SUPERSEDED
        private long createdAt = System.currentTimeMillis();
        private long updatedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getPolicyCode() { return policyCode; }
        public void setPolicyCode(String policyCode) { this.policyCode = policyCode; }
        public String getDataClass() { return dataClass; }
        public void setDataClass(String dataClass) { this.dataClass = dataClass; }
        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
        public int getArchiveAfterDays() { return archiveAfterDays; }
        public void setArchiveAfterDays(int archiveAfterDays) { this.archiveAfterDays = archiveAfterDays; }
        public String getLegalHoldBehavior() { return legalHoldBehavior; }
        public void setLegalHoldBehavior(String legalHoldBehavior) { this.legalHoldBehavior = legalHoldBehavior; }
        public int getLegalMinimumDays() { return legalMinimumDays; }
        public void setLegalMinimumDays(int legalMinimumDays) { this.legalMinimumDays = legalMinimumDays; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    }

    /**
     * ADM01_data_classifications — Versioned, audited data classification taxonomy (CSV Line 33).
     * exportRules affects downstream ExportRequest authorization decisions.
     */
    public static class DataClassification {
        private String id;
        private String classCode;           // PUBLIC, INTERNAL, RESTRICTED, HIGHLY_RESTRICTED
        private String sensitivity;         // LOW, MEDIUM, HIGH, CRITICAL
        private String handlingRules;       // Encryption requirements, access logging, etc.
        private String exportRules;         // ALLOWED, APPROVAL_REQUIRED, PROHIBITED
        private int version = 1;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getClassCode() { return classCode; }
        public void setClassCode(String classCode) { this.classCode = classCode; }
        public String getSensitivity() { return sensitivity; }
        public void setSensitivity(String sensitivity) { this.sensitivity = sensitivity; }
        public String getHandlingRules() { return handlingRules; }
        public void setHandlingRules(String handlingRules) { this.handlingRules = handlingRules; }
        public String getExportRules() { return exportRules; }
        public void setExportRules(String exportRules) { this.exportRules = exportRules; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
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
}
