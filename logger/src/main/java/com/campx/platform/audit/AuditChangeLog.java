package com.campx.platform.audit;

import com.campx.logger.context.SecurityMasker;

import java.time.Instant;
import java.util.*;

/**
 * Domain entity representing a row-level before/after entity state change
 * mapped to the {@code audit.change_log} partitioned table in PostgreSQL.
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class AuditChangeLog {

    private final UUID id;
    private final UUID tenantId;
    private final String tableSchema;
    private final String tableName;
    private final UUID recordId;
    private final String action; // I, U, D
    private final List<String> changedFields;
    private final String oldDataJson;
    private final String newDataJson;
    private final UUID actorId;
    private final String requestId;
    private final Instant createdAt;
    private final UUID createdBy;

    private AuditChangeLog(Builder builder) {
        if (builder.tableSchema == null || builder.tableSchema.trim().isEmpty()) {
            throw new IllegalArgumentException("tableSchema is required");
        }
        if (builder.tableName == null || builder.tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("tableName is required");
        }
        if (builder.action == null || !Arrays.asList("I", "U", "D").contains(builder.action.trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("action must be 'I', 'U', or 'D'");
        }

        String safeOld = builder.oldDataJson != null ? builder.oldDataJson : null;
        String safeNew = builder.newDataJson != null ? builder.newDataJson : null;
        if (safeOld != null) SecurityMasker.assertLeastPrivilege(safeOld);
        if (safeNew != null) SecurityMasker.assertLeastPrivilege(safeNew);

        this.id = builder.id != null ? builder.id : UUID.randomUUID();
        this.tenantId = builder.tenantId;
        this.tableSchema = builder.tableSchema.trim();
        this.tableName = builder.tableName.trim();
        this.recordId = builder.recordId;
        this.action = builder.action.trim().toUpperCase(Locale.ROOT);
        this.changedFields = builder.changedFields != null ? Collections.unmodifiableList(new ArrayList<>(builder.changedFields)) : Collections.emptyList();
        this.oldDataJson = safeOld;
        this.newDataJson = safeNew;
        this.actorId = builder.actorId;
        this.requestId = builder.requestId;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.createdBy = builder.createdBy != null ? builder.createdBy : this.actorId;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getTableSchema() { return tableSchema; }
    public String getTableName() { return tableName; }
    public UUID getRecordId() { return recordId; }
    public String getAction() { return action; }
    public List<String> getChangedFields() { return changedFields; }
    public String getOldDataJson() { return oldDataJson; }
    public String getNewDataJson() { return newDataJson; }
    public UUID getActorId() { return actorId; }
    public String getRequestId() { return requestId; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private UUID tenantId;
        private String tableSchema;
        private String tableName;
        private UUID recordId;
        private String action = "I";
        private List<String> changedFields = new ArrayList<>();
        private String oldDataJson;
        private String newDataJson;
        private UUID actorId;
        private String requestId;
        private Instant createdAt;
        private UUID createdBy;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder tenantId(UUID tenantId) { this.tenantId = tenantId; return this; }
        public Builder tableSchema(String tableSchema) { this.tableSchema = tableSchema; return this; }
        public Builder tableName(String tableName) { this.tableName = tableName; return this; }
        public Builder recordId(UUID recordId) { this.recordId = recordId; return this; }
        public Builder recordId(String recordId) {
            if (recordId == null) {
                this.recordId = null;
            } else {
                try {
                    this.recordId = UUID.fromString(recordId.trim());
                } catch (IllegalArgumentException e) {
                    this.recordId = UUID.nameUUIDFromBytes(recordId.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            return this;
        }
        public Builder action(String action) { this.action = action; return this; }
        public Builder changedFields(List<String> changedFields) {
            if (changedFields != null) this.changedFields = new ArrayList<>(changedFields);
            return this;
        }
        public Builder addChangedField(String field) {
            if (field != null) this.changedFields.add(field);
            return this;
        }
        public Builder oldDataJson(String oldDataJson) { this.oldDataJson = oldDataJson; return this; }
        public Builder newDataJson(String newDataJson) { this.newDataJson = newDataJson; return this; }
        public Builder actorId(UUID actorId) { this.actorId = actorId; return this; }
        public Builder requestId(String requestId) { this.requestId = requestId; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder createdBy(UUID createdBy) { this.createdBy = createdBy; return this; }

        public AuditChangeLog build() {
            return new AuditChangeLog(this);
        }
    }
}
