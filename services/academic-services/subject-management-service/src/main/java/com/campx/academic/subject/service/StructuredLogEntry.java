package com.campx.academic.subject.service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Structured Audit and Diagnostic Log Entry conforming to Enterprise Logging Standard (Story 67, §51).
 * Contains all mandatory fields:
 * 1. timestamp
 * 2. service
 * 3. tenantId
 * 4. requestId
 * 5. correlationId
 * 6. actorId
 * 7. operation
 * 8. outcome
 * 9. durationMs
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
        this.timestamp = b.timestamp != null ? b.timestamp : formatIso(new Date());
        this.service = b.service != null ? b.service : "ACD-03-SubjectManagementService";
        this.tenantId = b.tenantId != null ? b.tenantId : "UNKNOWN";
        this.requestId = b.requestId != null ? b.requestId : "REQ-UNKNOWN";
        this.correlationId = b.correlationId != null ? b.correlationId : "CORR-UNKNOWN";
        this.actorId = b.actorId != null ? b.actorId : "anonymous";
        this.operation = b.operation != null ? b.operation : "UNKNOWN";
        this.outcome = b.outcome != null ? b.outcome : "UNKNOWN";
        this.durationMs = b.durationMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String toJson() {
        return "{"
                + "\"timestamp\":\"" + escape(timestamp) + "\","
                + "\"service\":\"" + escape(service) + "\","
                + "\"tenantId\":\"" + escape(tenantId) + "\","
                + "\"requestId\":\"" + escape(requestId) + "\","
                + "\"correlationId\":\"" + escape(correlationId) + "\","
                + "\"actorId\":\"" + escape(actorId) + "\","
                + "\"operation\":\"" + escape(operation) + "\","
                + "\"outcome\":\"" + escape(outcome) + "\","
                + "\"durationMs\":" + durationMs
                + "}";
    }

    private static String formatIso(Date d) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(d);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
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

    public static class Builder {
        private String timestamp;
        private String service = "ACD-03-SubjectManagementService";
        private String tenantId;
        private String requestId;
        private String correlationId;
        private String actorId;
        private String operation;
        private String outcome;
        private long durationMs;

        public Builder timestamp(String timestamp) { this.timestamp = timestamp; return this; }
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
