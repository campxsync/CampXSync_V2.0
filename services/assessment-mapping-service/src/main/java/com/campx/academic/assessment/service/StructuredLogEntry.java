package com.campx.academic.assessment.service;

import java.time.Instant;

/**
 * Structured log record for 100% audit compliance, debugging, and tracing across ACD-09.
 */
public class StructuredLogEntry {

    private String tenantId;
    private String traceId;
    private String correlationId;
    private String userId;
    private String userRole;
    private String action;
    private String outcome;
    private long durationMs;
    private String timestamp;
    private String details;

    public StructuredLogEntry() {
        this.timestamp = Instant.now().toString();
    }

    public static StructuredLogEntry create(String tenantId, String traceId, String correlationId,
                                            String userId, String userRole, String action,
                                            String outcome, long durationMs) {
        StructuredLogEntry entry = new StructuredLogEntry();
        entry.tenantId = tenantId;
        entry.traceId = traceId;
        entry.correlationId = correlationId;
        entry.userId = userId;
        entry.userRole = userRole;
        entry.action = action;
        entry.outcome = outcome;
        entry.durationMs = durationMs;
        return entry;
    }

    public String toJson() {
        return "{"
                + "\"tenantId\":\"" + escape(tenantId) + "\","
                + "\"traceId\":\"" + escape(traceId) + "\","
                + "\"correlationId\":\"" + escape(correlationId) + "\","
                + "\"userId\":\"" + escape(userId) + "\","
                + "\"userRole\":\"" + escape(userRole) + "\","
                + "\"action\":\"" + escape(action) + "\","
                + "\"outcome\":\"" + escape(outcome) + "\","
                + "\"durationMs\":" + durationMs + ","
                + "\"timestamp\":\"" + timestamp + "\""
                + (details != null ? ",\"details\":\"" + escape(details) + "\"" : "")
                + "}";
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"");
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserRole() { return userRole; }
    public void setUserRole(String userRole) { this.userRole = userRole; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
