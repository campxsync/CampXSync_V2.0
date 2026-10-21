package com.campx.platform.outbox;

import java.time.Instant;
import java.util.*;

/**
 * Standardized metadata headers for outbox events, persisted into the
 * {@code sys.outbox_events.headers} JSONB column and propagated across
 * distributed services and the CampXSync Intelligence Layer.
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class OutboxHeaders {

    public static final String DEFAULT_SCHEMA_VERSION = "1.0.0";

    private final String correlationId;
    private final String causationId;
    private final String idempotencyKey;
    private final String schemaVersion;
    private final String traceId;
    private final String actorId;
    private final String eventTime;
    private final Map<String, String> customHeaders;

    private OutboxHeaders(Builder builder) {
        this.correlationId = builder.correlationId != null ? builder.correlationId : UUID.randomUUID().toString();
        this.causationId = builder.causationId;
        this.idempotencyKey = builder.idempotencyKey != null ? builder.idempotencyKey : this.correlationId;
        this.schemaVersion = builder.schemaVersion != null ? builder.schemaVersion : DEFAULT_SCHEMA_VERSION;
        this.traceId = builder.traceId;
        this.actorId = builder.actorId;
        this.eventTime = builder.eventTime != null ? builder.eventTime : Instant.now().toString();
        this.customHeaders = builder.customHeaders != null ? Collections.unmodifiableMap(new HashMap<>(builder.customHeaders)) : Collections.emptyMap();
    }

    public String getCorrelationId() { return correlationId; }
    public String getCausationId() { return causationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getSchemaVersion() { return schemaVersion; }
    public String getTraceId() { return traceId; }
    public String getActorId() { return actorId; }
    public String getEventTime() { return eventTime; }
    public Map<String, String> getCustomHeaders() { return customHeaders; }

    /**
     * Serializes this header bundle into a valid JSON string suitable for PostgreSQL JSONB persistence.
     *
     * @return JSON string representing the headers object
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{");
        sb.append("\"correlationId\":\"").append(escape(correlationId)).append("\",");
        if (causationId != null) {
            sb.append("\"causationId\":\"").append(escape(causationId)).append("\",");
        }
        sb.append("\"idempotencyKey\":\"").append(escape(idempotencyKey)).append("\",");
        sb.append("\"schemaVersion\":\"").append(escape(schemaVersion)).append("\",");
        if (traceId != null) {
            sb.append("\"traceId\":\"").append(escape(traceId)).append("\",");
        }
        if (actorId != null) {
            sb.append("\"actorId\":\"").append(escape(actorId)).append("\",");
        }
        sb.append("\"eventTime\":\"").append(escape(eventTime)).append("\"");

        if (!customHeaders.isEmpty()) {
            for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                sb.append(",\"").append(escape(entry.getKey())).append("\":\"")
                  .append(escape(entry.getValue())).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Parses a JSON string representation into an {@link OutboxHeaders} instance.
     * Restores correlationId, causationId, idempotencyKey, schemaVersion, traceId,
     * actorId, eventTime, and custom headers.
     *
     * @param json serialized JSON headers
     * @return deserialized OutboxHeaders instance
     */
    public static OutboxHeaders fromJson(String json) {
        if (json == null || json.trim().isEmpty() || "{}".equals(json.trim())) {
            return builder().build();
        }
        Builder builder = builder();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:\\s*(?:\"((?:\\\\\"|[^\"])*)\"|([a-zA-Z0-9_\\-\\.]+)|null)");
        java.util.regex.Matcher m = p.matcher(json);
        while (m.find()) {
            String key = m.group(1);
            String val = m.group(2) != null ? unescape(m.group(2)) : m.group(3);
            if (val == null || "null".equals(val)) continue;

            if ("correlationId".equals(key)) {
                builder.correlationId(val);
            } else if ("causationId".equals(key)) {
                builder.causationId(val);
            } else if ("idempotencyKey".equals(key)) {
                builder.idempotencyKey(val);
            } else if ("schemaVersion".equals(key)) {
                builder.schemaVersion(val);
            } else if ("traceId".equals(key)) {
                builder.traceId(val);
            } else if ("actorId".equals(key)) {
                builder.actorId(val);
            } else if ("eventTime".equals(key)) {
                builder.eventTime(val);
            } else {
                builder.addHeader(key, val);
            }
        }
        return builder.build();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescape(String s) {
        if (s == null) return null;
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\b", "\b")
                .replace("\\f", "\f");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OutboxHeaders that = (OutboxHeaders) o;
        return Objects.equals(correlationId, that.correlationId) &&
               Objects.equals(causationId, that.causationId) &&
               Objects.equals(idempotencyKey, that.idempotencyKey) &&
               Objects.equals(schemaVersion, that.schemaVersion) &&
               Objects.equals(traceId, that.traceId) &&
               Objects.equals(actorId, that.actorId) &&
               Objects.equals(customHeaders, that.customHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(correlationId, causationId, idempotencyKey, schemaVersion, traceId, actorId, customHeaders);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String correlationId;
        private String causationId;
        private String idempotencyKey;
        private String schemaVersion = DEFAULT_SCHEMA_VERSION;
        private String traceId;
        private String actorId;
        private String eventTime;
        private Map<String, String> customHeaders = new HashMap<>();

        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder causationId(String causationId) { this.causationId = causationId; return this; }
        public Builder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }
        public Builder schemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder actorId(String actorId) { this.actorId = actorId; return this; }
        public Builder eventTime(String eventTime) { this.eventTime = eventTime; return this; }
        public Builder eventTime(Instant eventTime) {
            this.eventTime = eventTime != null ? eventTime.toString() : null;
            return this;
        }
        public Builder customHeaders(Map<String, String> customHeaders) {
            if (customHeaders != null) {
                this.customHeaders.putAll(customHeaders);
            }
            return this;
        }
        public Builder addHeader(String key, String value) {
            if (key != null && value != null) {
                this.customHeaders.put(key, value);
            }
            return this;
        }

        public OutboxHeaders build() {
            return new OutboxHeaders(this);
        }
    }
}
