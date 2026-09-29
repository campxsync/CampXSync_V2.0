package com.campx.academic.resource.model;

import java.util.*;

/**
 * Domain entities, enums, and DTOs for the ACD-08 Learning Resource Service.
 * Implements the data dictionary defined in the ACD-08 Architecture Specification:
 * - resources
 * - resource_versions
 * - resource_access
 * - resource_history
 * - outbox_events
 * - idempotency_records
 * - dead_letter_events
 */
public class ResourceModels {

    // ==========================================
    // ENUMS
    // ==========================================

    public enum ResourceType {
        SYLLABUS,
        STUDY_MATERIAL,
        REFERENCE_LINK,
        LECTURE_NOTES,
        LAB_MANUAL,
        VIDEO,
        QUESTION_BANK
    }

    public enum ResourceStatus {
        DRAFT,
        REVIEW,
        APPROVED,
        PUBLISHED,
        ARCHIVED,
        REJECTED,
        CANCELLED
    }

    public enum VersionStatus {
        DRAFT,
        PUBLISHED,
        ARCHIVED
    }

    public enum PrincipalType {
        USER,
        ROLE,
        GROUP
    }

    public enum ScopeType {
        GLOBAL,
        INSTITUTION,
        CAMPUS,
        DEPARTMENT,
        COURSE,
        SUBJECT,
        BATCH,
        PROGRAM
    }

    public enum Permission {
        VIEW,
        DOWNLOAD,
        MANAGE
    }

    public enum AccessEffect {
        ALLOW,
        DENY
    }

    public enum GrantStatus {
        ACTIVE,
        REVOKED
    }

    public enum OutboxStatus {
        PENDING,
        PUBLISHED,
        FAILED
    }

    public enum DLQStatus {
        DEAD_LETTER,
        REPLAYED
    }

    // ==========================================
    // DOMAIN ENTITIES (MONGODB MAPPINGS)
    // ==========================================

    /**
     * Authoritative resource aggregate (Collection: resources).
     */
    public static class LearningResource {
        private String id;
        private String tenantId;
        private String institutionId;
        private String campusId;
        private String resourceCode;
        private String title;
        private ResourceType resourceType;
        private String subjectId;
        private String courseId;
        private String curriculumId;
        private String departmentId;
        private String ownerId;
        private ResourceStatus status;
        private long currentVersion;
        private Long publishedVersion;
        private String accessPolicyId;
        private List<String> tags = new ArrayList<>();
        private String description;
        private String academicPeriodId;
        private String createdBy;
        private String createdAt;
        private String updatedBy;
        private String updatedAt;
        private String correlationId;
        private String schemaVersion = "1.0.0";

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getInstitutionId() { return institutionId; }
        public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

        public String getCampusId() { return campusId; }
        public void setCampusId(String campusId) { this.campusId = campusId; }

        public String getResourceCode() { return resourceCode; }
        public void setResourceCode(String resourceCode) { this.resourceCode = resourceCode; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public ResourceType getResourceType() { return resourceType; }
        public void setResourceType(ResourceType resourceType) { this.resourceType = resourceType; }

        public String getSubjectId() { return subjectId; }
        public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }

        public String getCurriculumId() { return curriculumId; }
        public void setCurriculumId(String curriculumId) { this.curriculumId = curriculumId; }

        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

        public String getOwnerId() { return ownerId; }
        public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

        public ResourceStatus getStatus() { return status; }
        public void setStatus(ResourceStatus status) { this.status = status; }

        public long getCurrentVersion() { return currentVersion; }
        public void setCurrentVersion(long currentVersion) { this.currentVersion = currentVersion; }

        public Long getPublishedVersion() { return publishedVersion; }
        public void setPublishedVersion(Long publishedVersion) { this.publishedVersion = publishedVersion; }

        public String getAccessPolicyId() { return accessPolicyId; }
        public void setAccessPolicyId(String accessPolicyId) { this.accessPolicyId = accessPolicyId; }

        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags != null ? tags : new ArrayList<>(); }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getAcademicPeriodId() { return academicPeriodId; }
        public void setAcademicPeriodId(String academicPeriodId) { this.academicPeriodId = academicPeriodId; }

        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

        public String getSchemaVersion() { return schemaVersion; }
        public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    }

    /**
     * Immutable content/version metadata (Collection: resource_versions).
     */
    public static class ResourceVersion {
        private String id;
        private String tenantId;
        private String resourceId;
        private long versionNo;
        private String storageProvider = "SHARED_BLOB";
        private String objectKey;
        private String objectVersionId;
        private String fileName;
        private String mimeType;
        private long fileSize;
        private String checksum;
        private String contentHash;
        private String createdBy;
        private String createdAt;
        private String changeSummary;
        private VersionStatus status = VersionStatus.DRAFT;
        private String publishedAt;
        private String archivedAt;
        private String schemaVersion = "1.0.0";

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getResourceId() { return resourceId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }

        public long getVersionNo() { return versionNo; }
        public void setVersionNo(long versionNo) { this.versionNo = versionNo; }

        public String getStorageProvider() { return storageProvider; }
        public void setStorageProvider(String storageProvider) { this.storageProvider = storageProvider; }

        public String getObjectKey() { return objectKey; }
        public void setObjectKey(String objectKey) { this.objectKey = objectKey; }

        public String getObjectVersionId() { return objectVersionId; }
        public void setObjectVersionId(String objectVersionId) { this.objectVersionId = objectVersionId; }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }

        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }

        public String getChecksum() { return checksum; }
        public void setChecksum(String checksum) { this.checksum = checksum; }

        public String getContentHash() { return contentHash; }
        public void setContentHash(String contentHash) { this.contentHash = contentHash; }

        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

        public String getChangeSummary() { return changeSummary; }
        public void setChangeSummary(String changeSummary) { this.changeSummary = changeSummary; }

        public VersionStatus getStatus() { return status; }
        public void setStatus(VersionStatus status) { this.status = status; }

        public String getPublishedAt() { return publishedAt; }
        public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }

        public String getArchivedAt() { return archivedAt; }
        public void setArchivedAt(String archivedAt) { this.archivedAt = archivedAt; }

        public String getSchemaVersion() { return schemaVersion; }
        public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    }

    /**
     * Policy-driven access grant (Collection: resource_access).
     */
    public static class ResourceAccessGrant {
        private String id;
        private String tenantId;
        private String resourceId;
        private PrincipalType principalType;
        private String principalId;
        private ScopeType scopeType = ScopeType.GLOBAL;
        private String scopeId;
        private Permission permission = Permission.VIEW;
        private AccessEffect effect = AccessEffect.ALLOW;
        private String validFrom;
        private String validTo;
        private GrantStatus status = GrantStatus.ACTIVE;
        private String grantedBy;
        private String grantedAt;
        private String revokedBy;
        private String revokedAt;
        private long policyVersion = 1;
        private String schemaVersion = "1.0.0";

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getResourceId() { return resourceId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }

        public PrincipalType getPrincipalType() { return principalType; }
        public void setPrincipalType(PrincipalType principalType) { this.principalType = principalType; }

        public String getPrincipalId() { return principalId; }
        public void setPrincipalId(String principalId) { this.principalId = principalId; }

        public ScopeType getScopeType() { return scopeType; }
        public void setScopeType(ScopeType scopeType) { this.scopeType = scopeType; }

        public String getScopeId() { return scopeId; }
        public void setScopeId(String scopeId) { this.scopeId = scopeId; }

        public Permission getPermission() { return permission; }
        public void setPermission(Permission permission) { this.permission = permission; }

        public AccessEffect getEffect() { return effect; }
        public void setEffect(AccessEffect effect) { this.effect = effect; }

        public String getValidFrom() { return validFrom; }
        public void setValidFrom(String validFrom) { this.validFrom = validFrom; }

        public String getValidTo() { return validTo; }
        public void setValidTo(String validTo) { this.validTo = validTo; }

        public GrantStatus getStatus() { return status; }
        public void setStatus(GrantStatus status) { this.status = status; }

        public String getGrantedBy() { return grantedBy; }
        public void setGrantedBy(String grantedBy) { this.grantedBy = grantedBy; }

        public String getGrantedAt() { return grantedAt; }
        public void setGrantedAt(String grantedAt) { this.grantedAt = grantedAt; }

        public String getRevokedBy() { return revokedBy; }
        public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }

        public String getRevokedAt() { return revokedAt; }
        public void setRevokedAt(String revokedAt) { this.revokedAt = revokedAt; }

        public long getPolicyVersion() { return policyVersion; }
        public void setPolicyVersion(long policyVersion) { this.policyVersion = policyVersion; }

        public String getSchemaVersion() { return schemaVersion; }
        public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    }

    /**
     * Immutable lifecycle/version audit history (Collection: resource_history).
     */
    public static class ResourceHistory {
        private String id;
        private String tenantId;
        private String resourceId;
        private long versionNo;
        private String action;
        private String fromStatus;
        private String toStatus;
        private String changedBy;
        private String changedAt;
        private String reason;
        private String approvalRef;
        private String snapshotRef;
        private String correlationId;
        private String schemaVersion = "1.0.0";

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getResourceId() { return resourceId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }

        public long getVersionNo() { return versionNo; }
        public void setVersionNo(long versionNo) { this.versionNo = versionNo; }

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }

        public String getFromStatus() { return fromStatus; }
        public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }

        public String getToStatus() { return toStatus; }
        public void setToStatus(String toStatus) { this.toStatus = toStatus; }

        public String getChangedBy() { return changedBy; }
        public void setChangedBy(String changedBy) { this.changedBy = changedBy; }

        public String getChangedAt() { return changedAt; }
        public void setChangedAt(String changedAt) { this.changedAt = changedAt; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public String getApprovalRef() { return approvalRef; }
        public void setApprovalRef(String approvalRef) { this.approvalRef = approvalRef; }

        public String getSnapshotRef() { return snapshotRef; }
        public void setSnapshotRef(String snapshotRef) { this.snapshotRef = snapshotRef; }

        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

        public String getSchemaVersion() { return schemaVersion; }
        public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    }

    /**
     * Technical reliable outbox event (Collection: outbox_events).
     */
    public static class OutboxEvent {
        private String id;
        private String eventId;
        private String tenantId;
        private String aggregateType = "LearningResource";
        private String aggregateId;
        private String eventType;
        private String payload;
        private OutboxStatus status = OutboxStatus.PENDING;
        private int attemptCount = 0;
        private String nextAttemptAt;
        private String createdAt;
        private String publishedAt;
        private String correlationId;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getAggregateType() { return aggregateType; }
        public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

        public String getAggregateId() { return aggregateId; }
        public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }

        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }

        public OutboxStatus getStatus() { return status; }
        public void setStatus(OutboxStatus status) { this.status = status; }

        public int getAttemptCount() { return attemptCount; }
        public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

        public String getNextAttemptAt() { return nextAttemptAt; }
        public void setNextAttemptAt(String nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

        public String getPublishedAt() { return publishedAt; }
        public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }

        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    }

    /**
     * Idempotency record for retryable writes (Collection: idempotency_records).
     */
    public static class IdempotencyRecord {
        private String id;
        private String tenantId;
        private String idempotencyKey;
        private String requestHash;
        private String operation;
        private int responseStatus;
        private String responseBody;
        private String expiresAt;
        private String createdAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

        public String getRequestHash() { return requestHash; }
        public void setRequestHash(String requestHash) { this.requestHash = requestHash; }

        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }

        public int getResponseStatus() { return responseStatus; }
        public void setResponseStatus(int responseStatus) { this.responseStatus = responseStatus; }

        public String getResponseBody() { return responseBody; }
        public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

        public String getExpiresAt() { return expiresAt; }
        public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }

    /**
     * Dead letter event for unrecoverable failures (Collection: dead_letter_events).
     */
    public static class DeadLetterEvent {
        private String id;
        private String eventId;
        private String eventType;
        private String source = "ACD-08";
        private String payload;
        private String failureCode;
        private String failureReason;
        private int attemptCount = 0;
        private String firstFailedAt;
        private String lastFailedAt;
        private DLQStatus status = DLQStatus.DEAD_LETTER;
        private int replayCount = 0;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }

        public String getFailureCode() { return failureCode; }
        public void setFailureCode(String failureCode) { this.failureCode = failureCode; }

        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

        public int getAttemptCount() { return attemptCount; }
        public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

        public String getFirstFailedAt() { return firstFailedAt; }
        public void setFirstFailedAt(String firstFailedAt) { this.firstFailedAt = firstFailedAt; }

        public String getLastFailedAt() { return lastFailedAt; }
        public void setLastFailedAt(String lastFailedAt) { this.lastFailedAt = lastFailedAt; }

        public DLQStatus getStatus() { return status; }
        public void setStatus(DLQStatus status) { this.status = status; }

        public int getReplayCount() { return replayCount; }
        public void setReplayCount(int replayCount) { this.replayCount = replayCount; }
    }

    /**
     * API Key Record for external API authentication.
     */
    public static class ApiKeyRecord {
        private String keyId;
        private String apiKey;
        private String tenantId;
        private String description;
        private boolean active = true;

        public ApiKeyRecord() {}

        public ApiKeyRecord(String keyId, String apiKey, String tenantId, String description) {
            this.keyId = keyId;
            this.apiKey = apiKey;
            this.tenantId = tenantId;
            this.description = description;
            this.active = true;
        }

        public String getKeyId() { return keyId; }
        public void setKeyId(String keyId) { this.keyId = keyId; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    // ==========================================
    // DTO REQUESTS AND RESPONSES
    // ==========================================

    public static class CreateResourceRequest {
        public String resourceCode;
        public String title;
        public String resourceType; // e.g. "SYLLABUS", "STUDY_MATERIAL"
        public String subjectId;
        public String courseId;
        public String curriculumId;
        public String departmentId;
        public String campusId;
        public String institutionId;
        public String description;
        public List<String> tags = new ArrayList<>();
        public String academicPeriodId;

        // Initial version details
        public String storageObjectRef; // objectKey
        public String storageProvider;
        public String fileName;
        public String mimeType;
        public Long fileSize;
        public String checksum;
        public String contentHash;

        // Initial access grant options
        public String initialAccessScope;
        public String initialScopeId;
    }

    public static class UpdateResourceRequest {
        public String title;
        public String description;
        public List<String> tags;
        public Long expectedVersion;
    }

    public static class CreateVersionRequest {
        public String storageObjectRef;
        public String storageProvider;
        public String fileName;
        public String mimeType;
        public Long fileSize;
        public String checksum;
        public String contentHash;
        public String changeSummary;
        public Long expectedVersion;
    }

    public static class PublishResourceRequest {
        public Long expectedVersion;
        public String approvalRef;
        public String comment;
    }

    public static class CreateAccessGrantRequest {
        public String principalType;
        public String principalId;
        public String scopeType;
        public String scopeId;
        public String permission;
        public String effect; // ALLOW, DENY
        public String validFrom;
        public String validTo;
    }

    public static class DownloadResponse {
        public String resourceId;
        public long versionNo;
        public String fileName;
        public String mimeType;
        public long fileSize;
        public String checksum;
        public String downloadUrl; // Pre-signed short-lived retrieval reference
        public int expiresInSeconds;
    }

    public static class ResourceSearchFilter {
        public String query;
        public String resourceType;
        public String subjectId;
        public String courseId;
        public String curriculumId;
        public String departmentId;
        public String status;
        public List<String> tags;
        public int page = 1;
        public int limit = 20;
        public String sortBy = "updatedAt";
        public String sortOrder = "DESC";
    }

    public static class BulkImportRequest {
        public String importJobId;
        public List<CreateResourceRequest> items = new ArrayList<>();
    }

    public static class BulkImportResult {
        public String importJobId;
        public int totalRecords;
        public int successfulRecords;
        public int failedRecords;
        public List<String> errors = new ArrayList<>();
    }

    public static class ExportJobResponse {
        public String exportJobId;
        public String status;
        public String generatedAt;
        public int totalRecords;
        public List<LearningResource> resources = new ArrayList<>();
    }

    public static class ResourceUsageStats {
        public String resourceId;
        public long viewCount;
        public long downloadCount;
        public String lastAccessedAt;
        public Map<String, Long> accessByDepartment = new HashMap<>();
    }

    public static class AdvisoryInsightsResponse {
        public long totalResources;
        public long publishedResources;
        public long draftResources;
        public long archivedResources;
        public long staleResourcesCount;
        public Map<String, Long> resourceTypeDistribution = new HashMap<>();
        public Map<String, Long> subjectCoverage = new HashMap<>();
    }

    public static class DisasterRecoveryValidationResult {
        public boolean consistent;
        public int totalChecked;
        public int validReferences;
        public int brokenReferences;
        public List<String> inconsistencies = new ArrayList<>();
    }
}
