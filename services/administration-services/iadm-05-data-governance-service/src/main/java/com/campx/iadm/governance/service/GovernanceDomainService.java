package com.campx.iadm.governance.service;

import com.campx.iadm.governance.config.DatabaseConnectionManager;
import com.campx.iadm.governance.exception.InstituteAdminException;
import com.campx.iadm.governance.exception.UserProfileAccessDeniedException;
import com.campx.iadm.governance.security.UserSecurityContext;

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
 * Facade for IADM-05 reads used by desks (US-001, US-013, US-019, US-010, US-011, US-012).
 */
public class GovernanceDomainService {

    private final DatabaseConnectionManager db = DatabaseConnectionManager.getInstance();
    private final PolicyLifecycleService lifecycle = new PolicyLifecycleService();
    private final GovernanceRequestService requests = new GovernanceRequestService();

    public PolicyLifecycleService lifecycle() {
        return lifecycle;
    }

    public GovernanceRequestService requests() {
        return requests;
    }

    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("service", "iadm-05-data-governance-service");
        out.put("status", "UP");
        out.put("port", Integer.valueOf(8103));
        out.put("prefix", "/api/v1/iadm/05");
        out.put("boundary", "Policy governance only — domain services execute retention/export/erasure");
        return out;
    }

    public Map<String, Object> listClassifications(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, "iadm05.classification.view", "iadm05.adherence.view",
                        "iadm05.college.view", "iadm05.audit.view");
                String sql = "SELECT c.id, c.class_code, c.class_name, c.description, c.handling_guidance, "
                        + "c.permitted_use_notes, c.demo_label, p.status, p.version_no, p.owner_label, "
                        + "p.effective_from, p.id AS policy_id "
                        + "FROM gov.classification_defs c "
                        + "JOIN gov.policies p ON p.id = c.policy_id "
                        + "WHERE 1=1";
                if (!isPlatform(conn) && ctx.getTenantId() != null) {
                    sql += " AND p.status = 'PUBLISHED' AND (p.scope_tenant_id IS NULL OR p.scope_tenant_id = ?)";
                }
                sql += " ORDER BY c.class_code, p.version_no DESC";
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (!isPlatform(conn) && ctx.getTenantId() != null) {
                        ps.setObject(1, ctx.getTenantId());
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> m = new LinkedHashMap<String, Object>();
                            m.put("id", rs.getObject("id").toString());
                            m.put("code", rs.getString("class_code"));
                            m.put("name", rs.getString("class_name"));
                            m.put("description", rs.getString("description"));
                            m.put("handlingGuidance", rs.getString("handling_guidance"));
                            m.put("permittedUseNotes", rs.getString("permitted_use_notes"));
                            m.put("demoLabel", rs.getString("demo_label"));
                            m.put("status", rs.getString("status"));
                            m.put("versionNo", Integer.valueOf(rs.getInt("version_no")));
                            m.put("ownerLabel", rs.getString("owner_label"));
                            m.put("policyId", rs.getObject("policy_id").toString());
                            items.add(m);
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                out.put("count", Integer.valueOf(items.size()));
                out.put("notice", "DEMO ONLY / NOT LEGAL CLASSIFICATIONS (G-06 open)");
                return out;
            }
        });
    }

    public Map<String, Object> listRetention(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, "iadm05.retention.view", "iadm05.adherence.view",
                        "iadm05.college.view", "iadm05.audit.view");
                String sql = "SELECT r.*, p.status, p.version_no, p.title, p.code AS policy_code "
                        + "FROM gov.retention_rules r JOIN gov.policies p ON p.id = r.policy_id WHERE 1=1";
                if (!isPlatform(conn) && ctx.getTenantId() != null) {
                    sql += " AND p.status IN ('PUBLISHED','SUPERSEDED') "
                            + "AND (p.scope_tenant_id IS NULL OR p.scope_tenant_id = ?)";
                }
                sql += " ORDER BY r.domain_code, p.version_no DESC";
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (!isPlatform(conn) && ctx.getTenantId() != null) {
                        ps.setObject(1, ctx.getTenantId());
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> m = new LinkedHashMap<String, Object>();
                            m.put("id", rs.getObject("id").toString());
                            m.put("policyId", rs.getObject("policy_id").toString());
                            m.put("policyCode", rs.getString("policy_code"));
                            m.put("title", rs.getString("title"));
                            m.put("domainCode", rs.getString("domain_code"));
                            m.put("recordCategory", rs.getString("record_category"));
                            m.put("durationNote", rs.getString("duration_note"));
                            m.put("ruleReference", rs.getString("rule_reference"));
                            m.put("holdHandling", rs.getString("hold_handling"));
                            m.put("status", rs.getString("status"));
                            m.put("versionNo", Integer.valueOf(rs.getInt("version_no")));
                            m.put("demoLabel", rs.getString("demo_label"));
                            items.add(m);
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

    public Map<String, Object> adherence(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, "iadm05.adherence.view");
                if (ctx.getTenantId() == null && !isPlatform(conn)) {
                    throw new InstituteAdminException(400, "IADM05_TENANT_REQUIRED",
                            "Missing tenant context (fail closed).");
                }
                UUID tenant = ctx.getTenantId();
                List<Map<String, Object>> policies = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, policy_type, code, title, status, version_no, demo_label "
                                + "FROM gov.policies WHERE status = 'PUBLISHED' "
                                + "AND (scope_tenant_id IS NULL OR scope_tenant_id = ?) "
                                + "ORDER BY policy_type, code")) {
                    ps.setObject(1, tenant);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> m = new LinkedHashMap<String, Object>();
                            m.put("policyId", rs.getObject("id").toString());
                            m.put("policyType", rs.getString("policy_type"));
                            m.put("code", rs.getString("code"));
                            m.put("title", rs.getString("title"));
                            m.put("status", rs.getString("status"));
                            m.put("versionNo", Integer.valueOf(rs.getInt("version_no")));
                            m.put("demoLabel", rs.getString("demo_label"));
                            policies.add(m);
                        }
                    }
                }
                List<Map<String, Object>> ownRequests = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, request_type, status, correlation_id, demo_label, created_at "
                                + "FROM gov.governance_requests WHERE scope_tenant_id = ? "
                                + "ORDER BY created_at DESC")) {
                    ps.setObject(1, tenant);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> m = new LinkedHashMap<String, Object>();
                            m.put("requestId", rs.getObject("id").toString());
                            m.put("requestType", rs.getString("request_type"));
                            m.put("status", rs.getString("status"));
                            m.put("correlationId", rs.getObject("correlation_id").toString());
                            m.put("demoLabel", rs.getString("demo_label"));
                            ownRequests.add(m);
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("mode", "VIEW_ONLY");
                out.put("scopeTenantId", tenant == null ? null : tenant.toString());
                out.put("applicablePolicies", policies);
                out.put("ownRequests", ownRequests);
                out.put("writeControls", Boolean.FALSE);
                out.put("note", "R03 adherence is view-only (G-16); no write accepted.");
                return out;
            }
        });
    }

    public Map<String, Object> collegeView(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, "iadm05.college.view");
                List<Map<String, Object>> policies = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, policy_type, code, title, version_no, demo_label "
                                + "FROM gov.policies WHERE status = 'PUBLISHED' "
                                + "AND (scope_tenant_id IS NULL OR scope_tenant_id = ?) "
                                + "ORDER BY policy_type")) {
                    ps.setObject(1, ctx.getTenantId());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> m = new LinkedHashMap<String, Object>();
                            m.put("policyId", rs.getObject("id").toString());
                            m.put("policyType", rs.getString("policy_type"));
                            m.put("code", rs.getString("code"));
                            m.put("title", rs.getString("title"));
                            m.put("versionNo", Integer.valueOf(rs.getInt("version_no")));
                            m.put("demoLabel", rs.getString("demo_label"));
                            policies.add(m);
                        }
                    }
                }
                List<Map<String, Object>> entryPoints = new ArrayList<Map<String, Object>>();
                entryPoints.add(entry("STM documents", "/student/documents",
                        "Informational only — domain workflow owns execution (G-02)"));
                entryPoints.add(entry("Lifecycle petitions", "/student/lifecycle",
                        "Informational only — IADM-05 does not trigger domain execution"));
                entryPoints.add(entry("College documents desk", "/college/documents",
                        "Informational only — no override of IADM policy"));
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("applicablePolicies", policies);
                out.put("domainWorkflowEntryPoints", entryPoints);
                out.put("canEditPolicy", Boolean.FALSE);
                out.put("canTriggerDomain", Boolean.FALSE);
                out.put("note", "R04 view + informational entry points only (G-02 open).");
                return out;
            }
        });
    }

    public Map<String, Object> setOutageFlags(UserSecurityContext ctx, Boolean auditDisabled,
                                             Boolean simulatorDisabled) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                if (!isPlatform(conn)) {
                    throw new UserProfileAccessDeniedException("Platform admin required for outage flags.");
                }
                if (auditDisabled != null && auditDisabled.booleanValue()) {
                    DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                            "IADM05_OUTAGE_FLAGS", "dev", "outage",
                            "Disabling audit for fail-closed demo (US-025/US-031).");
                }
                GovernanceOutageFlags.set(auditDisabled, simulatorDisabled);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE gov.outage_flags SET audit_disabled = ?, simulator_disabled = ?, "
                                + "updated_at = now() WHERE id = 1")) {
                    ps.setBoolean(1, GovernanceOutageFlags.isAuditDisabled());
                    ps.setBoolean(2, GovernanceOutageFlags.isSimulatorDisabled());
                    ps.executeUpdate();
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("auditDisabled", Boolean.valueOf(GovernanceOutageFlags.isAuditDisabled()));
                out.put("simulatorDisabled", Boolean.valueOf(GovernanceOutageFlags.isSimulatorDisabled()));
                return out;
            }
        });
    }

    public Map<String, Object> getOutageFlags(UserSecurityContext ctx) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("auditDisabled", Boolean.valueOf(GovernanceOutageFlags.isAuditDisabled()));
        out.put("simulatorDisabled", Boolean.valueOf(GovernanceOutageFlags.isSimulatorDisabled()));
        return out;
    }

    private Map<String, Object> entry(String label, String path, String note) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("label", label);
        m.put("path", path);
        m.put("note", note);
        m.put("triggerable", Boolean.FALSE);
        return m;
    }

    private boolean isPlatform(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT iam.is_platform_admin()");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getBoolean(1);
        }
    }

    private void requireAny(Connection conn, String... keys) throws SQLException {
        if (isPlatform(conn)) {
            return;
        }
        for (int i = 0; i < keys.length; i++) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT iam.has_permission(?)")) {
                ps.setString(1, keys[i]);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getBoolean(1)) {
                        return;
                    }
                }
            }
        }
        throw new UserProfileAccessDeniedException("Missing required IADM-05 permission");
    }
}
