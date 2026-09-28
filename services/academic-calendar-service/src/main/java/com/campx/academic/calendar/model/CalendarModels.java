package com.campx.academic.calendar.model;

import java.util.*;

/**
 * Domain entities, DTOs, Enums, and Technical records for ACD-07 Academic Calendar Service.
 */
public class CalendarModels {

    // =========================================================================
    // Enums
    // =========================================================================

    public enum CalendarStatus {
        DRAFT,
        SUBMITTED,
        APPROVED,
        REJECTED,
        PUBLISHED,
        SUPERSEDED,
        CANCELLED
    }

    public enum TermStatus {
        ACTIVE,
        INACTIVE
    }

    public enum EventType {
        HOLIDAY,
        WORKING_DAY_OVERRIDE,
        ACADEMIC,
        EXAM,
        REGISTRATION,
        ORIENTATION
    }

    public enum WorkingDayImpact {
        WORKING,
        NON_WORKING,
        NO_IMPACT
    }

    // =========================================================================
    // Domain Entities
    // =========================================================================

    public static class AcademicCalendar {
        private String id;
        private String tenantId = "TENANT-001";
        private String institutionId = "INST-001";
        private String campusId = "CAMPUS-001";
        private String academicYear = "2026-2027";
        private String calendarCode;
        private String name;
        private String description;
        private String timezone = "Asia/Kolkata";
        private CalendarStatus status = CalendarStatus.DRAFT;
        private int currentVersion = 1;
        private String effectiveFrom; // YYYY-MM-DD
        private String effectiveTo;   // YYYY-MM-DD
        private Map<String, Object> policyConfig = new HashMap<>();
        private String workflowRef;
        private String rejectionReason;
        private String approvedBy;
        private Long approvedAt;
        private String publishedBy;
        private Long publishedAt;
        private String createdBy;
        private String updatedBy;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();

        public AcademicCalendar() {}

        public AcademicCalendar(String id, String tenantId, String institutionId, String campusId,
                                String academicYear, String calendarCode, String name, String timezone) {
            this.id = id;
            this.tenantId = tenantId;
            this.institutionId = institutionId;
            this.campusId = campusId;
            this.academicYear = academicYear;
            this.calendarCode = calendarCode;
            this.name = name;
            this.timezone = timezone != null ? timezone : "Asia/Kolkata";
            this.status = CalendarStatus.DRAFT;
            this.currentVersion = 1;
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = System.currentTimeMillis();
        }

        // Copy constructor for version cloning
        public AcademicCalendar(AcademicCalendar other, int newVersion, String newId) {
            this.id = newId;
            this.tenantId = other.tenantId;
            this.institutionId = other.institutionId;
            this.campusId = other.campusId;
            this.academicYear = other.academicYear;
            this.calendarCode = other.calendarCode;
            this.name = other.name;
            this.description = other.description;
            this.timezone = other.timezone;
            this.status = CalendarStatus.DRAFT;
            this.currentVersion = newVersion;
            this.effectiveFrom = other.effectiveFrom;
            this.effectiveTo = other.effectiveTo;
            this.policyConfig = new HashMap<>(other.policyConfig);
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = System.currentTimeMillis();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getInstitutionId() { return institutionId; }
        public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

        public String getCampusId() { return campusId; }
        public void setCampusId(String campusId) { this.campusId = campusId; }

        public String getAcademicYear() { return academicYear; }
        public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }

        public String getCalendarCode() { return calendarCode; }
        public void setCalendarCode(String calendarCode) { this.calendarCode = calendarCode; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getTimezone() { return timezone; }
        public void setTimezone(String timezone) { this.timezone = timezone; }

        public CalendarStatus getStatus() { return status; }
        public void setStatus(CalendarStatus status) { this.status = status; }

        public int getCurrentVersion() { return currentVersion; }
        public void setCurrentVersion(int currentVersion) { this.currentVersion = currentVersion; }

        public String getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(String effectiveFrom) { this.effectiveFrom = effectiveFrom; }

        public String getEffectiveTo() { return effectiveTo; }
        public void setEffectiveTo(String effectiveTo) { this.effectiveTo = effectiveTo; }

        public Map<String, Object> getPolicyConfig() { return policyConfig; }
        public void setPolicyConfig(Map<String, Object> policyConfig) { this.policyConfig = policyConfig; }

        public String getWorkflowRef() { return workflowRef; }
        public void setWorkflowRef(String workflowRef) { this.workflowRef = workflowRef; }

        public String getRejectionReason() { return rejectionReason; }
        public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

        public String getApprovedBy() { return approvedBy; }
        public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

        public Long getApprovedAt() { return approvedAt; }
        public void setApprovedAt(Long approvedAt) { this.approvedAt = approvedAt; }

        public String getPublishedBy() { return publishedBy; }
        public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }

        public Long getPublishedAt() { return publishedAt; }
        public void setPublishedAt(Long publishedAt) { this.publishedAt = publishedAt; }

        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class CalendarTerm {
        private String id;
        private String tenantId = "TENANT-001";
        private String calendarId;
        private String termCode;
        private String name;
        private int sequenceNo = 1;
        private String startDate; // YYYY-MM-DD
        private String endDate;   // YYYY-MM-DD
        private String instructionalStartDate;
        private String instructionalEndDate;
        private TermStatus status = TermStatus.ACTIVE;
        private int version = 1;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();

        public CalendarTerm() {}

        public CalendarTerm(String id, String tenantId, String calendarId, String termCode, String name,
                            int sequenceNo, String startDate, String endDate,
                            String instructionalStartDate, String instructionalEndDate) {
            this.id = id;
            this.tenantId = tenantId;
            this.calendarId = calendarId;
            this.termCode = termCode;
            this.name = name;
            this.sequenceNo = sequenceNo;
            this.startDate = startDate;
            this.endDate = endDate;
            this.instructionalStartDate = instructionalStartDate != null ? instructionalStartDate : startDate;
            this.instructionalEndDate = instructionalEndDate != null ? instructionalEndDate : endDate;
            this.status = TermStatus.ACTIVE;
            this.version = 1;
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = System.currentTimeMillis();
        }

        // Copy constructor
        public CalendarTerm(CalendarTerm other, String newCalendarId, String newId) {
            this.id = newId;
            this.tenantId = other.tenantId;
            this.calendarId = newCalendarId;
            this.termCode = other.termCode;
            this.name = other.name;
            this.sequenceNo = other.sequenceNo;
            this.startDate = other.startDate;
            this.endDate = other.endDate;
            this.instructionalStartDate = other.instructionalStartDate;
            this.instructionalEndDate = other.instructionalEndDate;
            this.status = other.status;
            this.version = 1;
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = System.currentTimeMillis();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getCalendarId() { return calendarId; }
        public void setCalendarId(String calendarId) { this.calendarId = calendarId; }

        public String getTermCode() { return termCode; }
        public void setTermCode(String termCode) { this.termCode = termCode; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public int getSequenceNo() { return sequenceNo; }
        public void setSequenceNo(int sequenceNo) { this.sequenceNo = sequenceNo; }

        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }

        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }

        public String getInstructionalStartDate() { return instructionalStartDate; }
        public void setInstructionalStartDate(String instructionalStartDate) { this.instructionalStartDate = instructionalStartDate; }

        public String getInstructionalEndDate() { return instructionalEndDate; }
        public void setInstructionalEndDate(String instructionalEndDate) { this.instructionalEndDate = instructionalEndDate; }

        public TermStatus getStatus() { return status; }
        public void setStatus(TermStatus status) { this.status = status; }

        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }

        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class CalendarEvent {
        private String id;
        private String tenantId = "TENANT-001";
        private String calendarId;
        private String termId;
        private String eventCode;
        private EventType eventType = EventType.ACADEMIC;
        private String title;
        private String description;
        private String startDate; // YYYY-MM-DD
        private String endDate;   // YYYY-MM-DD
        private boolean allDay = true;
        private WorkingDayImpact workingDayImpact = WorkingDayImpact.NO_IMPACT;
        private String category = "GENERAL";
        private TermStatus status = TermStatus.ACTIVE;
        private int version = 1;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();

        public CalendarEvent() {}

        public CalendarEvent(String id, String tenantId, String calendarId, String termId, String eventCode,
                             EventType eventType, String title, String description,
                             String startDate, String endDate, boolean allDay, WorkingDayImpact workingDayImpact) {
            this.id = id;
            this.tenantId = tenantId;
            this.calendarId = calendarId;
            this.termId = termId;
            this.eventCode = eventCode;
            this.eventType = eventType;
            this.title = title;
            this.description = description;
            this.startDate = startDate;
            this.endDate = endDate != null ? endDate : startDate;
            this.allDay = allDay;
            this.workingDayImpact = workingDayImpact != null ? workingDayImpact : WorkingDayImpact.NO_IMPACT;
            this.status = TermStatus.ACTIVE;
            this.version = 1;
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = System.currentTimeMillis();
        }

        // Copy constructor
        public CalendarEvent(CalendarEvent other, String newCalendarId, String newId) {
            this.id = newId;
            this.tenantId = other.tenantId;
            this.calendarId = newCalendarId;
            this.termId = other.termId;
            this.eventCode = other.eventCode;
            this.eventType = other.eventType;
            this.title = other.title;
            this.description = other.description;
            this.startDate = other.startDate;
            this.endDate = other.endDate;
            this.allDay = other.allDay;
            this.workingDayImpact = other.workingDayImpact;
            this.category = other.category;
            this.status = other.status;
            this.version = 1;
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = System.currentTimeMillis();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getCalendarId() { return calendarId; }
        public void setCalendarId(String calendarId) { this.calendarId = calendarId; }

        public String getTermId() { return termId; }
        public void setTermId(String termId) { this.termId = termId; }

        public String getEventCode() { return eventCode; }
        public void setEventCode(String eventCode) { this.eventCode = eventCode; }

        public EventType getEventType() { return eventType; }
        public void setEventType(EventType eventType) { this.eventType = eventType; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }

        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }

        public boolean isAllDay() { return allDay; }
        public void setAllDay(boolean allDay) { this.allDay = allDay; }

        public WorkingDayImpact getWorkingDayImpact() { return workingDayImpact; }
        public void setWorkingDayImpact(WorkingDayImpact workingDayImpact) { this.workingDayImpact = workingDayImpact; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public TermStatus getStatus() { return status; }
        public void setStatus(TermStatus status) { this.status = status; }

        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }

        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class CalendarHistory {
        private String id;
        private String tenantId;
        private String calendarId;
        private String action;
        private CalendarStatus statusFrom;
        private CalendarStatus statusTo;
        private String actorId;
        private String actorRole;
        private long timestamp = System.currentTimeMillis();
        private String reason;
        private String correlationId;
        private String approvalRef;
        private int version;

        public CalendarHistory() {}

        public CalendarHistory(String id, String tenantId, String calendarId, String action,
                               CalendarStatus statusFrom, CalendarStatus statusTo,
                               String actorId, String actorRole, String reason, String correlationId, int version) {
            this.id = id;
            this.tenantId = tenantId;
            this.calendarId = calendarId;
            this.action = action;
            this.statusFrom = statusFrom;
            this.statusTo = statusTo;
            this.actorId = actorId;
            this.actorRole = actorRole;
            this.reason = reason;
            this.correlationId = correlationId;
            this.version = version;
            this.timestamp = System.currentTimeMillis();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getCalendarId() { return calendarId; }
        public void setCalendarId(String calendarId) { this.calendarId = calendarId; }

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }

        public CalendarStatus getStatusFrom() { return statusFrom; }
        public void setStatusFrom(CalendarStatus statusFrom) { this.statusFrom = statusFrom; }

        public CalendarStatus getStatusTo() { return statusTo; }
        public void setStatusTo(CalendarStatus statusTo) { this.statusTo = statusTo; }

        public String getActorId() { return actorId; }
        public void setActorId(String actorId) { this.actorId = actorId; }

        public String getActorRole() { return actorRole; }
        public void setActorRole(String actorRole) { this.actorRole = actorRole; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

        public String getApprovalRef() { return approvalRef; }
        public void setApprovalRef(String approvalRef) { this.approvalRef = approvalRef; }

        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
    }

    // =========================================================================
    // Technical Collections
    // =========================================================================

    public static class OutboxEvent {
        private String id;
        private String aggregateType = "AcademicCalendar";
        private String aggregateId;
        private String eventType;
        private String payload;
        private String tenantId;
        private String status = "PENDING";
        private long createdAt = System.currentTimeMillis();
        private int retryCount = 0;
        private String lastError;

        public OutboxEvent() {}

        public OutboxEvent(String id, String aggregateId, String eventType, String payload, String tenantId) {
            this.id = id;
            this.aggregateId = aggregateId;
            this.eventType = eventType;
            this.payload = payload;
            this.tenantId = tenantId;
            this.status = "PENDING";
            this.createdAt = System.currentTimeMillis();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getAggregateType() { return aggregateType; }
        public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

        public String getAggregateId() { return aggregateId; }
        public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }

        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

        public String getLastError() { return lastError; }
        public void setLastError(String lastError) { this.lastError = lastError; }
    }

    public static class IdempotencyRecord {
        private String key;
        private String tenantId;
        private String requestHash;
        private int responseCode;
        private String responseBody;
        private long createdAt = System.currentTimeMillis();

        public IdempotencyRecord() {}

        public IdempotencyRecord(String key, String tenantId, String requestHash, int responseCode, String responseBody) {
            this.key = key;
            this.tenantId = tenantId;
            this.requestHash = requestHash;
            this.responseCode = responseCode;
            this.responseBody = responseBody;
            this.createdAt = System.currentTimeMillis();
        }

        public String getKey() { return key; }
        public String getTenantId() { return tenantId; }
        public String getRequestHash() { return requestHash; }
        public int getResponseCode() { return responseCode; }
        public String getResponseBody() { return responseBody; }
        public long getCreatedAt() { return createdAt; }
    }

    public static class DeadLetterEvent {
        private String id;
        private String originalEventId;
        private String eventType;
        private String payload;
        private String failureReason;
        private int retryCount;
        private long failedAt = System.currentTimeMillis();

        public DeadLetterEvent() {}

        public DeadLetterEvent(String id, String originalEventId, String eventType, String payload, String failureReason, int retryCount) {
            this.id = id;
            this.originalEventId = originalEventId;
            this.eventType = eventType;
            this.payload = payload;
            this.failureReason = failureReason;
            this.retryCount = retryCount;
            this.failedAt = System.currentTimeMillis();
        }

        public String getId() { return id; }
        public String getOriginalEventId() { return originalEventId; }
        public String getEventType() { return eventType; }
        public String getPayload() { return payload; }
        public String getFailureReason() { return failureReason; }
        public int getRetryCount() { return retryCount; }
        public long getFailedAt() { return failedAt; }
    }

    public static class CalendarAuditLog {
        private String id;
        private String tenantId;
        private String action;
        private String actorId;
        private String actorRole;
        private String resourceId;
        private String detail;
        private long timestamp = System.currentTimeMillis();
        private String correlationId;

        public CalendarAuditLog() {}

        public CalendarAuditLog(String id, String tenantId, String action, String actorId, String actorRole, String resourceId, String detail, String correlationId) {
            this.id = id;
            this.tenantId = tenantId;
            this.action = action;
            this.actorId = actorId;
            this.actorRole = actorRole;
            this.resourceId = resourceId;
            this.detail = detail;
            this.correlationId = correlationId;
            this.timestamp = System.currentTimeMillis();
        }

        public String getId() { return id; }
        public String getTenantId() { return tenantId; }
        public String getAction() { return action; }
        public String getActorId() { return actorId; }
        public String getActorRole() { return actorRole; }
        public String getResourceId() { return resourceId; }
        public String getDetail() { return detail; }
        public long getTimestamp() { return timestamp; }
        public String getCorrelationId() { return correlationId; }
    }

    public static class ApiKeyRecord {
        private String keyId;
        private String apiKey;
        private String tenantId;
        private String scopes = "READ";
        private boolean active = true;

        public ApiKeyRecord() {}

        public ApiKeyRecord(String keyId, String apiKey, String tenantId, String scopes) {
            this.keyId = keyId;
            this.apiKey = apiKey;
            this.tenantId = tenantId;
            this.scopes = scopes;
            this.active = true;
        }

        public String getKeyId() { return keyId; }
        public String getApiKey() { return apiKey; }
        public String getTenantId() { return tenantId; }
        public String getScopes() { return scopes; }
        public boolean isActive() { return active; }
    }

    // =========================================================================
    // Request & Response DTOs
    // =========================================================================

    public static class CreateCalendarRequest {
        public String calendarCode;
        public String name;
        public String description;
        public String timezone = "Asia/Kolkata";
        public String academicYear;
        public String campusId = "CAMPUS-001";
        public String institutionId = "INST-001";
        public String effectiveFrom; // YYYY-MM-DD
        public String effectiveTo;   // YYYY-MM-DD
        public Map<String, Object> policyConfig = new HashMap<>();
    }

    public static class UpdateCalendarRequest {
        public String name;
        public String description;
        public String timezone;
        public String effectiveFrom;
        public String effectiveTo;
        public Map<String, Object> policyConfig;
        public Integer expectedVersion;
    }

    public static class AddTermRequest {
        public String termCode;
        public String name;
        public int sequenceNo = 1;
        public String startDate;
        public String endDate;
        public String instructionalStartDate;
        public String instructionalEndDate;
    }

    public static class UpdateTermRequest {
        public String name;
        public String startDate;
        public String endDate;
        public String instructionalStartDate;
        public String instructionalEndDate;
        public Integer expectedVersion;
    }

    public static class AddEventRequest {
        public String termId;
        public String eventCode;
        public String eventType = "ACADEMIC";
        public String title;
        public String description;
        public String startDate;
        public String endDate;
        public boolean allDay = true;
        public String workingDayImpact = "NO_IMPACT";
        public String category = "GENERAL";
    }

    public static class UpdateEventRequest {
        public String title;
        public String description;
        public String startDate;
        public String endDate;
        public Boolean allDay;
        public String workingDayImpact;
        public String category;
        public Integer expectedVersion;
    }

    public static class SubmitCalendarRequest {
        public String reason;
    }

    public static class ApprovalRequest {
        public String decision; // APPROVED or REJECTED
        public String reason;
    }

    public static class PublishCalendarRequest {
        public Integer expectedVersion;
        public String effectiveFrom;
        public String effectiveTo;
    }

    public static class CloneVersionRequest {
        public String reason;
    }

    public static class ValidationIssue {
        public String severity; // BLOCKING, WARNING
        public String ruleCode;
        public String message;
        public String affectedResource;

        public ValidationIssue() {}

        public ValidationIssue(String severity, String ruleCode, String message, String affectedResource) {
            this.severity = severity;
            this.ruleCode = ruleCode;
            this.message = message;
            this.affectedResource = affectedResource;
        }
    }

    public static class ValidationReport {
        public boolean valid = true;
        public int blockingCount = 0;
        public int warningCount = 0;
        public List<ValidationIssue> issues = new ArrayList<>();

        public void addIssue(String severity, String ruleCode, String message, String resource) {
            issues.add(new ValidationIssue(severity, ruleCode, message, resource));
            if ("BLOCKING".equalsIgnoreCase(severity)) {
                blockingCount++;
                valid = false;
            } else {
                warningCount++;
            }
            valid = (blockingCount == 0);
        }
    }

    public static class EffectiveDateResolution {
        public String operatingDate;
        public String calendarId;
        public int calendarVersion;
        public String termId;
        public String termCode;
        public boolean isInstructionalDay;
        public boolean isHoliday;
        public String holidayTitle;
        public String workingDayStatus; // WORKING, NON_WORKING, OVERRIDE_WORKING, OVERRIDE_NON_WORKING
        public String effectivePrecedenceReason;
    }

    public static class CalendarAnalytics {
        public String calendarId;
        public int totalTerms;
        public int totalEvents;
        public int totalHolidays;
        public int totalInstructionalDays;
        public int workingDayOverrides;
    }
}
