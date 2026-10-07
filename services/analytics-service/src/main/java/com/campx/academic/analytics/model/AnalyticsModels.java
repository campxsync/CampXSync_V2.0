package com.campx.academic.analytics.model;

import java.time.Instant;
import java.util.*;

/**
 * Enterprise Domain Models, Entities, Value Objects, and DTOs
 * for ACD-10: Reporting & Analytics Service.
 *
 * Implements the 5 baseline business collections and 3 technical collections:
 * 1. analytics_facts
 * 2. attendance_metrics
 * 3. academic_metrics
 * 4. timetable_metrics
 * 5. audience_metrics
 * 6. outbox_events
 * 7. idempotency_records
 * 8. dead_letter_events
 * Along with ReportJob, ReportDefinition, AuditLog, and EventEnvelope.
 */
public class AnalyticsModels {

    // =========================================================================
    // Enums
    // =========================================================================

    public enum DataClassification {
        PUBLIC,
        INTERNAL,
        CONFIDENTIAL,
        RESTRICTED
    }

    public enum RiskLevel {
        NONE,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum ReportType {
        ATTENDANCE_SUMMARY,
        TIMETABLE_UTILIZATION,
        ACADEMIC_PROGRESSION,
        EXECUTIVE_DASHBOARD,
        AUDIENCE_ENGAGEMENT
    }

    public enum ReportFormat {
        CSV,
        XLSX,
        PDF
    }

    public enum JobStatus {
        REQUESTED,
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED,
        EXPIRED
    }

    public enum ReplayStatus {
        PENDING,
        REPLAYED,
        ABANDONED
    }

    public enum OutboxStatus {
        PENDING,
        PUBLISHED,
        FAILED
    }

    // =========================================================================
    // 1. Business Collection: analytics_facts
    // =========================================================================

    public static class AnalyticalFact {
        private String factId;
        private String tenantId;
        private String sourceEventId;
        private String sourceEventType;
        private String sourceService;
        private String entityType;
        private String entityId;
        private String subjectRef;
        private String courseRef;
        private String batchRef;
        private String facultyRef;
        private String termRef;
        private String occurredAt;
        private String processedAt;
        private String factType;
        private Map<String, Object> dimensions = new HashMap<>();
        private Map<String, Object> measures = new HashMap<>();
        private String schemaVersion = "1.0";
        private DataClassification dataClassification = DataClassification.INTERNAL;
        private String createdAt;

        public String getFactId() { return factId; }
        public void setFactId(String factId) { this.factId = factId; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getSourceEventId() { return sourceEventId; }
        public void setSourceEventId(String sourceEventId) { this.sourceEventId = sourceEventId; }

        public String getSourceEventType() { return sourceEventType; }
        public void setSourceEventType(String sourceEventType) { this.sourceEventType = sourceEventType; }

        public String getSourceService() { return sourceService; }
        public void setSourceService(String sourceService) { this.sourceService = sourceService; }

        public String getEntityType() { return entityType; }
        public void setEntityType(String entityType) { this.entityType = entityType; }

        public String getEntityId() { return entityId; }
        public void setEntityId(String entityId) { this.entityId = entityId; }

        public String getSubjectRef() { return subjectRef; }
        public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }

        public String getCourseRef() { return courseRef; }
        public void setCourseRef(String courseRef) { this.courseRef = courseRef; }

        public String getBatchRef() { return batchRef; }
        public void setBatchRef(String batchRef) { this.batchRef = batchRef; }

        public String getFacultyRef() { return facultyRef; }
        public void setFacultyRef(String facultyRef) { this.facultyRef = facultyRef; }

        public String getTermRef() { return termRef; }
        public void setTermRef(String termRef) { this.termRef = termRef; }

        public String getOccurredAt() { return occurredAt; }
        public void setOccurredAt(String occurredAt) { this.occurredAt = occurredAt; }

        public String getProcessedAt() { return processedAt; }
        public void setProcessedAt(String processedAt) { this.processedAt = processedAt; }

        public String getFactType() { return factType; }
        public void setFactType(String factType) { this.factType = factType; }

        public Map<String, Object> getDimensions() { return dimensions; }
        public void setDimensions(Map<String, Object> dimensions) { this.dimensions = dimensions; }

        public Map<String, Object> getMeasures() { return measures; }
        public void setMeasures(Map<String, Object> measures) { this.measures = measures; }

        public String getSchemaVersion() { return schemaVersion; }
        public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }

        public DataClassification getDataClassification() { return dataClassification; }
        public void setDataClassification(DataClassification dataClassification) { this.dataClassification = dataClassification; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }

    // =========================================================================
    // 2. Business Collection: attendance_metrics
    // =========================================================================

    public static class AttendanceMetric {
        private String metricId;
        private String tenantId;
        private String periodKey;
        private String batchRef;
        private String subjectRef;
        private String facultyRef;
        private String dateKey;
        private long scheduledCount = 0;
        private long presentCount = 0;
        private long absentCount = 0;
        private long leaveCount = 0;
        private double attendancePercentage = 0.0;
        private double shortageThreshold = 75.0;
        private RiskLevel riskLevel = RiskLevel.NONE;
        private String asOf;
        private long projectionVersion = 1;
        private String updatedAt;

        public String getMetricId() { return metricId; }
        public void setMetricId(String metricId) { this.metricId = metricId; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getPeriodKey() { return periodKey; }
        public void setPeriodKey(String periodKey) { this.periodKey = periodKey; }

        public String getBatchRef() { return batchRef; }
        public void setBatchRef(String batchRef) { this.batchRef = batchRef; }

        public String getSubjectRef() { return subjectRef; }
        public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }

        public String getFacultyRef() { return facultyRef; }
        public void setFacultyRef(String facultyRef) { this.facultyRef = facultyRef; }

        public String getDateKey() { return dateKey; }
        public void setDateKey(String dateKey) { this.dateKey = dateKey; }

        public long getScheduledCount() { return scheduledCount; }
        public void setScheduledCount(long scheduledCount) { this.scheduledCount = Math.max(0, scheduledCount); }

        public long getPresentCount() { return presentCount; }
        public void setPresentCount(long presentCount) { this.presentCount = Math.max(0, presentCount); }

        public long getAbsentCount() { return absentCount; }
        public void setAbsentCount(long absentCount) { this.absentCount = Math.max(0, absentCount); }

        public long getLeaveCount() { return leaveCount; }
        public void setLeaveCount(long leaveCount) { this.leaveCount = Math.max(0, leaveCount); }

        public double getAttendancePercentage() { return attendancePercentage; }
        public void setAttendancePercentage(double attendancePercentage) {
            this.attendancePercentage = Math.min(100.0, Math.max(0.0, attendancePercentage));
        }

        public double getShortageThreshold() { return shortageThreshold; }
        public void setShortageThreshold(double shortageThreshold) { this.shortageThreshold = shortageThreshold; }

        public RiskLevel getRiskLevel() { return riskLevel; }
        public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }

        public String getAsOf() { return asOf; }
        public void setAsOf(String asOf) { this.asOf = asOf; }

        public long getProjectionVersion() { return projectionVersion; }
        public void setProjectionVersion(long projectionVersion) { this.projectionVersion = projectionVersion; }

        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

        public void calculatePercentage() {
            long total = presentCount + absentCount + leaveCount;
            if (total > 0) {
                this.attendancePercentage = Math.round(((double) presentCount / total) * 10000.0) / 100.0;
            } else {
                this.attendancePercentage = 0.0;
            }
        }
    }

    // =========================================================================
    // 3. Business Collection: academic_metrics
    // =========================================================================

    public static class AcademicMetric {
        private String metricId;
        private String tenantId;
        private String periodKey;
        private String courseRef;
        private String subjectRef;
        private String batchRef;
        private String curriculumRef;
        private String assessmentMapRef;
        private int plannedUnits = 0;
        private int completedUnits = 0;
        private double completionPercentage = 0.0;
        private double assessmentCoveragePercentage = 0.0;
        private double progressionScore = 0.0;
        private RiskLevel riskLevel = RiskLevel.NONE;
        private String asOf;
        private long projectionVersion = 1;
        private String updatedAt;

        public String getMetricId() { return metricId; }
        public void setMetricId(String metricId) { this.metricId = metricId; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getPeriodKey() { return periodKey; }
        public void setPeriodKey(String periodKey) { this.periodKey = periodKey; }

        public String getCourseRef() { return courseRef; }
        public void setCourseRef(String courseRef) { this.courseRef = courseRef; }

        public String getSubjectRef() { return subjectRef; }
        public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }

        public String getBatchRef() { return batchRef; }
        public void setBatchRef(String batchRef) { this.batchRef = batchRef; }

        public String getCurriculumRef() { return curriculumRef; }
        public void setCurriculumRef(String curriculumRef) { this.curriculumRef = curriculumRef; }

        public String getAssessmentMapRef() { return assessmentMapRef; }
        public void setAssessmentMapRef(String assessmentMapRef) { this.assessmentMapRef = assessmentMapRef; }

        public int getPlannedUnits() { return plannedUnits; }
        public void setPlannedUnits(int plannedUnits) { this.plannedUnits = Math.max(0, plannedUnits); }

        public int getCompletedUnits() { return completedUnits; }
        public void setCompletedUnits(int completedUnits) { this.completedUnits = Math.max(0, completedUnits); }

        public double getCompletionPercentage() { return completionPercentage; }
        public void setCompletionPercentage(double completionPercentage) {
            this.completionPercentage = Math.min(100.0, Math.max(0.0, completionPercentage));
        }

        public double getAssessmentCoveragePercentage() { return assessmentCoveragePercentage; }
        public void setAssessmentCoveragePercentage(double assessmentCoveragePercentage) {
            this.assessmentCoveragePercentage = Math.min(100.0, Math.max(0.0, assessmentCoveragePercentage));
        }

        public double getProgressionScore() { return progressionScore; }
        public void setProgressionScore(double progressionScore) {
            this.progressionScore = Math.min(100.0, Math.max(0.0, progressionScore));
        }

        public RiskLevel getRiskLevel() { return riskLevel; }
        public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }

        public String getAsOf() { return asOf; }
        public void setAsOf(String asOf) { this.asOf = asOf; }

        public long getProjectionVersion() { return projectionVersion; }
        public void setProjectionVersion(long projectionVersion) { this.projectionVersion = projectionVersion; }

        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

        public void recalculate() {
            if (plannedUnits > 0) {
                this.completionPercentage = Math.round(((double) completedUnits / plannedUnits) * 10000.0) / 100.0;
            } else {
                this.completionPercentage = 0.0;
            }
            this.progressionScore = Math.round((completionPercentage * 0.6 + assessmentCoveragePercentage * 0.4) * 100.0) / 100.0;
        }
    }

    // =========================================================================
    // 4. Business Collection: timetable_metrics
    // =========================================================================

    public static class TimetableMetric {
        private String metricId;
        private String tenantId;
        private String periodKey;
        private String batchRef;
        private String roomRef;
        private String facultyRef;
        private String slotType;
        private long scheduledSlots = 0;
        private long executedSlots = 0;
        private long cancelledSlots = 0;
        private double utilizationPercentage = 0.0;
        private String asOf;
        private long projectionVersion = 1;
        private String updatedAt;

        public String getMetricId() { return metricId; }
        public void setMetricId(String metricId) { this.metricId = metricId; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getPeriodKey() { return periodKey; }
        public void setPeriodKey(String periodKey) { this.periodKey = periodKey; }

        public String getBatchRef() { return batchRef; }
        public void setBatchRef(String batchRef) { this.batchRef = batchRef; }

        public String getRoomRef() { return roomRef; }
        public void setRoomRef(String roomRef) { this.roomRef = roomRef; }

        public String getFacultyRef() { return facultyRef; }
        public void setFacultyRef(String facultyRef) { this.facultyRef = facultyRef; }

        public String getSlotType() { return slotType; }
        public void setSlotType(String slotType) { this.slotType = slotType; }

        public long getScheduledSlots() { return scheduledSlots; }
        public void setScheduledSlots(long scheduledSlots) { this.scheduledSlots = Math.max(0, scheduledSlots); }

        public long getExecutedSlots() { return executedSlots; }
        public void setExecutedSlots(long executedSlots) { this.executedSlots = Math.max(0, executedSlots); }

        public long getCancelledSlots() { return cancelledSlots; }
        public void setCancelledSlots(long cancelledSlots) { this.cancelledSlots = Math.max(0, cancelledSlots); }

        public double getUtilizationPercentage() { return utilizationPercentage; }
        public void setUtilizationPercentage(double utilizationPercentage) {
            this.utilizationPercentage = Math.min(100.0, Math.max(0.0, utilizationPercentage));
        }

        public String getAsOf() { return asOf; }
        public void setAsOf(String asOf) { this.asOf = asOf; }

        public long getProjectionVersion() { return projectionVersion; }
        public void setProjectionVersion(long projectionVersion) { this.projectionVersion = projectionVersion; }

        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

        public void calculateUtilization() {
            if (scheduledSlots > 0) {
                this.utilizationPercentage = Math.round(((double) executedSlots / scheduledSlots) * 10000.0) / 100.0;
            } else {
                this.utilizationPercentage = 0.0;
            }
        }
    }

    // =========================================================================
    // 5. Business Collection: audience_metrics
    // =========================================================================

    public static class AudienceMetric {
        private String metricId;
        private String tenantId;
        private String audienceType;
        private String scopeRef;
        private String metricType;
        private String periodKey;
        private long count = 0;
        private double rate = 0.0;
        private DataClassification dataClassification = DataClassification.INTERNAL;
        private String asOf;
        private String updatedAt;

        public String getMetricId() { return metricId; }
        public void setMetricId(String metricId) { this.metricId = metricId; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getAudienceType() { return audienceType; }
        public void setAudienceType(String audienceType) { this.audienceType = audienceType; }

        public String getScopeRef() { return scopeRef; }
        public void setScopeRef(String scopeRef) { this.scopeRef = scopeRef; }

        public String getMetricType() { return metricType; }
        public void setMetricType(String metricType) { this.metricType = metricType; }

        public String getPeriodKey() { return periodKey; }
        public void setPeriodKey(String periodKey) { this.periodKey = periodKey; }

        public long getCount() { return count; }
        public void setCount(long count) { this.count = Math.max(0, count); }

        public double getRate() { return rate; }
        public void setRate(double rate) { this.rate = rate; }

        public DataClassification getDataClassification() { return dataClassification; }
        public void setDataClassification(DataClassification dataClassification) { this.dataClassification = dataClassification; }

        public String getAsOf() { return asOf; }
        public void setAsOf(String asOf) { this.asOf = asOf; }

        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    }

    // =========================================================================
    // 6. Technical Collection: outbox_events
    // =========================================================================

    public static class OutboxEvent {
        private String eventId;
        private String tenantId;
        private String eventType;
        private String aggregateType;
        private String aggregateId;
        private String payload;
        private OutboxStatus status = OutboxStatus.PENDING;
        private int attemptCount = 0;
        private String nextAttemptAt;
        private String createdAt;
        private String publishedAt;
        private String lastError;

        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }

        public String getAggregateType() { return aggregateType; }
        public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

        public String getAggregateId() { return aggregateId; }
        public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

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

        public String getLastError() { return lastError; }
        public void setLastError(String lastError) { this.lastError = lastError; }
    }

    // =========================================================================
    // 7. Technical Collection: idempotency_records
    // =========================================================================

    public static class IdempotencyRecord {
        private String tenantId;
        private String idempotencyKey;
        private String operation;
        private String requestHash;
        private String status; // PROCESSING, COMPLETED, FAILED
        private String responseBody;
        private int statusCode;
        private String createdAt;
        private String expiresAt;

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }

        public String getRequestHash() { return requestHash; }
        public void setRequestHash(String requestHash) { this.requestHash = requestHash; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getResponseBody() { return responseBody; }
        public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

        public int getStatusCode() { return statusCode; }
        public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

        public String getExpiresAt() { return expiresAt; }
        public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
    }

    // =========================================================================
    // 8. Technical Collection: dead_letter_events
    // =========================================================================

    public static class DeadLetterEvent {
        private String tenantId;
        private String eventId;
        private String eventType;
        private String sourceService;
        private String payload;
        private String failureCode;
        private String failureMessage;
        private int attemptCount = 1;
        private String firstFailedAt;
        private String lastFailedAt;
        private ReplayStatus replayStatus = ReplayStatus.PENDING;
        private String createdAt;

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }

        public String getSourceService() { return sourceService; }
        public void setSourceService(String sourceService) { this.sourceService = sourceService; }

        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }

        public String getFailureCode() { return failureCode; }
        public void setFailureCode(String failureCode) { this.failureCode = failureCode; }

        public String getFailureMessage() { return failureMessage; }
        public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }

        public int getAttemptCount() { return attemptCount; }
        public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

        public String getFirstFailedAt() { return firstFailedAt; }
        public void setFirstFailedAt(String firstFailedAt) { this.firstFailedAt = firstFailedAt; }

        public String getLastFailedAt() { return lastFailedAt; }
        public void setLastFailedAt(String lastFailedAt) { this.lastFailedAt = lastFailedAt; }

        public ReplayStatus getReplayStatus() { return replayStatus; }
        public void setReplayStatus(ReplayStatus replayStatus) { this.replayStatus = replayStatus; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }

    // =========================================================================
    // 9. Report Job / Execution (Export Lifecycle)
    // =========================================================================

    public static class ReportJob {
        private String jobId;
        private String tenantId;
        private ReportType reportType;
        private ReportFormat format;
        private String requesterId;
        private String requesterRole;
        private Map<String, String> filters = new HashMap<>();
        private JobStatus status = JobStatus.QUEUED;
        private String outputRef;
        private String pollUri;
        private long fileSize = 0;
        private int rowCount = 0;
        private String createdAt;
        private String completedAt;
        private String expiresAt;
        private String diagnosticMessage;
        private String fileContent; // Simulated storage content

        public String getJobId() { return jobId; }
        public void setJobId(String jobId) { this.jobId = jobId; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public ReportType getReportType() { return reportType; }
        public void setReportType(ReportType reportType) { this.reportType = reportType; }

        public ReportFormat getFormat() { return format; }
        public void setFormat(ReportFormat format) { this.format = format; }

        public String getRequesterId() { return requesterId; }
        public void setRequesterId(String requesterId) { this.requesterId = requesterId; }

        public String getRequesterRole() { return requesterRole; }
        public void setRequesterRole(String requesterRole) { this.requesterRole = requesterRole; }

        public Map<String, String> getFilters() { return filters; }
        public void setFilters(Map<String, String> filters) { this.filters = filters; }

        public JobStatus getStatus() { return status; }
        public void setStatus(JobStatus status) { this.status = status; }

        public String getOutputRef() { return outputRef; }
        public void setOutputRef(String outputRef) { this.outputRef = outputRef; }

        public String getPollUri() { return pollUri; }
        public void setPollUri(String pollUri) { this.pollUri = pollUri; }

        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }

        public int getRowCount() { return rowCount; }
        public void setRowCount(int rowCount) { this.rowCount = rowCount; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

        public String getCompletedAt() { return completedAt; }
        public void setCompletedAt(String completedAt) { this.completedAt = completedAt; }

        public String getExpiresAt() { return expiresAt; }
        public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }

        public String getDiagnosticMessage() { return diagnosticMessage; }
        public void setDiagnosticMessage(String diagnosticMessage) { this.diagnosticMessage = diagnosticMessage; }

        public String getFileContent() { return fileContent; }
        public void setFileContent(String fileContent) { this.fileContent = fileContent; }
    }

    // =========================================================================
    // 10. Report Definition
    // =========================================================================

    public static class ReportDefinition {
        private String definitionId;
        private String name;
        private String code;
        private String description;
        private ReportType type;
        private List<String> parameters = new ArrayList<>();
        private List<String> allowedFilters = new ArrayList<>();
        private boolean systemFlag = true;
        private String createdBy = "SYSTEM";
        private String createdAt;
        private String updatedAt;

        public String getDefinitionId() { return definitionId; }
        public void setDefinitionId(String definitionId) { this.definitionId = definitionId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public ReportType getType() { return type; }
        public void setType(ReportType type) { this.type = type; }

        public List<String> getParameters() { return parameters; }
        public void setParameters(List<String> parameters) { this.parameters = parameters; }

        public List<String> getAllowedFilters() { return allowedFilters; }
        public void setAllowedFilters(List<String> allowedFilters) { this.allowedFilters = allowedFilters; }

        public boolean isSystemFlag() { return systemFlag; }
        public void setSystemFlag(boolean systemFlag) { this.systemFlag = systemFlag; }

        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    }

    // =========================================================================
    // 11. Audit Log Entry
    // =========================================================================

    public static class AuditLogEntry {
        private String auditId;
        private String tenantId;
        private String action;
        private String actorId;
        private String actorRole;
        private String scope;
        private String decision; // GRANTED, DENIED, COMPLETED, FAILED
        private String details;
        private String correlationId;
        private String timestamp;

        public String getAuditId() { return auditId; }
        public void setAuditId(String auditId) { this.auditId = auditId; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }

        public String getActorId() { return actorId; }
        public void setActorId(String actorId) { this.actorId = actorId; }

        public String getActorRole() { return actorRole; }
        public void setActorRole(String actorRole) { this.actorRole = actorRole; }

        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }

        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }

        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }

        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }

    // =========================================================================
    // 12. Standard Event Envelope
    // =========================================================================

    public static class EventEnvelope {
        private String eventId;
        private String eventType;
        private String source = "ACD-10";
        private String occurredAt;
        private String tenantId;
        private String entityType = "Reporting & Analytics";
        private String entityId;
        private String version = "1.0";
        private String correlationId;
        private String causationId;
        private Map<String, Object> data = new LinkedHashMap<>();

        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public String getOccurredAt() { return occurredAt; }
        public void setOccurredAt(String occurredAt) { this.occurredAt = occurredAt; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getEntityType() { return entityType; }
        public void setEntityType(String entityType) { this.entityType = entityType; }

        public String getEntityId() { return entityId; }
        public void setEntityId(String entityId) { this.entityId = entityId; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

        public String getCausationId() { return causationId; }
        public void setCausationId(String causationId) { this.causationId = causationId; }

        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> data) { this.data = data; }
    }
}
