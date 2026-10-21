package com.campx.platform.outbox;

import com.campx.logger.context.SecurityMasker;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing an immutable transactional outbox record aligned with
 * the {@code sys.outbox_events} table schema in PostgreSQL.
 * <p>
 * Written atomically within the same database transaction as the domain aggregate
 * modification, guaranteeing at-least-once asynchronous delivery to subscribers and
 * the CampXSync Intelligence Layer.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class OutboxEvent {

    private final UUID id;
    private final UUID tenantId;
    private final String service;
    private final String aggregateType;
    private final UUID aggregateId;
    private final String eventType;
    private final String payload;
    private final OutboxHeaders headers;
    private final OutboxStatus status;
    private final int attempts;
    private final Instant nextAttemptAt;
    private final Instant dispatchedAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private OutboxEvent(Builder builder) {
        if (builder.tenantId == null) {
            throw new IllegalArgumentException("tenantId is strictly required for outbox events to guarantee tenant isolation");
        }
        if (builder.service == null || builder.service.trim().isEmpty()) {
            throw new IllegalArgumentException("service name is required for outbox event attribution");
        }
        if (builder.aggregateType == null || builder.aggregateType.trim().isEmpty()) {
            throw new IllegalArgumentException("aggregateType is required for outbox event categorization");
        }
        if (builder.eventType == null || builder.eventType.trim().isEmpty()) {
            throw new IllegalArgumentException("eventType is required for outbox event routing");
        }

        String rawPayload = builder.payload != null ? builder.payload.trim() : "{}";
        // Enforce least privilege: never allow unmasked passwords, secrets, or tokens in outbox payload
        SecurityMasker.assertLeastPrivilege(rawPayload);

        this.id = builder.id != null ? builder.id : UUID.randomUUID();
        this.tenantId = builder.tenantId;
        this.service = builder.service.trim();
        this.aggregateType = builder.aggregateType.trim();
        this.aggregateId = builder.aggregateId;
        this.eventType = builder.eventType.trim();
        this.payload = rawPayload;
        this.headers = builder.headers != null ? builder.headers : OutboxHeaders.builder().build();
        this.status = builder.status != null ? builder.status : OutboxStatus.PENDING;
        this.attempts = Math.max(0, builder.attempts);
        this.nextAttemptAt = builder.nextAttemptAt;
        this.dispatchedAt = builder.dispatchedAt;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getService() { return service; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public OutboxHeaders getHeaders() { return headers; }
    public OutboxStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getDispatchedAt() { return dispatchedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /**
     * Creates a new event copy marked as DISPATCHED with current timestamp.
     */
    public OutboxEvent markDispatched() {
        return toBuilder()
                .status(OutboxStatus.DISPATCHED)
                .dispatchedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Creates a new event copy recording a retry failure with incremented attempt count
     * and scheduled next attempt time.
     */
    public OutboxEvent recordAttempt(Instant nextAttempt) {
        return toBuilder()
                .attempts(this.attempts + 1)
                .nextAttemptAt(nextAttempt)
                .status(OutboxStatus.PENDING)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Creates a new event copy marked as FAILED when retries have been exhausted.
     */
    public OutboxEvent markFailed() {
        return toBuilder()
                .status(OutboxStatus.FAILED)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Creates a new event copy recording a retry failure with specified attempt count and next attempt timestamp.
     */
    public OutboxEvent recordFailure(int attempts, Instant nextAttempt) {
        return toBuilder()
                .attempts(attempts)
                .nextAttemptAt(nextAttempt)
                .status(OutboxStatus.PENDING)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Converts this exhausted or failed outbox event into a dead-letter queue event.
     */
    public DeadLetterEvent toDeadLetterEvent(String failureReason) {
        return DeadLetterEvent.builder()
                .tenantId(this.tenantId)
                .sourceService(this.service)
                .originalEventId(this.id)
                .failureReason(failureReason)
                .payload(this.payload)
                .status(DeadLetterStatus.FAILED)
                .expiresAt(Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS))
                .build();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .tenantId(this.tenantId)
                .service(this.service)
                .aggregateType(this.aggregateType)
                .aggregateId(this.aggregateId)
                .eventType(this.eventType)
                .payload(this.payload)
                .headers(this.headers)
                .status(this.status)
                .attempts(this.attempts)
                .nextAttemptAt(this.nextAttemptAt)
                .dispatchedAt(this.dispatchedAt)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OutboxEvent that = (OutboxEvent) o;
        return Objects.equals(id, that.id) && Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId);
    }

    @Override
    public String toString() {
        return "OutboxEvent{" +
                "id=" + id +
                ", tenantId=" + tenantId +
                ", service='" + service + '\'' +
                ", aggregateType='" + aggregateType + '\'' +
                ", aggregateId=" + aggregateId +
                ", eventType='" + eventType + '\'' +
                ", status=" + status +
                ", attempts=" + attempts +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private UUID tenantId;
        private String service;
        private String aggregateType;
        private UUID aggregateId;
        private String eventType;
        private String payload = "{}";
        private OutboxHeaders headers;
        private OutboxStatus status = OutboxStatus.PENDING;
        private int attempts = 0;
        private Instant nextAttemptAt;
        private Instant dispatchedAt;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder tenantId(UUID tenantId) { this.tenantId = tenantId; return this; }
        public Builder service(String service) { this.service = service; return this; }
        public Builder aggregateType(String aggregateType) { this.aggregateType = aggregateType; return this; }
        public Builder aggregateId(UUID aggregateId) { this.aggregateId = aggregateId; return this; }
        public Builder aggregateId(String aggregateId) {
            if (aggregateId == null) {
                this.aggregateId = null;
            } else {
                try {
                    this.aggregateId = UUID.fromString(aggregateId.trim());
                } catch (IllegalArgumentException e) {
                    this.aggregateId = UUID.nameUUIDFromBytes(aggregateId.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            return this;
        }
        public Builder eventType(String eventType) { this.eventType = eventType; return this; }
        public Builder payload(String payload) { this.payload = payload; return this; }
        public Builder headers(OutboxHeaders headers) { this.headers = headers; return this; }
        public Builder status(OutboxStatus status) { this.status = status; return this; }
        public Builder attempts(int attempts) { this.attempts = attempts; return this; }
        public Builder nextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; return this; }
        public Builder dispatchedAt(Instant dispatchedAt) { this.dispatchedAt = dispatchedAt; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public OutboxEvent build() {
            return new OutboxEvent(this);
        }
    }
}
