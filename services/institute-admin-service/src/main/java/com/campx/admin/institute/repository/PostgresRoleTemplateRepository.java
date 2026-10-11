package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.RoleTemplate;
import com.campx.admin.institute.model.InstituteModels.RoleTemplatePermission;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL implementation of {@link RoleTemplateRepository} backed by {@code iam.role_templates}
 * and {@code iam.role_template_permissions}.
 * Governed by RLS policies where all authenticated users can read, and platform administrators can modify.
 */
public class PostgresRoleTemplateRepository implements RoleTemplateRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresRoleTemplateRepository.class);
    private static final UUID DEFAULT_SUPER_ADMIN_UUID = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");

    private final DatabaseConnectionManager connectionManager;

    public PostgresRoleTemplateRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresRoleTemplateRepository(DatabaseConnectionManager connectionManager) {
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
    public RoleTemplate createRoleTemplate(UserSecurityContext context, RoleTemplate template) {
        UserSecurityContext effectiveContext = resolveContext(context);
        if (template == null) {
            throw new MalformedPayloadException("RoleTemplate payload cannot be null");
        }
        if (template.getCode() == null || template.getCode().trim().isEmpty()) {
            throw new MalformedPayloadException("RoleTemplate code is required");
        }
        if (template.getName() == null || template.getName().trim().isEmpty()) {
            throw new MalformedPayloadException("RoleTemplate name is required");
        }

        UUID templateId = (template.getId() != null && !template.getId().trim().isEmpty())
                ? UUID.fromString(template.getId().trim())
                : UUID.randomUUID();
        String code = template.getCode().trim().toUpperCase(Locale.ROOT);
        String name = template.getName().trim();

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO iam.role_templates " +
                    "(id, code, name, catalogue_id, kind, scope_note, created_at, updated_at, created_by, updated_by, row_version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, now(), now(), ?, ?, 1) " +
                    "RETURNING id, code, name, catalogue_id, kind, scope_note, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, templateId);
                ps.setString(2, code);
                ps.setString(3, name);
                ps.setString(4, template.getCatalogueId());
                ps.setString(5, template.getKind());
                ps.setString(6, template.getScopeNote());
                ps.setObject(7, effectiveContext.getUserId());
                ps.setObject(8, effectiveContext.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        RoleTemplate persisted = mapTemplateRow(rs);
                        logger.info("Persisted role template [{}] code [{}]", persisted.getId(), persisted.getCode());
                        return persisted;
                    }
                }
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new MalformedPayloadException("RoleTemplate code already exists: " + code);
                }
                throw e;
            }
            throw new MalformedPayloadException("Failed to persist role template");
        });
    }

    @Override
    public Optional<RoleTemplate> findById(UserSecurityContext context, String id) {
        UserSecurityContext effectiveContext = resolveContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();

        UUID templateUuid;
        try {
            templateUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, code, name, catalogue_id, kind, scope_note, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM iam.role_templates WHERE id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, templateUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapTemplateRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<RoleTemplate> findByCode(UserSecurityContext context, String code) {
        UserSecurityContext effectiveContext = resolveContext(context);
        if (code == null || code.trim().isEmpty()) return Optional.empty();

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, code, name, catalogue_id, kind, scope_note, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM iam.role_templates WHERE code = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, code.trim().toUpperCase(Locale.ROOT));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapTemplateRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<RoleTemplate> listRoleTemplates(UserSecurityContext context) {
        UserSecurityContext effectiveContext = resolveContext(context);
        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, code, name, catalogue_id, kind, scope_note, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM iam.role_templates WHERE deleted_at IS NULL ORDER BY code ASC";
            List<RoleTemplate> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapTemplateRow(rs));
                }
            }
            return Collections.unmodifiableList(list);
        });
    }

    @Override
    public RoleTemplate updateRoleTemplate(UserSecurityContext context, RoleTemplate template) {
        UserSecurityContext effectiveContext = resolveContext(context);
        if (template == null || template.getId() == null || template.getId().trim().isEmpty()) {
            throw new MalformedPayloadException("Template ID is required for update");
        }

        UUID templateUuid;
        try {
            templateUuid = UUID.fromString(template.getId().trim());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("RoleTemplate", template.getId());
        }

        String name = template.getName() != null && !template.getName().trim().isEmpty() ? template.getName().trim() : null;
        String catId = template.getCatalogueId();
        String kind = template.getKind();
        String scopeNote = template.getScopeNote();

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE iam.role_templates SET " +
                    "name = COALESCE(?, name), " +
                    "catalogue_id = COALESCE(?, catalogue_id), " +
                    "kind = COALESCE(?, kind), " +
                    "scope_note = COALESCE(?, scope_note), " +
                    "updated_by = ?, " +
                    "updated_at = now(), " +
                    "row_version = row_version + 1 " +
                    "WHERE id = ? AND deleted_at IS NULL " +
                    "RETURNING id, code, name, catalogue_id, kind, scope_note, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, catId);
                ps.setString(3, kind);
                ps.setString(4, scopeNote);
                ps.setObject(5, effectiveContext.getUserId());
                ps.setObject(6, templateUuid);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapTemplateRow(rs);
                    }
                }
            }
            throw new ResourceNotFoundException("RoleTemplate", templateUuid.toString());
        });
    }

    @Override
    public void deleteRoleTemplate(UserSecurityContext context, String id) {
        UserSecurityContext effectiveContext = resolveContext(context);
        if (id == null || id.trim().isEmpty()) {
            throw new MalformedPayloadException("Template ID is required for deletion");
        }

        UUID templateUuid;
        try {
            templateUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("RoleTemplate", id);
        }

        effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE iam.role_templates SET deleted_at = now(), updated_at = now(), updated_by = ? " +
                    "WHERE id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, effectiveContext.getUserId());
                ps.setObject(2, templateUuid);
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    logger.info("Deleted role template [{}]", templateUuid);
                }
            }
            return null;
        });
    }

    @Override
    public RoleTemplatePermission addPermission(UserSecurityContext context, String roleCode, String permissionCode) {
        UserSecurityContext effectiveContext = resolveContext(context);
        if (roleCode == null || roleCode.trim().isEmpty()) {
            throw new MalformedPayloadException("roleCode is required");
        }
        if (permissionCode == null || permissionCode.trim().isEmpty()) {
            throw new MalformedPayloadException("permissionCode is required");
        }

        String rc = roleCode.trim().toUpperCase(Locale.ROOT);
        String pc = permissionCode.trim();
        UUID permId = UUID.randomUUID();

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO iam.role_template_permissions " +
                    "(id, role_code, permission_code, created_at, updated_at, created_by, updated_by, row_version) " +
                    "VALUES (?, ?, ?, now(), now(), ?, ?, 1) " +
                    "RETURNING id, role_code, permission_code, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, permId);
                ps.setString(2, rc);
                ps.setString(3, pc);
                ps.setObject(4, effectiveContext.getUserId());
                ps.setObject(5, effectiveContext.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        RoleTemplatePermission perm = mapPermissionRow(rs);
                        logger.info("Bound permission [{}] to role template [{}]", perm.getPermissionCode(), perm.getRoleCode());
                        return perm;
                    }
                }
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    // Already exists, return existing
                    String selectSql = "SELECT id, role_code, permission_code, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                            "FROM iam.role_template_permissions WHERE role_code = ? AND permission_code = ? AND deleted_at IS NULL";
                    try (PreparedStatement sel = conn.prepareStatement(selectSql)) {
                        sel.setString(1, rc);
                        sel.setString(2, pc);
                        try (ResultSet rs = sel.executeQuery()) {
                            if (rs.next()) return mapPermissionRow(rs);
                        }
                    }
                }
                throw e;
            }
            throw new MalformedPayloadException("Failed to bind permission to role template");
        });
    }

    @Override
    public List<RoleTemplatePermission> listPermissions(UserSecurityContext context, String roleCode) {
        UserSecurityContext effectiveContext = resolveContext(context);
        if (roleCode == null || roleCode.trim().isEmpty()) return Collections.emptyList();

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, role_code, permission_code, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM iam.role_template_permissions WHERE role_code = ? AND deleted_at IS NULL ORDER BY permission_code ASC";
            List<RoleTemplatePermission> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, roleCode.trim().toUpperCase(Locale.ROOT));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapPermissionRow(rs));
                    }
                }
            }
            return Collections.unmodifiableList(list);
        });
    }

    @Override
    public void removePermission(UserSecurityContext context, String roleCode, String permissionCode) {
        UserSecurityContext effectiveContext = resolveContext(context);
        if (roleCode == null || permissionCode == null) return;

        effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE iam.role_template_permissions SET deleted_at = now(), updated_at = now(), updated_by = ? " +
                    "WHERE role_code = ? AND permission_code = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, effectiveContext.getUserId());
                ps.setString(2, roleCode.trim().toUpperCase(Locale.ROOT));
                ps.setString(3, permissionCode.trim());
                ps.executeUpdate();
            }
            return null;
        });
    }

    private RoleTemplate mapTemplateRow(ResultSet rs) throws SQLException {
        RoleTemplate t = new RoleTemplate();
        t.setId(rs.getString("id"));
        t.setCode(rs.getString("code"));
        t.setName(rs.getString("name"));
        t.setCatalogueId(rs.getString("catalogue_id"));
        t.setKind(rs.getString("kind"));
        t.setScopeNote(rs.getString("scope_note"));

        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) t.setCreatedAt(ca.getTime());

        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) t.setUpdatedAt(ua.getTime());

        Object cb = rs.getObject("created_by");
        if (cb != null) t.setCreatedBy(cb.toString());

        Object ub = rs.getObject("updated_by");
        if (ub != null) t.setUpdatedBy(ub.toString());

        t.setRowVersion(rs.getInt("row_version"));

        Timestamp da = rs.getTimestamp("deleted_at");
        if (da != null) t.setDeletedAt(da.getTime());

        return t;
    }

    private RoleTemplatePermission mapPermissionRow(ResultSet rs) throws SQLException {
        RoleTemplatePermission p = new RoleTemplatePermission();
        p.setId(rs.getString("id"));
        p.setRoleCode(rs.getString("role_code"));
        p.setPermissionCode(rs.getString("permission_code"));

        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) p.setCreatedAt(ca.getTime());

        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) p.setUpdatedAt(ua.getTime());

        Object cb = rs.getObject("created_by");
        if (cb != null) p.setCreatedBy(cb.toString());

        Object ub = rs.getObject("updated_by");
        if (ub != null) p.setUpdatedBy(ub.toString());

        p.setRowVersion(rs.getInt("row_version"));

        Timestamp da = rs.getTimestamp("deleted_at");
        if (da != null) p.setDeletedAt(da.getTime());

        return p;
    }
}
