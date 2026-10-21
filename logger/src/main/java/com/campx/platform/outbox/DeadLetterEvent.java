package com.campx.platform.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an undeliverable or exhausted event routed to the dead-letter table,
 * aligned with {@code sys.dead_letter_events} in PostgreSQL.
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class DeadLetterEvent {

    private final UUID id;
    private final UUID tenantId;
    private final String sourceService;
    private final String originalEventId;
    private final String failureReason;
    private final String payload;
    private final DeadLetterStatus status;
    private final Instant replayedAt;
    private final Instant expiresAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private DeadLetterEvent(Builder builder) {
        if (builder.tenantId == null) {
            throw new IllegalArgumentException("tenantId is required for dead-letter event isolation");
        }
        if (builder.failureReason == null || builder.failureReason.trim().isEmpty()) {
            throw new IllegalArgumentException("failureReason is required for dead-letter diagnostics");
        }
        this.id = builder.id != null ? builder.id : UUID.randomUUID();
        this.tenantId = builder.tenantId;
        this.sourceService = builder.sourceService != null ? builder.sourceService.trim() : "UNKNOWN_SERVICE";
        this.originalEventId = builder.originalEventId;
        this.failureReason = builder.failureReason.trim();
        this.payload = builder.payload != null ? builder.payload : "{}";
        this.status = builder.status != null ? builder.status : DeadLetterStatus.FAILED;
        this.replayedAt = builder.replayedAt;
        this.expiresAt = builder.expiresAt;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getSourceService() { return sourceService; }
    public String getOriginalEventId() { return originalEventId; }
    public String getFailureReason() { return failureReason; }
    public String getPayload() { return payload; }
    public DeadLetterStatus getStatus() { return status; }
    public Instant getReplayedAt() { return replayedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public static Builder fromOutboxEvent(OutboxEvent event, String failureReason) {
        return builder()
                .tenantId(event.getTenantId())
                .sourceService(event.getService())
                .originalEventId(event.getId().toString())
                .failureReason(failureReason)
                .payload(event.getPayload())
                .status(DeadLetterStatus.FAILED);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private UUID tenantId;
        private String sourceService;
        private String originalEventId;
        private String failureReason;
        private String payload = "{}";
        private DeadLetterStatus status = DeadLetterStatus.FAILED;
        private Instant replayedAt;
        private Instant expiresAt;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder tenantId(UUID tenantId) { this.tenantId = tenantId; return this; }
        public Builder sourceService(String sourceService) { this.sourceService = sourceService; return this; }
        public Builder originalEventId(String originalEventId) { this.originalEventId = originalEventId; return this; }
        public Builder originalEventId(UUID originalEventId) {
            this.originalEventId = originalEventId != null ? originalEventId.toString() : null;
            return this;
        }
        public Builder failureReason(String failureReason) { this.failureReason = failureReason; return this; }
        public Builder payload(String payload) { this.payload = payload; return this; }
        public Builder status(DeadLetterStatus status) { this.status = status; return this; }
        public Builder replayedAt(Instant replayedAt) { this.replayedAt = replayedAt; return this; }
        public Builder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public DeadLetterEvent build() {
            return new DeadLetterEvent(this);
        }
    }
}
