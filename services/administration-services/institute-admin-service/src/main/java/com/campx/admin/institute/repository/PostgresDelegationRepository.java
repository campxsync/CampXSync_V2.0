package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.Delegation;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL implementation of {@link DelegationRepository} backed by {@code iam.delegations}.
 * Executes under PostgreSQL RLS kernel enforcement with user session context.
 */
public class PostgresDelegationRepository implements DelegationRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresDelegationRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresDelegationRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresDelegationRepository(DatabaseConnectionManager connectionManager) {
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
    public Delegation createDelegation(UserSecurityContext context, Delegation delegation) {
        checkContext(context);
        if (delegation == null) {
            throw new MalformedPayloadException("Delegation payload cannot be null");
        }
        if (delegation.getFromUserId() == null || delegation.getFromUserId().trim().isEmpty()) {
            throw new MalformedPayloadException("Delegating user (fromUserId) is required");
        }
        if (delegation.getToUserId() == null || delegation.getToUserId().trim().isEmpty()) {
            throw new MalformedPayloadException("Target user (toUserId) is required");
        }
        if (delegation.getRoleId() == null || delegation.getRoleId().trim().isEmpty()) {
            throw new MalformedPayloadException("Delegated role (roleId) is required");
        }
        if (delegation.getValidTo() <= delegation.getValidFrom()) {
            throw new MalformedPayloadException("validTo must be strictly greater than validFrom");
        }

        UUID delegationId = (delegation.getId() != null && !delegation.getId().trim().isEmpty())
                ? UUID.fromString(delegation.getId().trim())
                : UUID.randomUUID();
        UUID fromUserUuid = UUID.fromString(delegation.getFromUserId().trim());
        UUID toUserUuid = UUID.fromString(delegation.getToUserId().trim());
        UUID roleUuid = UUID.fromString(delegation.getRoleId().trim());
        String status = (delegation.getStatus() != null && !delegation.getStatus().trim().isEmpty())
                ? delegation.getStatus().trim().toUpperCase(Locale.ROOT)
                : "ACTIVE";

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO iam.delegations " +
                    "(id, tenant_id, from_user_id, to_user_id, role_id, valid_from, valid_to, reason, status, created_at, updated_at, created_by, updated_by, row_version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(), ?, ?, 1) " +
                    "RETURNING id, tenant_id, from_user_id, to_user_id, role_id, valid_from, valid_to, reason, status, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, delegationId);
                ps.setObject(2, context.getTenantId());
                ps.setObject(3, fromUserUuid);
                ps.setObject(4, toUserUuid);
                ps.setObject(5, roleUuid);
                ps.setTimestamp(6, new Timestamp(delegation.getValidFrom()));
                ps.setTimestamp(7, new Timestamp(delegation.getValidTo()));
                ps.setString(8, delegation.getReason());
                ps.setString(9, status);
                ps.setObject(10, context.getUserId());
                ps.setObject(11, context.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Delegation persisted = mapRow(rs);
                        logger.info("Persisted delegation [{}] from [{}] to [{}] in tenant [{}]",
                                persisted.getId(), persisted.getFromUserId(), persisted.getToUserId(), persisted.getTenantId());
                        return persisted;
                    }
                }
            }
            throw new MalformedPayloadException("Failed to persist delegation record");
        });
    }

    @Override
    public Optional<Delegation> findById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();

        UUID delegationUuid;
        try {
            delegationUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, from_user_id, to_user_id, role_id, valid_from, valid_to, reason, status, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM iam.delegations WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, delegationUuid);
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
    public List<Delegation> listDelegations(UserSecurityContext context) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, from_user_id, to_user_id, role_id, valid_from, valid_to, reason, status, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM iam.delegations WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY created_at ASC";
            List<Delegation> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                }
            }
            return Collections.unmodifiableList(list);
        });
    }

    @Override
    public List<Delegation> listDelegationsForUser(UserSecurityContext context, String userId) {
        checkContext(context);
        if (userId == null || userId.trim().isEmpty()) return Collections.emptyList();

        UUID userUuid;
        try {
            userUuid = UUID.fromString(userId.trim());
        } catch (IllegalArgumentException e) {
            return Collections.emptyList();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, from_user_id, to_user_id, role_id, valid_from, valid_to, reason, status, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM iam.delegations WHERE tenant_id = ? AND (from_user_id = ? OR to_user_id = ?) AND deleted_at IS NULL ORDER BY created_at ASC";
            List<Delegation> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setObject(2, userUuid);
                ps.setObject(3, userUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                }
            }
            return Collections.unmodifiableList(list);
        });
    }

    @Override
    public Delegation updateDelegation(UserSecurityContext context, Delegation delegation) {
        checkContext(context);
        if (delegation == null || delegation.getId() == null || delegation.getId().trim().isEmpty()) {
            throw new MalformedPayloadException("Delegation ID is required for update");
        }

        UUID delegationUuid;
        try {
            delegationUuid = UUID.fromString(delegation.getId().trim());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Delegation", delegation.getId());
        }

        boolean hasValidTo = delegation.getValidTo() > 0;
        String reason = delegation.getReason();
        String status = delegation.getStatus() != null && !delegation.getStatus().trim().isEmpty()
                ? delegation.getStatus().trim().toUpperCase(Locale.ROOT) : null;

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE iam.delegations SET " +
                    "valid_to = CASE WHEN ? THEN ? ELSE valid_to END, " +
                    "reason = COALESCE(?, reason), " +
                    "status = COALESCE(?, status), " +
                    "updated_by = ?, " +
                    "updated_at = now(), " +
                    "row_version = row_version + 1 " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL " +
                    "RETURNING id, tenant_id, from_user_id, to_user_id, role_id, valid_from, valid_to, reason, status, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setBoolean(1, hasValidTo);
                if (hasValidTo) {
                    ps.setTimestamp(2, new Timestamp(delegation.getValidTo()));
                } else {
                    ps.setNull(2, Types.TIMESTAMP);
                }
                ps.setString(3, reason);
                ps.setString(4, status);
                ps.setObject(5, context.getUserId());
                ps.setObject(6, delegationUuid);
                ps.setObject(7, context.getTenantId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }
            throw new ResourceNotFoundException("Delegation", delegationUuid.toString());
        });
    }

    @Override
    public void revokeDelegation(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) {
            throw new MalformedPayloadException("Delegation ID is required for revocation");
        }

        UUID delegationUuid;
        try {
            delegationUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Delegation", id);
        }

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE iam.delegations SET status = 'REVOKED', deleted_at = now(), updated_at = now(), updated_by = ? " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getUserId());
                ps.setObject(2, delegationUuid);
                ps.setObject(3, context.getTenantId());
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    logger.info("Revoked delegation [{}] for tenant [{}]", delegationUuid, context.getTenantId());
                }
            }
            return null;
        });
    }

    private Delegation mapRow(ResultSet rs) throws SQLException {
        Delegation d = new Delegation();
        d.setId(rs.getString("id"));
        d.setTenantId(rs.getString("tenant_id"));
        d.setFromUserId(rs.getString("from_user_id"));
        d.setToUserId(rs.getString("to_user_id"));
        d.setRoleId(rs.getString("role_id"));

        Timestamp vf = rs.getTimestamp("valid_from");
        if (vf != null) d.setValidFrom(vf.getTime());

        Timestamp vt = rs.getTimestamp("valid_to");
        if (vt != null) d.setValidTo(vt.getTime());

        d.setReason(rs.getString("reason"));
        d.setStatus(rs.getString("status"));

        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) d.setCreatedAt(ca.getTime());

        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) d.setUpdatedAt(ua.getTime());

        Object cb = rs.getObject("created_by");
        if (cb != null) d.setCreatedBy(cb.toString());

        Object ub = rs.getObject("updated_by");
        if (ub != null) d.setUpdatedBy(ub.toString());

        d.setRowVersion(rs.getInt("row_version"));

        Timestamp da = rs.getTimestamp("deleted_at");
        if (da != null) d.setDeletedAt(da.getTime());

        return d;
    }
}
