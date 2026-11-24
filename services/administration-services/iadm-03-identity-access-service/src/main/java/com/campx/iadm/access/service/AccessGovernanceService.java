package com.campx.iadm.access.service;

import com.campx.iadm.access.config.DatabaseConnectionManager;
import com.campx.iadm.access.exception.InstituteAdminException;
import com.campx.iadm.access.exception.UserProfileAccessDeniedException;
import com.campx.iadm.access.security.GrantorCeiling;
import com.campx.iadm.access.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-03 S0 foundation: health, catalogue reads (reuse iam.*), idempotency, durable audit helpers.
 */
public class AccessGovernanceService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AccessGovernanceService.class);

    private final DatabaseConnectionManager db;

    public AccessGovernanceService() {
        this(DatabaseConnectionManager.getInstance());
    }

    public AccessGovernanceService(DatabaseConnectionManager db) {
        this.db = db == null ? DatabaseConnectionManager.getInstance() : db;
    }

    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("status", "UP");
        out.put("service", "IADM-03");
        out.put("prefix", "/api/v1/iadm/03");
        out.put("port", Integer.valueOf(8101));
        out.put("runtimePath", "NOT_PER_REQUEST");
        out.put("adm03Family", "DORMANT");
        return out;
    }

    /** IADM-03-US-005 — reuse iam.role_templates; scope by actor. */
    public Map<String, Object> listRoleCatalogue(final UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iam.catalog.roles.view");
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                boolean platform = isPlatformOperator(conn, ctx);
                String sql = "SELECT code, name, catalogue_id, kind, scope_note "
                        + "FROM iam.role_templates WHERE deleted_at IS NULL ORDER BY code";
                try (PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String code = rs.getString("code");
                        if (!platform && !isAssignableInsideInstitute(code)) {
                            continue;
                        }
                        Map<String, Object> row = new LinkedHashMap<String, Object>();
                        row.put("code", code);
                        row.put("name", rs.getString("name"));
                        row.put("catalogueId", rs.getString("catalogue_id"));
                        row.put("kind", rs.getString("kind"));
                        row.put("scopeNote", rs.getString("scope_note"));
                        row.put("status", "ACTIVE");
                        items.add(row);
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                out.put("count", Integer.valueOf(items.size()));
                out.put("scope", platform ? "PLATFORM" : "INSTITUTE");
                return out;
            }
        });
    }

    /** IADM-03-US-005 — reuse iam.permissions (search). */
    public Map<String, Object> searchPermissions(final UserSecurityContext ctx, final String q) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iam.catalog.permissions.view");
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                String like = q == null || q.trim().isEmpty() ? "%" : "%" + q.trim().toLowerCase() + "%";
                String sql = "SELECT p.code, p.module, p.action, p.description, "
                        + "coalesce(string_agg(DISTINCT rtp.role_code, ','), '') AS mapped_roles "
                        + "FROM iam.permissions p "
                        + "LEFT JOIN iam.role_template_permissions rtp "
                        + "  ON rtp.permission_code = p.code AND rtp.deleted_at IS NULL "
                        + "WHERE p.deleted_at IS NULL "
                        + "AND (lower(p.code) LIKE ? OR lower(coalesce(p.description,'')) LIKE ? "
                        + "     OR lower(coalesce(p.module,'')) LIKE ? OR lower(coalesce(p.action,'')) LIKE ?) "
                        + "GROUP BY p.code, p.module, p.action, p.description "
                        + "ORDER BY p.code LIMIT 100";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, like);
                    ps.setString(2, like);
                    ps.setString(3, like);
                    ps.setString(4, like);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<String, Object>();
                            row.put("code", rs.getString("code"));
                            row.put("module", rs.getString("module"));
                            row.put("action", rs.getString("action"));
                            row.put("description", rs.getString("description"));
                            String mapped = rs.getString("mapped_roles");
                            List<String> roles = new ArrayList<String>();
                            if (mapped != null && !mapped.isEmpty()) {
                                for (String part : mapped.split(",")) {
                                    if (part != null && !part.trim().isEmpty()) {
                                        roles.add(part.trim());
                                    }
                                }
                            }
                            row.put("mappedRoles", roles);
                            items.add(row);
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                out.put("count", Integer.valueOf(items.size()));
                out.put("query", q == null ? "" : q);
                return out;
            }
        });
    }

    /**
     * IADM-03-US-006 — begin an idempotent operation; returns prior result payload if key seen.
     */
    public Map<String, Object> beginIdempotent(final UserSecurityContext ctx, final String operation,
                                               final String idempotencyKey, final String correlationId) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_IDEMPOTENCY_REQUIRED",
                    "Idempotency-Key is required for this mutation.");
        }
        final String key = idempotencyKey.trim();
        final String corr = correlationId == null || correlationId.trim().isEmpty()
                ? UUID.randomUUID().toString() : correlationId.trim();
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                try (PreparedStatement find = conn.prepareStatement(
                        "SELECT result_payload::text, status FROM iam.governance_idempotency "
                                + "WHERE idempotency_key = ? AND deleted_at IS NULL")) {
                    find.setString(1, key);
                    try (ResultSet rs = find.executeQuery()) {
                        if (rs.next()) {
                            Map<String, Object> out = new LinkedHashMap<String, Object>();
                            out.put("replay", Boolean.TRUE);
                            out.put("status", rs.getString("status"));
                            out.put("resultPayload", rs.getString(1));
                            out.put("idempotencyKey", key);
                            out.put("correlationId", corr);
                            return out;
                        }
                    }
                }
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO iam.governance_idempotency "
                                + "(idempotency_key, operation, status, correlation_id, tenant_id, actor_user_id, created_by) "
                                + "VALUES (?, ?, 'IN_PROGRESS', ?, ?, ?, ?)")) {
                    ins.setString(1, key);
                    ins.setString(2, operation);
                    ins.setString(3, corr);
                    ins.setObject(4, ctx.getTenantId());
                    ins.setObject(5, ctx.getUserId());
                    ins.setObject(6, ctx.getUserId());
                    ins.executeUpdate();
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("replay", Boolean.FALSE);
                out.put("status", "IN_PROGRESS");
                out.put("idempotencyKey", key);
                out.put("correlationId", corr);
                return out;
            }
        });
    }

    public void completeIdempotent(final UserSecurityContext ctx, final String idempotencyKey,
                                   final String status, final String resultJson) {
        final String key = idempotencyKey.trim();
        ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException, InstituteAdminException {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE iam.governance_idempotency SET status = ?, result_payload = ?::jsonb, "
                                + "updated_at = now(), updated_by = ? "
                                + "WHERE idempotency_key = ? AND deleted_at IS NULL")) {
                    ps.setString(1, status);
                    ps.setString(2, resultJson == null ? "{}" : resultJson);
                    ps.setObject(3, ctx.getUserId());
                    ps.setString(4, key);
                    ps.executeUpdate();
                }
                return null;
            }
        });
    }

    public void requireDurableAuditOrFail(Connection conn, UserSecurityContext ctx, UUID tenantId,
                                          String action, String entityType, String entityId, String message)
            throws SQLException {
        DurableAuditWriter.append(conn, ctx, tenantId, action, entityType, entityId, message);
    }

    public String evaluateGrantCeiling(String grantorPersona, String targetRole, boolean selfGrant) {
        return GrantorCeiling.denyReason(grantorPersona, targetRole, selfGrant);
    }

    private void requirePermission(Connection conn, UserSecurityContext ctx, String key) throws SQLException {
        if (isPlatformOperator(conn, ctx)) {
            return;
        }
        try {
            ctx.checkPermission(conn, key);
        } catch (UserProfileAccessDeniedException ex) {
            throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                    "You are not authorized for this action (" + key + ").");
        }
    }

    private boolean isPlatformOperator(Connection conn, UserSecurityContext ctx) throws SQLException {
        try {
            ctx.checkPlatformAdmin(conn);
            return true;
        } catch (UserProfileAccessDeniedException ex) {
            return false;
        }
    }

    private static boolean isAssignableInsideInstitute(String code) {
        if (code == null) {
            return false;
        }
        String c = code.toUpperCase();
        if ("SUPER_ADMIN".equals(c) || "SYSTEM_ADMIN".equals(c) || "INSTITUTION_ADMIN".equals(c)) {
            return false;
        }
        if ("API_CLIENT".equals(c)) {
            return false;
        }
        return true;
    }
}
