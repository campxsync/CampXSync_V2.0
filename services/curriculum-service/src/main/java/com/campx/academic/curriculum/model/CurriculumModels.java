package com.campx.academic.curriculum.model;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Enterprise domain models, entities, technical collection schemas, and response envelopes
 * for the ACD-02 Curriculum Management Service.
 */
public final class CurriculumModels {

    private CurriculumModels() {}

    // =========================================================================
    // 1. Enums
    // =========================================================================

    public enum CurriculumStatus {
        DRAFT,
        REVIEW,
        APPROVED,
        PUBLISHED,
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

    public enum OutcomeType {
        CO,
        PO,
        PROGRAM,
        COURSE,
        UNIT
    }

    public enum RelationshipType {
        MANDATORY,
        RECOMMENDED,
        CO_REQUISITE
    }

    public enum DataClassification {
        L1_PUBLIC,
        L2_INTERNAL,
        L3_CONFIDENTIAL,
        L4_RESTRICTED
    }

    // =========================================================================
    // 2. Aggregate Root: Curriculum
    // =========================================================================

    public static class Curriculum {
        private String id;
        private String tenantId;
        private String institutionId;
        private String campusId;
        private String courseId;
        private String departmentId;
        private String name;
        private String academicPattern; // CBCS, NEP, etc.
        private CurriculumStatus status;
        private int currentVersion;
        private String effectiveFrom;
        private String effectiveTo;
        private String regulation;
        private boolean flaggedForReview;
        private String flagReason;
        private long createdAt;
        private long updatedAt;
        private String createdBy;
        private String updatedBy;
        private long version; // Optimistic lock

        public Curriculum() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getInstitutionId() { return institutionId; }
        public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }
        public String getCampusId() { return campusId; }
        public void setCampusId(String campusId) { this.campusId = campusId; }
        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }
        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getAcademicPattern() { return academicPattern; }
        public void setAcademicPattern(String academicPattern) { this.academicPattern = academicPattern; }
        public CurriculumStatus getStatus() { return status; }
        public void setStatus(CurriculumStatus status) { this.status = status; }
        public int getCurrentVersion() { return currentVersion; }
        public void setCurrentVersion(int currentVersion) { this.currentVersion = currentVersion; }
        public String getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(String effectiveFrom) { this.effectiveFrom = effectiveFrom; }
        public String getEffectiveTo() { return effectiveTo; }
        public void setEffectiveTo(String effectiveTo) { this.effectiveTo = effectiveTo; }
        public String getRegulation() { return regulation; }
        public void setRegulation(String regulation) { this.regulation = regulation; }
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
    }

    // =========================================================================
    // 3. Curriculum Version Entity
    // =========================================================================

    public static class CurriculumVersion {
        private String id;
        private String curriculumId;
        private String tenantId;
        private int versionNo;
        private String academicYear;
        private VersionStatus status;
        private String effectiveFrom;
        private String effectiveTo;
        private double totalCredits;
        private int semesterCount;
        private String changeSummary;
        private String regulation;
        private String approvedBy;
        private String approvedAt;
        private String publishedBy;
        private String publishedAt;
        private String checksum;
        private List<Semester> semesters = new ArrayList<>();
        private List<SyllabusModule> syllabus = new ArrayList<>();
        private long createdAt;
        private long updatedAt;
        private String createdBy;
        private String updatedBy;
        private long version;

        public CurriculumVersion() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCurriculumId() { return curriculumId; }
        public void setCurriculumId(String curriculumId) { this.curriculumId = curriculumId; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public int getVersionNo() { return versionNo; }
        public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
        public String getAcademicYear() { return academicYear; }
        public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }
        public VersionStatus getStatus() { return status; }
        public void setStatus(VersionStatus status) { this.status = status; }
        public String getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(String effectiveFrom) { this.effectiveFrom = effectiveFrom; }
        public String getEffectiveTo() { return effectiveTo; }
        public void setEffectiveTo(String effectiveTo) { this.effectiveTo = effectiveTo; }
        public double getTotalCredits() { return totalCredits; }
        public void setTotalCredits(double totalCredits) { this.totalCredits = totalCredits; }
        public int getSemesterCount() { return semesterCount; }
        public void setSemesterCount(int semesterCount) { this.semesterCount = semesterCount; }
        public String getChangeSummary() { return changeSummary; }
        public void setChangeSummary(String changeSummary) { this.changeSummary = changeSummary; }
        public String getRegulation() { return regulation; }
        public void setRegulation(String regulation) { this.regulation = regulation; }
        public String getApprovedBy() { return approvedBy; }
        public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
        public String getApprovedAt() { return approvedAt; }
        public void setApprovedAt(String approvedAt) { this.approvedAt = approvedAt; }
        public String getPublishedBy() { return publishedBy; }
        public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
        public String getPublishedAt() { return publishedAt; }
        public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }
        public String getChecksum() { return checksum; }
        public void setChecksum(String checksum) { this.checksum = checksum; }
        public List<Semester> getSemesters() { return semesters; }
        public void setSemesters(List<Semester> semesters) { this.semesters = semesters; }
        public List<SyllabusModule> getSyllabus() { return syllabus; }
        public void setSyllabus(List<SyllabusModule> syllabus) { this.syllabus = syllabus; }
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
    // 4. Semester Structure
    // =========================================================================

    public static class Semester {
        private int semesterNo;
        private String name;
        private String academicYear;

        public Semester() {}
        public Semester(int semesterNo, String name, String academicYear) {
            this.semesterNo = semesterNo;
            this.name = name;
            this.academicYear = academicYear;
        }

        public int getSemesterNo() { return semesterNo; }
        public void setSemesterNo(int semesterNo) { this.semesterNo = semesterNo; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getAcademicYear() { return academicYear; }
        public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }
    }

    // =========================================================================
    // 5. Subject Mapping: CurriculumSubject
    // =========================================================================

    public static class CurriculumSubject {
        private String id;
        private String tenantId;
        private String curriculumId;
        private int versionNo;
        private int semesterNo;
        private String subjectId;
        private int subjectOrder;
        private String subjectType;
        private double credits;
        private double contactHours;
        private boolean mandatory;
        private String electiveGroupId;
        private String status = "ACTIVE";
        private boolean isFlagged;
        private String flagReason;
        private long createdAt;
        private long updatedAt;
        private String createdBy;
        private long version;

        public CurriculumSubject() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getCurriculumId() { return curriculumId; }
        public void setCurriculumId(String curriculumId) { this.curriculumId = curriculumId; }
        public int getVersionNo() { return versionNo; }
        public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
        public int getSemesterNo() { return semesterNo; }
        public void setSemesterNo(int semesterNo) { this.semesterNo = semesterNo; }
        public String getSubjectId() { return subjectId; }
        public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
        public int getSubjectOrder() { return subjectOrder; }
        public void setSubjectOrder(int subjectOrder) { this.subjectOrder = subjectOrder; }
        public String getSubjectType() { return subjectType; }
        public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
        public double getCredits() { return credits; }
        public void setCredits(double credits) { this.credits = credits; }
        public double getContactHours() { return contactHours; }
        public void setContactHours(double contactHours) { this.contactHours = contactHours; }
        public boolean isMandatory() { return mandatory; }
        public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
        public String getElectiveGroupId() { return electiveGroupId; }
        public void setElectiveGroupId(String electiveGroupId) { this.electiveGroupId = electiveGroupId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public boolean isFlagged() { return isFlagged; }
        public void setFlagged(boolean flagged) { isFlagged = flagged; }
        public String getFlagReason() { return flagReason; }
        public void setFlagReason(String flagReason) { this.flagReason = flagReason; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public long getVersion() { return version; }
        public void setVersion(long version) { this.version = version; }
    }

    // =========================================================================
    // 6. Syllabus Structure
    // =========================================================================

    public static class SyllabusModule {
        private String moduleId;
        private String title;
        private int order;
        private List<String> topics = new ArrayList<>();
        private double hours;

        public SyllabusModule() {}
        public SyllabusModule(String moduleId, String title, int order, List<String> topics, double hours) {
            this.moduleId = moduleId;
            this.title = title;
            this.order = order;
            if (topics != null) this.topics = topics;
            this.hours = hours;
        }

        public String getModuleId() { return moduleId; }
        public void setModuleId(String moduleId) { this.moduleId = moduleId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public int getOrder() { return order; }
        public void setOrder(int order) { this.order = order; }
        public List<String> getTopics() { return topics; }
        public void setTopics(List<String> topics) { this.topics = topics; }
        public double getHours() { return hours; }
        public void setHours(double hours) { this.hours = hours; }
    }

    // =========================================================================
    // 7. Learning Outcomes: CurriculumOutcome
    // =========================================================================

    public static class CurriculumOutcome {
        private String id;
        private String tenantId;
        private String curriculumId;
        private int versionNo;
        private String outcomeId;
        private String outcomeCode;
        private String outcomeType; // CO, PO, PROGRAM, COURSE
        private String description;
        private String bloomLevel;
        private String mappedElementType;
        private String mappedElementId;
        private String status = "ACTIVE";
        private long createdAt;
        private String createdBy;

        public CurriculumOutcome() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getCurriculumId() { return curriculumId; }
        public void setCurriculumId(String curriculumId) { this.curriculumId = curriculumId; }
        public int getVersionNo() { return versionNo; }
        public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
        public String getOutcomeId() { return outcomeId; }
        public void setOutcomeId(String outcomeId) { this.outcomeId = outcomeId; }
        public String getOutcomeCode() { return outcomeCode; }
        public void setOutcomeCode(String outcomeCode) { this.outcomeCode = outcomeCode; }
        public String getOutcomeType() { return outcomeType; }
        public void setOutcomeType(String outcomeType) { this.outcomeType = outcomeType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getBloomLevel() { return bloomLevel; }
        public void setBloomLevel(String bloomLevel) { this.bloomLevel = bloomLevel; }
        public String getMappedElementType() { return mappedElementType; }
        public void setMappedElementType(String mappedElementType) { this.mappedElementType = mappedElementType; }
        public String getMappedElementId() { return mappedElementId; }
        public void setMappedElementId(String mappedElementId) { this.mappedElementId = mappedElementId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    }

    // =========================================================================
    // 8. Prerequisites & DAG: CurriculumPrerequisite
    // =========================================================================

    public static class CurriculumPrerequisite {
        private String id;
        private String tenantId;
        private String curriculumId;
        private int versionNo;
        private String prerequisiteCourseId;
        private String prerequisiteCurriculumId;
        private String relationshipType = "MANDATORY"; // MANDATORY, RECOMMENDED, CO_REQUISITE
        private String minimumGrade;
        private String status = "ACTIVE";
        private long createdAt;
        private String createdBy;

        public CurriculumPrerequisite() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getCurriculumId() { return curriculumId; }
        public void setCurriculumId(String curriculumId) { this.curriculumId = curriculumId; }
        public int getVersionNo() { return versionNo; }
        public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
        public String getPrerequisiteCourseId() { return prerequisiteCourseId; }
        public void setPrerequisiteCourseId(String prerequisiteCourseId) { this.prerequisiteCourseId = prerequisiteCourseId; }
        public String getPrerequisiteCurriculumId() { return prerequisiteCurriculumId; }
        public void setPrerequisiteCurriculumId(String prerequisiteCurriculumId) { this.prerequisiteCurriculumId = prerequisiteCurriculumId; }
        public String getRelationshipType() { return relationshipType; }
        public void setRelationshipType(String relationshipType) { this.relationshipType = relationshipType; }
        public String getMinimumGrade() { return minimumGrade; }
        public void setMinimumGrade(String minimumGrade) { this.minimumGrade = minimumGrade; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    }

    // =========================================================================
    // 9. Append-Only Audit History: CurriculumHistory
    // =========================================================================

    public static class CurriculumHistory {
        private String id;
        private String tenantId;
        private String curriculumId;
        private int versionNo;
        private String action;
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
        private String source = "ACD-02";
        private long sequenceNo;

        public CurriculumHistory() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getCurriculumId() { return curriculumId; }
        public void setCurriculumId(String curriculumId) { this.curriculumId = curriculumId; }
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
        public long getSequenceNo() { return sequenceNo; }
        public void setSequenceNo(long sequenceNo) { this.sequenceNo = sequenceNo; }
    }

    // =========================================================================
    // 10. Technical Collections: Outbox, Idempotency & Dead-Letter
    // =========================================================================

    public static class OutboxEvent {
        private String id;
        private String eventId;
        private String eventType;
        private String source = "ACD-02";
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

    /**
     * Entity recording request idempotency execution state and cached responses (Story 55).
     */
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

    /**
     * Technical Dead Letter Queue (DLQ) model for unroutable or exhausted outbox events (Story 54).
     */
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

    // =========================================================================
    // 11. Compliance & Accreditation View DTO
    // =========================================================================

    /**
     * Read-only aggregate view aggregating curriculum versions, learning outcomes,
     * subject allocations, and audit history for external accreditation inspections (NAAC/NBA/UGC - Story 39).
     */
    public static class ComplianceView {
        private Curriculum curriculum;
        private List<CurriculumVersion> versions = new ArrayList<>();
        private List<CurriculumOutcome> outcomes = new ArrayList<>();
        private List<CurriculumSubject> subjectMappings = new ArrayList<>();
        private List<CurriculumHistory> auditHistory = new ArrayList<>();

        public ComplianceView() {}

        public ComplianceView(Curriculum curriculum, List<CurriculumVersion> versions,
                              List<CurriculumOutcome> outcomes, List<CurriculumSubject> subjectMappings,
                              List<CurriculumHistory> auditHistory) {
            this.curriculum = curriculum;
            this.versions = versions != null ? versions : new ArrayList<>();
            this.outcomes = outcomes != null ? outcomes : new ArrayList<>();
            this.subjectMappings = subjectMappings != null ? subjectMappings : new ArrayList<>();
            this.auditHistory = auditHistory != null ? auditHistory : new ArrayList<>();
        }

        public Curriculum getCurriculum() { return curriculum; }
        public void setCurriculum(Curriculum curriculum) { this.curriculum = curriculum; }
        public List<CurriculumVersion> getVersions() { return versions; }
        public void setVersions(List<CurriculumVersion> versions) { this.versions = versions; }
        public List<CurriculumOutcome> getOutcomes() { return outcomes; }
        public void setOutcomes(List<CurriculumOutcome> outcomes) { this.outcomes = outcomes; }
        public List<CurriculumSubject> getSubjectMappings() { return subjectMappings; }
        public void setSubjectMappings(List<CurriculumSubject> subjectMappings) { this.subjectMappings = subjectMappings; }
        public List<CurriculumHistory> getAuditHistory() { return auditHistory; }
        public void setAuditHistory(List<CurriculumHistory> auditHistory) { this.auditHistory = auditHistory; }
    }

    // =========================================================================
    // 12. API Key Authentication Record
    // =========================================================================

    /**
     * API Key authentication and authorization record for external SIS and reporting integrations (Story 41).
     */
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
}
