package com.campx.academic.timetable.model;

import java.util.*;

/**
 * Enterprise domain models, entities, technical collection schemas, and response envelopes
 * for the ACD-05 Timetable Management Service.
 */
public final class TimetableModels {

    private TimetableModels() {}

    // =========================================================================
    // 1. Enums
    // =========================================================================

    public enum TimetableStatus {
        DRAFT,
        VALIDATING,
        VALIDATED,
        INVALID,
        PUBLISHED,
        SUPERSEDED,
        ARCHIVED,
        DELETED
    }

    public enum EntryType {
        TH,   // Theory
        PR,   // Practical
        LAB,  // Laboratory
        TU,   // Tutorial
        SEM,  // Seminar
        EVT,  // Event
        EXM   // Exam
    }

    public enum DayOfWeek {
        MONDAY,
        TUESDAY,
        WEDNESDAY,
        THURSDAY,
        FRIDAY,
        SATURDAY,
        SUNDAY
    }

    public enum EntryStatus {
        ACTIVE,
        REMOVED
    }

    public enum ConflictType {
        FACULTY,
        ROOM,
        BATCH,
        DUPLICATE_ENTRY,
        CALENDAR,
        AVAILABILITY,
        POLICY
    }

    public enum ConflictSeverity {
        BLOCKING,
        WARNING
    }

    public enum ConflictResolutionStatus {
        OPEN,
        RESOLVED,
        ACCEPTED
    }

    public enum ValidationStatus {
        NOT_VALIDATED,
        PASSED,
        FAILED
    }

    public enum EventStatus {
        PENDING,
        RELAYED,
        FAILED
    }

    public enum DataClassification {
        L1_PUBLIC,
        L2_INTERNAL,
        L3_RESTRICTED,
        L4_OPERATIONS_ONLY
    }

    // =========================================================================
    // 2. Collection 1: timetables
    // =========================================================================

    public static class Timetable {
        private String id;
        private String tenantId = "TENANT-001";
        private String institutionId = "INST-001";
        private String campusId;
        private String departmentId;
        private String programId;
        private String timetableCode;
        private String name;
        private String academicYear;
        private String semester;
        private String termId;
        private String batchId;
        private TimetableStatus status = TimetableStatus.DRAFT;
        private int currentVersionNo = 1;
        private String effectiveFrom;
        private String effectiveTo;
        private long createdAt;
        private long updatedAt;
        private String createdBy;
        private String updatedBy;
        private long version = 1L; // Optimistic concurrency lock (BR-10)
        private boolean deleted = false; // Soft deletion (FR-01)
        private boolean revalidationFlag = false;

        public Timetable() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getInstitutionId() { return institutionId; }
        public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

        public String getCampusId() { return campusId; }
        public void setCampusId(String campusId) { this.campusId = campusId; }

        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

        public String getProgramId() { return programId; }
        public void setProgramId(String programId) { this.programId = programId; }

        public String getTimetableCode() { return timetableCode; }
        public void setTimetableCode(String timetableCode) { this.timetableCode = timetableCode; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getAcademicYear() { return academicYear; }
        public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }

        public String getSemester() { return semester; }
        public void setSemester(String semester) { this.semester = semester; }

        public String getTermId() { return termId; }
        public void setTermId(String termId) { this.termId = termId; }

        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }

        public TimetableStatus getStatus() { return status; }
        public void setStatus(TimetableStatus status) { this.status = status; }

        public int getCurrentVersionNo() { return currentVersionNo; }
        public void setCurrentVersionNo(int currentVersionNo) { this.currentVersionNo = currentVersionNo; }

        public String getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(String effectiveFrom) { this.effectiveFrom = effectiveFrom; }

        public String getEffectiveTo() { return effectiveTo; }
        public void setEffectiveTo(String effectiveTo) { this.effectiveTo = effectiveTo; }

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

        public boolean isDeleted() { return deleted; }
        public void setDeleted(boolean deleted) { this.deleted = deleted; }

        public boolean isRevalidationFlag() { return revalidationFlag; }
        public void setRevalidationFlag(boolean revalidationFlag) { this.revalidationFlag = revalidationFlag; }
    }

    // =========================================================================
    // 3. Collection 2: timetable_entries
    // =========================================================================

    public static class TimetableEntry {
        private String id;
        private String tenantId = "TENANT-001";
        private String timetableId;
        private int versionNo = 1;
        private String batchId;
        private String subjectId;
        private String facultyId;
        private String roomId;
        private DayOfWeek dayOfWeek;
        private int period;
        private String periodId;
        private String startTime;
        private String endTime;
        private EntryType entryType = EntryType.TH;
        private EntryStatus status = EntryStatus.ACTIVE;
        private boolean isAdHoc = false;
        private long createdAt;
        private long updatedAt;

        public TimetableEntry() {}

        public TimetableEntry(TimetableEntry other) {
            this.id = other.id;
            this.tenantId = other.tenantId;
            this.timetableId = other.timetableId;
            this.versionNo = other.versionNo;
            this.batchId = other.batchId;
            this.subjectId = other.subjectId;
            this.facultyId = other.facultyId;
            this.roomId = other.roomId;
            this.dayOfWeek = other.dayOfWeek;
            this.period = other.period;
            this.periodId = other.periodId;
            this.startTime = other.startTime;
            this.endTime = other.endTime;
            this.entryType = other.entryType;
            this.status = other.status;
            this.isAdHoc = other.isAdHoc;
            this.createdAt = other.createdAt;
            this.updatedAt = other.updatedAt;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getTimetableId() { return timetableId; }
        public void setTimetableId(String timetableId) { this.timetableId = timetableId; }

        public int getVersionNo() { return versionNo; }
        public void setVersionNo(int versionNo) { this.versionNo = versionNo; }

        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }

        public String getSubjectId() { return subjectId; }
        public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

        public String getFacultyId() { return facultyId; }
        public void setFacultyId(String facultyId) { this.facultyId = facultyId; }

        public String getRoomId() { return roomId; }
        public void setRoomId(String roomId) { this.roomId = roomId; }

        public DayOfWeek getDayOfWeek() { return dayOfWeek; }
        public void setDayOfWeek(DayOfWeek dayOfWeek) { this.dayOfWeek = dayOfWeek; }

        public int getPeriod() { return period; }
        public void setPeriod(int period) {
            this.period = period;
            if (this.periodId == null) {
                this.periodId = "P" + period;
            }
        }

        public String getPeriodId() { return periodId; }
        public void setPeriodId(String periodId) { this.periodId = periodId; }

        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }

        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }

        public EntryType getEntryType() { return entryType; }
        public void setEntryType(EntryType entryType) { this.entryType = entryType; }

        public EntryStatus getStatus() { return status; }
        public void setStatus(EntryStatus status) { this.status = status; }

        public boolean isAdHoc() { return isAdHoc; }
        public void setAdHoc(boolean adHoc) { isAdHoc = adHoc; }

        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    }

    // =========================================================================
    // 4. Collection 3: timetable_versions
    // =========================================================================

    public static class TimetableVersion {
        private String id;
        private String tenantId = "TENANT-001";
        private String timetableId;
        private int versionNo = 1;
        private TimetableStatus status = TimetableStatus.DRAFT;
        private String effectiveFrom;
        private String effectiveTo;
        private ValidationStatus validationStatus = ValidationStatus.NOT_VALIDATED;
        private int blockingConflictCount = 0;
        private int warningCount = 0;
        private Long publishedAt;
        private String publishedBy;
        private Integer sourceVersionNo;
        private long createdAt;
        private List<TimetableEntry> entriesSnapshot = new ArrayList<>();

        public TimetableVersion() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getTimetableId() { return timetableId; }
        public void setTimetableId(String timetableId) { this.timetableId = timetableId; }

        public int getVersionNo() { return versionNo; }
        public void setVersionNo(int versionNo) { this.versionNo = versionNo; }

        public TimetableStatus getStatus() { return status; }
        public void setStatus(TimetableStatus status) { this.status = status; }

        public String getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(String effectiveFrom) { this.effectiveFrom = effectiveFrom; }

        public String getEffectiveTo() { return effectiveTo; }
        public void setEffectiveTo(String effectiveTo) { this.effectiveTo = effectiveTo; }

        public ValidationStatus getValidationStatus() { return validationStatus; }
        public void setValidationStatus(ValidationStatus validationStatus) { this.validationStatus = validationStatus; }

        public int getBlockingConflictCount() { return blockingConflictCount; }
        public void setBlockingConflictCount(int blockingConflictCount) { this.blockingConflictCount = blockingConflictCount; }

        public int getWarningCount() { return warningCount; }
        public void setWarningCount(int warningCount) { this.warningCount = warningCount; }

        public Long getPublishedAt() { return publishedAt; }
        public void setPublishedAt(Long publishedAt) { this.publishedAt = publishedAt; }

        public String getPublishedBy() { return publishedBy; }
        public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }

        public Integer getSourceVersionNo() { return sourceVersionNo; }
        public void setSourceVersionNo(Integer sourceVersionNo) { this.sourceVersionNo = sourceVersionNo; }

        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

        public List<TimetableEntry> getEntriesSnapshot() { return entriesSnapshot; }
        public void setEntriesSnapshot(List<TimetableEntry> entriesSnapshot) { this.entriesSnapshot = entriesSnapshot; }
    }

    // =========================================================================
    // 5. Collection 4: conflict_results
    // =========================================================================

    public static class ConflictResult {
        private String id;
        private String tenantId = "TENANT-001";
        private String timetableId;
        private int versionNo;
        private ConflictType conflictType;
        private ConflictSeverity severity = ConflictSeverity.BLOCKING;
        private List<String> entryIds = new ArrayList<>();
        private String message;
        private long detectedAt;
        private ConflictResolutionStatus resolutionStatus = ConflictResolutionStatus.OPEN;
        private String resolvedBy;
        private Long resolvedAt;

        public ConflictResult() {}

        public ConflictResult(String timetableId, int versionNo, ConflictType conflictType,
                              ConflictSeverity severity, List<String> entryIds, String message) {
            this.id = "CNF-" + UUID.randomUUID().toString().substring(0, 8);
            this.timetableId = timetableId;
            this.versionNo = versionNo;
            this.conflictType = conflictType;
            this.severity = severity;
            this.entryIds = entryIds != null ? entryIds : new ArrayList<>();
            this.message = message;
            this.detectedAt = System.currentTimeMillis();
            this.resolutionStatus = ConflictResolutionStatus.OPEN;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getTimetableId() { return timetableId; }
        public void setTimetableId(String timetableId) { this.timetableId = timetableId; }

        public int getVersionNo() { return versionNo; }
        public void setVersionNo(int versionNo) { this.versionNo = versionNo; }

        public ConflictType getConflictType() { return conflictType; }
        public void setConflictType(ConflictType conflictType) { this.conflictType = conflictType; }

        public ConflictSeverity getSeverity() { return severity; }
        public void setSeverity(ConflictSeverity severity) { this.severity = severity; }

        public List<String> getEntryIds() { return entryIds; }
        public void setEntryIds(List<String> entryIds) { this.entryIds = entryIds; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public long getDetectedAt() { return detectedAt; }
        public void setDetectedAt(long detectedAt) { this.detectedAt = detectedAt; }

        public ConflictResolutionStatus getResolutionStatus() { return resolutionStatus; }
        public void setResolutionStatus(ConflictResolutionStatus resolutionStatus) { this.resolutionStatus = resolutionStatus; }

        public String getResolvedBy() { return resolvedBy; }
        public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

        public Long getResolvedAt() { return resolvedAt; }
        public void setResolvedAt(Long resolvedAt) { this.resolvedAt = resolvedAt; }
    }

    // =========================================================================
    // 6. Collection 5: outbox_events
    // =========================================================================

    public static class OutboxEvent {
        private String id;
        private String eventId;
        private String eventType;
        private String source = "ACD-05";
        private String occurredAt;
        private String entityType = "Timetable Management";
        private String entityId;
        private String version = "1.0";
        private String correlationId;
        private Map<String, Object> data = new LinkedHashMap<>();
        private EventStatus status = EventStatus.PENDING;
        private int retryCount = 0;

        public OutboxEvent() {
            this.id = UUID.randomUUID().toString();
            this.eventId = "EVT-ACD-05-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            this.occurredAt = java.time.Instant.now().toString();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public String getOccurredAt() { return occurredAt; }
        public void setOccurredAt(String occurredAt) { this.occurredAt = occurredAt; }

        public String getEntityType() { return entityType; }
        public void setEntityType(String entityType) { this.entityType = entityType; }

        public String getEntityId() { return entityId; }
        public void setEntityId(String entityId) { this.entityId = entityId; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> data) { this.data = data; }

        public EventStatus getStatus() { return status; }
        public void setStatus(EventStatus status) { this.status = status; }

        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    }

    // =========================================================================
    // 7. Collection 6: idempotency_records
    // =========================================================================

    public static class IdempotencyRecord {
        private String id;
        private String idempotencyKey;
        private String tenantId;
        private String requestHash;
        private String status = "COMPLETED";
        private int statusCode;
        private String responseBody;
        private long createdAt;
        private long expiresAt;

        public IdempotencyRecord() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getRequestHash() { return requestHash; }
        public void setRequestHash(String requestHash) { this.requestHash = requestHash; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public int getStatusCode() { return statusCode; }
        public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

        public String getResponseBody() { return responseBody; }
        public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

        public long getExpiresAt() { return expiresAt; }
        public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
    }

    // =========================================================================
    // 8. Collection 7: dead_letter_events
    // =========================================================================

    public static class DeadLetterEvent {
        private String id;
        private String eventId;
        private String eventType;
        private String source;
        private long occurredAt;
        private String payload;
        private String failureReason;
        private int retryAttempts;
        private long createdAt;

        public DeadLetterEvent() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public long getOccurredAt() { return occurredAt; }
        public void setOccurredAt(long occurredAt) { this.occurredAt = occurredAt; }

        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }

        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

        public int getRetryAttempts() { return retryAttempts; }
        public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }

        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    // =========================================================================
    // 9. TimetableAuditLog (Append-Only)
    // =========================================================================

    public static class TimetableAuditLog {
        private String id;
        private String tenantId;
        private String timetableId;
        private String action;
        private String actorId;
        private String userRole;
        private String details;
        private long timestamp;

        public TimetableAuditLog() {}

        public TimetableAuditLog(String tenantId, String timetableId, String action,
                                 String actorId, String userRole, String details) {
            this.id = UUID.randomUUID().toString();
            this.tenantId = tenantId;
            this.timetableId = timetableId;
            this.action = action;
            this.actorId = actorId;
            this.userRole = userRole;
            this.details = details;
            this.timestamp = System.currentTimeMillis();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getTimetableId() { return timetableId; }
        public void setTimetableId(String timetableId) { this.timetableId = timetableId; }

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }

        public String getActorId() { return actorId; }
        public void setActorId(String actorId) { this.actorId = actorId; }

        public String getUserRole() { return userRole; }
        public void setUserRole(String userRole) { this.userRole = userRole; }

        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    // =========================================================================
    // 10. WeeklyTemplate (Story 46)
    // =========================================================================

    public static class WeeklyTemplate {
        private String id;
        private String tenantId = "TENANT-001";
        private String templateCode;
        private String name;
        private String departmentId;
        private String programId;
        private List<CreateEntryRequest> templateEntries = new ArrayList<>();

        public WeeklyTemplate() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getTemplateCode() { return templateCode; }
        public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

        public String getProgramId() { return programId; }
        public void setProgramId(String programId) { this.programId = programId; }

        public List<CreateEntryRequest> getTemplateEntries() { return templateEntries; }
        public void setTemplateEntries(List<CreateEntryRequest> templateEntries) { this.templateEntries = templateEntries; }
    }

    // =========================================================================
    // 11. Security & External Access Record
    // =========================================================================

    public static class ApiKeyRecord {
        private String keyId;
        private String apiKey;
        private String tenantId;
        private String role = "EXTERNAL_API";
        private boolean active = true;

        public ApiKeyRecord() {}

        public ApiKeyRecord(String keyId, String apiKey, String tenantId) {
            this.keyId = keyId;
            this.apiKey = apiKey;
            this.tenantId = tenantId;
        }

        public String getKeyId() { return keyId; }
        public void setKeyId(String keyId) { this.keyId = keyId; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    // =========================================================================
    // 12. Request / Response DTOs
    // =========================================================================

    public static class CreateTimetableRequest {
        public String timetableCode;
        public String name;
        public String academicYear;
        public String semester;
        public String termId;
        public String departmentId;
        public String programId;
        public String batchId;
        public String effectiveFrom;
        public String effectiveTo;
    }

    public static class UpdateTimetableRequest {
        public String name;
        public String academicYear;
        public String semester;
        public String termId;
        public String departmentId;
        public String programId;
        public String effectiveFrom;
        public String effectiveTo;
        public Long version; // Optimistic concurrency check
    }

    public static class CreateEntryRequest {
        public String batchId;
        public String subjectId;
        public String facultyId;
        public String roomId;
        public String dayOfWeek;
        public Integer period;
        public String periodId;
        public String startTime;
        public String endTime;
        public String entryType = "TH";
        public Boolean isAdHoc = false;
    }

    public static class UpdateEntryRequest {
        public String batchId;
        public String subjectId;
        public String facultyId;
        public String roomId;
        public String dayOfWeek;
        public Integer period;
        public String periodId;
        public String startTime;
        public String endTime;
        public String entryType;
        public Boolean isAdHoc;
    }

    public static class BulkEntriesRequest {
        public List<CreateEntryRequest> entries = new ArrayList<>();
    }

    public static class BulkRowError {
        public int rowIndex;
        public String errorCode;
        public String reason;

        public BulkRowError(int rowIndex, String errorCode, String reason) {
            this.rowIndex = rowIndex;
            this.errorCode = errorCode;
            this.reason = reason;
        }
    }

    public static class BulkEntriesResult {
        public List<TimetableEntry> successfulEntries = new ArrayList<>();
        public List<BulkRowError> errors = new ArrayList<>();
    }

    public static class PublishRequest {
        public String effectiveFrom;
        public String effectiveTo;
        public String comment;
    }

    public static class CloneRequest {
        public String reason;
    }

    public static class AdHocSessionRequest {
        public String date;
        public String dayOfWeek;
        public Integer period;
        public String batchId;
        public String subjectId;
        public String facultyId;
        public String roomId;
        public String entryType = "EVT";
        public String reason;
    }
}
