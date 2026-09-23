package com.campx.academic.subject.model;

import java.util.*;

/**
 * Enterprise domain models, entities, technical collection schemas, and response envelopes
 * for the ACD-03 Subject Management Service.
 */
public final class SubjectModels {

    private SubjectModels() {}

    // =========================================================================
    // 1. Enums
    // =========================================================================

    public enum SubjectStatus {
        DRAFT,
        ACTIVE,
        DEPRECATED,
        DEACTIVATED,
        RETIRED
    }

    public enum VersionStatus {
        DRAFT,
        REVIEW,
        APPROVED,
        PUBLISHED,
        SUPERSEDED,
        RETIRED
    }

    public enum SubjectType {
        CORE,
        ELECTIVE,
        PRACTICAL,
        PROJECT,
        AUDIT
    }

    public enum Classification {
        THEORY,
        PRACTICAL,
        TUTORIAL,
        PROJECT,
        ELECTIVE,
        AUDIT
    }

    public enum RelationshipType {
        PREREQUISITE,
        CO_REQUISITE,
        RECOMMENDED
    }

    public enum DataClassification {
        L1_PUBLIC,
        L2_INTERNAL,
        L3_CONFIDENTIAL,
        L4_RESTRICTED
    }

    // =========================================================================
    // 2. Aggregate Root: Subject
    // =========================================================================

    public static class Subject {
        private String id;
        private String tenantId;
        private String institutionId;
        private String campusId;
        private String subjectCode;
        private String name;
        private String shortName;
        private String description;
        private String departmentId;
        private String courseId;
        private String subjectType;
        private String classification;
        private boolean elective;
        private double credits;
        private double contactHours;
        private SubjectStatus status;
        private int currentVersion;
        private String effectiveFrom;
        private String effectiveTo;
        private String academicYear;
        private boolean departmentActive = true;
        private boolean flaggedForReview = false;
        private String flagReason;
        private long createdAt;
        private long updatedAt;
        private String createdBy;
        private String updatedBy;
        private long version; // Optimistic concurrency lock

        public Subject() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getInstitutionId() { return institutionId; }
        public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }
        public String getCampusId() { return campusId; }
        public void setCampusId(String campusId) { this.campusId = campusId; }
        public String getSubjectCode() { return subjectCode; }
        public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getShortName() { return shortName; }
        public void setShortName(String shortName) { this.shortName = shortName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }
        public String getSubjectType() { return subjectType; }
        public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
        public String getClassification() { return classification; }
        public void setClassification(String classification) { this.classification = classification; }
        public boolean isElective() { return elective; }
        public void setElective(boolean elective) { this.elective = elective; }
        public double getCredits() { return credits; }
        public void setCredits(double credits) { this.credits = credits; }
        public double getContactHours() { return contactHours; }
        public void setContactHours(double contactHours) { this.contactHours = contactHours; }
        public SubjectStatus getStatus() { return status; }
        public void setStatus(SubjectStatus status) { this.status = status; }
        public int getCurrentVersion() { return currentVersion; }
        public void setCurrentVersion(int currentVersion) { this.currentVersion = currentVersion; }
        public String getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(String effectiveFrom) { this.effectiveFrom = effectiveFrom; }
        public String getEffectiveTo() { return effectiveTo; }
        public void setEffectiveTo(String effectiveTo) { this.effectiveTo = effectiveTo; }
        public String getAcademicYear() { return academicYear; }
        public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }
        public boolean isDepartmentActive() { return departmentActive; }
        public void setDepartmentActive(boolean departmentActive) { this.departmentActive = departmentActive; }
        public boolean isFlaggedForReview() { return flaggedForReview; }
        public void setFlaggedForReview(boolean flaggedForReview) { this.flaggedForReview = flaggedForReview; }
        public String getFlagReason() { return flagReason; }
        public void setFlagReason(String flagReason) { this.flagReason = flagReason; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
        public long getVersion() { return version; }
        public void setVersion(long version) { this.version = version; }

        /**
         * Clones subject data for safe snapshot storage.
         */
        public Subject copy() {
            Subject s = new Subject();
            s.id = this.id;
            s.tenantId = this.tenantId;
            s.institutionId = this.institutionId;
            s.campusId = this.campusId;
            s.subjectCode = this.subjectCode;
            s.name = this.name;
            s.shortName = this.shortName;
            s.description = this.description;
            s.departmentId = this.departmentId;
            s.courseId = this.courseId;
            s.subjectType = this.subjectType;
            s.classification = this.classification;
            s.elective = this.elective;
            s.credits = this.credits;
            s.contactHours = this.contactHours;
            s.status = this.status;
            s.currentVersion = this.currentVersion;
            s.effectiveFrom = this.effectiveFrom;
            s.effectiveTo = this.effectiveTo;
            s.academicYear = this.academicYear;
            s.departmentActive = this.departmentActive;
            s.flaggedForReview = this.flaggedForReview;
            s.flagReason = this.flagReason;
            s.createdAt = this.createdAt;
            s.updatedAt = this.updatedAt;
            s.createdBy = this.createdBy;
            s.updatedBy = this.updatedBy;
            s.version = this.version;
            return s;
        }
    }

    // =========================================================================
    // 3. Subject Version Entity (§38)
    // =========================================================================

    public static class SubjectVersion {
        private String id;
        private String subjectId;
        private String tenantId;
        private int versionNo;
        private VersionStatus status;
        private String academicYear;
        private String effectiveFrom;
        private String effectiveTo;
        private double credits;
        private double contactHours;
        private String subjectType;
        private String classification;
        private boolean elective;
        private String changeSummary;
        private String approvedBy;
        private Long approvedAt;
        private String publishedBy;
        private Long publishedAt;
        private String checksum;
        private long createdAt;
        private String createdBy;
        private long updatedAt;
        private String updatedBy;

        public SubjectVersion() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getSubjectId() { return subjectId; }
        public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public int getVersionNo() { return versionNo; }
        public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
        public VersionStatus getStatus() { return status; }
        public void setStatus(VersionStatus status) { this.status = status; }
        public String getAcademicYear() { return academicYear; }
        public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }
        public String getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(String effectiveFrom) { this.effectiveFrom = effectiveFrom; }
        public String getEffectiveTo() { return effectiveTo; }
        public void setEffectiveTo(String effectiveTo) { this.effectiveTo = effectiveTo; }
        public double getCredits() { return credits; }
        public void setCredits(double credits) { this.credits = credits; }
        public double getContactHours() { return contactHours; }
        public void setContactHours(double contactHours) { this.contactHours = contactHours; }
        public String getSubjectType() { return subjectType; }
        public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
        public String getClassification() { return classification; }
        public void setClassification(String classification) { this.classification = classification; }
        public boolean isElective() { return elective; }
        public void setElective(boolean elective) { this.elective = elective; }
        public String getChangeSummary() { return changeSummary; }
        public void setChangeSummary(String changeSummary) { this.changeSummary = changeSummary; }
        public String getApprovedBy() { return approvedBy; }
        public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
        public Long getApprovedAt() { return approvedAt; }
        public void setApprovedAt(Long approvedAt) { this.approvedAt = approvedAt; }
        public String getPublishedBy() { return publishedBy; }
        public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
        public Long getPublishedAt() { return publishedAt; }
        public void setPublishedAt(Long publishedAt) { this.publishedAt = publishedAt; }
        public String getChecksum() { return checksum; }
        public void setChecksum(String checksum) { this.checksum = checksum; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    }

    // =========================================================================
    // 4. Extended Subject Metadata (§39)
    // =========================================================================

    public static class SubjectMetadata {
        private String id;
        private String tenantId;
        private String subjectId;
        private List<String> tags = new ArrayList<>();
        private String category;
        private String deliveryMode; // OFFLINE, ONLINE, HYBRID
        private String assessmentMode; // THEORY_ONLY, PRACTICAL_ONLY, INTEGRATED
        private String regulatoryCode; // L3 Confidential
        private List<String> industryRelevance = new ArrayList<>();
        private String languageOfInstruction;
        private String prerequisiteNotes; // L3
        private Map<String, Object> customAttributes = new LinkedHashMap<>();
        private long createdAt;
        private long updatedAt;
        private String updatedBy;
        private long version;

        public SubjectMetadata() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getSubjectId() { return subjectId; }
        public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags != null ? tags : new ArrayList<>(); }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getDeliveryMode() { return deliveryMode; }
        public void setDeliveryMode(String deliveryMode) { this.deliveryMode = deliveryMode; }
        public String getAssessmentMode() { return assessmentMode; }
        public void setAssessmentMode(String assessmentMode) { this.assessmentMode = assessmentMode; }
        public String getRegulatoryCode() { return regulatoryCode; }
        public void setRegulatoryCode(String regulatoryCode) { this.regulatoryCode = regulatoryCode; }
        public List<String> getIndustryRelevance() { return industryRelevance; }
        public void setIndustryRelevance(List<String> industryRelevance) {
            this.industryRelevance = industryRelevance != null ? industryRelevance : new ArrayList<>();
        }
        public String getLanguageOfInstruction() { return languageOfInstruction; }
        public void setLanguageOfInstruction(String languageOfInstruction) { this.languageOfInstruction = languageOfInstruction; }
        public String getPrerequisiteNotes() { return prerequisiteNotes; }
        public void setPrerequisiteNotes(String prerequisiteNotes) { this.prerequisiteNotes = prerequisiteNotes; }
        public Map<String, Object> getCustomAttributes() { return customAttributes; }
        public void setCustomAttributes(Map<String, Object> customAttributes) {
            this.customAttributes = customAttributes != null ? customAttributes : new LinkedHashMap<>();
        }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
        public long getVersion() { return version; }
        public void setVersion(long version) { this.version = version; }
    }

    // =========================================================================
    // 5. Subject Prerequisites & Co-requisites (§39)
    // =========================================================================

    public static class SubjectPrerequisite {
        private String id;
        private String tenantId;
        private String institutionId;
        private String subjectId;
        private String prerequisiteSubjectId;
        private RelationshipType relationshipType;
        private boolean mandatory;
        private String minimumGrade;
        private String effectiveFrom;
        private String effectiveTo;
        private String status = "ACTIVE"; // ACTIVE, INACTIVE, RETIRED
        private long createdAt;
        private long updatedAt;
        private String createdBy;
        private String updatedBy;
        private long version;

        public SubjectPrerequisite() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getInstitutionId() { return institutionId; }
        public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }
        public String getSubjectId() { return subjectId; }
        public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
        public String getPrerequisiteSubjectId() { return prerequisiteSubjectId; }
        public void setPrerequisiteSubjectId(String prerequisiteSubjectId) { this.prerequisiteSubjectId = prerequisiteSubjectId; }
        public RelationshipType getRelationshipType() { return relationshipType; }
        public void setRelationshipType(RelationshipType relationshipType) { this.relationshipType = relationshipType; }
        public boolean isMandatory() { return mandatory; }
        public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
        public String getMinimumGrade() { return minimumGrade; }
        public void setMinimumGrade(String minimumGrade) { this.minimumGrade = minimumGrade; }
        public String getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(String effectiveFrom) { this.effectiveFrom = effectiveFrom; }
        public String getEffectiveTo() { return effectiveTo; }
        public void setEffectiveTo(String effectiveTo) { this.effectiveTo = effectiveTo; }
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
        public long getVersion() { return version; }
        public void setVersion(long version) { this.version = version; }
    }

    // =========================================================================
    // 6. Prerequisite Graph Models
    // =========================================================================

    public static class PrerequisiteNode {
        private String subjectId;
        private String subjectCode;
        private String subjectName;
        private String relationshipType;
        private boolean mandatory;
        private String minimumGrade;
        private String direction; // FORWARD, REVERSE

        public PrerequisiteNode() {}

        public PrerequisiteNode(String subjectId, String subjectCode, String subjectName,
                                String relationshipType, boolean mandatory, String minimumGrade, String direction) {
            this.subjectId = subjectId;
            this.subjectCode = subjectCode;
            this.subjectName = subjectName;
            this.relationshipType = relationshipType;
            this.mandatory = mandatory;
            this.minimumGrade = minimumGrade;
            this.direction = direction;
        }

        public String getSubjectId() { return subjectId; }
        public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
        public String getSubjectCode() { return subjectCode; }
        public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
        public String getSubjectName() { return subjectName; }
        public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
        public String getRelationshipType() { return relationshipType; }
        public void setRelationshipType(String relationshipType) { this.relationshipType = relationshipType; }
        public boolean isMandatory() { return mandatory; }
        public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
        public String getMinimumGrade() { return minimumGrade; }
        public void setMinimumGrade(String minimumGrade) { this.minimumGrade = minimumGrade; }
        public String getDirection() { return direction; }
        public void setDirection(String direction) { this.direction = direction; }
    }

    public static class PrerequisiteGraphView {
        private String subjectId;
        private List<PrerequisiteNode> prerequisites = new ArrayList<>(); // Dependencies (incoming)
        private List<PrerequisiteNode> dependents = new ArrayList<>();    // Dependent subjects (reverse)

        public PrerequisiteGraphView() {}

        public PrerequisiteGraphView(String subjectId) {
            this.subjectId = subjectId;
        }

        public String getSubjectId() { return subjectId; }
        public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
        public List<PrerequisiteNode> getPrerequisites() { return prerequisites; }
        public void setPrerequisites(List<PrerequisiteNode> prerequisites) { this.prerequisites = prerequisites; }
        public List<PrerequisiteNode> getDependents() { return dependents; }
        public void setDependents(List<PrerequisiteNode> dependents) { this.dependents = dependents; }
    }

    // =========================================================================
    // 7. Subject History / Audit Trail (§46)
    // =========================================================================

    public static class SubjectHistory {
        private String id;
        private String tenantId;
        private String subjectId;
        private int versionNo;
        private String action; // CREATE, UPDATE, VERSION_CREATED, VERSION_PUBLISHED, DEACTIVATE, REACTIVATE, etc.
        private String fromStatus;
        private String toStatus;
        private List<String> changedFields = new ArrayList<>();
        private String beforeJson;
        private String afterJson;
        private String reason;
        private String actorId;
        private String actorRole;
        private String correlationId;
        private long occurredAt;
        private String source = "ACD-03";

        public SubjectHistory() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getSubjectId() { return subjectId; }
        public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
        public int getVersionNo() { return versionNo; }
        public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getFromStatus() { return fromStatus; }
        public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
        public String getToStatus() { return toStatus; }
        public void setToStatus(String toStatus) { this.toStatus = toStatus; }
        public List<String> getChangedFields() { return changedFields; }
        public void setChangedFields(List<String> changedFields) { this.changedFields = changedFields; }
        public String getBeforeJson() { return beforeJson; }
        public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }
        public String getAfterJson() { return afterJson; }
        public void setAfterJson(String afterJson) { this.afterJson = afterJson; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getActorId() { return actorId; }
        public void setActorId(String actorId) { this.actorId = actorId; }
        public String getActorRole() { return actorRole; }
        public void setActorRole(String actorRole) { this.actorRole = actorRole; }
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
        public long getOccurredAt() { return occurredAt; }
        public void setOccurredAt(long occurredAt) { this.occurredAt = occurredAt; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }

    // =========================================================================
    // 8. Technical Collections: Outbox, Idempotency & Dead Letter Queue
    // =========================================================================

    public static class OutboxEvent {
        private String id;
        private String eventId;
        private String eventType;
        private String source = "ACD-03";
        private String tenantId;
        private String aggregateId;
        private String payload;
        private String status = "PENDING"; // PENDING, PUBLISHED, FAILED
        private int attempts = 0;
        private long occurredAt;
        private String correlationId;

        public OutboxEvent() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getAggregateId() { return aggregateId; }
        public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getAttempts() { return attempts; }
        public void setAttempts(int attempts) { this.attempts = attempts; }
        public long getOccurredAt() { return occurredAt; }
        public void setOccurredAt(long occurredAt) { this.occurredAt = occurredAt; }
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    }

    public static class IdempotencyRecord {
        private String id;
        private String tenantId;
        private String idempotencyKey;
        private String requestHash;
        private String operation;
        private int statusCode;
        private String responseBody;
        private long createdAt;
        private long expiresAt;

        public IdempotencyRecord() {}

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
        public int getStatusCode() { return statusCode; }
        public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
        public String getResponseBody() { return responseBody; }
        public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getExpiresAt() { return expiresAt; }
        public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
    }

    public static class DeadLetterEvent {
        private String id;
        private String eventId;
        private String source;
        private String errorReason;
        private String payload;
        private int attempts;
        private long createdAt;

        public DeadLetterEvent() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getErrorReason() { return errorReason; }
        public void setErrorReason(String errorReason) { this.errorReason = errorReason; }
        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }
        public int getAttempts() { return attempts; }
        public void setAttempts(int attempts) { this.attempts = attempts; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    public static class ApiKeyRecord {
        private String keyId;
        private String rawKey;
        private String hashedKey;
        private String tenantId;
        private List<String> scopes = new ArrayList<>();
        private long createdAt;
        private long expiresAt;
        private boolean active = true;

        public ApiKeyRecord() {}

        public ApiKeyRecord(String keyId, String rawKey, String tenantId, List<String> scopes, long expiresAt) {
            this.keyId = keyId;
            this.rawKey = rawKey;
            this.tenantId = tenantId;
            this.scopes = scopes != null ? scopes : new ArrayList<>();
            this.createdAt = System.currentTimeMillis();
            this.expiresAt = expiresAt;
            this.active = true;
        }

        public String getKeyId() { return keyId; }
        public void setKeyId(String keyId) { this.keyId = keyId; }
        public String getRawKey() { return rawKey; }
        public void setRawKey(String rawKey) { this.rawKey = rawKey; }
        public String getHashedKey() { return hashedKey; }
        public void setHashedKey(String hashedKey) { this.hashedKey = hashedKey; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public List<String> getScopes() { return scopes; }
        public void setScopes(List<String> scopes) { this.scopes = scopes; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getExpiresAt() { return expiresAt; }
        public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    // =========================================================================
    // 9. Standard Response Envelope (§29.2) & Bulk Results
    // =========================================================================

    public static class BulkImportResult {
        private int totalRows;
        private int successfulRows;
        private int failedRows;
        private List<RowError> errors = new ArrayList<>();
        private List<String> createdSubjectIds = new ArrayList<>();

        public BulkImportResult() {}

        public int getTotalRows() { return totalRows; }
        public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
        public int getSuccessfulRows() { return successfulRows; }
        public void setSuccessfulRows(int successfulRows) { this.successfulRows = successfulRows; }
        public int getFailedRows() { return failedRows; }
        public void setFailedRows(int failedRows) { this.failedRows = failedRows; }
        public List<RowError> getErrors() { return errors; }
        public void setErrors(List<RowError> errors) { this.errors = errors; }
        public List<String> getCreatedSubjectIds() { return createdSubjectIds; }
        public void setCreatedSubjectIds(List<String> createdSubjectIds) { this.createdSubjectIds = createdSubjectIds; }

        public static class RowError {
            private int rowNumber;
            private String subjectCode;
            private String errorCode;
            private String message;

            public RowError() {}

            public RowError(int rowNumber, String subjectCode, String errorCode, String message) {
                this.rowNumber = rowNumber;
                this.subjectCode = subjectCode;
                this.errorCode = errorCode;
                this.message = message;
            }

            public int getRowNumber() { return rowNumber; }
            public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }
            public String getSubjectCode() { return subjectCode; }
            public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
            public String getErrorCode() { return errorCode; }
            public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
            public String getMessage() { return message; }
            public void setMessage(String message) { this.message = message; }
        }
    }
}
