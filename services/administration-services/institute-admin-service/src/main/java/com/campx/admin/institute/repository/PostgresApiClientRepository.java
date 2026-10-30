package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.ApiClient;
import com.campx.admin.institute.model.InstituteModels.ApiClientGrant;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL implementation of {@link ApiClientRepository} backed by {@code iam.api_clients}
 * and {@code iam.api_client_grants}.
 * Operates under RLS kernel enforcement with user session context.
 */
public class PostgresApiClientRepository implements ApiClientRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresApiClientRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresApiClientRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresApiClientRepository(DatabaseConnectionManager connectionManager) {
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
    public ApiClient createApiClient(UserSecurityContext context, ApiClient client) {
        checkContext(context);
        if (client == null || client.getName() == null || client.getName().trim().isEmpty()) {
            throw new MalformedPayloadException("API Client name is required");
        }
        if (client.getUserId() == null || client.getUserId().trim().isEmpty()) {
            throw new MalformedPayloadException("API Client associated user ID is required");
        }

        UUID clientId = (client.getId() != null && !client.getId().trim().isEmpty())
                ? UUID.fromString(client.getId().trim())
                : UUID.randomUUID();
        UUID userUuid = UUID.fromString(client.getUserId().trim());
        UUID ownerUuid = (client.getOwnerUserId() != null && !client.getOwnerUserId().trim().isEmpty())
                ? UUID.fromString(client.getOwnerUserId().trim())
                : null;
        String status = (client.getStatus() != null && !client.getStatus().trim().isEmpty())
                ? client.getStatus().trim().toUpperCase(Locale.ROOT)
                : "ACTIVE";

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO iam.api_clients " +
                    "(id, tenant_id, user_id, name, owner_user_id, description, status, allowed_ips, expires_at, created_at, updated_at, created_by, updated_by, row_version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(), ?, ?, 1) " +
                    "RETURNING id, tenant_id, user_id, name, owner_user_id, description, status, allowed_ips, expires_at, last_used_at, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, clientId);
                ps.setObject(2, context.getTenantId());
                ps.setObject(3, userUuid);
                ps.setString(4, client.getName().trim());
                ps.setObject(5, ownerUuid);
                ps.setString(6, client.getDescription());
                ps.setString(7, status);

                if (client.getAllowedIps() != null && !client.getAllowedIps().isEmpty()) {
                    ps.setArray(8, conn.createArrayOf("text", client.getAllowedIps().toArray(new String[0])));
                } else {
                    ps.setNull(8, Types.ARRAY);
                }

                if (client.getExpiresAt() != null) {
                    ps.setTimestamp(9, new Timestamp(client.getExpiresAt()));
                } else {
                    ps.setNull(9, Types.TIMESTAMP);
                }

                ps.setObject(10, context.getUserId());
                ps.setObject(11, context.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        ApiClient persisted = mapClientRow(rs);
                        logger.info("Persisted API client [{}] for tenant [{}]", persisted.getId(), persisted.getTenantId());
                        return persisted;
                    }
                }
            }
            throw new MalformedPayloadException("Failed to persist API client record");
        });
    }

    @Override
    public Optional<ApiClient> findById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();

        UUID clientUuid;
        try {
            clientUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, user_id, name, owner_user_id, description, status, allowed_ips, expires_at, last_used_at, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM iam.api_clients WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, clientUuid);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapClientRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<ApiClient> listApiClients(UserSecurityContext context) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, user_id, name, owner_user_id, description, status, allowed_ips, expires_at, last_used_at, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM iam.api_clients WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY created_at ASC";
            List<ApiClient> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapClientRow(rs));
                    }
                }
            }
            return Collections.unmodifiableList(list);
        });
    }

    @Override
    public ApiClient updateApiClient(UserSecurityContext context, ApiClient client) {
        checkContext(context);
        if (client == null || client.getId() == null || client.getId().trim().isEmpty()) {
            throw new MalformedPayloadException("Client ID is required for update");
        }

        UUID clientUuid;
        try {
            clientUuid = UUID.fromString(client.getId().trim());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("ApiClient", client.getId());
        }

        String name = client.getName() != null && !client.getName().trim().isEmpty() ? client.getName().trim() : null;
        String desc = client.getDescription();
        String status = client.getStatus() != null && !client.getStatus().trim().isEmpty()
                ? client.getStatus().trim().toUpperCase(Locale.ROOT) : null;
        boolean hasAllowedIps = client.getAllowedIps() != null;
        boolean hasExpiresAt = client.getExpiresAt() != null;

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE iam.api_clients SET " +
                    "name = COALESCE(?, name), " +
                    "description = COALESCE(?, description), " +
                    "status = COALESCE(?, status), " +
                    "allowed_ips = CASE WHEN ? THEN ? ELSE allowed_ips END, " +
                    "expires_at = CASE WHEN ? THEN ? ELSE expires_at END, " +
                    "updated_by = ?, " +
                    "updated_at = now(), " +
                    "row_version = row_version + 1 " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL " +
                    "RETURNING id, tenant_id, user_id, name, owner_user_id, description, status, allowed_ips, expires_at, last_used_at, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, desc);
                ps.setString(3, status);

                ps.setBoolean(4, hasAllowedIps);
                if (hasAllowedIps) {
                    ps.setArray(5, conn.createArrayOf("text", client.getAllowedIps().toArray(new String[0])));
                } else {
                    ps.setNull(5, Types.ARRAY);
                }

                ps.setBoolean(6, hasExpiresAt);
                if (hasExpiresAt) {
                    ps.setTimestamp(7, new Timestamp(client.getExpiresAt()));
                } else {
                    ps.setNull(7, Types.TIMESTAMP);
                }

                ps.setObject(8, context.getUserId());
                ps.setObject(9, clientUuid);
                ps.setObject(10, context.getTenantId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapClientRow(rs);
                    }
                }
            }
            throw new ResourceNotFoundException("ApiClient", clientUuid.toString());
        });
    }

    @Override
    public void revokeApiClient(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) {
            throw new MalformedPayloadException("Client ID is required for revocation");
        }

        UUID clientUuid;
        try {
            clientUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("ApiClient", id);
        }

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE iam.api_clients SET status = 'REVOKED', deleted_at = now(), updated_at = now(), updated_by = ? " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getUserId());
                ps.setObject(2, clientUuid);
                ps.setObject(3, context.getTenantId());
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    logger.info("Revoked API client [{}] for tenant [{}]", clientUuid, context.getTenantId());
                }
            }
            return null;
        });
    }

    @Override
    public ApiClientGrant addGrant(UserSecurityContext context, ApiClientGrant grant) {
        checkContext(context);
        if (grant == null || grant.getClientId() == null || grant.getClientId().trim().isEmpty()) {
            throw new MalformedPayloadException("Client ID is required for grant creation");
        }
        if (grant.getPermissionId() == null || grant.getPermissionId().trim().isEmpty()) {
            throw new MalformedPayloadException("Permission ID is required for grant creation");
        }

        UUID grantId = (grant.getId() != null && !grant.getId().trim().isEmpty())
                ? UUID.fromString(grant.getId().trim())
                : UUID.randomUUID();
        UUID clientUuid = UUID.fromString(grant.getClientId().trim());
        UUID permUuid = UUID.fromString(grant.getPermissionId().trim());
        UUID collegeUuid = (grant.getCollegeId() != null && !grant.getCollegeId().trim().isEmpty())
                ? UUID.fromString(grant.getCollegeId().trim()) : null;
        UUID deptUuid = (grant.getDepartmentId() != null && !grant.getDepartmentId().trim().isEmpty())
                ? UUID.fromString(grant.getDepartmentId().trim()) : null;
        String resourceScope = grant.getResourceScope() != null ? grant.getResourceScope() : "{}";

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO iam.api_client_grants " +
                    "(id, tenant_id, client_id, permission_id, college_id, department_id, resource_scope, valid_to, created_at, updated_at, created_by, updated_by, row_version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, now(), now(), ?, ?, 1) " +
                    "RETURNING id, tenant_id, client_id, permission_id, college_id, department_id, resource_scope, valid_to, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, grantId);
                ps.setObject(2, context.getTenantId());
                ps.setObject(3, clientUuid);
                ps.setObject(4, permUuid);
                ps.setObject(5, collegeUuid);
                ps.setObject(6, deptUuid);
                ps.setString(7, resourceScope);

                if (grant.getValidTo() != null) {
                    ps.setTimestamp(8, new Timestamp(grant.getValidTo()));
                } else {
                    ps.setNull(8, Types.TIMESTAMP);
                }

                ps.setObject(9, context.getUserId());
                ps.setObject(10, context.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        ApiClientGrant persisted = mapGrantRow(rs);
                        logger.info("Persisted grant [{}] for client [{}]", persisted.getId(), persisted.getClientId());
                        return persisted;
                    }
                }
            }
            throw new MalformedPayloadException("Failed to persist API client grant");
        });
    }

    @Override
    public List<ApiClientGrant> listGrants(UserSecurityContext context, String clientId) {
        checkContext(context);
        if (clientId == null || clientId.trim().isEmpty()) return Collections.emptyList();

        UUID clientUuid;
        try {
            clientUuid = UUID.fromString(clientId.trim());
        } catch (IllegalArgumentException e) {
            return Collections.emptyList();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, client_id, permission_id, college_id, department_id, resource_scope, valid_to, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM iam.api_client_grants WHERE client_id = ? AND tenant_id = ? AND deleted_at IS NULL ORDER BY created_at ASC";
            List<ApiClientGrant> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, clientUuid);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapGrantRow(rs));
                    }
                }
            }
            return Collections.unmodifiableList(list);
        });
    }

    @Override
    public void revokeGrant(UserSecurityContext context, String grantId) {
        checkContext(context);
        if (grantId == null || grantId.trim().isEmpty()) return;

        UUID grantUuid;
        try {
            grantUuid = UUID.fromString(grantId.trim());
        } catch (IllegalArgumentException e) {
            return;
        }

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE iam.api_client_grants SET deleted_at = now(), updated_at = now(), updated_by = ? " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getUserId());
                ps.setObject(2, grantUuid);
                ps.setObject(3, context.getTenantId());
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    logger.info("Revoked grant [{}] for tenant [{}]", grantUuid, context.getTenantId());
                }
            }
            return null;
        });
    }

    private ApiClient mapClientRow(ResultSet rs) throws SQLException {
        ApiClient c = new ApiClient();
        c.setId(rs.getString("id"));
        c.setTenantId(rs.getString("tenant_id"));
        c.setUserId(rs.getString("user_id"));
        c.setName(rs.getString("name"));

        Object owner = rs.getObject("owner_user_id");
        if (owner != null) c.setOwnerUserId(owner.toString());

        c.setDescription(rs.getString("description"));
        c.setStatus(rs.getString("status"));

        Array arr = rs.getArray("allowed_ips");
        if (arr != null) {
            Object arrayObj = arr.getArray();
            if (arrayObj instanceof String[]) {
                c.setAllowedIps(Arrays.asList((String[]) arrayObj));
            } else if (arrayObj instanceof Object[]) {
                Object[] objs = (Object[]) arrayObj;
                List<String> ips = new ArrayList<>(objs.length);
                for (Object o : objs) {
                    if (o != null) ips.add(o.toString());
                }
                c.setAllowedIps(ips);
            }
        }

        Timestamp exp = rs.getTimestamp("expires_at");
        if (exp != null) c.setExpiresAt(exp.getTime());

        Timestamp lastUsed = rs.getTimestamp("last_used_at");
        if (lastUsed != null) c.setLastUsedAt(lastUsed.getTime());

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) c.setCreatedAt(createdAt.getTime());

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) c.setUpdatedAt(updatedAt.getTime());

        Object cb = rs.getObject("created_by");
        if (cb != null) c.setCreatedBy(cb.toString());

        Object ub = rs.getObject("updated_by");
        if (ub != null) c.setUpdatedBy(ub.toString());

        c.setRowVersion(rs.getInt("row_version"));

        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        if (deletedAt != null) c.setDeletedAt(deletedAt.getTime());

        return c;
    }

    private ApiClientGrant mapGrantRow(ResultSet rs) throws SQLException {
        ApiClientGrant g = new ApiClientGrant();
        g.setId(rs.getString("id"));
        g.setTenantId(rs.getString("tenant_id"));
        g.setClientId(rs.getString("client_id"));
        g.setPermissionId(rs.getString("permission_id"));

        Object col = rs.getObject("college_id");
        if (col != null) g.setCollegeId(col.toString());

        Object dept = rs.getObject("department_id");
        if (dept != null) g.setDepartmentId(dept.toString());

        g.setResourceScope(rs.getString("resource_scope"));

        Timestamp vt = rs.getTimestamp("valid_to");
        if (vt != null) g.setValidTo(vt.getTime());

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) g.setCreatedAt(createdAt.getTime());

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) g.setUpdatedAt(updatedAt.getTime());

        Object cb = rs.getObject("created_by");
        if (cb != null) g.setCreatedBy(cb.toString());

        Object ub = rs.getObject("updated_by");
        if (ub != null) g.setUpdatedBy(ub.toString());

        g.setRowVersion(rs.getInt("row_version"));

        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        if (deletedAt != null) g.setDeletedAt(deletedAt.getTime());

        return g;
    }
}
