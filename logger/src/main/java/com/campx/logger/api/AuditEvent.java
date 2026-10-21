package com.campx.logger.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Specialized model for security and compliance audit logging in CampXSync ERP.
 * <p>
 * Captures user actions, principal identity, security roles, target resources,
 * client network IP addresses, outcome status (SUCCESS, FAILURE, ATTEMPT),
 * human-readable descriptions, and extensible metadata maps. Can be transformed
 * into a standard {@link LogEvent} for asynchronous pipeline processing.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class AuditEvent {

    /** Action or operation identifier (e.g. "TENANT_PROVISIONED", "ROLE_ASSIGNED"). */
    private final String action;

    /** Unique identifier of the acting subject (user, service, or API key). */
    private final String principalId;

    /** Security role of the acting subject (e.g. "SUPER_ADMIN", "DEAN"). */
    private final String principalRole;

    /** Category of the entity being acted upon (e.g. "INSTITUTE", "DOCUMENT"). */
    private final String resourceType;

    /** Unique identity of the entity being acted upon. */
    private final String resourceId;

    /** Multi-tenant identifier. */
    private final String tenantId;

    /** Multi-tenant college partition identifier if applicable. */
    private final String collegeId;

    /** Distributed tracing correlation ID. */
    private final String correlationId;

    /** Severity level of the audit event (e.g. "INFO", "WARN", "CRITICAL"). */
    private final String severity;

    /** Principal type (e.g. "USER", "SERVICE", "SYSTEM"). */
    private final String principalType;

    /** Originating network IP address of the caller. */
    private final String clientIp;

    /** Outcome of the audited action: "SUCCESS", "FAILURE", or "ATTEMPT". */
    private final String status;

    /** Human-readable explanation of the action. */
    private final String description;

    /** Extensible metadata key-value pairs for contextual audit detail. */
    private final Map<String, String> metadata;

    /** Timestamp in milliseconds since epoch. */
    private final long timestamp;

    private AuditEvent(Builder builder) {
        this.action = builder.action != null ? builder.action : "UNKNOWN_ACTION";
        this.principalId = builder.principalId != null ? builder.principalId : "ANONYMOUS";
        this.principalRole = builder.principalRole != null ? builder.principalRole : "GUEST";
        this.resourceType = builder.resourceType != null ? builder.resourceType : "SYSTEM";
        this.resourceId = builder.resourceId != null ? builder.resourceId : "N/A";
        this.tenantId = builder.tenantId;
        this.collegeId = builder.collegeId;
        this.correlationId = builder.correlationId;
        this.severity = builder.severity != null ? builder.severity : "INFO";
        this.principalType = builder.principalType != null ? builder.principalType : "USER";
        this.clientIp = builder.clientIp != null ? builder.clientIp : "0.0.0.0";
        this.status = builder.status != null ? builder.status : "SUCCESS";
        this.description = builder.description != null ? builder.description : "";
        this.metadata = builder.metadata != null ? Collections.unmodifiableMap(new HashMap<>(builder.metadata)) : Collections.emptyMap();
        this.timestamp = builder.timestamp > 0 ? builder.timestamp : System.currentTimeMillis();
    }

    public AuditEvent(String action, String principalId, String resourceType, String resourceId, String description, Map<String, String> metadata) {
        this(builder().action(action).principalId(principalId).resourceType(resourceType).resourceId(resourceId).description(description).metadata(metadata));
    }

    public AuditEvent(String tenantId, String collegeId, String action, String principalId, String principalType, String resourceType, String resourceId, String status, String severity, String correlationId, String description, Map<String, String> metadata) {
        this(builder().tenantId(tenantId).collegeId(collegeId).action(action).principalId(principalId).principalType(principalType).resourceType(resourceType).resourceId(resourceId).status(status).severity(severity).correlationId(correlationId).description(description).metadata(metadata));
    }

    /** @return Action name. */
    public String getAction() { return action; }

    /** @return Principal identity. */
    public String getPrincipalId() { return principalId; }

    /** @return Principal security role. */
    public String getPrincipalRole() { return principalRole; }

    /** @return Type of resource targeted. */
    public String getResourceType() { return resourceType; }

    /** @return ID of resource targeted. */
    public String getResourceId() { return resourceId; }

    /** @return Multi-tenant ID. */
    public String getTenantId() { return tenantId; }

    /** @return College ID. */
    public String getCollegeId() { return collegeId; }

    /** @return Correlation ID for distributed tracing. */
    public String getCorrelationId() { return correlationId; }

    /** @return Severity level (INFO, WARN, CRITICAL). */
    public String getSeverity() { return severity; }

    /** @return Principal type (USER, SERVICE, SYSTEM). */
    public String getPrincipalType() { return principalType; }

    /** @return Caller IP address. */
    public String getClientIp() { return clientIp; }

    /** @return Audit execution status (SUCCESS, FAILURE, ATTEMPT). */
    public String getStatus() { return status; }

    /** @return Descriptive summary. */
    public String getDescription() { return description; }

    /** @return Unmodifiable map of audit metadata. */
    public Map<String, String> getMetadata() { return metadata; }

    /** @return Event timestamp in milliseconds since epoch. */
    public long getTimestamp() { return timestamp; }

    /**
     * Converts this structured AuditEvent into a standard {@link LogEvent}
     * with {@link LogLevel#AUDIT} severity for appender emission.
     *
     * @param loggerName The category or logger name to assign.
     * @return Fully populated LogEvent.
     */
    public LogEvent toLogEvent(String loggerName) {
        Map<String, String> ctx = new HashMap<>(metadata);
        ctx.put("audit.action", action);
        ctx.put("audit.principalId", principalId);
        ctx.put("audit.principalRole", principalRole);
        ctx.put("audit.resourceType", resourceType);
        ctx.put("audit.resourceId", resourceId);
        ctx.put("audit.clientIp", clientIp);
        ctx.put("audit.status", status);
        if (tenantId != null) ctx.put("audit.tenantId", tenantId);
        if (collegeId != null) ctx.put("audit.collegeId", collegeId);
        if (correlationId != null) ctx.put("audit.correlationId", correlationId);
        if (severity != null) ctx.put("audit.severity", severity);
        if (principalType != null) ctx.put("audit.principalType", principalType);

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
        private String tenantId;
        private String collegeId;
        private String correlationId;
        private String severity = "INFO";
        private String principalType = "USER";
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
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder collegeId(String collegeId) { this.collegeId = collegeId; return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder severity(String severity) { this.severity = severity; return this; }
        public Builder principalType(String principalType) { this.principalType = principalType; return this; }
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
        public Builder metadata(Map<String, String> metadata) {
            if (metadata != null) {
                this.metadata.putAll(metadata);
            }
            return this;
        }

        public AuditEvent build() {
            return new AuditEvent(this);
        }
    }
}
