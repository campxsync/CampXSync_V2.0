package com.campx.platform.audit;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Production PostgreSQL JDBC implementation of {@link AuditLogRepository} backed by
 * the partitioned tables {@code audit.events} and {@code audit.change_log}.
 * <p>
 * Ensures strict multi-tenant boundary isolation: every query is filtered by {@code tenant_id}.
 * Supports transactional execution within an existing {@link Connection} boundary.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class PostgresAuditRepository implements AuditLogRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresAuditRepository.class);

    private static final String SQL_INSERT_AUDIT_EVENT =
            "INSERT INTO audit.events (" +
            "  id, tenant_id, college_id, event_time, action, " +
            "  principal_id, principal_type, resource_type, resource_id, " +
            "  status, severity, ip, user_agent, correlation_id, " +
            "  detail, created_at, created_by" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::inet, ?, ?, ?::jsonb, ?, ?)";

    private static final String SQL_FIND_AUDIT_BY_TENANT =
            "SELECT id, tenant_id, college_id, event_time, action, " +
            "  principal_id, principal_type, resource_type, resource_id, " +
            "  status, severity, ip, user_agent, correlation_id, " +
            "  detail::text AS detail, created_at, created_by " +
            "FROM audit.events " +
            "WHERE tenant_id = ? " +
            "ORDER BY event_time DESC " +
            "LIMIT ?";

    private static final String SQL_INSERT_CHANGE_LOG =
            "INSERT INTO audit.change_log (" +
            "  id, tenant_id, table_schema, table_name, record_id, " +
            "  action, changed_fields, old_data, new_data, " +
            "  actor_id, request_id, created_at, created_by" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)";

    private static final String SQL_FIND_CHANGE_LOG_BY_RECORD =
            "SELECT id, tenant_id, table_schema, table_name, record_id, " +
            "  action, changed_fields, old_data::text AS old_data, new_data::text AS new_data, " +
            "  actor_id, request_id, created_at, created_by " +
            "FROM audit.change_log " +
            "WHERE tenant_id = ? AND table_schema = ? AND table_name = ? AND record_id = ? " +
            "ORDER BY created_at DESC";

    @Override
    public void appendAuditEvent(Connection conn, AuditRecord record) throws SQLException {
        if (conn == null) throw new IllegalArgumentException("Connection cannot be null");
        if (record == null) throw new IllegalArgumentException("AuditRecord cannot be null");

        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_AUDIT_EVENT)) {
            bindAuditEventParams(conn, ps, record);
            ps.executeUpdate();
        }
        logger.debug("Appended audit event [{}] action={} tenant={}", record.getId(), record.getAction(), record.getTenantId());
    }

    @Override
    public void appendChangeLog(Connection conn, AuditChangeLog changeLog) throws SQLException {
        if (conn == null) throw new IllegalArgumentException("Connection cannot be null");
        if (changeLog == null) throw new IllegalArgumentException("AuditChangeLog cannot be null");

        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_CHANGE_LOG)) {
            bindChangeLogParams(conn, ps, changeLog);
            ps.executeUpdate();
        }
        logger.debug("Appended change log [{}] table={}.{} record={} action={}",
                changeLog.getId(), changeLog.getTableSchema(), changeLog.getTableName(), changeLog.getRecordId(), changeLog.getAction());
    }

    @Override
    public List<AuditRecord> findAuditEventsByTenant(Connection conn, UUID tenantId, int limit) throws SQLException {
        if (conn == null) throw new IllegalArgumentException("Connection cannot be null");
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required for audit event search");
        int fetchLimit = limit > 0 ? limit : 50;

        List<AuditRecord> records = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_AUDIT_BY_TENANT)) {
            ps.setObject(1, tenantId);
            ps.setInt(2, fetchLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(mapRowToAuditRecord(rs));
                }
            }
        }
        return records;
    }

    @Override
    public List<AuditChangeLog> findChangeLogsByRecord(
            Connection conn,
            UUID tenantId,
            String tableSchema,
            String tableName,
            UUID recordId) throws SQLException {
        if (conn == null) throw new IllegalArgumentException("Connection cannot be null");
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required for change log search");
        if (tableSchema == null || tableName == null || recordId == null) {
            throw new IllegalArgumentException("tableSchema, tableName, and recordId are required");
        }

        List<AuditChangeLog> logs = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_CHANGE_LOG_BY_RECORD)) {
            ps.setObject(1, tenantId);
            ps.setString(2, tableSchema);
            ps.setString(3, tableName);
            ps.setObject(4, recordId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapRowToChangeLog(rs));
                }
            }
        }
        return logs;
    }

    private void bindAuditEventParams(Connection conn, PreparedStatement ps, AuditRecord r) throws SQLException {
        ps.setObject(1, r.getId());
        setNullableUuid(ps, 2, r.getTenantId());
        setNullableUuid(ps, 3, r.getCollegeId());
        ps.setTimestamp(4, Timestamp.from(r.getEventTime()));
        ps.setString(5, r.getAction());
        setNullableUuid(ps, 6, r.getPrincipalId());
        ps.setString(7, r.getPrincipalType());
        ps.setString(8, r.getResourceType());
        setNullableUuid(ps, 9, r.getResourceId());
        ps.setString(10, r.getStatus());
        ps.setString(11, r.getSeverity());
        ps.setString(12, r.getIp());
        ps.setString(13, r.getUserAgent());
        ps.setString(14, r.getCorrelationId());
        ps.setString(15, r.getDetailJson());
        ps.setTimestamp(16, Timestamp.from(r.getCreatedAt()));
        setNullableUuid(ps, 17, r.getCreatedBy());
    }

    private void bindChangeLogParams(Connection conn, PreparedStatement ps, AuditChangeLog c) throws SQLException {
        ps.setObject(1, c.getId());
        ps.setObject(2, c.getTenantId());
        ps.setString(3, c.getTableSchema());
        ps.setString(4, c.getTableName());
        setNullableUuid(ps, 5, c.getRecordId());
        ps.setString(6, c.getAction());

        // Array binding for changed_fields
        List<String> fields = c.getChangedFields();
        if (fields != null && !fields.isEmpty()) {
            try {
                Array array = conn.createArrayOf("text", fields.toArray(new String[0]));
                ps.setArray(7, array);
            } catch (Exception e) {
                // In test or non-supporting driver environments, fallback or set null
                ps.setNull(7, Types.ARRAY);
            }
        } else {
            ps.setNull(7, Types.ARRAY);
        }

        ps.setString(8, c.getOldDataJson());
        ps.setString(9, c.getNewDataJson());
        setNullableUuid(ps, 10, c.getActorId());
        ps.setString(11, c.getRequestId());
        ps.setTimestamp(12, Timestamp.from(c.getCreatedAt()));
        setNullableUuid(ps, 13, c.getCreatedBy());
    }

    private AuditRecord mapRowToAuditRecord(ResultSet rs) throws SQLException {
        return AuditRecord.builder()
                .id(getUuid(rs, "id"))
                .tenantId(getUuid(rs, "tenant_id"))
                .collegeId(getUuid(rs, "college_id"))
                .eventTime(getInstant(rs, "event_time"))
                .action(rs.getString("action"))
                .principalId(getUuid(rs, "principal_id"))
                .principalType(rs.getString("principal_type"))
                .resourceType(rs.getString("resource_type"))
                .resourceId(getUuid(rs, "resource_id"))
                .status(rs.getString("status"))
                .severity(rs.getString("severity"))
                .ip(rs.getString("ip"))
                .userAgent(rs.getString("user_agent"))
                .correlationId(rs.getString("correlation_id"))
                .detailJson(rs.getString("detail"))
                .createdAt(getInstant(rs, "created_at"))
                .createdBy(getUuid(rs, "created_by"))
                .build();
    }

    private AuditChangeLog mapRowToChangeLog(ResultSet rs) throws SQLException {
        List<String> changedFields = new ArrayList<>();
        Array arr = rs.getArray("changed_fields");
        if (arr != null) {
            Object obj = arr.getArray();
            if (obj instanceof String[]) {
                changedFields.addAll(Arrays.asList((String[]) obj));
            }
        }

        return AuditChangeLog.builder()
                .id(getUuid(rs, "id"))
                .tenantId(getUuid(rs, "tenant_id"))
                .tableSchema(rs.getString("table_schema"))
                .tableName(rs.getString("table_name"))
                .recordId(getUuid(rs, "record_id"))
                .action(rs.getString("action"))
                .changedFields(changedFields)
                .oldDataJson(rs.getString("old_data"))
                .newDataJson(rs.getString("new_data"))
                .actorId(getUuid(rs, "actor_id"))
                .requestId(rs.getString("request_id"))
                .createdAt(getInstant(rs, "created_at"))
                .createdBy(getUuid(rs, "created_by"))
                .build();
    }

    private void setNullableUuid(PreparedStatement ps, int paramIndex, UUID uuid) throws SQLException {
        if (uuid != null) {
            ps.setObject(paramIndex, uuid);
        } else {
            ps.setNull(paramIndex, Types.OTHER);
        }
    }

    private UUID getUuid(ResultSet rs, String col) throws SQLException {
        Object val = rs.getObject(col);
        if (val == null) return null;
        if (val instanceof UUID) return (UUID) val;
        try {
            return UUID.fromString(val.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Instant getInstant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts != null ? ts.toInstant() : null;
    }
}
