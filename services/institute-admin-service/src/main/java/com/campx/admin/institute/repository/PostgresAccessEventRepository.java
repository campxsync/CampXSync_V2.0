package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.AccessEvent;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL implementation of {@link AccessEventRepository} backed by {@code audit.access_events}.
 * Enforces RLS context, tenant boundary isolation, and immutable audit event logging.
 */
public class PostgresAccessEventRepository implements AccessEventRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresAccessEventRepository.class);

    private final DatabaseConnectionManager connectionManager;

    private static final Set<String> VALID_ACCESS_TYPES = new HashSet<>(Arrays.asList(
            "READ", "EXPORT", "PRINT", "DOWNLOAD"));

    public PostgresAccessEventRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresAccessEventRepository(DatabaseConnectionManager connectionManager) {
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
    public AccessEvent recordEvent(UserSecurityContext context, AccessEvent event) {
        checkContext(context);
        if (event == null) throw new MalformedPayloadException("AccessEvent cannot be null");
        if (event.getResourceType() == null || event.getResourceType().trim().isEmpty()) {
            throw new MalformedPayloadException("resourceType is required");
        }
        String accessType = event.getAccessType() != null ? event.getAccessType().trim().toUpperCase(Locale.ROOT) : "READ";
        if (!VALID_ACCESS_TYPES.contains(accessType)) {
            throw new MalformedPayloadException("Invalid accessType: " + accessType);
        }

        UUID eventId = (event.getId() != null && !event.getId().trim().isEmpty())
                ? UUID.fromString(event.getId().trim()) : UUID.randomUUID();
        UUID principalId = (event.getPrincipalId() != null && !event.getPrincipalId().trim().isEmpty())
                ? UUID.fromString(event.getPrincipalId().trim()) : context.getUserId();
        UUID resourceId = (event.getResourceId() != null && !event.getResourceId().trim().isEmpty())
                ? UUID.fromString(event.getResourceId().trim()) : null;

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO audit.access_events " +
                    "(id, tenant_id, principal_id, resource_type, resource_id, access_type, sensitivity, ip, reason, created_at, created_by) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?::inet, ?, now(), ?) " +
                    "RETURNING id, tenant_id, principal_id, resource_type, resource_id, access_type, sensitivity, ip, reason, created_at, created_by";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, eventId);
                ps.setObject(2, context.getTenantId());
                ps.setObject(3, principalId);
                ps.setString(4, event.getResourceType().trim());
                ps.setObject(5, resourceId);
                ps.setString(6, accessType);
                ps.setString(7, event.getSensitivity());
                if (event.getIp() != null && !event.getIp().trim().isEmpty()) {
                    ps.setString(8, event.getIp().trim());
                } else {
                    ps.setNull(8, Types.OTHER);
                }
                ps.setString(9, event.getReason());
                ps.setObject(10, context.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        AccessEvent persisted = mapRow(rs);
                        logger.info("Persisted access event [{}] on [{}] for tenant [{}]",
                                persisted.getId(), persisted.getResourceType(), persisted.getTenantId());
                        return persisted;
                    }
                }
            }
            throw new MalformedPayloadException("Failed to persist access event record");
        });
    }

    @Override
    public Optional<AccessEvent> findById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, principal_id, resource_type, resource_id, access_type, sensitivity, ip, reason, created_at, created_by " +
                    "FROM audit.access_events WHERE id = ? AND tenant_id = ?";
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
    public List<AccessEvent> listEventsByResource(UserSecurityContext context, String resourceType, String resourceId) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            UUID rid = (resourceId != null && !resourceId.trim().isEmpty()) ? UUID.fromString(resourceId.trim()) : null;
            String sql = "SELECT id, tenant_id, principal_id, resource_type, resource_id, access_type, sensitivity, ip, reason, created_at, created_by " +
                    "FROM audit.access_events WHERE tenant_id = ? AND resource_type = ? " +
                    (rid == null ? "" : "AND resource_id = ? ") +
                    "ORDER BY created_at DESC";
            List<AccessEvent> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setString(2, resourceType);
                if (rid != null) {
                    ps.setObject(3, rid);
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
    public List<AccessEvent> listEventsByPrincipal(UserSecurityContext context, String principalId) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, principal_id, resource_type, resource_id, access_type, sensitivity, ip, reason, created_at, created_by " +
                    "FROM audit.access_events WHERE tenant_id = ? AND principal_id = ? ORDER BY created_at DESC";
            List<AccessEvent> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setObject(2, UUID.fromString(principalId.trim()));
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
    public List<AccessEvent> listRecentEvents(UserSecurityContext context, int limit) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, principal_id, resource_type, resource_id, access_type, sensitivity, ip, reason, created_at, created_by " +
                    "FROM audit.access_events WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ?";
            List<AccessEvent> list = new ArrayList<>();
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

    private AccessEvent mapRow(ResultSet rs) throws SQLException {
        AccessEvent e = new AccessEvent();
        e.setId(rs.getString("id"));
        e.setTenantId(rs.getString("tenant_id"));
        e.setPrincipalId(rs.getString("principal_id"));
        e.setResourceType(rs.getString("resource_type"));
        e.setResourceId(rs.getString("resource_id"));
        e.setAccessType(rs.getString("access_type"));
        e.setSensitivity(rs.getString("sensitivity"));
        Object ipObj = rs.getObject("ip");
        e.setIp(ipObj != null ? ipObj.toString() : null);
        e.setReason(rs.getString("reason"));
        Timestamp createdTs = rs.getTimestamp("created_at");
        e.setCreatedAt(createdTs != null ? createdTs.getTime() : 0L);
        e.setCreatedBy(rs.getString("created_by"));
        return e;
    }
}
