package com.campx.academic.course.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain models and projections for ACD-01: Course Management Service.
 * Defines enterprise course aggregates, version snapshots, prerequisite DAG edges,
 * transactional reliability events, and academic integration models.
 */
public final class CourseModels {

    /**
     * Private constructor to prevent direct instantiation of utility model container.
     */
    private CourseModels() {}

    /**
     * Core Course Aggregate representing an academic course offering.
     */
    public static class Course {
        private String id;
        private String tenantId = "DEFAULT_CAMPUS";
        private String institutionId = "INST_DEFAULT";
        private String campusId = "CAMPUS_MAIN";
        private String courseCode; // Normalized unique business code
        private String courseName;
        private String description;
        private int durationYears = 1;
        private double totalCredits = 4.0;
        private String departmentId;
        private String courseType = "THEORY"; // THEORY, LAB, INTEGRATED, PROJECT
        private String courseCategory = "CORE"; // CORE, ELECTIVE, REGULATORY, AUDIT
        private String status = "DRAFT"; // DRAFT, UNDER_REVIEW, APPROVED, ACTIVE, SUSPENDED, DEACTIVATED, ARCHIVED
        private int currentVersion = 1;
        private List<String> tags = new ArrayList<>();
        private List<String> accreditationReferences = new ArrayList<>();
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private String createdBy = "admin";

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getInstitutionId() { return institutionId; }
        public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }
        public String getCampusId() { return campusId; }
        public void setCampusId(String campusId) { this.campusId = campusId; }
        public String getCourseCode() { return courseCode; }
        public void setCourseCode(String courseCode) { this.courseCode = courseCode; }
        public String getCourseName() { return courseName; }
        public void setCourseName(String courseName) { this.courseName = courseName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public int getDurationYears() { return durationYears; }
        public void setDurationYears(int durationYears) { this.durationYears = durationYears; }
        public double getTotalCredits() { return totalCredits; }
        public void setTotalCredits(double totalCredits) { this.totalCredits = totalCredits; }
        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
        public String getCourseType() { return courseType; }
        public void setCourseType(String courseType) { this.courseType = courseType; }
        public String getCourseCategory() { return courseCategory; }
        public void setCourseCategory(String courseCategory) { this.courseCategory = courseCategory; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getCurrentVersion() { return currentVersion; }
        public void setCurrentVersion(int currentVersion) { this.currentVersion = currentVersion; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
        public List<String> getAccreditationReferences() { return accreditationReferences; }
        public void setAccreditationReferences(List<String> accreditationReferences) { this.accreditationReferences = accreditationReferences; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    }

    /**
     * Immutable Point-in-Time Course Version Snapshot.
     */
    public static class CourseVersion {
        private String id;
        private String courseId;
        private String tenantId;
        private int versionNo;
        private String status = "DRAFT"; // DRAFT, EFFECTIVE, SUPERSEDED
        private String snapshotJson;
        private String changeSummary;
        private long effectiveFrom = System.currentTimeMillis();
        private long effectiveTo = 0L;
        private String approvedBy;
        private long approvedAt = 0L;
        private String publishedBy;
        private long publishedAt = 0L;
        private String checksum;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public int getVersionNo() { return versionNo; }
        public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getSnapshotJson() { return snapshotJson; }
        public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
        public String getChangeSummary() { return changeSummary; }
        public void setChangeSummary(String changeSummary) { this.changeSummary = changeSummary; }
        public long getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(long effectiveFrom) { this.effectiveFrom = effectiveFrom; }
        public long getEffectiveTo() { return effectiveTo; }
        public void setEffectiveTo(long effectiveTo) { this.effectiveTo = effectiveTo; }
        public String getApprovedBy() { return approvedBy; }
        public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
        public long getApprovedAt() { return approvedAt; }
        public void setApprovedAt(long approvedAt) { this.approvedAt = approvedAt; }
        public String getPublishedBy() { return publishedBy; }
        public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
        public long getPublishedAt() { return publishedAt; }
        public void setPublishedAt(long publishedAt) { this.publishedAt = publishedAt; }
        public String getChecksum() { return checksum; }
        public void setChecksum(String checksum) { this.checksum = checksum; }
    }

    /**
     * Course Prerequisite Relationship Edge.
     */
    public static class CoursePrerequisite {
        private String id;
        private String tenantId;
        private String courseId;
        private String prerequisiteCourseId;
        private String relationshipType = "MANDATORY"; // MANDATORY, RECOMMENDED
        private String minimumGrade = "C";
        private String status = "ACTIVE"; // ACTIVE, INACTIVE
        private long createdAt = System.currentTimeMillis();
        private String createdBy;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }
        public String getPrerequisiteCourseId() { return prerequisiteCourseId; }
        public void setPrerequisiteCourseId(String prerequisiteCourseId) { this.prerequisiteCourseId = prerequisiteCourseId; }
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

    /**
     * Immutable Course History Record.
     */
    public static class CourseHistory {
        private String id;
        private String tenantId;
        private String courseId;
        private String action; // CREATE, UPDATE, SUBMIT, APPROVE, PUBLISH, ACTIVATE, SUSPEND, DEACTIVATE, ARCHIVE
        private String fromStatus;
        private String toStatus;
        private String actorId;
        private String actorRole;
        private String correlationId;
        private String reason;
        private long occurredAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getFromStatus() { return fromStatus; }
        public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
        public String getToStatus() { return toStatus; }
        public void setToStatus(String toStatus) { this.toStatus = toStatus; }
        public String getActorId() { return actorId; }
        public void setActorId(String actorId) { this.actorId = actorId; }
        public String getActorRole() { return actorRole; }
        public void setActorRole(String actorRole) { this.actorRole = actorRole; }
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public long getOccurredAt() { return occurredAt; }
        public void setOccurredAt(long occurredAt) { this.occurredAt = occurredAt; }
    }

    /**
     * Public / Student Filtered Course Catalog Projection.
     */
    public static class CourseCatalogItem {
        private String courseId;
        private String courseCode;
        private String courseName;
        private String departmentId;
        private double totalCredits;
        private int durationYears;
        private String courseType;
        private String description;
        private int version;
        private List<String> tags = new ArrayList<>();

        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }
        public String getCourseCode() { return courseCode; }
        public void setCourseCode(String courseCode) { this.courseCode = courseCode; }
        public String getCourseName() { return courseName; }
        public void setCourseName(String courseName) { this.courseName = courseName; }
        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
        public double getTotalCredits() { return totalCredits; }
        public void setTotalCredits(double totalCredits) { this.totalCredits = totalCredits; }
        public int getDurationYears() { return durationYears; }
        public void setDurationYears(int durationYears) { this.durationYears = durationYears; }
        public String getCourseType() { return courseType; }
        public void setCourseType(String courseType) { this.courseType = courseType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
    }

    /**
     * Course Batch Offerings (ACD-04 Integration & Deactivation Gating).
     */
    public static class CourseBatchOffering {
        private String id;
        private String courseId;
        private String batchId;
        private String term;
        private String status = "ACTIVE"; // ACTIVE, CLOSED
        private String academicYear = "2026-2027";

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }
        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }
        public String getTerm() { return term; }
        public void setTerm(String term) { this.term = term; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getAcademicYear() { return academicYear; }
        public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }
    }

    /**
     * Course Accreditation & Regulatory Compliance.
     */
    public static class CourseAccreditation {
        private String id;
        private String courseId;
        private String authority; // UGC, AICTE, NBA, NAAC
        private String referenceNumber;
        private long validFrom;
        private long validTo;
        private String status = "ACTIVE";

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }
        public String getAuthority() { return authority; }
        public void setAuthority(String authority) { this.authority = authority; }
        public String getReferenceNumber() { return referenceNumber; }
        public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
        public long getValidFrom() { return validFrom; }
        public void setValidFrom(long validFrom) { this.validFrom = validFrom; }
        public long getValidTo() { return validTo; }
        public void setValidTo(long validTo) { this.validTo = validTo; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /**
     * Course Attendance Policy (ACD-06 Integration).
     */
    public static class CourseAttendancePolicy {
        private String id;
        private String courseId;
        private double minAttendancePercent = 75.0;
        private boolean condonationAllowed = true;
        private double condonationMaxPercent = 65.0;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }
        public double getMinAttendancePercent() { return minAttendancePercent; }
        public void setMinAttendancePercent(double minAttendancePercent) { this.minAttendancePercent = minAttendancePercent; }
        public boolean isCondonationAllowed() { return condonationAllowed; }
        public void setCondonationAllowed(boolean condonationAllowed) { this.condonationAllowed = condonationAllowed; }
        public double getCondonationMaxPercent() { return condonationMaxPercent; }
        public void setCondonationMaxPercent(double condonationMaxPercent) { this.condonationMaxPercent = condonationMaxPercent; }
    }

    /**
     * Course Evaluation Policy (EXM Integration).
     */
    public static class CourseEvaluationPolicy {
        private String id;
        private String courseId;
        private double internalWeightage = 40.0;
        private double externalWeightage = 60.0;
        private double passMarkPercent = 40.0;
        private double totalCredits = 4.0;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }
        public double getInternalWeightage() { return internalWeightage; }
        public void setInternalWeightage(double internalWeightage) { this.internalWeightage = internalWeightage; }
        public double getExternalWeightage() { return externalWeightage; }
        public void setExternalWeightage(double externalWeightage) { this.externalWeightage = externalWeightage; }
        public double getPassMarkPercent() { return passMarkPercent; }
        public void setPassMarkPercent(double passMarkPercent) { this.passMarkPercent = passMarkPercent; }
        public double getTotalCredits() { return totalCredits; }
        public void setTotalCredits(double totalCredits) { this.totalCredits = totalCredits; }
    }

    /**
     * Course Document Metadata Attachment.
     */
    public static class CourseDocument {
        private String id;
        private String courseId;
        private String docType; // SYLLABUS, BROCHURE, REGULATION
        private String title;
        private String fileUrl;
        private int version = 1;
        private String classification = "INTERNAL"; // PUBLIC, INTERNAL, RESTRICTED
        private boolean isCurrent = true;
        private long uploadedAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }
        public String getDocType() { return docType; }
        public void setDocType(String docType) { this.docType = docType; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getFileUrl() { return fileUrl; }
        public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public String getClassification() { return classification; }
        public void setClassification(String classification) { this.classification = classification; }
        public boolean isCurrent() { return isCurrent; }
        public void setCurrent(boolean current) { isCurrent = current; }
        public long getUploadedAt() { return uploadedAt; }
        public void setUploadedAt(long uploadedAt) { this.uploadedAt = uploadedAt; }
    }

    /**
     * Transactional Outbox Event.
     */
    public static class OutboxEvent {
        private String eventId;
        private String eventType;
        private String aggregateId;
        private String tenantId;
        private String payload;
        private String status = "PENDING"; // PENDING, RELAYED, FAILED
        private int retryCount = 0;
        private long createdAt = System.currentTimeMillis();

        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }
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
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public String getId() { return eventId; }
        public void setId(String id) { this.eventId = id; }
        public long getPublishedAt() { return createdAt; }
        public void setPublishedAt(long publishedAt) { }
    }

    // =========================================================================
    // Phase 2: Transactional Reliability Layer Models (User Story Lines 67–68)
    // =========================================================================

    /**
     * ACD01_idempotency_records — Academic command deduplication and request hash verification.
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
     * ACD01_inbox_events — Inbound event deduplication for idempotent event handling (CSV Line 67).
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
     * ACD01_dead_letter_events — Failure observability and manual replay (CSV Line 68).
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
    // Phase 5: ACD-01 Completeness Models (User Story Lines 31–32, 39–40, 59)
    // =========================================================================

    /**
     * ACD01_course_department_associations — Department ownership history (CSV Lines 31–32).
     */
    public static class CourseDepartmentAssociation {
        private String id;
        private String courseId;
        private String departmentId;
        private long effectiveFrom = System.currentTimeMillis();
        private long effectiveTo = 0; // 0 = currently active
        private String reassignedBy;
        private String reason;
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }
        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
        public long getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(long effectiveFrom) { this.effectiveFrom = effectiveFrom; }
        public long getEffectiveTo() { return effectiveTo; }
        public void setEffectiveTo(long effectiveTo) { this.effectiveTo = effectiveTo; }
        public String getReassignedBy() { return reassignedBy; }
        public void setReassignedBy(String reassignedBy) { this.reassignedBy = reassignedBy; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    /**
     * ACD01_course_curriculum_maps — Cross-module reference to ACD-02 Curriculum (CSV Line 39).
     */
    public static class CourseCurriculumMap {
        private String id;
        private String courseId;
        private String curriculumId;
        private long mappedAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }
        public String getCurriculumId() { return curriculumId; }
        public void setCurriculumId(String curriculumId) { this.curriculumId = curriculumId; }
        public long getMappedAt() { return mappedAt; }
        public void setMappedAt(long mappedAt) { this.mappedAt = mappedAt; }
    }

    /**
     * ACD01_course_subject_mappings — Cross-module reference to ACD-03 Subject/Syllabus (CSV Line 40).
     */
    public static class CourseSubjectMapping {
        private String id;
        private String courseId;
        private String subjectId;
        private int semesterNo = 1;
        private long mappedAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }
        public String getSubjectId() { return subjectId; }
        public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
        public int getSemesterNo() { return semesterNo; }
        public void setSemesterNo(int semesterNo) { this.semesterNo = semesterNo; }
        public long getMappedAt() { return mappedAt; }
        public void setMappedAt(long mappedAt) { this.mappedAt = mappedAt; }
    }

    /**
     * ACD01_course_archive_records — Immutable archived course snapshot for historical queries (CSV Line 59).
     */
    public static class CourseArchiveRecord {
        private String id;
        private String courseId;
        private int finalVersionNo;
        private String snapshotJson;
        private String archivedBy;
        private long archivedAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }
        public int getFinalVersionNo() { return finalVersionNo; }
        public void setFinalVersionNo(int finalVersionNo) { this.finalVersionNo = finalVersionNo; }
        public String getSnapshotJson() { return snapshotJson; }
        public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
        public String getArchivedBy() { return archivedBy; }
        public void setArchivedBy(String archivedBy) { this.archivedBy = archivedBy; }
        public long getArchivedAt() { return archivedAt; }
        public void setArchivedAt(long archivedAt) { this.archivedAt = archivedAt; }
    }
}
