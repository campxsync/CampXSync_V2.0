package com.campx.iadm.access.service;

import com.campx.iadm.access.config.DatabaseConnectionManager;
import com.campx.iadm.access.exception.InstituteAdminException;
import com.campx.iadm.access.exception.UserProfileAccessDeniedException;
import com.campx.iadm.access.security.AccessPermissionMatrix;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-03 US-030 — R01 proposes role↔permission mapping change; different R01 approves/applies.
 * Never creates roles. R02/R03 cannot propose.
 */
public class CatalogChangeGovernanceService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(CatalogChangeGovernanceService.class);

    private final DatabaseConnectionManager db;

    public CatalogChangeGovernanceService() {
        this(DatabaseConnectionManager.getInstance());
    }

    public CatalogChangeGovernanceService(DatabaseConnectionManager db) {
        this.db = db == null ? DatabaseConnectionManager.getInstance() : db;
    }

    public Map<String, Object> list(final UserSecurityContext ctx, final String statusFilter) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireR01(conn, ctx, "iam.catalog.change.propose");
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                StringBuilder sql = new StringBuilder(
                        "SELECT id, change_type, role_code, permission_code, justification, status, "
                                + "impact_preview::text, proposed_by, proposed_at, decided_by, decided_at, "
                                + "decision_reason, applied_at "
                                + "FROM iam.catalog_change_requests WHERE deleted_at IS NULL ");
                String st = statusFilter == null ? "ALL" : statusFilter.trim().toUpperCase(Locale.ROOT);
                if (!"ALL".equals(st) && !st.isEmpty()) {
                    sql.append("AND status = ? ");
                }
                sql.append("ORDER BY proposed_at DESC LIMIT 100");
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    if (!"ALL".equals(st) && !st.isEmpty()) {
                        ps.setString(1, st);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            items.add(mapRow(rs));
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                out.put("count", Integer.valueOf(items.size()));
                return out;
            }
        });
    }

    public Map<String, Object> propose(final UserSecurityContext ctx,
                                       final String changeType,
                                       final String roleCode,
                                       final String permissionCode,
                                       final String justification,
                                       final String idempotencyKey,
                                       final String correlationId) {
        if (changeType == null || changeType.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "changeType is required");
        }
        if (roleCode == null || roleCode.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "roleCode is required");
        }
        if (permissionCode == null || permissionCode.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "permissionCode is required");
        }
        if (justification == null || justification.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "A business justification is required.");
        }
        final String type = changeType.trim().toUpperCase(Locale.ROOT);
        if (!"ADD_MAPPING".equals(type) && !"REMOVE_MAPPING".equals(type)) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION",
                    "Only ADD_MAPPING or REMOVE_MAPPING is allowed. Role catalogue R01–R39 cannot gain roles here.");
        }
        final String role = roleCode.trim().toUpperCase(Locale.ROOT);
        final String perm = permissionCode.trim();
        final String key = idempotencyKey == null || idempotencyKey.trim().isEmpty()
                ? UUID.randomUUID().toString() : idempotencyKey.trim();
        final String corr = correlationId == null || correlationId.trim().isEmpty()
                ? UUID.randomUUID().toString() : correlationId.trim();

        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireR01(conn, ctx, "iam.catalog.change.propose");

                Map<String, Object> replay = findIdempotent(conn, key);
                if (replay != null) {
                    return replay;
                }

                assertRoleTemplateExists(conn, role);
                assertPermissionExists(conn, perm);
                Map<String, Object> impact = buildImpactPreview(conn, type, role, perm);

                UUID id = UUID.randomUUID();
                String impactJson = toImpactJson(impact);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO iam.catalog_change_requests "
                                + "(id, change_type, role_code, permission_code, justification, status, "
                                + "impact_preview, proposed_by, idempotency_key, correlation_id, created_by, updated_by) "
                                + "VALUES (?, ?, ?, ?, ?, 'PROPOSED', ?::jsonb, ?, ?, ?, ?, ?)")) {
                    ps.setObject(1, id);
                    ps.setString(2, type);
                    ps.setString(3, role);
                    ps.setString(4, perm);
                    ps.setString(5, justification.trim());
                    ps.setString(6, impactJson);
                    ps.setObject(7, ctx.getUserId());
                    ps.setString(8, key);
                    ps.setString(9, corr);
                    ps.setObject(10, ctx.getUserId());
                    ps.setObject(11, ctx.getUserId());
                    ps.executeUpdate();
                } catch (SQLException ex) {
                    if ("23505".equals(ex.getSQLState())) {
                        Map<String, Object> existing = findIdempotent(conn, key);
                        if (existing != null) {
                            return existing;
                        }
                    }
                    throw ex;
                }

                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM03_CATALOG_CHANGE_PROPOSED", "CATALOG_CHANGE", id.toString(),
                        type + " " + role + "↔" + perm + "; " + justification.trim());

                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", id.toString());
                out.put("changeType", type);
                out.put("roleCode", role);
                out.put("permissionCode", perm);
                out.put("status", "PROPOSED");
                out.put("impactPreview", impact);
                out.put("proposedBy", ctx.getUserId().toString());
                out.put("idempotencyKey", key);
                out.put("correlationId", corr);
                out.put("replay", Boolean.FALSE);
                out.put("message", "Proposal stored. A different R01 must approve before apply.");
                saveIdempotent(conn, key, "CATALOG_CHANGE_PROPOSE", out, corr, ctx.getUserId());
                logger.info("IADM-03 catalog change proposed id={} {} {}", id, type, role);
                return out;
            }
        });
    }

    public Map<String, Object> decide(final UserSecurityContext ctx,
                                      final UUID requestId,
                                      final String decision,
                                      final String reason) {
        if (requestId == null) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "requestId is required");
        }
        if (decision == null || decision.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "decision is required");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "A decision reason is required.");
        }
        final String dec = decision.trim().toUpperCase(Locale.ROOT);
        if (!"APPROVE".equals(dec) && !"REJECT".equals(dec) && !"APPROVED".equals(dec) && !"REJECTED".equals(dec)) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "decision must be APPROVE or REJECT");
        }
        final boolean approve = "APPROVE".equals(dec) || "APPROVED".equals(dec);

        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireR01(conn, ctx, "iam.catalog.change.approve");

                String changeType = null;
                String role = null;
                String perm = null;
                UUID proposedBy = null;
                String status = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT change_type, role_code, permission_code, proposed_by, status "
                                + "FROM iam.catalog_change_requests WHERE id = ? AND deleted_at IS NULL FOR UPDATE")) {
                    ps.setObject(1, requestId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new InstituteAdminException(404, "IADM03_NOT_FOUND", "Catalogue change request not found.");
                        }
                        changeType = rs.getString("change_type");
                        role = rs.getString("role_code");
                        perm = rs.getString("permission_code");
                        proposedBy = (UUID) rs.getObject("proposed_by");
                        status = rs.getString("status");
                    }
                }

                if (!"PROPOSED".equals(status)) {
                    throw new InstituteAdminException(409, "IADM03_STATE",
                            "Only PROPOSED catalogue changes can be decided (current=" + status + ").");
                }
                if (proposedBy != null && proposedBy.equals(ctx.getUserId())) {
                    throw new InstituteAdminException(403, "IADM03_SELF_APPROVE",
                            "A different Super Admin (R01) must approve. The proposer cannot decide their own change.");
                }

                if (!approve) {
                    try (PreparedStatement upd = conn.prepareStatement(
                            "UPDATE iam.catalog_change_requests SET status = 'REJECTED', decided_by = ?, "
                                    + "decided_at = now(), decision_reason = ?, updated_at = now(), updated_by = ?, "
                                    + "row_version = row_version + 1 WHERE id = ?")) {
                        upd.setObject(1, ctx.getUserId());
                        upd.setString(2, reason.trim());
                        upd.setObject(3, ctx.getUserId());
                        upd.setObject(4, requestId);
                        upd.executeUpdate();
                    }
                    DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                            "IADM03_CATALOG_CHANGE_REJECTED", "CATALOG_CHANGE", requestId.toString(),
                            reason.trim());
                    Map<String, Object> out = new LinkedHashMap<String, Object>();
                    out.put("id", requestId.toString());
                    out.put("decision", "REJECTED");
                    out.put("status", "REJECTED");
                    out.put("applied", Boolean.FALSE);
                    return out;
                }

                // Revalidate just before apply
                assertRoleTemplateExists(conn, role);
                assertPermissionExists(conn, perm);
                UUID rtpId = applyMapping(conn, ctx, changeType, role, perm);

                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE iam.catalog_change_requests SET status = 'APPLIED', decided_by = ?, "
                                + "decided_at = now(), decision_reason = ?, applied_at = now(), "
                                + "resulting_template_permission_id = ?, updated_at = now(), updated_by = ?, "
                                + "row_version = row_version + 1 WHERE id = ?")) {
                    upd.setObject(1, ctx.getUserId());
                    upd.setString(2, reason.trim());
                    upd.setObject(3, rtpId);
                    upd.setObject(4, ctx.getUserId());
                    upd.setObject(5, requestId);
                    upd.executeUpdate();
                }

                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM03_CATALOG_CHANGE_APPLIED", "CATALOG_CHANGE", requestId.toString(),
                        changeType + " " + role + "↔" + perm + "; " + reason.trim());

                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", requestId.toString());
                out.put("decision", "APPROVED");
                out.put("status", "APPLIED");
                out.put("applied", Boolean.TRUE);
                out.put("resultingTemplatePermissionId", rtpId == null ? null : rtpId.toString());
                out.put("changeType", changeType);
                out.put("roleCode", role);
                out.put("permissionCode", perm);
                return out;
            }
        });
    }

    private UUID applyMapping(Connection conn, UserSecurityContext ctx, String changeType, String role, String perm)
            throws SQLException {
        if ("ADD_MAPPING".equals(changeType)) {
            UUID existing = findActiveTemplatePermissionId(conn, role, perm);
            if (existing != null) {
                propagateToTenants(conn, role, perm);
                return existing;
            }
            UUID id = UUID.randomUUID();
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO iam.role_template_permissions (id, role_code, permission_code, created_by, updated_by) "
                            + "VALUES (?, ?, ?, ?, ?)")) {
                ins.setObject(1, id);
                ins.setString(2, role);
                ins.setString(3, perm);
                ins.setObject(4, ctx.getUserId());
                ins.setObject(5, ctx.getUserId());
                ins.executeUpdate();
            } catch (SQLException ex) {
                // Some schemas may omit id default differently — fall back without explicit id
                if (ex.getMessage() != null && ex.getMessage().toLowerCase(Locale.ROOT).contains("column")) {
                    try (PreparedStatement ins2 = conn.prepareStatement(
                            "INSERT INTO iam.role_template_permissions (role_code, permission_code, created_by, updated_by) "
                                    + "VALUES (?, ?, ?, ?) RETURNING id")) {
                        ins2.setString(1, role);
                        ins2.setString(2, perm);
                        ins2.setObject(3, ctx.getUserId());
                        ins2.setObject(4, ctx.getUserId());
                        try (ResultSet rs = ins2.executeQuery()) {
                            if (rs.next()) {
                                id = (UUID) rs.getObject(1);
                            }
                        }
                    }
                } else {
                    throw ex;
                }
            }
            propagateToTenants(conn, role, perm);
            return id;
        }
        // REMOVE_MAPPING
        UUID rtpId = findActiveTemplatePermissionId(conn, role, perm);
        if (rtpId == null) {
            throw new InstituteAdminException(409, "IADM03_STATE",
                    "No active mapping " + role + "↔" + perm + " to remove.");
        }
        try (PreparedStatement del = conn.prepareStatement(
                "UPDATE iam.role_template_permissions SET deleted_at = now(), updated_at = now(), updated_by = ?, "
                        + "row_version = row_version + 1 WHERE id = ? AND deleted_at IS NULL")) {
            del.setObject(1, ctx.getUserId());
            del.setObject(2, rtpId);
            del.executeUpdate();
        }
        // iam.role_permissions has no deleted_at — hard delete matching rows
        try (PreparedStatement hard = conn.prepareStatement(
                "DELETE FROM iam.role_permissions rp USING iam.roles r, iam.permissions p "
                        + "WHERE rp.role_id = r.id AND rp.permission_id = p.id "
                        + "AND r.code = ? AND p.code = ? AND r.deleted_at IS NULL AND p.deleted_at IS NULL")) {
            hard.setString(1, role);
            hard.setString(2, perm);
            hard.executeUpdate();
        }
        return rtpId;
    }

    private void propagateToTenants(Connection conn, String role, String perm) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO iam.role_permissions (tenant_id, role_id, permission_id) "
                        + "SELECT r.tenant_id, r.id, p.id "
                        + "FROM iam.roles r "
                        + "JOIN iam.permissions p ON p.code = ? AND p.deleted_at IS NULL "
                        + "WHERE r.code = ? AND r.deleted_at IS NULL "
                        + "AND NOT EXISTS ("
                        + "  SELECT 1 FROM iam.role_permissions rp "
                        + "  WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.tenant_id = r.tenant_id"
                        + ")")) {
            ps.setString(1, perm);
            ps.setString(2, role);
            ps.executeUpdate();
        }
    }

    private UUID findActiveTemplatePermissionId(Connection conn, String role, String perm) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM iam.role_template_permissions "
                        + "WHERE role_code = ? AND permission_code = ? AND deleted_at IS NULL LIMIT 1")) {
            ps.setString(1, role);
            ps.setString(2, perm);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject(1);
                }
            }
        }
        return null;
    }

    private Map<String, Object> buildImpactPreview(Connection conn, String type, String role, String perm)
            throws SQLException {
        Map<String, Object> impact = new LinkedHashMap<String, Object>();
        impact.put("changeType", type);
        impact.put("roleCode", role);
        impact.put("permissionCode", perm);
        boolean currentlyMapped = findActiveTemplatePermissionId(conn, role, perm) != null;
        impact.put("currentlyMapped", Boolean.valueOf(currentlyMapped));
        int tenantRoleCount = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT count(*) FROM iam.roles WHERE code = ? AND deleted_at IS NULL")) {
            ps.setString(1, role);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    tenantRoleCount = rs.getInt(1);
                }
            }
        }
        impact.put("tenantRoleCopies", Integer.valueOf(tenantRoleCount));
        impact.put("createsNewRole", Boolean.FALSE);
        impact.put("roleCatalogueUnchanged", Boolean.TRUE);
        if ("ADD_MAPPING".equals(type) && currentlyMapped) {
            impact.put("noopNote", "Mapping already active; approve will re-propagate to tenant roles only.");
        }
        if ("REMOVE_MAPPING".equals(type) && !currentlyMapped) {
            impact.put("blocker", "No active mapping to remove.");
        }
        return impact;
    }

    private void assertRoleTemplateExists(Connection conn, String role) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM iam.role_templates WHERE code = ? AND deleted_at IS NULL")) {
            ps.setString(1, role);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new InstituteAdminException(400, "IADM03_VALIDATION",
                            "Unknown role code. This story cannot add roles to the R01–R39 catalogue.");
                }
            }
        }
    }

    private void assertPermissionExists(Connection conn, String perm) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM iam.permissions WHERE code = ? AND deleted_at IS NULL")) {
            ps.setString(1, perm);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new InstituteAdminException(400, "IADM03_VALIDATION",
                            "Unknown permission code: " + perm);
                }
            }
        }
    }

    /**
     * Platform operators bypass generic has_permission, so R01 vs R02 must be enforced here.
     * R03 never reaches platform path for catalogue change.
     */
    private void requireR01(Connection conn, UserSecurityContext ctx, String key) throws SQLException {
        String persona = resolvePlatformPersona(conn, ctx);
        if (!"R01".equals(persona)) {
            if ("R02".equals(persona) || "R03".equals(persona) || "R04".equals(persona)) {
                throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                        "Only Super Admin (R01) may propose or approve catalogue mapping changes (" + key + ").");
            }
            // Non-platform: check key then still refuse unless somehow R01
            try {
                ctx.checkPermission(conn, key);
            } catch (UserProfileAccessDeniedException ex) {
                throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                        "You are not authorized for this action (" + key + ").");
            }
            throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                    "Only Super Admin (R01) may propose or approve catalogue mapping changes.");
        }
        if (!AccessPermissionMatrix.allows("R01", key)) {
            throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                    "Permission key not granted for R01: " + key);
        }
    }

    private String resolvePlatformPersona(Connection conn, UserSecurityContext ctx) throws SQLException {
        try {
            ctx.checkPlatformAdmin(conn);
        } catch (UserProfileAccessDeniedException ex) {
            return AccessPermissionMatrix.normalizePersona("R03");
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT role_code FROM plat.platform_admins WHERE user_id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, ctx.getUserId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && "SYSTEM_ADMIN".equalsIgnoreCase(rs.getString(1))) {
                    return "R02";
                }
            }
        }
        return "R01";
    }

    private Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", rs.getObject("id").toString());
        row.put("changeType", rs.getString("change_type"));
        row.put("roleCode", rs.getString("role_code"));
        row.put("permissionCode", rs.getString("permission_code"));
        row.put("justification", rs.getString("justification"));
        row.put("status", rs.getString("status"));
        row.put("impactPreviewRaw", rs.getString("impact_preview"));
        Object pb = rs.getObject("proposed_by");
        row.put("proposedBy", pb == null ? null : pb.toString());
        row.put("proposedAt", rs.getTimestamp("proposed_at") == null ? null
                : rs.getTimestamp("proposed_at").toInstant().toString());
        Object db = rs.getObject("decided_by");
        row.put("decidedBy", db == null ? null : db.toString());
        row.put("decidedAt", rs.getTimestamp("decided_at") == null ? null
                : rs.getTimestamp("decided_at").toInstant().toString());
        row.put("decisionReason", rs.getString("decision_reason"));
        row.put("appliedAt", rs.getTimestamp("applied_at") == null ? null
                : rs.getTimestamp("applied_at").toInstant().toString());
        return row;
    }

    private String toImpactJson(Map<String, Object> impact) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : impact.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Boolean || v instanceof Number) {
                sb.append(v.toString());
            } else {
                sb.append('"').append(escape(String.valueOf(v))).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private Map<String, Object> findIdempotent(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT result_payload::text, status FROM iam.governance_idempotency "
                        + "WHERE idempotency_key = ? AND deleted_at IS NULL")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String payload = rs.getString(1);
                if (payload == null || payload.isEmpty()) {
                    return null;
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("replay", Boolean.TRUE);
                out.put("raw", payload);
                out.put("status", "REPLAY");
                // Prefer structured fields when present
                out.put("message", "Idempotent replay of catalogue change proposal.");
                return out;
            }
        }
    }

    private void saveIdempotent(Connection conn, String key, String op, Map<String, Object> result,
                                String corr, UUID actor) throws SQLException {
        String json = toImpactJson(result);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO iam.governance_idempotency "
                        + "(idempotency_key, operation, status, result_payload, correlation_id, actor_user_id, created_by, updated_by) "
                        + "VALUES (?, ?, 'COMPLETED', ?::jsonb, ?, ?, ?, ?) "
                        + "ON CONFLICT (idempotency_key) DO NOTHING")) {
            ps.setString(1, key);
            ps.setString(2, op);
            ps.setString(3, json);
            ps.setString(4, corr);
            ps.setObject(5, actor);
            ps.setObject(6, actor);
            ps.setObject(7, actor);
            ps.executeUpdate();
        }
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
