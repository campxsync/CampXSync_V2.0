package com.campx.academic.attendance.service;

import java.time.Instant;

/**
 * Structured diagnostic and audit log entry conforming to CampX Enterprise Standard.
 */
public class StructuredLogEntry {

    private final String timestamp;
    private final String service;
    private final String tenantId;
    private final String requestId;
    private final String correlationId;
    private final String actorId;
    private final String operation;
    private final String outcome;
    private final long durationMs;

    private StructuredLogEntry(Builder b) {
        this.timestamp = Instant.now().toString();
        this.service = b.service != null ? b.service : "ACD-06-AttendanceManagementService";
        this.tenantId = b.tenantId != null ? b.tenantId : "TENANT-001";
        this.requestId = b.requestId != null ? b.requestId : "REQ-UNKNOWN";
        this.correlationId = b.correlationId != null ? b.correlationId : "CORR-UNKNOWN";
        this.actorId = b.actorId != null ? b.actorId : "ANONYMOUS";
        this.operation = b.operation != null ? b.operation : "UNKNOWN_OPERATION";
        this.outcome = b.outcome != null ? b.outcome : "UNKNOWN";
        this.durationMs = b.durationMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String toJson() {
        return "{" +
                "\"timestamp\":\"" + timestamp + "\"," +
                "\"service\":\"" + escape(service) + "\"," +
                "\"tenantId\":\"" + escape(tenantId) + "\"," +
                "\"requestId\":\"" + escape(requestId) + "\"," +
                "\"correlationId\":\"" + escape(correlationId) + "\"," +
                "\"actorId\":\"" + escape(actorId) + "\"," +
                "\"operation\":\"" + escape(operation) + "\"," +
                "\"outcome\":\"" + escape(outcome) + "\"," +
                "\"durationMs\":" + durationMs +
                "}";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static class Builder {
        private String service = "ACD-06-AttendanceManagementService";
        private String tenantId;
        private String requestId;
        private String correlationId;
        private String actorId;
        private String operation;
        private String outcome;
        private long durationMs;

        public Builder service(String service) { this.service = service; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder requestId(String requestId) { this.requestId = requestId; return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder actorId(String actorId) { this.actorId = actorId; return this; }
        public Builder operation(String operation) { this.operation = operation; return this; }
        public Builder outcome(String outcome) { this.outcome = outcome; return this; }
        public Builder durationMs(long durationMs) { this.durationMs = durationMs; return this; }

        public StructuredLogEntry build() {
            return new StructuredLogEntry(this);
        }
    }
}
