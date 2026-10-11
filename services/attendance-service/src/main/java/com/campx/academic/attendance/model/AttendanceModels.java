package com.campx.academic.attendance.model;

import java.util.*;

/**
 * Data structures and domain models for ACD-06: Attendance Management Service.
 */
public class AttendanceModels {

    // =========================================================================
    // Enums
    // =========================================================================

    public enum SessionStatus {
        DRAFT,
        OPEN,
        SUBMITTED,
        LOCKED,
        CORRECTED,
        ARCHIVED,
        CANCELLED
    }

    public enum AttendanceStatus {
        PRESENT,
        ABSENT,
        LEAVE,
        LATE,
        ON_DUTY
    }

    public enum CaptureSource {
        FACULTY,
        MOBILE,
        BULK,
        IMPORT,
        SYSTEM,
        BIOMETRIC,
        RFID
    }

    public enum CorrectionWorkflowStatus {
        APPROVED,
        PENDING_APPROVAL,
        REJECTED
    }

    // =========================================================================
    // 1. AttendanceSession
    // =========================================================================

    public static class AttendanceSession {
        private String id;
        private String tenantId = "TENANT-001";
        private String batchId;
        private String subjectId;
        private String timetableEntryId;
        private String attendanceDate; // YYYY-MM-DD
        private int periodNo = 1;
        private String startTime = "09:00";
        private String endTime = "10:00";
        private SessionStatus status = SessionStatus.OPEN;
        private int rosterCount = 0;
        private int presentCount = 0;
        private int absentCount = 0;
        private int leaveCount = 0;
        private int lateCount = 0;
        private String markedBy;
        private Long markedAt;
        private String submittedBy;
        private Long submittedAt;
        private String lockedBy;
        private Long lockedAt;
        private int version = 1;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();

        public AttendanceSession() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }

        public String getSubjectId() { return subjectId; }
        public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

        public String getTimetableEntryId() { return timetableEntryId; }
        public void setTimetableEntryId(String timetableEntryId) { this.timetableEntryId = timetableEntryId; }

        public String getAttendanceDate() { return attendanceDate; }
        public void setAttendanceDate(String attendanceDate) { this.attendanceDate = attendanceDate; }

        public int getPeriodNo() { return periodNo; }
        public void setPeriodNo(int periodNo) { this.periodNo = periodNo; }

        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }

        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }

        public SessionStatus getStatus() { return status; }
        public void setStatus(SessionStatus status) { this.status = status; }

        public int getRosterCount() { return rosterCount; }
        public void setRosterCount(int rosterCount) { this.rosterCount = rosterCount; }

        public int getPresentCount() { return presentCount; }
        public void setPresentCount(int presentCount) { this.presentCount = presentCount; }

        public int getAbsentCount() { return absentCount; }
        public void setAbsentCount(int absentCount) { this.absentCount = absentCount; }

        public int getLeaveCount() { return leaveCount; }
        public void setLeaveCount(int leaveCount) { this.leaveCount = leaveCount; }

        public int getLateCount() { return lateCount; }
        public void setLateCount(int lateCount) { this.lateCount = lateCount; }

        public String getMarkedBy() { return markedBy; }
        public void setMarkedBy(String markedBy) { this.markedBy = markedBy; }

        public Long getMarkedAt() { return markedAt; }
        public void setMarkedAt(Long markedAt) { this.markedAt = markedAt; }

        public String getSubmittedBy() { return submittedBy; }
        public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }

        public Long getSubmittedAt() { return submittedAt; }
        public void setSubmittedAt(Long submittedAt) { this.submittedAt = submittedAt; }

        public String getLockedBy() { return lockedBy; }
        public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }

        public Long getLockedAt() { return lockedAt; }
        public void setLockedAt(Long lockedAt) { this.lockedAt = lockedAt; }

        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }

        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    }

    // =========================================================================
    // 2. AttendanceRecord
    // =========================================================================

    public static class AttendanceRecord {
        private String id;
        private String tenantId = "TENANT-001";
        private String sessionId;
        private String studentId;
        private AttendanceStatus status = AttendanceStatus.PRESENT;
        private String statusReason;
        private String markedBy;
        private long markedAt = System.currentTimeMillis();
        private CaptureSource source = CaptureSource.FACULTY;
        private int recordVersion = 1;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();

        public AttendanceRecord() {}

        public AttendanceRecord(String id, String tenantId, String sessionId, String studentId,
                                AttendanceStatus status, String statusReason, String markedBy, CaptureSource source) {
            this.id = id;
            this.tenantId = tenantId;
            this.sessionId = sessionId;
            this.studentId = studentId;
            this.status = status;
            this.statusReason = statusReason;
            this.markedBy = markedBy;
            this.source = source != null ? source : CaptureSource.FACULTY;
            this.markedAt = System.currentTimeMillis();
            this.recordVersion = 1;
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = System.currentTimeMillis();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }

        public AttendanceStatus getStatus() { return status; }
        public void setStatus(AttendanceStatus status) { this.status = status; }

        public String getStatusReason() { return statusReason; }
        public void setStatusReason(String statusReason) { this.statusReason = statusReason; }

        public String getMarkedBy() { return markedBy; }
        public void setMarkedBy(String markedBy) { this.markedBy = markedBy; }

        public long getMarkedAt() { return markedAt; }
        public void setMarkedAt(long markedAt) { this.markedAt = markedAt; }

        public CaptureSource getSource() { return source; }
        public void setSource(CaptureSource source) { this.source = source; }

        public int getRecordVersion() { return recordVersion; }
        public void setRecordVersion(int recordVersion) { this.recordVersion = recordVersion; }

        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    }

    // =========================================================================
    // 3. AttendanceCorrection
    // =========================================================================

    public static class AttendanceCorrection {
        private String id;
        private String tenantId = "TENANT-001";
        private String sessionId;
        private String studentId;
        private String recordId;
        private AttendanceStatus oldStatus;
        private AttendanceStatus newStatus;
        private String reason; // Mandatory
        private String correctedBy;
        private long correctedAt = System.currentTimeMillis();
        private String authorizationScope = "FACULTY";
        private int previousVersion = 1;
        private int newVersion = 2;
        private String correlationId;
        private CorrectionWorkflowStatus workflowStatus = CorrectionWorkflowStatus.APPROVED;
        private String approvedBy;
        private Long approvedAt;
        private String rejectionReason;

        public AttendanceCorrection() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }

        public String getRecordId() { return recordId; }
        public void setRecordId(String recordId) { this.recordId = recordId; }

        public AttendanceStatus getOldStatus() { return oldStatus; }
        public void setOldStatus(AttendanceStatus oldStatus) { this.oldStatus = oldStatus; }

        public AttendanceStatus getNewStatus() { return newStatus; }
        public void setNewStatus(AttendanceStatus newStatus) { this.newStatus = newStatus; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public String getCorrectedBy() { return correctedBy; }
        public void setCorrectedBy(String correctedBy) { this.correctedBy = correctedBy; }

        public long getCorrectedAt() { return correctedAt; }
        public void setCorrectedAt(long correctedAt) { this.correctedAt = correctedAt; }

        public String getAuthorizationScope() { return authorizationScope; }
        public void setAuthorizationScope(String authorizationScope) { this.authorizationScope = authorizationScope; }

        public int getPreviousVersion() { return previousVersion; }
        public void setPreviousVersion(int previousVersion) { this.previousVersion = previousVersion; }

        public int getNewVersion() { return newVersion; }
        public void setNewVersion(int newVersion) { this.newVersion = newVersion; }

        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

        public CorrectionWorkflowStatus getWorkflowStatus() { return workflowStatus; }
        public void setWorkflowStatus(CorrectionWorkflowStatus workflowStatus) { this.workflowStatus = workflowStatus; }

        public String getApprovedBy() { return approvedBy; }
        public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

        public Long getApprovedAt() { return approvedAt; }
        public void setApprovedAt(Long approvedAt) { this.approvedAt = approvedAt; }

        public String getRejectionReason() { return rejectionReason; }
        public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    }

    // =========================================================================
    // 4. AttendanceSummary
    // =========================================================================

    public static class AttendanceSummary {
        private String id;
        private String tenantId = "TENANT-001";
        private String studentId;
        private String subjectId;
        private String termId = "TERM-2026-FALL";
        private String batchId;
        private int totalSessions = 0;
        private int presentCount = 0;
        private int absentCount = 0;
        private int leaveCount = 0;
        private int lateCount = 0;
        private double attendancePercentage = 0.0;
        private boolean shortageFlag = false;
        private double shortageThreshold = 75.0; // 75% minimum
        private long calculatedAt = System.currentTimeMillis();
        private int sourceVersion = 1;
        private Long reconciledAt;

        public AttendanceSummary() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }

        public String getSubjectId() { return subjectId; }
        public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

        public String getTermId() { return termId; }
        public void setTermId(String termId) { this.termId = termId; }

        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }

        public int getTotalSessions() { return totalSessions; }
        public void setTotalSessions(int totalSessions) { this.totalSessions = totalSessions; }

        public int getPresentCount() { return presentCount; }
        public void setPresentCount(int presentCount) { this.presentCount = presentCount; }

        public int getAbsentCount() { return absentCount; }
        public void setAbsentCount(int absentCount) { this.absentCount = absentCount; }

        public int getLeaveCount() { return leaveCount; }
        public void setLeaveCount(int leaveCount) { this.leaveCount = leaveCount; }

        public int getLateCount() { return lateCount; }
        public void setLateCount(int lateCount) { this.lateCount = lateCount; }

        public double getAttendancePercentage() { return attendancePercentage; }
        public void setAttendancePercentage(double attendancePercentage) { this.attendancePercentage = attendancePercentage; }

        public boolean isShortageFlag() { return shortageFlag; }
        public void setShortageFlag(boolean shortageFlag) { this.shortageFlag = shortageFlag; }

        public double getShortageThreshold() { return shortageThreshold; }
        public void setShortageThreshold(double shortageThreshold) { this.shortageThreshold = shortageThreshold; }

        public long getCalculatedAt() { return calculatedAt; }
        public void setCalculatedAt(long calculatedAt) { this.calculatedAt = calculatedAt; }

        public int getSourceVersion() { return sourceVersion; }
        public void setSourceVersion(int sourceVersion) { this.sourceVersion = sourceVersion; }

        public Long getReconciledAt() { return reconciledAt; }
        public void setReconciledAt(Long reconciledAt) { this.reconciledAt = reconciledAt; }
    }

    // =========================================================================
    // 5. OutboxEvent
    // =========================================================================

    public static class OutboxEvent {
        private String id;
        private String eventId;
        private String eventType;
        private String source = "ACD-06";
        private long occurredAt = System.currentTimeMillis();
        private String entityType = "ATTENDANCE_SESSION";
        private String entityId;
        private int version = 1;
        private String correlationId;
        private String payload;
        private String status = "PENDING"; // PENDING, PUBLISHED, FAILED
        private int attempts = 0;

        public OutboxEvent() {}

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

        public String getEntityType() { return entityType; }
        public void setEntityType(String entityType) { this.entityType = entityType; }

        public String getEntityId() { return entityId; }
        public void setEntityId(String entityId) { this.entityId = entityId; }

        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }

        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public int getAttempts() { return attempts; }
        public void setAttempts(int attempts) { this.attempts = attempts; }
    }

    // =========================================================================
    // 6. IdempotencyRecord
    // =========================================================================

    public static class IdempotencyRecord {
        private String idempotencyKey;
        private String tenantId;
        private String requestHash;
        private int statusCode;
        private String responseBody;
        private long createdAt = System.currentTimeMillis();
        private long expiresAt = System.currentTimeMillis() + 86400000L; // 24 hours

        public IdempotencyRecord() {}

        public IdempotencyRecord(String idempotencyKey, String tenantId, String requestHash, int statusCode, String responseBody) {
            this.idempotencyKey = idempotencyKey;
            this.tenantId = tenantId;
            this.requestHash = requestHash;
            this.statusCode = statusCode;
            this.responseBody = responseBody;
            this.createdAt = System.currentTimeMillis();
            this.expiresAt = System.currentTimeMillis() + 86400000L;
        }

        public String getIdempotencyKey() { return idempotencyKey; }
        public String getTenantId() { return tenantId; }
        public String getRequestHash() { return requestHash; }
        public int getStatusCode() { return statusCode; }
        public String getResponseBody() { return responseBody; }
        public long getCreatedAt() { return createdAt; }
        public long getExpiresAt() { return expiresAt; }
    }

    // =========================================================================
    // 7. DeadLetterEvent
    // =========================================================================

    public static class DeadLetterEvent {
        private String id;
        private String eventId;
        private String eventType;
        private String source;
        private String payload;
        private String failureCode;
        private String failureReason;
        private int attemptCount = 3;
        private long createdAt = System.currentTimeMillis();

        public DeadLetterEvent() {}

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

        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    // =========================================================================
    // 8. AttendanceAuditLog
    // =========================================================================

    public static class AttendanceAuditLog {
        private String id = UUID.randomUUID().toString();
        private String tenantId;
        private String action;
        private String actorId;
        private String actorRole;
        private String resourceId;
        private String details;
        private long timestamp = System.currentTimeMillis();
        private String correlationId;

        public AttendanceAuditLog() {}

        public AttendanceAuditLog(String tenantId, String action, String actorId, String actorRole,
                                  String resourceId, String details, String correlationId) {
            this.id = UUID.randomUUID().toString();
            this.tenantId = tenantId;
            this.action = action;
            this.actorId = actorId;
            this.actorRole = actorRole;
            this.resourceId = resourceId;
            this.details = details;
            this.timestamp = System.currentTimeMillis();
            this.correlationId = correlationId;
        }

        public String getId() { return id; }
        public String getTenantId() { return tenantId; }
        public String getAction() { return action; }
        public String getActorId() { return actorId; }
        public String getActorRole() { return actorRole; }
        public String getResourceId() { return resourceId; }
        public String getDetails() { return details; }
        public long getTimestamp() { return timestamp; }
        public String getCorrelationId() { return correlationId; }
    }

    // =========================================================================
    // 9. ApiKeyRecord
    // =========================================================================

    public static class ApiKeyRecord {
        private String keyId;
        private String tenantId;
        private String apiKey;
        private boolean active = true;
        private List<String> allowedScopes = Collections.singletonList("ATTENDANCE_READ");

        public ApiKeyRecord() {}

        public ApiKeyRecord(String keyId, String tenantId, String apiKey) {
            this.keyId = keyId;
            this.tenantId = tenantId;
            this.apiKey = apiKey;
            this.active = true;
            this.allowedScopes = Collections.singletonList("ATTENDANCE_READ");
        }

        public String getKeyId() { return keyId; }
        public String getTenantId() { return tenantId; }
        public String getApiKey() { return apiKey; }
        public boolean isActive() { return active; }
        public List<String> getAllowedScopes() { return allowedScopes; }
    }

    // =========================================================================
    // Reference Caches
    // =========================================================================

    public static class TimetableSlotRef {
        public String timetableEntryId;
        public String batchId;
        public String subjectId;
        public String facultyId;
        public String dayOfWeek;
        public String startTime;
        public String endTime;
        public int version;
        public boolean active = true;

        public TimetableSlotRef() {}

        public TimetableSlotRef(String timetableEntryId, String batchId, String subjectId,
                                String facultyId, String dayOfWeek, String startTime, String endTime, int version) {
            this.timetableEntryId = timetableEntryId;
            this.batchId = batchId;
            this.subjectId = subjectId;
            this.facultyId = facultyId;
            this.dayOfWeek = dayOfWeek;
            this.startTime = startTime;
            this.endTime = endTime;
            this.version = version;
            this.active = true;
        }
    }

    public static class BatchRosterRef {
        public String batchId;
        public String tenantId;
        public Set<String> studentIds = new HashSet<>();

        public BatchRosterRef() {}

        public BatchRosterRef(String batchId, String tenantId, Collection<String> studentIds) {
            this.batchId = batchId;
            this.tenantId = tenantId;
            if (studentIds != null) this.studentIds.addAll(studentIds);
        }
    }

    // =========================================================================
    // Request DTOs
    // =========================================================================

    public static class CreateSessionRequest {
        public String batchId;
        public String subjectId;
        public String timetableEntryId;
        public String attendanceDate; // YYYY-MM-DD
        public int periodNo = 1;
        public String startTime = "09:00";
        public String endTime = "10:00";
    }

    public static class UpdateSessionRequest {
        public String startTime;
        public String endTime;
        public Integer expectedVersion;
    }

    public static class RecordItem {
        public String studentId;
        public String status = "PRESENT";
        public String statusReason;

        public RecordItem() {}

        public RecordItem(String studentId, String status) {
            this.studentId = studentId;
            this.status = status;
        }

        public RecordItem(String studentId, String status, String statusReason) {
            this.studentId = studentId;
            this.status = status;
            this.statusReason = statusReason;
        }
    }

    public static class MarkAttendanceRequest {
        public String submittedBy;
        public List<RecordItem> records = new ArrayList<>();
    }

    public static class CorrectionRequest {
        public String studentId;
        public String newStatus;
        public String reason;
        public Integer expectedVersion;
    }

    public static class BulkImportRow {
        public String batchId;
        public String subjectId;
        public String attendanceDate;
        public int periodNo;
        public String studentId;
        public String status;
        public String statusReason;
    }

    public static class DeviceCaptureRequest {
        public String deviceId;
        public String cardUid;
        public String studentId;
        public long timestamp = System.currentTimeMillis();
        public String readerLocation;
    }

    // =========================================================================
    // StatusCatalogEntry
    // =========================================================================

    public static class StatusCatalogEntry {
        public String statusCode;
        public String name;
        public boolean countsAsPresent;
        public double weight = 1.0;
        public boolean requiresReason = false;

        public StatusCatalogEntry() {}

        public StatusCatalogEntry(String statusCode, String name, boolean countsAsPresent, double weight, boolean requiresReason) {
            this.statusCode = statusCode;
            this.name = name;
            this.countsAsPresent = countsAsPresent;
            this.weight = weight;
            this.requiresReason = requiresReason;
        }

        public String getStatusCode() { return statusCode; }
        public void setStatusCode(String statusCode) { this.statusCode = statusCode; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public boolean isCountsAsPresent() { return countsAsPresent; }
        public void setCountsAsPresent(boolean countsAsPresent) { this.countsAsPresent = countsAsPresent; }

        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }

        public boolean isRequiresReason() { return requiresReason; }
        public void setRequiresReason(boolean requiresReason) { this.requiresReason = requiresReason; }
    }

    // =========================================================================
    // MarkSummaryResponse
    // =========================================================================

    public static class MarkSummaryResponse {
        public String sessionId;
        public int markedCount;
        public int presentCount;
        public int absentCount;
        public int leaveCount;
        public List<String> shortageStudents = new ArrayList<>();

        public MarkSummaryResponse() {}

        public MarkSummaryResponse(String sessionId, int markedCount, int presentCount, int absentCount, int leaveCount, List<String> shortageStudents) {
            this.sessionId = sessionId;
            this.markedCount = markedCount;
            this.presentCount = presentCount;
            this.absentCount = absentCount;
            this.leaveCount = leaveCount;
            this.shortageStudents = shortageStudents != null ? shortageStudents : new ArrayList<>();
        }
    }
}
