package com.campx.platform.audit;

import com.campx.logger.api.AuditEvent;
import com.campx.logger.context.SecurityMasker;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain entity representing an immutable semantic security and compliance audit record
 * directly mapped to the {@code audit.events} partitioned table in PostgreSQL.
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class AuditRecord {

    private final UUID id;
    private final UUID tenantId;
    private final UUID collegeId;
    private final Instant eventTime;
    private final String action;
    private final UUID principalId;
    private final String principalType;
    private final String resourceType;
    private final UUID resourceId;
    private final String status;
    private final String severity;
    private final String ip;
    private final String userAgent;
    private final String correlationId;
    private final String detailJson;
    private final Instant createdAt;
    private final UUID createdBy;

    private AuditRecord(Builder builder) {
        if (builder.action == null || builder.action.trim().isEmpty()) {
            throw new IllegalArgumentException("action is required for audit event record");
        }

        String rawDetail = builder.detailJson != null ? builder.detailJson.trim() : "{}";
        SecurityMasker.assertLeastPrivilege(rawDetail);

        this.id = builder.id != null ? builder.id : UUID.randomUUID();
        this.tenantId = builder.tenantId;
        this.collegeId = builder.collegeId;
        this.eventTime = builder.eventTime != null ? builder.eventTime : Instant.now();
        this.action = builder.action.trim();
        this.principalId = builder.principalId;
        this.principalType = builder.principalType != null ? builder.principalType.trim() : "USER";
        this.resourceType = builder.resourceType != null ? builder.resourceType.trim() : "SYSTEM";
        this.resourceId = builder.resourceId;
        this.status = builder.status != null ? builder.status.trim() : "SUCCESS";
        this.severity = builder.severity != null ? builder.severity.trim() : "INFO";
        this.ip = builder.ip;
        this.userAgent = builder.userAgent;
        this.correlationId = builder.correlationId;
        this.detailJson = rawDetail;
        this.createdAt = builder.createdAt != null ? builder.createdAt : this.eventTime;
        this.createdBy = builder.createdBy != null ? builder.createdBy : this.principalId;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCollegeId() { return collegeId; }
    public Instant getEventTime() { return eventTime; }
    public String getAction() { return action; }
    public UUID getPrincipalId() { return principalId; }
    public String getPrincipalType() { return principalType; }
    public String getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public String getStatus() { return status; }
    public String getSeverity() { return severity; }
    public String getIp() { return ip; }
    public String getUserAgent() { return userAgent; }
    public String getCorrelationId() { return correlationId; }
    public String getDetailJson() { return detailJson; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }

    /**
     * Bridges an existing {@link AuditEvent} into an {@link AuditRecord}.
     */
    public static AuditRecord fromAuditEvent(AuditEvent event) {
        if (event == null) throw new IllegalArgumentException("AuditEvent cannot be null");

        UUID tenantUuid = parseUuidSafely(event.getTenantId());
        UUID collegeUuid = parseUuidSafely(event.getCollegeId());
        UUID principalUuid = parseUuidSafely(event.getPrincipalId());
        UUID resourceUuid = parseUuidSafely(event.getResourceId());

        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"description\":\"").append(escape(event.getDescription())).append("\"");
        if (event.getMetadata() != null && !event.getMetadata().isEmpty()) {
            sb.append(",\"metadata\":{");
            int i = 0;
            for (java.util.Map.Entry<String, String> entry : event.getMetadata().entrySet()) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escape(entry.getKey())).append("\":\"")
                  .append(escape(entry.getValue())).append("\"");
                i++;
            }
            sb.append("}");
        }
        sb.append("}");

        return builder()
                .tenantId(tenantUuid)
                .collegeId(collegeUuid)
                .action(event.getAction())
                .principalId(principalUuid)
                .principalType(event.getPrincipalType())
                .resourceType(event.getResourceType())
                .resourceId(resourceUuid)
                .status(event.getStatus())
                .severity(event.getSeverity())
                .ip(event.getClientIp())
                .correlationId(event.getCorrelationId())
                .detailJson(sb.toString())
                .eventTime(Instant.ofEpochMilli(event.getTimestamp()))
                .build();
    }

    private static UUID parseUuidSafely(String val) {
        if (val == null || val.trim().isEmpty()) return null;
        try {
            return UUID.fromString(val.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private UUID tenantId;
        private UUID collegeId;
        private Instant eventTime;
        private String action;
        private UUID principalId;
        private String principalType = "USER";
        private String resourceType = "SYSTEM";
        private UUID resourceId;
        private String status = "SUCCESS";
        private String severity = "INFO";
        private String ip;
        private String userAgent;
        private String correlationId;
        private String detailJson = "{}";
        private Instant createdAt;
        private UUID createdBy;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder tenantId(UUID tenantId) { this.tenantId = tenantId; return this; }
        public Builder collegeId(UUID collegeId) { this.collegeId = collegeId; return this; }
        public Builder eventTime(Instant eventTime) { this.eventTime = eventTime; return this; }
        public Builder action(String action) { this.action = action; return this; }
        public Builder principalId(UUID principalId) { this.principalId = principalId; return this; }
        public Builder principalType(String principalType) { this.principalType = principalType; return this; }
        public Builder resourceType(String resourceType) { this.resourceType = resourceType; return this; }
        public Builder resourceId(UUID resourceId) { this.resourceId = resourceId; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder severity(String severity) { this.severity = severity; return this; }
        public Builder ip(String ip) { this.ip = ip; return this; }
        public Builder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder detailJson(String detailJson) { this.detailJson = detailJson; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder createdBy(UUID createdBy) { this.createdBy = createdBy; return this; }

        public AuditRecord build() {
            return new AuditRecord(this);
        }
    }
}
