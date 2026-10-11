package com.campx.academic.calendar.service;

import java.time.Instant;

/**
 * Structured log entry for compliance, auditing, and observability across ACD-07.
 */
public class StructuredLogEntry {

    private String timestamp;
    private String service = "ACD-07-AcademicCalendarService";
    private String tenantId;
    private String requestId;
    private String correlationId;
    private String actorId;
    private String operation;
    private String outcome;
    private long durationMs;
    private String errorCode;
    private String details;

    public StructuredLogEntry() {
        this.timestamp = Instant.now().toString();
    }

    public static StructuredLogEntry create(String tenantId, String requestId, String correlationId,
                                            String actorId, String operation, String outcome, long durationMs) {
        StructuredLogEntry entry = new StructuredLogEntry();
        entry.tenantId = tenantId;
        entry.requestId = requestId;
        entry.correlationId = correlationId;
        entry.actorId = actorId;
        entry.operation = operation;
        entry.outcome = outcome;
        entry.durationMs = durationMs;
        return entry;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"timestamp\":\"").append(timestamp).append("\",");
        sb.append("\"service\":\"").append(service).append("\",");
        sb.append("\"tenantId\":\"").append(escape(tenantId)).append("\",");
        sb.append("\"requestId\":\"").append(escape(requestId)).append("\",");
        sb.append("\"correlationId\":\"").append(escape(correlationId)).append("\",");
        sb.append("\"actorId\":\"").append(escape(actorId)).append("\",");
        sb.append("\"operation\":\"").append(escape(operation)).append("\",");
        sb.append("\"outcome\":\"").append(escape(outcome)).append("\",");
        sb.append("\"durationMs\":").append(durationMs);
        if (errorCode != null) {
            sb.append(",\"errorCode\":\"").append(escape(errorCode)).append("\"");
        }
        if (details != null) {
            sb.append(",\"details\":\"").append(escape(details)).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    public String getTimestamp() { return timestamp; }
    public String getService() { return service; }
    public String getTenantId() { return tenantId; }
    public String getRequestId() { return requestId; }
    public String getCorrelationId() { return correlationId; }
    public String getActorId() { return actorId; }
    public String getOperation() { return operation; }
    public String getOutcome() { return outcome; }
    public long getDurationMs() { return durationMs; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
