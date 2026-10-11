package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.PlatformAdmin;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL implementation of {@link PlatformAdminRepository} backed by {@code plat.platform_admins}.
 * Executes under RLS with role {@code authenticated} validated by {@code iam.is_platform_admin()}.
 */
public class PostgresPlatformAdminRepository implements PlatformAdminRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresPlatformAdminRepository.class);
    private static final UUID DEFAULT_SUPER_ADMIN_UUID = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");

    private final DatabaseConnectionManager connectionManager;

    public PostgresPlatformAdminRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresPlatformAdminRepository(DatabaseConnectionManager connectionManager) {
        if (connectionManager == null) {
            throw new IllegalArgumentException("DatabaseConnectionManager cannot be null");
        }
        this.connectionManager = connectionManager;
    }

    private UserSecurityContext resolveContext(UserSecurityContext context) {
        if (context == null || context.getUserId() == null) {
            return UserSecurityContext.forPlatformAdmin(DEFAULT_SUPER_ADMIN_UUID);
        }
        return context;
    }

    @Override
    public PlatformAdmin createPlatformAdmin(UserSecurityContext context, PlatformAdmin admin) {
        UserSecurityContext effectiveContext = resolveContext(context);
        if (admin == null || admin.getUserId() == null || admin.getUserId().trim().isEmpty()) {
            throw new MalformedPayloadException("User ID is mandatory for PlatformAdmin creation");
        }
        if (admin.getRoleCode() == null || admin.getRoleCode().trim().isEmpty()) {
            throw new MalformedPayloadException("Role code is mandatory for PlatformAdmin creation");
        }

        UUID adminId = (admin.getId() != null && !admin.getId().trim().isEmpty())
                ? UUID.fromString(admin.getId().trim())
                : UUID.randomUUID();
        UUID userUuid = UUID.fromString(admin.getUserId().trim());
        String roleCode = admin.getRoleCode().trim().toUpperCase(Locale.ROOT);
        String status = (admin.getStatus() != null && !admin.getStatus().trim().isEmpty())
                ? admin.getStatus().trim().toUpperCase(Locale.ROOT)
                : "ACTIVE";
        String fullName = admin.getFullName();

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO plat.platform_admins " +
                    "(id, user_id, full_name, role_code, status, created_by, updated_by, created_at, updated_at, row_version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, now(), now(), 1) " +
                    "RETURNING id, user_id, full_name, role_code, status, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, adminId);
                ps.setObject(2, userUuid);
                ps.setString(3, fullName);
                ps.setString(4, roleCode);
                ps.setString(5, status);
                ps.setObject(6, effectiveContext.getUserId());
                ps.setObject(7, effectiveContext.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        PlatformAdmin persisted = mapRow(rs);
                        logger.info("Persisted platform admin [{}] user={} role={}",
                                persisted.getId(), persisted.getUserId(), persisted.getRoleCode());
                        return persisted;
                    }
                }
            }
            throw new MalformedPayloadException("Failed to persist platform admin record");
        });
    }

    @Override
    public Optional<PlatformAdmin> findById(UserSecurityContext context, String id) {
        UserSecurityContext effectiveContext = resolveContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();

        UUID adminUuid;
        try {
            adminUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, user_id, full_name, role_code, status, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM plat.platform_admins WHERE id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, adminUuid);
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
    public Optional<PlatformAdmin> findByUserId(UserSecurityContext context, String userId) {
        UserSecurityContext effectiveContext = resolveContext(context);
        if (userId == null || userId.trim().isEmpty()) return Optional.empty();

        UUID userUuid;
        try {
            userUuid = UUID.fromString(userId.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, user_id, full_name, role_code, status, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM plat.platform_admins WHERE user_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, userUuid);
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
    public List<PlatformAdmin> listPlatformAdmins(UserSecurityContext context) {
        UserSecurityContext effectiveContext = resolveContext(context);
        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, user_id, full_name, role_code, status, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM plat.platform_admins WHERE deleted_at IS NULL ORDER BY created_at ASC";
            List<PlatformAdmin> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return Collections.unmodifiableList(list);
        });
    }

    @Override
    public PlatformAdmin updatePlatformAdmin(UserSecurityContext context, PlatformAdmin admin) {
        UserSecurityContext effectiveContext = resolveContext(context);
        if (admin == null || admin.getId() == null || admin.getId().trim().isEmpty()) {
            throw new MalformedPayloadException("PlatformAdmin ID is required for update");
        }

        UUID adminUuid;
        try {
            adminUuid = UUID.fromString(admin.getId().trim());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("PlatformAdmin", admin.getId());
        }

        String fullName = admin.getFullName();
        String roleCode = admin.getRoleCode() != null && !admin.getRoleCode().trim().isEmpty()
                ? admin.getRoleCode().trim().toUpperCase(Locale.ROOT) : null;
        String status = admin.getStatus() != null && !admin.getStatus().trim().isEmpty()
                ? admin.getStatus().trim().toUpperCase(Locale.ROOT) : null;

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE plat.platform_admins SET " +
                    "full_name = COALESCE(?, full_name), " +
                    "role_code = COALESCE(?, role_code), " +
                    "status = COALESCE(?, status), " +
                    "updated_by = ?, " +
                    "updated_at = now(), " +
                    "row_version = row_version + 1 " +
                    "WHERE id = ? AND deleted_at IS NULL " +
                    "RETURNING id, user_id, full_name, role_code, status, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, fullName);
                ps.setString(2, roleCode);
                ps.setString(3, status);
                ps.setObject(4, effectiveContext.getUserId());
                ps.setObject(5, adminUuid);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }
            throw new ResourceNotFoundException("PlatformAdmin", adminUuid.toString());
        });
    }

    @Override
    public void deletePlatformAdmin(UserSecurityContext context, String id) {
        UserSecurityContext effectiveContext = resolveContext(context);
        if (id == null || id.trim().isEmpty()) {
            throw new MalformedPayloadException("PlatformAdmin ID is required for deletion");
        }

        UUID adminUuid;
        try {
            adminUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("PlatformAdmin", id);
        }

        effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE plat.platform_admins SET deleted_at = now(), updated_at = now(), updated_by = ? " +
                    "WHERE id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, effectiveContext.getUserId());
                ps.setObject(2, adminUuid);
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    logger.info("Soft-deleted platform admin [{}]", adminUuid);
                }
            }
            return null;
        });
    }

    private PlatformAdmin mapRow(ResultSet rs) throws SQLException {
        PlatformAdmin a = new PlatformAdmin();
        a.setId(rs.getString("id"));
        a.setUserId(rs.getString("user_id"));
        a.setFullName(rs.getString("full_name"));
        a.setRoleCode(rs.getString("role_code"));
        a.setStatus(rs.getString("status"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) a.setCreatedAt(createdAt.getTime());

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) a.setUpdatedAt(updatedAt.getTime());

        Object cb = rs.getObject("created_by");
        if (cb != null) a.setCreatedBy(cb.toString());

        Object ub = rs.getObject("updated_by");
        if (ub != null) a.setUpdatedBy(ub.toString());

        a.setRowVersion(rs.getInt("row_version"));

        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        if (deletedAt != null) a.setDeletedAt(deletedAt.getTime());

        return a;
    }
}
