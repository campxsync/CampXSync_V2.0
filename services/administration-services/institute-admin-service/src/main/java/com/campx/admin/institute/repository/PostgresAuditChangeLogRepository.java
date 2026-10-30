package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.AuditChangeLog;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL implementation of {@link AuditChangeLogRepository} backed by {@code audit.change_log}.
 * Enforces RLS, tenant isolation, and immutable row-level change logging.
 */
public class PostgresAuditChangeLogRepository implements AuditChangeLogRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresAuditChangeLogRepository.class);

    private final DatabaseConnectionManager connectionManager;

    private static final Set<String> VALID_ACTIONS = new HashSet<>(Arrays.asList("I", "U", "D"));

    public PostgresAuditChangeLogRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresAuditChangeLogRepository(DatabaseConnectionManager connectionManager) {
        if (connectionManager == null) {
            throw new IllegalArgumentException("DatabaseConnectionManager cannot be null");
        }
        this.connectionManager = connectionManager;
    }

    private void checkContext(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
    }

    @Override
    public AuditChangeLog recordChange(UserSecurityContext context, AuditChangeLog log) {
        checkContext(context);
        if (log == null) throw new MalformedPayloadException("AuditChangeLog cannot be null");
        if (log.getTableSchema() == null || log.getTableSchema().trim().isEmpty()) {
            throw new MalformedPayloadException("tableSchema is required");
        }
        if (log.getTableName() == null || log.getTableName().trim().isEmpty()) {
            throw new MalformedPayloadException("tableName is required");
        }
        String action = log.getAction() != null ? log.getAction().trim().toUpperCase(Locale.ROOT) : "I";
        if (!VALID_ACTIONS.contains(action)) {
            throw new MalformedPayloadException("Invalid action: " + action);
        }

        UUID logId = (log.getId() != null && !log.getId().trim().isEmpty())
                ? UUID.fromString(log.getId().trim()) : UUID.randomUUID();
        UUID recordId = (log.getRecordId() != null && !log.getRecordId().trim().isEmpty())
                ? UUID.fromString(log.getRecordId().trim()) : null;
        UUID actorId = (log.getActorId() != null && !log.getActorId().trim().isEmpty())
                ? UUID.fromString(log.getActorId().trim()) : context.getUserId();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO audit.change_log " +
                    "(id, tenant_id, table_schema, table_name, record_id, action, changed_fields, old_data, new_data, actor_id, request_id, created_at, created_by) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, now(), ?) " +
                    "RETURNING id, tenant_id, table_schema, table_name, record_id, action, changed_fields, old_data, new_data, actor_id, request_id, created_at, created_by";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, logId);
                ps.setObject(2, context.getTenantId());
                ps.setString(3, log.getTableSchema().trim().toLowerCase(Locale.ROOT));
                ps.setString(4, log.getTableName().trim().toLowerCase(Locale.ROOT));
                ps.setObject(5, recordId);
                ps.setString(6, action);

                if (log.getChangedFields() != null && !log.getChangedFields().isEmpty()) {
                    ps.setArray(7, conn.createArrayOf("text", log.getChangedFields().toArray(new String[0])));
                } else {
                    ps.setNull(7, Types.ARRAY);
                }

                if (log.getOldData() != null && !log.getOldData().trim().isEmpty()) {
                    ps.setString(8, log.getOldData().trim());
                } else {
                    ps.setNull(8, Types.OTHER);
                }

                if (log.getNewData() != null && !log.getNewData().trim().isEmpty()) {
                    ps.setString(9, log.getNewData().trim());
                } else {
                    ps.setNull(9, Types.OTHER);
                }

                ps.setObject(10, actorId);
                ps.setString(11, log.getRequestId());
                ps.setObject(12, context.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        AuditChangeLog persisted = mapRow(rs);
                        logger.info("Persisted change log [{}] for [{}.{}] in tenant [{}]",
                                persisted.getId(), persisted.getTableSchema(), persisted.getTableName(), persisted.getTenantId());
                        return persisted;
                    }
                }
            }
            throw new MalformedPayloadException("Failed to persist audit change log record");
        });
    }

    @Override
    public Optional<AuditChangeLog> findById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, table_schema, table_name, record_id, action, changed_fields, old_data, new_data, actor_id, request_id, created_at, created_by " +
                    "FROM audit.change_log WHERE id = ? AND tenant_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, UUID.fromString(id.trim()));
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<AuditChangeLog> listChangesByRecord(UserSecurityContext context, String tableSchema, String tableName, String recordId) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            UUID rid = (recordId != null && !recordId.trim().isEmpty()) ? UUID.fromString(recordId.trim()) : null;
            String sql = "SELECT id, tenant_id, table_schema, table_name, record_id, action, changed_fields, old_data, new_data, actor_id, request_id, created_at, created_by " +
                    "FROM audit.change_log WHERE tenant_id = ? AND table_schema = ? AND table_name = ? " +
                    (rid == null ? "" : "AND record_id = ? ") +
                    "ORDER BY created_at DESC";
            List<AuditChangeLog> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setString(2, tableSchema.toLowerCase(Locale.ROOT));
                ps.setString(3, tableName.toLowerCase(Locale.ROOT));
                if (rid != null) {
                    ps.setObject(4, rid);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public List<AuditChangeLog> listChangesByActor(UserSecurityContext context, String actorId) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, table_schema, table_name, record_id, action, changed_fields, old_data, new_data, actor_id, request_id, created_at, created_by " +
                    "FROM audit.change_log WHERE tenant_id = ? AND actor_id = ? ORDER BY created_at DESC";
            List<AuditChangeLog> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setObject(2, UUID.fromString(actorId.trim()));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public List<AuditChangeLog> listRecentChanges(UserSecurityContext context, int limit) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, table_schema, table_name, record_id, action, changed_fields, old_data, new_data, actor_id, request_id, created_at, created_by " +
                    "FROM audit.change_log WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ?";
            List<AuditChangeLog> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setInt(2, limit > 0 ? limit : 50);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                }
            }
            return list;
        });
    }

    private AuditChangeLog mapRow(ResultSet rs) throws SQLException {
        AuditChangeLog l = new AuditChangeLog();
        l.setId(rs.getString("id"));
        l.setTenantId(rs.getString("tenant_id"));
        l.setTableSchema(rs.getString("table_schema"));
        l.setTableName(rs.getString("table_name"));
        l.setRecordId(rs.getString("record_id"));
        l.setAction(rs.getString("action"));

        Array changedArray = rs.getArray("changed_fields");
        if (changedArray != null) {
            String[] arr = (String[]) changedArray.getArray();
            l.setChangedFields(Arrays.asList(arr));
        } else {
            l.setChangedFields(Collections.emptyList());
        }

        l.setOldData(rs.getString("old_data"));
        l.setNewData(rs.getString("new_data"));
        l.setActorId(rs.getString("actor_id"));
        l.setRequestId(rs.getString("request_id"));
        Timestamp createdTs = rs.getTimestamp("created_at");
        l.setCreatedAt(createdTs != null ? createdTs.getTime() : 0L);
        l.setCreatedBy(rs.getString("created_by"));
        return l;
    }
}
