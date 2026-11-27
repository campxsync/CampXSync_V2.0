package com.campx.iadm.access.service;

import com.campx.iadm.access.config.DatabaseConnectionManager;
import com.campx.iadm.access.exception.InstituteAdminException;
import com.campx.iadm.access.security.AccessPermissionMatrix;
import com.campx.iadm.access.security.UserSecurityContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * IADM-03-US-021 / US-001 / US-002 / US-012:
 * Computes the permission-key register, holders, and R01-R39 role matrix on request
 * from the live catalogue. Never stores snapshots or mutates the catalogue.
 */
public class CatalogueReadService {

    private static final String[][] RBAC_V3_POSTURE = {
            {"R01", "SUPER_ADMIN", "M (platform/cross-institute)", "PLATFORM"},
            {"R02", "SYSTEM_ADMIN", "M (platform technical; no human grant approval)", "PLATFORM"},
            {"R03", "INSTITUTION_ADMIN", "M within institute ceiling", "INSTITUTE"},
            {"R04", "COLLEGE_ADMIN", "request only (CADM ceiling)", "COLLEGE"},
            {"R05", "PRINCIPAL", "Domain admin (ACD/LMS)", "COLLEGE"},
            {"R06", "VICE_PRINCIPAL", "Academic ops", "COLLEGE"},
            {"R07", "DEAN", "Domain admin (ACD)", "INSTITUTE"},
            {"R08", "HOD", "Department ops", "DEPARTMENT"},
            {"R09", "PROGRAM_COORDINATOR", "Program ops", "DEPARTMENT"},
            {"R10", "FACULTY", "Teaching & grading", "DEPARTMENT"},
            {"R11", "SECTION_COORDINATOR", "Section ops", "DEPARTMENT"},
            {"R12", "EXAM_CONTROLLER", "EXM owner", "INSTITUTE"},
            {"R13", "EXAM_CELL", "EXM ops", "COLLEGE"},
            {"R14", "VALUATOR", "Evaluation", "COLLEGE"},
            {"R15", "FINANCE_MANAGER", "FIN owner", "INSTITUTE"},
            {"R16", "ACCOUNTANT", "FIN ops", "COLLEGE"},
            {"R17", "HR_MANAGER", "HRM owner", "INSTITUTE"},
            {"R18", "HR_OFFICER", "HRM ops", "COLLEGE"},
            {"R19", "COMM_MANAGER", "CAM owner", "INSTITUTE"},
            {"R20", "PLACEMENT_OFFICER", "Placement ops", "COLLEGE"},
            {"R21", "LIBRARIAN", "Library ops", "COLLEGE"},
            {"R22", "HOSTEL_WARDEN", "Hostel ops", "COLLEGE"},
            {"R23", "TRANSPORT_MANAGER", "Transport ops", "COLLEGE"},
            {"R24", "FACILITY_MANAGER", "Facility ops", "COLLEGE"},
            {"R25", "STUDENT", "Self-service student", "SELF"},
            {"R26", "PARENT", "Self-service guardian", "SELF"},
            {"R27", "API_CLIENT", "Parked (no IADM-03 surface)", "NONE"},
            {"R28", "AUDITOR", "V (read-only)", "PLATFORM"},
            {"R29", "REPORT_USER", "Read reports", "COLLEGE"},
            {"R30", "GUEST", "Minimal guest", "NONE"},
            {"R31", "REGISTRAR", "Student master (STM)", "INSTITUTE"},
            {"R32", "APPLICANT", "Self-registered applicant", "SELF"},
            {"R33", "ADMISSIONS_OFFICER", "Admissions ops", "COLLEGE"},
            {"R34", "DEPARTMENT_STAFF", "Department staff", "DEPARTMENT"},
            {"R35", "ACADEMIC_RECORDS_OFFICER", "Academic records", "COLLEGE"},
            {"R36", "DOCUMENT_VERIFIER", "Document verification", "COLLEGE"},
            {"R37", "EXTERNAL_VERIFIER", "External verification", "NONE"},
            {"R38", "IAM_OPERATOR", "STM-07 R25/R26 only", "INSTITUTE"},
            {"R39", "FACULTY_ADVISOR", "Advisee scope", "DEPARTMENT"}
    };

    public static String deriveOwningService(String keyCode) {
        if (keyCode == null || keyCode.trim().isEmpty()) {
            return "Unknown";
        }
        String k = keyCode.trim().toLowerCase(Locale.ROOT);
        for (int i = 1; i <= 8; i++) {
            String prefix = "iadm0" + i + ".";
            if (k.startsWith(prefix)) {
                return "IADM-0" + i;
            }
        }
        if (k.startsWith("adm")) {
            return "Legacy";
        }
        return "Unknown";
    }

    public static String deriveKeyStatus(String keyCode, String owningService, int grantCount) {
        if ("Legacy".equals(owningService)) {
            return "legacy";
        }
        if (owningService.startsWith("IADM-")) {
            return grantCount > 0 ? "granted" : "ungranted-reserved";
        }
        if (grantCount > 0) {
            return "granted";
        }
        if (keyCode != null && (keyCode.startsWith("cfg.") || keyCode.startsWith("iam.")
                || keyCode.startsWith("stu") || keyCode.startsWith("audit")
                || keyCode.startsWith("core") || keyCode.startsWith("doc")
                || keyCode.startsWith("plat") || keyCode.startsWith("rpt")
                || keyCode.startsWith("wf") || keyCode.startsWith("acd")
                || keyCode.startsWith("cam") || keyCode.startsWith("exm")
                || keyCode.startsWith("fin") || keyCode.startsWith("hrm")
                || keyCode.startsWith("lms") || keyCode.startsWith("recruit")
                || "*".equals(keyCode))) {
            return "dormant";
        }
        return "unknown";
    }

    public Map<String, Object> getRegister(UserSecurityContext ctx,
                                           String serviceFilter,
                                           String statusFilter,
                                           String roleFilter,
                                           String queryText,
                                           String correlationId) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                String persona = resolveCallerPersona(conn, ctx);
                if (!AccessPermissionMatrix.allowsIadm03(persona, "iadm03.register.view")) {
                    DurableAuditWriter.appendWithRefs(conn, ctx, ctx.getTenantId(),
                            "IADM03_REGISTER_DENIED", scopeLabel(ctx), "PERMISSION_REGISTER", null,
                            null, "403", correlationId, "Register read denied for " + persona);
                    conn.commit();
                    throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                            "Role " + persona + " is not permitted to view the permission-key register.");
                }

                int liveTotal = countLivePermissions(conn);
                Map<String, Set<String>> templateRolesByKey = loadTemplateRolesByKey(conn);
                Map<String, Integer> liveRoleCountByKey = loadLiveRoleCountByKey(conn);

                List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
                Map<String, Integer> byService = new LinkedHashMap<String, Integer>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT code, module, resource, action, description " +
                                "FROM iam.permissions WHERE deleted_at IS NULL ORDER BY code");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String code = rs.getString("code");
                        String owning = deriveOwningService(code);
                        Set<String> roles = templateRolesByKey.getOrDefault(code, Collections.<String>emptySet());
                        int liveCount = liveRoleCountByKey.getOrDefault(code, 0);
                        int effectiveGrantCount = Math.max(roles.size(), liveCount);
                        String status = deriveKeyStatus(code, owning, effectiveGrantCount);
                        byService.put(owning, byService.getOrDefault(owning, 0) + 1);

                        if (serviceFilter != null && !serviceFilter.trim().isEmpty()
                                && !owning.equalsIgnoreCase(serviceFilter.trim())) {
                            continue;
                        }
                        if (statusFilter != null && !statusFilter.trim().isEmpty()
                                && !status.equalsIgnoreCase(statusFilter.trim())) {
                            continue;
                        }
                        if (roleFilter != null && !roleFilter.trim().isEmpty()
                                && !roles.contains(roleFilter.trim().toUpperCase(Locale.ROOT))) {
                            continue;
                        }
                        if (queryText != null && !queryText.trim().isEmpty()
                                && !code.toLowerCase(Locale.ROOT).contains(queryText.trim().toLowerCase(Locale.ROOT))) {
                            continue;
                        }

                        Map<String, Object> item = new LinkedHashMap<String, Object>();
                        item.put("key", code);
                        item.put("owningService", owning);
                        item.put("module", rs.getString("module"));
                        item.put("resource", rs.getString("resource"));
                        item.put("action", rs.getString("action"));
                        item.put("description", rs.getString("description"));
                        item.put("status", status);
                        item.put("templateHolders", new ArrayList<String>(roles));
                        item.put("templateHolderCount", roles.size());
                        item.put("liveTenantRoleCount", liveCount);
                        rows.add(item);
                    }
                }

                UUID auditId = DurableAuditWriter.appendWithRefs(conn, ctx, ctx.getTenantId(),
                        "IADM03_REGISTER_READ", scopeLabel(ctx), "PERMISSION_REGISTER", null,
                        "liveTotal=" + liveTotal, "returned=" + rows.size(), correlationId,
                        "Computed permission-key register read");
                conn.commit();

                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("lastVerifiedAt", Instant.now().toString());
                out.put("liveCatalogueTotal", liveTotal);
                out.put("reconciled", Boolean.TRUE);
                out.put("filteredCount", rows.size());
                out.put("countsByOwningService", byService);
                out.put("readOnly", Boolean.TRUE);
                out.put("auditEventId", auditId.toString());
                out.put("items", rows);
                return out;
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new InstituteAdminException(500, "IADM03_DB_ERROR", "Unable to read permission register.");
        }
    }

    public Map<String, Object> getKeyHolders(UserSecurityContext ctx,
                                             String keyCode,
                                             UUID requestedTenantId,
                                             String correlationId) {
        if (keyCode == null || keyCode.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "Permission key is required.");
        }
        String key = keyCode.trim();
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                String persona = resolveCallerPersona(conn, ctx);
                if (!AccessPermissionMatrix.allowsIadm03(persona, "iadm03.holders.view")) {
                    DurableAuditWriter.appendWithRefs(conn, ctx, ctx.getTenantId(),
                            "IADM03_HOLDERS_DENIED", scopeLabel(ctx), "PERMISSION_HOLDERS", null,
                            key, "403", correlationId, "Holder read denied for " + persona);
                    conn.commit();
                    throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                            "Role " + persona + " is not permitted to view permission holders.");
                }

                UUID scopeTenant = null;
                if ("R03".equals(persona)) {
                    if (ctx.getTenantId() == null) {
                        throw new InstituteAdminException(403, "IADM03_SCOPE_REQUIRED",
                                "Institution Admin requires an authenticated institute scope.");
                    }
                    if (requestedTenantId != null && !requestedTenantId.equals(ctx.getTenantId())) {
                        DurableAuditWriter.appendWithRefs(conn, ctx, ctx.getTenantId(),
                                "IADM03_CROSS_TENANT_DENIED", scopeLabel(ctx), "PERMISSION_HOLDERS", null,
                                requestedTenantId.toString(), "403", correlationId, "R03 cross-tenant holder read denied");
                        conn.commit();
                        throw new InstituteAdminException(403, "IADM03_CROSS_TENANT_FORBIDDEN",
                                "Institution Admin cannot inspect holders of another institute.");
                    }
                    scopeTenant = ctx.getTenantId();
                } else if (requestedTenantId != null) {
                    scopeTenant = requestedTenantId;
                }

                StringBuilder sql = new StringBuilder(
                        "SELECT ra.user_id, r.code AS role_code, ra.tenant_id, t.code AS tenant_code, " +
                                "ra.college_id, ra.valid_from, ra.valid_to " +
                                "FROM iam.role_assignments ra " +
                                "JOIN iam.roles r ON r.id = ra.role_id AND r.deleted_at IS NULL " +
                                "JOIN iam.role_permissions rp ON rp.role_id = r.id AND rp.tenant_id = ra.tenant_id " +
                                "JOIN iam.permissions p ON p.id = rp.permission_id AND p.deleted_at IS NULL " +
                                "LEFT JOIN core.tenants t ON t.id = ra.tenant_id " +
                                "WHERE ra.deleted_at IS NULL AND p.code = ? ");
                if (scopeTenant != null) {
                    sql.append("AND ra.tenant_id = ? ");
                }
                sql.append("ORDER BY t.code, r.code, ra.user_id LIMIT 200");

                List<Map<String, Object>> holders = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    ps.setString(1, key);
                    if (scopeTenant != null) {
                        ps.setObject(2, scopeTenant);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> h = new LinkedHashMap<String, Object>();
                            h.put("accountRef", String.valueOf(rs.getObject("user_id")));
                            h.put("roleCode", rs.getString("role_code"));
                            h.put("tenantId", String.valueOf(rs.getObject("tenant_id")));
                            h.put("tenantCode", rs.getString("tenant_code"));
                            h.put("collegeId", rs.getObject("college_id") == null ? null : String.valueOf(rs.getObject("college_id")));
                            h.put("validFrom", rs.getObject("valid_from") == null ? null : String.valueOf(rs.getObject("valid_from")));
                            h.put("validTo", rs.getObject("valid_to") == null ? null : String.valueOf(rs.getObject("valid_to")));
                            holders.add(h);
                        }
                    }
                }

                UUID auditId = DurableAuditWriter.appendWithRefs(conn, ctx, ctx.getTenantId(),
                        "IADM03_HOLDERS_READ", scopeLabel(ctx), "PERMISSION_HOLDERS", null,
                        key, "holders=" + holders.size(), correlationId,
                        "Holder-level read for key " + key);
                conn.commit();

                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("key", key);
                out.put("owningService", deriveOwningService(key));
                out.put("lastVerifiedAt", Instant.now().toString());
                out.put("holderCount", holders.size());
                out.put("holders", holders);
                out.put("auditEventId", auditId.toString());
                return out;
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new InstituteAdminException(500, "IADM03_DB_ERROR", "Unable to read key holders.");
        }
    }

    public Map<String, Object> getRoleMatrix(UserSecurityContext ctx, String correlationId) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                String persona = resolveCallerPersona(conn, ctx);
                if (!AccessPermissionMatrix.allowsIadm03(persona, "iadm03.matrix.view")) {
                    DurableAuditWriter.appendWithRefs(conn, ctx, ctx.getTenantId(),
                            "IADM03_MATRIX_DENIED", scopeLabel(ctx), "ROLE_MATRIX", null,
                            null, "403", correlationId, "Role matrix read denied for " + persona);
                    conn.commit();
                    throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                            "Role " + persona + " is not permitted to view the role-to-permission matrix.");
                }

                Map<String, List<String>> keysByRole = new LinkedHashMap<String, List<String>>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT role_code, permission_code FROM iam.role_template_permissions ORDER BY role_code, permission_code");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String rc = rs.getString("role_code");
                        String pc = rs.getString("permission_code");
                        List<String> list = keysByRole.get(rc);
                        if (list == null) {
                            list = new ArrayList<String>();
                            keysByRole.put(rc, list);
                        }
                        list.add(pc);
                    }
                }

                Set<String> presentTemplates = new LinkedHashSet<String>();
                try (PreparedStatement ps = conn.prepareStatement("SELECT code FROM iam.role_templates");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        presentTemplates.add(rs.getString("code"));
                    }
                }

                List<Map<String, Object>> roles = new ArrayList<Map<String, Object>>();
                List<String> differences = new ArrayList<String>();
                for (String[] row : RBAC_V3_POSTURE) {
                    String rCode = row[0];
                    String templateCode = row[1];
                    String posture = row[2];
                    String scope = row[3];
                    List<String> effectiveKeys = keysByRole.getOrDefault(templateCode, Collections.<String>emptyList());
                    boolean templateExists = presentTemplates.contains(templateCode);
                    String diffNote = null;
                    if ("R01".equals(rCode) && !templateExists) {
                        diffNote = "R01 Super Admin has no role_templates row; authority lives in plat.platform_admins.";
                    } else if ("R27".equals(rCode) && !templateExists) {
                        diffNote = "R27 API Client is absent from iam.role_templates (parked).";
                    } else if (templateExists && effectiveKeys.isEmpty()) {
                        diffNote = rCode + " (" + templateCode + ") template exists with 0 granted keys.";
                    }
                    if (diffNote != null) {
                        differences.add(diffNote);
                    }

                    Map<String, Object> item = new LinkedHashMap<String, Object>();
                    item.put("rCode", rCode);
                    item.put("roleCode", templateCode);
                    item.put("rbacV3Posture", posture);
                    item.put("scope", scope);
                    item.put("templateExists", templateExists);
                    item.put("effectiveKeyCount", effectiveKeys.size());
                    item.put("effectiveKeys", effectiveKeys);
                    item.put("differenceNote", diffNote);
                    roles.add(item);
                }

                UUID auditId = DurableAuditWriter.appendWithRefs(conn, ctx, ctx.getTenantId(),
                        "IADM03_MATRIX_READ", scopeLabel(ctx), "ROLE_MATRIX", null,
                        "RBAC_V3.0", "roles=" + roles.size(), correlationId,
                        "Computed R01-R39 role matrix read");
                conn.commit();

                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("sourceVersion", "RBAC v3.0");
                out.put("noImplicitInheritance", Boolean.TRUE);
                out.put("readOnly", Boolean.TRUE);
                out.put("lastVerifiedAt", Instant.now().toString());
                out.put("roleCount", roles.size());
                out.put("differences", differences);
                out.put("roles", roles);
                out.put("auditEventId", auditId.toString());
                return out;
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new InstituteAdminException(500, "IADM03_DB_ERROR", "Unable to compute role matrix.");
        }
    }

    public static String resolveCallerPersona(Connection conn, UserSecurityContext ctx) throws SQLException {
        if (ctx == null || ctx.getUserId() == null) {
            return "UNKNOWN";
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT role_code FROM plat.platform_admins WHERE user_id = ? AND deleted_at IS NULL LIMIT 1")) {
            ps.setObject(1, ctx.getUserId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return AccessPermissionMatrix.normalizePersona(rs.getString("role_code"));
                }
            }
        }
        if (ctx.getTenantId() != null) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT r.code FROM iam.role_assignments ra " +
                            "JOIN iam.roles r ON r.id = ra.role_id AND r.deleted_at IS NULL " +
                            "WHERE ra.user_id = ? AND ra.tenant_id = ? AND ra.deleted_at IS NULL " +
                            "ORDER BY ra.created_at DESC LIMIT 1")) {
                ps.setObject(1, ctx.getUserId());
                ps.setObject(2, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return AccessPermissionMatrix.normalizePersona(rs.getString("code"));
                    }
                }
            }
        } else {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT r.code FROM iam.role_assignments ra " +
                            "JOIN iam.roles r ON r.id = ra.role_id AND r.deleted_at IS NULL " +
                            "WHERE ra.user_id = ? AND ra.deleted_at IS NULL " +
                            "ORDER BY ra.created_at DESC LIMIT 1")) {
                ps.setObject(1, ctx.getUserId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return AccessPermissionMatrix.normalizePersona(rs.getString("code"));
                    }
                }
            }
        }
        return "UNKNOWN";
    }

    private int countLivePermissions(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT count(*)::int FROM iam.permissions WHERE deleted_at IS NULL");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private Map<String, Set<String>> loadTemplateRolesByKey(Connection conn) throws SQLException {
        Map<String, Set<String>> map = new LinkedHashMap<String, Set<String>>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT permission_code, role_code FROM iam.role_template_permissions ORDER BY permission_code, role_code");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String p = rs.getString("permission_code");
                String r = rs.getString("role_code");
                Set<String> s = map.get(p);
                if (s == null) {
                    s = new LinkedHashSet<String>();
                    map.put(p, s);
                }
                s.add(r);
            }
        }
        return map;
    }

    private Map<String, Integer> loadLiveRoleCountByKey(Connection conn) throws SQLException {
        Map<String, Integer> map = new LinkedHashMap<String, Integer>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT p.code, count(DISTINCT rp.role_id)::int AS cnt " +
                        "FROM iam.role_permissions rp " +
                        "JOIN iam.permissions p ON p.id = rp.permission_id AND p.deleted_at IS NULL " +
                        "GROUP BY p.code");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("code"), rs.getInt("cnt"));
            }
        }
        return map;
    }

    private static String scopeLabel(UserSecurityContext ctx) {
        return ctx != null && ctx.getTenantId() != null ? "INSTITUTE:" + ctx.getTenantId() : "PLATFORM";
    }
}
