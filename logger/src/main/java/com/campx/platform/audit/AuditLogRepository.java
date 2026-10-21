package com.campx.platform.audit;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Transaction-aware repository interface for persisting and querying compliance audit records
 * in {@code audit.events} and row-level mutations in {@code audit.change_log}.
 *
 * <p>Every operation strictly enforces tenant isolation invariants.</p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public interface AuditLogRepository {

    /**
     * Appends an immutable audit event to {@code audit.events}.
     *
     * @param conn   the active transactional JDBC connection
     * @param record the audit record to persist
     * @throws SQLException             if a database access error occurs
     * @throws IllegalArgumentException if required fields are missing or data minimization fails
     */
    void appendAuditEvent(Connection conn, AuditRecord record) throws SQLException;

    /**
     * Appends an immutable row-level change log record to {@code audit.change_log}.
     *
     * @param conn      the active transactional JDBC connection
     * @param changeLog the change log entry to persist
     * @throws SQLException             if a database access error occurs
     * @throws IllegalArgumentException if required fields are missing
     */
    void appendChangeLog(Connection conn, AuditChangeLog changeLog) throws SQLException;

    /**
     * Finds recent audit events partitioned by tenant.
     *
     * @param conn     the JDBC connection
     * @param tenantId the tenant UUID partition key
     * @param limit    the maximum number of records to return
     * @return list of audit records ordered descending by event_time
     * @throws SQLException if a database access error occurs
     */
    List<AuditRecord> findAuditEventsByTenant(Connection conn, UUID tenantId, int limit) throws SQLException;

    /**
     * Finds mutation history for a specific database entity within tenant boundary.
     *
     * @param conn        the JDBC connection
     * @param tenantId    the tenant UUID
     * @param tableSchema the schema of the target table (e.g. "acd", "stu")
     * @param tableName   the name of the target table
     * @param recordId    the unique record UUID identifier
     * @return list of change log records ordered descending by created_at
     * @throws SQLException if a database access error occurs
     */
    List<AuditChangeLog> findChangeLogsByRecord(
            Connection conn,
            UUID tenantId,
            String tableSchema,
            String tableName,
            UUID recordId) throws SQLException;

    /**
     * Finds mutation history for a specific database entity using a string ID within tenant boundary.
     *
     * @param conn        the JDBC connection
     * @param tenantId    the tenant UUID
     * @param tableSchema the schema of the target table
     * @param tableName   the name of the target table
     * @param recordId    the record identifier string (parsed as UUID)
     * @return list of change log records ordered descending by created_at
     * @throws SQLException if a database access error occurs
     */
    default List<AuditChangeLog> findChangeLogsByRecord(
            Connection conn,
            UUID tenantId,
            String tableSchema,
            String tableName,
            String recordId) throws SQLException {
        if (recordId == null) {
            throw new IllegalArgumentException("recordId is required");
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(recordId.trim());
        } catch (IllegalArgumentException e) {
            uuid = UUID.nameUUIDFromBytes(recordId.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return findChangeLogsByRecord(conn, tenantId, tableSchema, tableName, uuid);
    }
}
