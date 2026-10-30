package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code audit.events} table (ADM-01 Item 10).
 * Records tamper-evident structured audit events directly to the database.
 */
public class PostgresSystemAuditLogRepository implements SystemAuditLogRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresSystemAuditLogRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresSystemAuditLogRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresSystemAuditLogRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public String recordEvent(UserSecurityContext context, AuditEvent event) {
        UUID eventId = UUID.randomUUID();
        UUID tenantUuid = context != null ? context.getTenantId() : null;
        UUID userUuid = context != null ? context.getUserId() : null;

        String action = event != null && event.getAction() != null ? event.getAction() : "ACTION";
        String status = event != null && event.getStatus() != null ? event.getStatus() : "SUCCESS";
        String resourceType = event != null && event.getResourceType() != null ? event.getResourceType() : "SYSTEM";

        UUID principalUuid = null;
        if (event != null && event.getPrincipalId() != null) {
            try {
                principalUuid = UUID.fromString(event.getPrincipalId().trim());
            } catch (IllegalArgumentException ignored) {}
        }
        if (principalUuid == null) {
            principalUuid = userUuid;
        }

        UUID resourceUuid = null;
        if (event != null && event.getResourceId() != null) {
            try {
                resourceUuid = UUID.fromString(event.getResourceId().trim());
            } catch (IllegalArgumentException ignored) {}
        }

        String description = event != null && event.getDescription() != null ? event.getDescription() : "";
        String detailJson = "{\"description\":\"" + description.replace("\"", "\\\"") + "\"}";

        UUID finalPrincipal = principalUuid;
        UUID finalResource = resourceUuid;

        UserSecurityContext effectiveContext = context != null ? context :
                UserSecurityContext.forPlatformAdmin(UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880"));

        effectiveContext.executeInTransaction(connectionManager, conn -> {
            String insertSql = "INSERT INTO audit.events ("
                    + "id, tenant_id, event_time, action, principal_id, principal_type, "
                    + "resource_type, resource_id, status, severity, correlation_id, detail, created_at, created_by"
                    + ") VALUES (?, ?, now(), ?, ?, 'USER', ?, ?, ?, 'INFO', ?, ?::jsonb, now(), ?)";

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setObject(1, eventId);
                ps.setObject(2, tenantUuid);
                ps.setString(3, action);
                ps.setObject(4, finalPrincipal);
                ps.setString(5, resourceType);
                ps.setObject(6, finalResource);
                ps.setString(7, status);
                ps.setString(8, eventId.toString());
                ps.setString(9, detailJson);
                ps.setObject(10, finalPrincipal);
                ps.executeUpdate();
            }
            return null;
        });

        logger.debug("Recorded audit event [{}] action={} tenant={}", eventId, action, tenantUuid);
        return eventId.toString();
    }

    @Override
    public List<Map<String, Object>> listRecentEvents(UserSecurityContext context, int limit) {
        if (context == null || context.getTenantId() == null) {
            return Collections.emptyList();
        }

        UUID tenantUuid = context.getTenantId();

        return context.executeInTransaction(connectionManager, conn -> {
            List<Map<String, Object>> list = new ArrayList<>();
            String sql = "SELECT id, tenant_id, college_id, event_time, action, principal_id, resource_type, resource_id, status, severity, correlation_id "
                    + "FROM audit.events WHERE tenant_id = ? ORDER BY event_time DESC LIMIT ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, tenantUuid);
                ps.setInt(2, limit > 0 ? limit : 50);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getString("id"));
                        row.put("tenantId", rs.getString("tenant_id"));
                        row.put("collegeId", rs.getString("college_id"));
                        Timestamp et = rs.getTimestamp("event_time");
                        row.put("eventTime", et != null ? et.getTime() : null);
                        row.put("action", rs.getString("action"));
                        row.put("principalId", rs.getString("principal_id"));
                        row.put("resourceType", rs.getString("resource_type"));
                        row.put("resourceId", rs.getString("resource_id"));
                        row.put("status", rs.getString("status"));
                        row.put("severity", rs.getString("severity"));
                        row.put("correlationId", rs.getString("correlation_id"));
                        list.add(row);
                    }
                }
            }
            return list;
        });
    }
}
