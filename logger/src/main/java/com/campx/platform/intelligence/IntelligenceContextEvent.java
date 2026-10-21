package com.campx.platform.intelligence;

import com.campx.platform.outbox.OutboxEvent;

import java.time.Instant;
import java.util.*;

/**
 * Governed contract event model for publishing transactional domain outputs
 * to the CampXSync Intelligence Layer (US-051, US-063, Wave 1 Task 1.1).
 * <p>
 * Enforces:
 * <ul>
 *   <li>Governed schema versioning ({@code v1.0.0}).</li>
 *   <li>Tenant isolation boundary preservation.</li>
 *   <li>Least-privilege payload: strictly sanitized of passwords, secrets, or tokens.</li>
 *   <li>Distributed correlation and causation propagation.</li>
 *   <li>Idempotent deduplication keys.</li>
 * </ul>
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 1.0.0
 * @since 2.0.0
 */
public class IntelligenceContextEvent {

    public static final String SCHEMA_VERSION = "1.0.0";

    private final String eventId;
    private final String eventType;
    private final String schemaVersion;
    private final String aggregateType;
    private final String aggregateId;
    private final String tenantId;
    private final String correlationId;
    private final String traceId;
    private final String actorId;
    private final String idempotencyKey;
    private final String timestamp;
    private final String sourceService;
    private final String payloadJson;
    private final Map<String, String> metadata;

    private IntelligenceContextEvent(Builder builder) {
        this.eventId = builder.eventId != null ? builder.eventId : UUID.randomUUID().toString();
        this.eventType = builder.eventType != null ? builder.eventType : "UNKNOWN_EVENT";
        this.schemaVersion = builder.schemaVersion != null ? builder.schemaVersion : SCHEMA_VERSION;
        this.aggregateType = builder.aggregateType != null ? builder.aggregateType : "GENERAL";
        this.aggregateId = builder.aggregateId != null ? builder.aggregateId : "N/A";
        this.tenantId = builder.tenantId;
        this.correlationId = builder.correlationId != null ? builder.correlationId : UUID.randomUUID().toString();
        this.traceId = builder.traceId;
        this.actorId = builder.actorId;
        this.idempotencyKey = builder.idempotencyKey != null ? builder.idempotencyKey : this.eventId;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now().toString();
        this.sourceService = builder.sourceService != null ? builder.sourceService : "campx-service";
        this.payloadJson = builder.payloadJson != null ? builder.payloadJson : "{}";
        this.metadata = builder.metadata != null ? Collections.unmodifiableMap(new HashMap<>(builder.metadata)) : Collections.emptyMap();

        IntelligenceContractValidator.validate(this);
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getSchemaVersion() { return schemaVersion; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getTenantId() { return tenantId; }
    public String getCorrelationId() { return correlationId; }
    public String getTraceId() { return traceId; }
    public String getActorId() { return actorId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getTimestamp() { return timestamp; }
    public String getSourceService() { return sourceService; }
    public String getPayloadJson() { return payloadJson; }
    public Map<String, String> getMetadata() { return metadata; }

    /**
     * Bridges an {@link OutboxEvent} into a governed {@link IntelligenceContextEvent}.
     *
     * @param outboxEvent source outbox event from PostgreSQL sys.outbox_events
     * @return validated IntelligenceContextEvent
     */
    public static IntelligenceContextEvent fromOutboxEvent(OutboxEvent outboxEvent) {
        if (outboxEvent == null) {
            throw new IllegalArgumentException("OutboxEvent cannot be null");
        }
        return builder()
                .eventId(outboxEvent.getId().toString())
                .eventType(outboxEvent.getEventType())
                .aggregateType(outboxEvent.getAggregateType())
                .aggregateId(outboxEvent.getAggregateId() != null ? outboxEvent.getAggregateId().toString() : null)
                .tenantId(outboxEvent.getTenantId().toString())
                .sourceService(outboxEvent.getService())
                .correlationId(outboxEvent.getHeaders().getCorrelationId())
                .traceId(outboxEvent.getHeaders().getTraceId())
                .actorId(outboxEvent.getHeaders().getActorId())
                .idempotencyKey(outboxEvent.getHeaders().getIdempotencyKey())
                .timestamp(outboxEvent.getCreatedAt().toString())
                .payloadJson(outboxEvent.getPayload())
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String eventId;
        private String eventType;
        private String schemaVersion = SCHEMA_VERSION;
        private String aggregateType;
        private String aggregateId;
        private String tenantId;
        private String correlationId;
        private String traceId;
        private String actorId;
        private String idempotencyKey;
        private String timestamp;
        private String sourceService;
        private String payloadJson;
        private Map<String, String> metadata = new HashMap<>();

        public Builder eventId(String eventId) { this.eventId = eventId; return this; }
        public Builder eventType(String eventType) { this.eventType = eventType; return this; }
        public Builder schemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; return this; }
        public Builder aggregateType(String aggregateType) { this.aggregateType = aggregateType; return this; }
        public Builder aggregateId(String aggregateId) { this.aggregateId = aggregateId; return this; }
        public Builder aggregateId(UUID aggregateId) {
            this.aggregateId = aggregateId != null ? aggregateId.toString() : null;
            return this;
        }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder tenantId(UUID tenantId) {
            this.tenantId = tenantId != null ? tenantId.toString() : null;
            return this;
        }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder actorId(String actorId) { this.actorId = actorId; return this; }
        public Builder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }
        public Builder timestamp(String timestamp) { this.timestamp = timestamp; return this; }
        public Builder sourceService(String sourceService) { this.sourceService = sourceService; return this; }
        public Builder payloadJson(String payloadJson) { this.payloadJson = payloadJson; return this; }
        public Builder addMetadata(String key, String value) {
            if (key != null && value != null) {
                this.metadata.put(key, value);
            }
            return this;
        }

        public IntelligenceContextEvent build() {
            return new IntelligenceContextEvent(this);
        }
    }
}
