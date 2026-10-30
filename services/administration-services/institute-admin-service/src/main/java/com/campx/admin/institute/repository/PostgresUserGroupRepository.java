package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.UserGroup;
import com.campx.admin.institute.model.InstituteModels.UserGroupMember;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL implementation of {@link UserGroupRepository} backed by {@code iam.user_groups}
 * and {@code iam.user_group_members}.
 * Executes under PostgreSQL RLS kernel enforcement with user session context.
 */
public class PostgresUserGroupRepository implements UserGroupRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresUserGroupRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresUserGroupRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresUserGroupRepository(DatabaseConnectionManager connectionManager) {
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
    public UserGroup createUserGroup(UserSecurityContext context, UserGroup group) {
        checkContext(context);
        if (group == null) {
            throw new MalformedPayloadException("UserGroup payload cannot be null");
        }
        if (group.getCode() == null || group.getCode().trim().isEmpty()) {
            throw new MalformedPayloadException("UserGroup code is required");
        }
        if (group.getName() == null || group.getName().trim().isEmpty()) {
            throw new MalformedPayloadException("UserGroup name is required");
        }

        UUID groupId = (group.getId() != null && !group.getId().trim().isEmpty())
                ? UUID.fromString(group.getId().trim())
                : UUID.randomUUID();
        String code = group.getCode().trim().toUpperCase(Locale.ROOT);
        String name = group.getName().trim();
        String desc = group.getDescription();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO iam.user_groups " +
                    "(id, tenant_id, code, name, description, created_at, updated_at, created_by, updated_by, row_version) " +
                    "VALUES (?, ?, ?, ?, ?, now(), now(), ?, ?, 1) " +
                    "RETURNING id, tenant_id, code, name, description, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, groupId);
                ps.setObject(2, context.getTenantId());
                ps.setString(3, code);
                ps.setString(4, name);
                ps.setString(5, desc);
                ps.setObject(6, context.getUserId());
                ps.setObject(7, context.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        UserGroup persisted = mapGroupRow(rs);
                        logger.info("Persisted user group [{}] code [{}] in tenant [{}]",
                                persisted.getId(), persisted.getCode(), persisted.getTenantId());
                        return persisted;
                    }
                }
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new MalformedPayloadException("UserGroup code already exists: " + code);
                }
                throw e;
            }
            throw new MalformedPayloadException("Failed to persist user group");
        });
    }

    @Override
    public Optional<UserGroup> findById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();

        UUID groupUuid;
        try {
            groupUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, code, name, description, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM iam.user_groups WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, groupUuid);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapGroupRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<UserGroup> findByCode(UserSecurityContext context, String code) {
        checkContext(context);
        if (code == null || code.trim().isEmpty()) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, code, name, description, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM iam.user_groups WHERE code = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, code.trim().toUpperCase(Locale.ROOT));
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapGroupRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<UserGroup> listUserGroups(UserSecurityContext context) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, code, name, description, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM iam.user_groups WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY created_at ASC";
            List<UserGroup> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapGroupRow(rs));
                    }
                }
            }
            return Collections.unmodifiableList(list);
        });
    }

    @Override
    public UserGroup updateUserGroup(UserSecurityContext context, UserGroup group) {
        checkContext(context);
        if (group == null || group.getId() == null || group.getId().trim().isEmpty()) {
            throw new MalformedPayloadException("Group ID is required for update");
        }

        UUID groupUuid;
        try {
            groupUuid = UUID.fromString(group.getId().trim());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("UserGroup", group.getId());
        }

        String name = group.getName() != null && !group.getName().trim().isEmpty() ? group.getName().trim() : null;
        String desc = group.getDescription();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE iam.user_groups SET " +
                    "name = COALESCE(?, name), " +
                    "description = COALESCE(?, description), " +
                    "updated_by = ?, " +
                    "updated_at = now(), " +
                    "row_version = row_version + 1 " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL " +
                    "RETURNING id, tenant_id, code, name, description, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, desc);
                ps.setObject(3, context.getUserId());
                ps.setObject(4, groupUuid);
                ps.setObject(5, context.getTenantId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapGroupRow(rs);
                    }
                }
            }
            throw new ResourceNotFoundException("UserGroup", groupUuid.toString());
        });
    }

    @Override
    public void deleteUserGroup(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) {
            throw new MalformedPayloadException("Group ID is required for deletion");
        }

        UUID groupUuid;
        try {
            groupUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("UserGroup", id);
        }

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE iam.user_groups SET deleted_at = now(), updated_at = now(), updated_by = ? " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getUserId());
                ps.setObject(2, groupUuid);
                ps.setObject(3, context.getTenantId());
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    logger.info("Deleted user group [{}] in tenant [{}]", groupUuid, context.getTenantId());
                }
            }
            return null;
        });
    }

    @Override
    public UserGroupMember addMember(UserSecurityContext context, String groupId, String userId) {
        checkContext(context);
        if (groupId == null || groupId.trim().isEmpty()) {
            throw new MalformedPayloadException("groupId is required");
        }
        if (userId == null || userId.trim().isEmpty()) {
            throw new MalformedPayloadException("userId is required");
        }

        UUID groupUuid = UUID.fromString(groupId.trim());
        UUID userUuid = UUID.fromString(userId.trim());
        UUID memberId = UUID.randomUUID();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO iam.user_group_members " +
                    "(id, tenant_id, group_id, user_id, created_at, created_by) " +
                    "VALUES (?, ?, ?, ?, now(), ?) " +
                    "ON CONFLICT (tenant_id, group_id, user_id) DO UPDATE SET tenant_id = EXCLUDED.tenant_id " +
                    "RETURNING id, tenant_id, group_id, user_id, created_at, created_by";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, memberId);
                ps.setObject(2, context.getTenantId());
                ps.setObject(3, groupUuid);
                ps.setObject(4, userUuid);
                ps.setObject(5, context.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        UserGroupMember member = mapMemberRow(rs);
                        logger.info("Added user [{}] to group [{}]", member.getUserId(), member.getGroupId());
                        return member;
                    }
                }
            }
            throw new MalformedPayloadException("Failed to add user to group");
        });
    }

    @Override
    public List<UserGroupMember> listMembers(UserSecurityContext context, String groupId) {
        checkContext(context);
        if (groupId == null || groupId.trim().isEmpty()) return Collections.emptyList();

        UUID groupUuid;
        try {
            groupUuid = UUID.fromString(groupId.trim());
        } catch (IllegalArgumentException e) {
            return Collections.emptyList();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, group_id, user_id, created_at, created_by " +
                    "FROM iam.user_group_members WHERE group_id = ? AND tenant_id = ? ORDER BY created_at ASC";
            List<UserGroupMember> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, groupUuid);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapMemberRow(rs));
                    }
                }
            }
            return Collections.unmodifiableList(list);
        });
    }

    @Override
    public void removeMember(UserSecurityContext context, String groupId, String userId) {
        checkContext(context);
        if (groupId == null || userId == null) return;

        UUID groupUuid;
        UUID userUuid;
        try {
            groupUuid = UUID.fromString(groupId.trim());
            userUuid = UUID.fromString(userId.trim());
        } catch (IllegalArgumentException e) {
            return;
        }

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "DELETE FROM iam.user_group_members WHERE group_id = ? AND user_id = ? AND tenant_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, groupUuid);
                ps.setObject(2, userUuid);
                ps.setObject(3, context.getTenantId());
                ps.executeUpdate();
            }
            return null;
        });
    }

    private UserGroup mapGroupRow(ResultSet rs) throws SQLException {
        UserGroup g = new UserGroup();
        g.setId(rs.getString("id"));
        g.setTenantId(rs.getString("tenant_id"));
        g.setCode(rs.getString("code"));
        g.setName(rs.getString("name"));
        g.setDescription(rs.getString("description"));

        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) g.setCreatedAt(ca.getTime());

        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) g.setUpdatedAt(ua.getTime());

        Object cb = rs.getObject("created_by");
        if (cb != null) g.setCreatedBy(cb.toString());

        Object ub = rs.getObject("updated_by");
        if (ub != null) g.setUpdatedBy(ub.toString());

        g.setRowVersion(rs.getInt("row_version"));

        Timestamp da = rs.getTimestamp("deleted_at");
        if (da != null) g.setDeletedAt(da.getTime());

        return g;
    }

    private UserGroupMember mapMemberRow(ResultSet rs) throws SQLException {
        UserGroupMember m = new UserGroupMember();
        m.setId(rs.getString("id"));
        m.setTenantId(rs.getString("tenant_id"));
        m.setGroupId(rs.getString("group_id"));
        m.setUserId(rs.getString("user_id"));

        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) m.setCreatedAt(ca.getTime());

        Object cb = rs.getObject("created_by");
        if (cb != null) m.setCreatedBy(cb.toString());

        return m;
    }
}
