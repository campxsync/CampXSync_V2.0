package com.campx.logger.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Specialized model for security and compliance audit logging in CampXSync ERP.
 * Captures user actions, target resources, client IPs, and status.
 */
public class AuditEvent {
    private final String action;
    private final String principalId;
    private final String principalRole;
    private final String resourceType;
    private final String resourceId;
    private final String clientIp;
    private final String status; // SUCCESS, FAILURE, ATTEMPT
    private final String description;
    private final Map<String, String> metadata;
    private final long timestamp;

    private AuditEvent(Builder builder) {
        this.action = builder.action != null ? builder.action : "UNKNOWN_ACTION";
        this.principalId = builder.principalId != null ? builder.principalId : "ANONYMOUS";
        this.principalRole = builder.principalRole != null ? builder.principalRole : "GUEST";
        this.resourceType = builder.resourceType != null ? builder.resourceType : "SYSTEM";
        this.resourceId = builder.resourceId != null ? builder.resourceId : "N/A";
        this.clientIp = builder.clientIp != null ? builder.clientIp : "0.0.0.0";
        this.status = builder.status != null ? builder.status : "SUCCESS";
        this.description = builder.description != null ? builder.description : "";
        this.metadata = builder.metadata != null ? Collections.unmodifiableMap(new HashMap<>(builder.metadata)) : Collections.emptyMap();
        this.timestamp = builder.timestamp > 0 ? builder.timestamp : System.currentTimeMillis();
    }

    public String getAction() { return action; }
    public String getPrincipalId() { return principalId; }
    public String getPrincipalRole() { return principalRole; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getClientIp() { return clientIp; }
    public String getStatus() { return status; }
    public String getDescription() { return description; }
    public Map<String, String> getMetadata() { return metadata; }
    public long getTimestamp() { return timestamp; }

    public LogEvent toLogEvent(String loggerName) {
        Map<String, String> ctx = new HashMap<>(metadata);
        ctx.put("audit.action", action);
        ctx.put("audit.principalId", principalId);
        ctx.put("audit.principalRole", principalRole);
        ctx.put("audit.resourceType", resourceType);
        ctx.put("audit.resourceId", resourceId);
        ctx.put("audit.clientIp", clientIp);
        ctx.put("audit.status", status);

        return LogEvent.builder()
                .level(LogLevel.AUDIT)
                .loggerName(loggerName)
                .operation(action)
                .addTag("AUDIT")
                .addTag(status)
                .context(ctx)
                .message("AUDIT [" + action + "] principal=" + principalId + " (" + principalRole + ") resource=" + resourceType + ":" + resourceId + " status=" + status + " - " + description)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String action;
        private String principalId;
        private String principalRole;
        private String resourceType;
        private String resourceId;
        private String clientIp;
        private String status = "SUCCESS";
        private String description;
        private Map<String, String> metadata = new HashMap<>();
        private long timestamp;

        public Builder action(String action) { this.action = action; return this; }
        public Builder principalId(String principalId) { this.principalId = principalId; return this; }
        public Builder principalRole(String principalRole) { this.principalRole = principalRole; return this; }
        public Builder resourceType(String resourceType) { this.resourceType = resourceType; return this; }
        public Builder resourceId(String resourceId) { this.resourceId = resourceId; return this; }
        public Builder clientIp(String clientIp) { this.clientIp = clientIp; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder timestamp(long timestamp) { this.timestamp = timestamp; return this; }
        public Builder addMetadata(String key, String value) {
            if (key != null && value != null) {
                this.metadata.put(key, value);
            }
            return this;
        }

        public AuditEvent build() {
            return new AuditEvent(this);
        }
    }
}
