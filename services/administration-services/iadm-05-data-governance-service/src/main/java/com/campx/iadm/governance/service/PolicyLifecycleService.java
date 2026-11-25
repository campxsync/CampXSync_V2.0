package com.campx.iadm.governance.service;

import com.campx.iadm.governance.config.DatabaseConnectionManager;
import com.campx.iadm.governance.exception.InstituteAdminException;
import com.campx.iadm.governance.exception.ResourceNotFoundException;
import com.campx.iadm.governance.exception.UserProfileAccessDeniedException;
import com.campx.iadm.governance.security.UserSecurityContext;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-05-US-026 — versioned policy lifecycle for classification, retention, privacy/consent.
 * Plain language: draft → in review → approved → published → superseded.
 * Published versions are immutable; author cannot approve own revision (G-15 / US-003).
 */
public class PolicyLifecycleService {

    private final DatabaseConnectionManager db = DatabaseConnectionManager.getInstance();

    public Map<String, Object> listPolicies(UserSecurityContext ctx, String policyType, String statusFilter) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, ctx, "iadm05.classification.view", "iadm05.retention.view",
                        "iadm05.privacy.view", "iadm05.policy.history", "iadm05.adherence.view",
                        "iadm05.college.view", "iadm05.audit.view");
                StringBuilder sql = new StringBuilder(
                        "SELECT id, policy_type, code, title, owner_label, status, version_no, "
                                + "effective_from, effective_to, demo_label, author_user_id, "
                                + "tenant_scope, scope_tenant_id, created_at "
                                + "FROM gov.policies WHERE 1=1");
                List<Object> params = new ArrayList<Object>();
                if (policyType != null && !policyType.trim().isEmpty()) {
                    sql.append(" AND policy_type = ?");
                    params.add(policyType.trim().toUpperCase());
                }
                if (statusFilter != null && !statusFilter.trim().isEmpty()) {
                    sql.append(" AND status = ?");
                    params.add(statusFilter.trim().toUpperCase());
                }
                if (!isPlatformOperator(conn) && ctx.getTenantId() != null) {
                    sql.append(" AND (scope_tenant_id IS NULL OR scope_tenant_id = ?)");
                    params.add(ctx.getTenantId());
                    sql.append(" AND status IN ('PUBLISHED','SUPERSEDED')");
                }
                sql.append(" ORDER BY code, version_no DESC");
                List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rows.add(mapPolicy(rs));
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", rows);
                out.put("count", Integer.valueOf(rows.size()));
                return out;
            }
        });
    }

    public Map<String, Object> proposeClassification(UserSecurityContext ctx, Map<String, Object> body) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, ctx, "iadm05.classification.propose");
                String code = str(body, "code");
                String title = str(body, "title");
                String className = str(body, "className");
                String description = str(body, "description");
                String handling = strOr(body, "handlingGuidance", "TBC");
                String permitted = strOr(body, "permittedUseNotes", "TBC");
                String owner = strOr(body, "ownerLabel", "TBC");
                if (code.isEmpty() || title.isEmpty() || className.isEmpty()) {
                    throw new InstituteAdminException(400, "IADM05_VALIDATION",
                            "code, title and className are required.");
                }
                int nextVersion = nextVersion(conn, code);
                UUID policyId = UUID.randomUUID();
                insertPolicy(conn, policyId, "CLASSIFICATION", code, title, owner, "DRAFT",
                        nextVersion, ctx.getUserId(), "PLATFORM", null);
                UUID revId = insertRevision(conn, policyId, 1, "DRAFT", ctx.getUserId(),
                        "{\"className\":\"" + esc(className) + "\"}");
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO gov.classification_defs "
                                + "(policy_id, class_code, class_name, description, handling_guidance, "
                                + "permitted_use_notes, demo_label) VALUES (?,?,?,?,?,?,?)")) {
                    ps.setObject(1, policyId);
                    ps.setString(2, code);
                    ps.setString(3, className);
                    ps.setString(4, description);
                    ps.setString(5, handling);
                    ps.setString(6, permitted);
                    ps.setString(7, "DEMO ONLY / NOT LEGAL CLASSIFICATIONS");
                    ps.executeUpdate();
                }
                UUID auditId = DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM05_CLASSIFICATION_PROPOSE", "policy", policyId.toString(),
                        "Proposed classification revision " + code + " v" + nextVersion);
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("policyId", policyId.toString());
                out.put("revisionId", revId.toString());
                out.put("versionNo", Integer.valueOf(nextVersion));
                out.put("status", "DRAFT");
                out.put("demoLabel", "DEMO ONLY / NOT LEGAL CLASSIFICATIONS");
                out.put("auditEventId", auditId.toString());
                return out;
            }
        });
    }

    public Map<String, Object> proposeRetention(UserSecurityContext ctx, Map<String, Object> body) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, ctx, "iadm05.retention.propose");
                String code = str(body, "code");
                String title = str(body, "title");
                String domain = str(body, "domainCode");
                String category = str(body, "recordCategory");
                if (code.isEmpty() || title.isEmpty() || domain.isEmpty() || category.isEmpty()) {
                    throw new InstituteAdminException(400, "IADM05_VALIDATION",
                            "code, title, domainCode and recordCategory are required.");
                }
                // Duration never invented — force TBC unless owner supplied explicitly as TBC
                String duration = strOr(body, "durationNote", "TBC");
                if (duration.matches("(?i).*\\d+\\s*(day|days|year|years|month|months).*")
                        && !duration.toUpperCase().contains("TBC")) {
                    duration = "TBC";
                }
                int nextVersion = nextVersion(conn, code);
                UUID policyId = UUID.randomUUID();
                insertPolicy(conn, policyId, "RETENTION", code, title, strOr(body, "ownerLabel", "TBC"),
                        "DRAFT", nextVersion, ctx.getUserId(), "PLATFORM", null);
                UUID revId = insertRevision(conn, policyId, 1, "DRAFT", ctx.getUserId(),
                        "{\"domain\":\"" + esc(domain) + "\"}");
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO gov.retention_rules "
                                + "(policy_id, domain_code, record_category, scope_note, trigger_note, "
                                + "rule_reference, duration_note, hold_handling, demo_label) "
                                + "VALUES (?,?,?,?,?,?,?,?,?)")) {
                    ps.setObject(1, policyId);
                    ps.setString(2, domain);
                    ps.setString(3, category);
                    ps.setString(4, strOr(body, "scopeNote", "TBC"));
                    ps.setString(5, strOr(body, "triggerNote", "TBC"));
                    ps.setString(6, strOr(body, "ruleReference", "TBC"));
                    ps.setString(7, duration);
                    ps.setString(8, strOr(body, "holdHandling", "TBC"));
                    ps.setString(9, "DEMO ONLY");
                    ps.executeUpdate();
                }
                UUID auditId = DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM05_RETENTION_PROPOSE", "policy", policyId.toString(),
                        "Proposed retention rule " + code + " (duration TBC)");
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("policyId", policyId.toString());
                out.put("revisionId", revId.toString());
                out.put("versionNo", Integer.valueOf(nextVersion));
                out.put("status", "DRAFT");
                out.put("durationNote", duration);
                out.put("auditEventId", auditId.toString());
                return out;
            }
        });
    }

    public Map<String, Object> proposePrivacy(UserSecurityContext ctx, Map<String, Object> body) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, ctx, "iadm05.privacy.propose");
                String code = str(body, "code");
                String title = str(body, "title");
                if (code.isEmpty() || title.isEmpty()) {
                    throw new InstituteAdminException(400, "IADM05_VALIDATION", "code and title required.");
                }
                int nextVersion = nextVersion(conn, code);
                UUID policyId = UUID.randomUUID();
                insertPolicy(conn, policyId, "PRIVACY_CONSENT", code, title,
                        strOr(body, "ownerLabel", "TBC"), "DRAFT", nextVersion, ctx.getUserId(),
                        "PLATFORM", null);
                UUID revId = insertRevision(conn, policyId, 1, "DRAFT", ctx.getUserId(),
                        "{\"legalGate\":\"D-RBAC-M17\",\"note\":\"TBC\"}");
                UUID auditId = DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM05_PRIVACY_PROPOSE", "policy", policyId.toString(),
                        "Proposed privacy/consent revision (publish gated)");
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("policyId", policyId.toString());
                out.put("revisionId", revId.toString());
                out.put("versionNo", Integer.valueOf(nextVersion));
                out.put("status", "DRAFT");
                out.put("publishGate", "Blocked: awaiting children's-data legal check (D-RBAC-M17)");
                out.put("auditEventId", auditId.toString());
                return out;
            }
        });
    }

    public Map<String, Object> submitForReview(UserSecurityContext ctx, UUID policyId) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, ctx, "iadm05.classification.propose", "iadm05.retention.propose",
                        "iadm05.privacy.propose");
                PolicyRow row = loadPolicy(conn, policyId);
                if (!"DRAFT".equals(row.status) && !"REJECTED".equals(row.status)) {
                    throw new InstituteAdminException(409, "IADM05_INVALID_STATE",
                            "Only DRAFT or REJECTED policies can be submitted for review.");
                }
                updateStatus(conn, policyId, "IN_REVIEW");
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM05_POLICY_SUBMIT", "policy", policyId.toString(), "Submitted for review");
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("policyId", policyId.toString());
                out.put("status", "IN_REVIEW");
                return out;
            }
        });
    }

    public Map<String, Object> decide(UserSecurityContext ctx, UUID policyId, String decision, String rationaleRef) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                // G-15: R01 only — platform admin path OR iadm05.policy.approve (never on R02 template)
                if (!isPlatformOperator(conn)) {
                    requirePermission(conn, "iadm05.policy.approve");
                }
                PolicyRow row = loadPolicy(conn, policyId);
                if (!"IN_REVIEW".equals(row.status)) {
                    throw new InstituteAdminException(409, "IADM05_INVALID_STATE",
                            "Only IN_REVIEW policies can be decided.");
                }
                if (row.authorUserId.equals(ctx.getUserId())) {
                    throw new InstituteAdminException(403, "IADM05_SOD_VIOLATION",
                            "Approver must differ from the author (separation of duties).");
                }
                String d = decision == null ? "" : decision.trim().toUpperCase();
                if (!"APPROVE".equals(d) && !"REJECT".equals(d)) {
                    throw new InstituteAdminException(400, "IADM05_VALIDATION", "decision must be APPROVE or REJECT.");
                }
                if ("REJECT".equals(d) && (rationaleRef == null || rationaleRef.trim().isEmpty())) {
                    throw new InstituteAdminException(400, "IADM05_VALIDATION",
                            "Rejection requires a rationale reference.");
                }
                String newStatus = "APPROVE".equals(d) ? "APPROVED" : "REJECTED";
                updateStatus(conn, policyId, newStatus);
                UUID auditId = DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM05_POLICY_DECIDE", "policy", policyId.toString(),
                        d + " by reviewer; rationale=" + (rationaleRef == null ? "" : rationaleRef));
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO gov.policy_decisions "
                                + "(policy_id, decision, actor_user_id, rationale_ref, audit_event_id) "
                                + "VALUES (?,?,?,?,?)")) {
                    ps.setObject(1, policyId);
                    ps.setString(2, d);
                    ps.setObject(3, ctx.getUserId());
                    ps.setString(4, rationaleRef);
                    ps.setObject(5, auditId);
                    ps.executeUpdate();
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("policyId", policyId.toString());
                out.put("decision", d);
                out.put("status", newStatus);
                out.put("actorUserId", ctx.getUserId().toString());
                out.put("auditEventId", auditId.toString());
                return out;
            }
        });
    }

    public Map<String, Object> publish(UserSecurityContext ctx, UUID policyId, String effectiveFromIso) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                if (!isPlatformOperator(conn)) {
                    requirePermission(conn, "iadm05.policy.publish");
                }
                PolicyRow row = loadPolicy(conn, policyId);
                if ("PRIVACY_CONSENT".equals(row.policyType)) {
                    // US-015: hard legal gate — no way to open in this build
                    throw new InstituteAdminException(423, "IADM05_LEGAL_GATE",
                            "Blocked: awaiting children's-data legal check (D-RBAC-M17)");
                }
                if (!"APPROVED".equals(row.status)) {
                    throw new InstituteAdminException(409, "IADM05_INVALID_STATE",
                            "Only APPROVED versions can be published.");
                }
                if (effectiveFromIso == null || effectiveFromIso.trim().isEmpty()) {
                    throw new InstituteAdminException(400, "IADM05_VALIDATION",
                            "effectiveFrom is required to publish.");
                }
                // Supersede previous published versions of same code
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE gov.policies SET status = 'SUPERSEDED', updated_at = now() "
                                + "WHERE code = ? AND status = 'PUBLISHED' AND id <> ?")) {
                    ps.setString(1, row.code);
                    ps.setObject(2, policyId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE gov.policies SET status = 'PUBLISHED', effective_from = ?::timestamptz, "
                                + "updated_at = now() WHERE id = ?")) {
                    ps.setString(1, effectiveFromIso.trim());
                    ps.setObject(2, policyId);
                    ps.executeUpdate();
                }
                UUID auditId = DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM05_POLICY_PUBLISH", "policy", policyId.toString(),
                        "Published " + row.code + " v" + row.versionNo);
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("policyId", policyId.toString());
                out.put("status", "PUBLISHED");
                out.put("effectiveFrom", effectiveFromIso);
                out.put("auditEventId", auditId.toString());
                return out;
            }
        });
    }

    public Map<String, Object> history(UserSecurityContext ctx, UUID policyId) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, ctx, "iadm05.policy.history", "iadm05.classification.view",
                        "iadm05.retention.view", "iadm05.privacy.view", "iadm05.audit.view");
                PolicyRow row = loadPolicy(conn, policyId);
                if (!isPlatformOperator(conn) && !"PUBLISHED".equals(row.status)
                        && !"SUPERSEDED".equals(row.status)) {
                    throw new UserProfileAccessDeniedException("Institute actors may only see published history.");
                }
                List<Map<String, Object>> decisions = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, decision, actor_user_id, decided_at, rationale_ref, audit_event_id "
                                + "FROM gov.policy_decisions WHERE policy_id = ? ORDER BY decided_at")) {
                    ps.setObject(1, policyId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> d = new LinkedHashMap<String, Object>();
                            d.put("id", rs.getObject("id").toString());
                            d.put("decision", rs.getString("decision"));
                            d.put("actorUserId", rs.getObject("actor_user_id").toString());
                            d.put("decidedAt", rs.getTimestamp("decided_at").toInstant().toString());
                            d.put("rationaleRef", rs.getString("rationale_ref"));
                            Object ae = rs.getObject("audit_event_id");
                            d.put("auditEventId", ae == null ? null : ae.toString());
                            decisions.add(d);
                        }
                    }
                }
                // Sibling versions by code
                List<Map<String, Object>> versions = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, version_no, status, author_user_id, effective_from, demo_label "
                                + "FROM gov.policies WHERE code = ? ORDER BY version_no")) {
                    ps.setString(1, row.code);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> v = new LinkedHashMap<String, Object>();
                            v.put("policyId", rs.getObject("id").toString());
                            v.put("versionNo", Integer.valueOf(rs.getInt("version_no")));
                            v.put("status", rs.getString("status"));
                            v.put("authorUserId", rs.getObject("author_user_id").toString());
                            Timestamp ef = rs.getTimestamp("effective_from");
                            v.put("effectiveFrom", ef == null ? null : ef.toInstant().toString());
                            v.put("demoLabel", rs.getString("demo_label"));
                            versions.add(v);
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("code", row.code);
                out.put("policyType", row.policyType);
                out.put("versions", versions);
                out.put("decisions", decisions);
                return out;
            }
        });
    }

    public Map<String, Object> resolvePublished(UserSecurityContext ctx, String policyType, String domainCode) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, ctx, "iadm05.domain.contract", "iadm05.classification.view",
                        "iadm05.retention.view", "iadm05.policy.history");
                String sql = "SELECT p.id, p.code, p.version_no, p.policy_type, p.effective_from "
                        + "FROM gov.policies p WHERE p.policy_type = ? AND p.status = 'PUBLISHED'";
                if (domainCode != null && !domainCode.trim().isEmpty()
                        && "RETENTION".equalsIgnoreCase(policyType)) {
                    sql = "SELECT p.id, p.code, p.version_no, p.policy_type, p.effective_from "
                            + "FROM gov.policies p JOIN gov.retention_rules r ON r.policy_id = p.id "
                            + "WHERE p.policy_type = 'RETENTION' AND p.status = 'PUBLISHED' "
                            + "AND r.domain_code = ?";
                }
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (sql.contains("domain_code")) {
                        ps.setString(1, domainCode.trim());
                    } else {
                        ps.setString(1, policyType.trim().toUpperCase());
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new InstituteAdminException(404, "IADM05_NO_APPLICABLE_POLICY",
                                    "No applicable published policy (fail closed).");
                        }
                        Map<String, Object> out = new LinkedHashMap<String, Object>();
                        out.put("policyId", rs.getObject("id").toString());
                        out.put("code", rs.getString("code"));
                        out.put("versionNo", Integer.valueOf(rs.getInt("version_no")));
                        out.put("policyType", rs.getString("policy_type"));
                        if (rs.next()) {
                            throw new InstituteAdminException(409, "IADM05_POLICY_CONFLICT",
                                    "Conflicting published policy versions; operation blocked.");
                        }
                        return out;
                    }
                }
            }
        });
    }

    // ---- helpers ----

    private void insertPolicy(Connection conn, UUID id, String type, String code, String title,
                              String owner, String status, int version, UUID author,
                              String tenantScope, UUID scopeTenant) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO gov.policies "
                        + "(id, policy_type, code, title, owner_label, status, version_no, "
                        + "author_user_id, tenant_scope, scope_tenant_id, demo_label) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setObject(1, id);
            ps.setString(2, type);
            ps.setString(3, code);
            ps.setString(4, title);
            ps.setString(5, owner);
            ps.setString(6, status);
            ps.setInt(7, version);
            ps.setObject(8, author);
            ps.setString(9, tenantScope);
            if (scopeTenant == null) {
                ps.setNull(10, Types.OTHER);
            } else {
                ps.setObject(10, scopeTenant);
            }
            ps.setString(11, "DEMO ONLY");
            ps.executeUpdate();
        }
    }

    private UUID insertRevision(Connection conn, UUID policyId, int revNo, String status,
                                UUID createdBy, String payloadJson) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO gov.policy_revisions "
                        + "(id, policy_id, revision_no, status, payload, created_by) "
                        + "VALUES (?,?,?,?,?::jsonb,?)")) {
            ps.setObject(1, id);
            ps.setObject(2, policyId);
            ps.setInt(3, revNo);
            ps.setString(4, status);
            ps.setString(5, payloadJson);
            ps.setObject(6, createdBy);
            ps.executeUpdate();
        }
        return id;
    }

    private int nextVersion(Connection conn, String code) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT coalesce(max(version_no),0) + 1 FROM gov.policies WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void updateStatus(Connection conn, UUID policyId, String status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE gov.policies SET status = ?, updated_at = now() WHERE id = ?")) {
            ps.setString(1, status);
            ps.setObject(2, policyId);
            ps.executeUpdate();
        }
    }

    private PolicyRow loadPolicy(Connection conn, UUID policyId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, policy_type, code, status, version_no, author_user_id, scope_tenant_id "
                        + "FROM gov.policies WHERE id = ?")) {
            ps.setObject(1, policyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("Policy not found: " + policyId);
                }
                PolicyRow row = new PolicyRow();
                row.id = (UUID) rs.getObject("id");
                row.policyType = rs.getString("policy_type");
                row.code = rs.getString("code");
                row.status = rs.getString("status");
                row.versionNo = rs.getInt("version_no");
                row.authorUserId = (UUID) rs.getObject("author_user_id");
                row.scopeTenantId = (UUID) rs.getObject("scope_tenant_id");
                return row;
            }
        }
    }

    private Map<String, Object> mapPolicy(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", rs.getObject("id").toString());
        m.put("policyType", rs.getString("policy_type"));
        m.put("code", rs.getString("code"));
        m.put("title", rs.getString("title"));
        m.put("ownerLabel", rs.getString("owner_label"));
        m.put("status", rs.getString("status"));
        m.put("versionNo", Integer.valueOf(rs.getInt("version_no")));
        Timestamp ef = rs.getTimestamp("effective_from");
        m.put("effectiveFrom", ef == null ? null : ef.toInstant().toString());
        Timestamp et = rs.getTimestamp("effective_to");
        m.put("effectiveTo", et == null ? null : et.toInstant().toString());
        m.put("demoLabel", rs.getString("demo_label"));
        m.put("authorUserId", rs.getObject("author_user_id").toString());
        m.put("tenantScope", rs.getString("tenant_scope"));
        Object st = rs.getObject("scope_tenant_id");
        m.put("scopeTenantId", st == null ? null : st.toString());
        m.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
        return m;
    }

    private boolean isPlatformOperator(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT iam.is_platform_admin()");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getBoolean(1);
        }
    }

    private void requirePermission(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT iam.has_permission(?)")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !rs.getBoolean(1)) {
                    if (!isPlatformOperator(conn)) {
                        throw new UserProfileAccessDeniedException("Missing permission: " + key);
                    }
                }
            }
        }
    }

    private void requireAny(Connection conn, UserSecurityContext ctx, String... keys) throws SQLException {
        if (isPlatformOperator(conn)) {
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

    private static String str(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String strOr(Map<String, Object> body, String key, String def) {
        String s = str(body, key);
        return s.isEmpty() ? def : s;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class PolicyRow {
        UUID id;
        String policyType;
        String code;
        String status;
        int versionNo;
        UUID authorUserId;
        UUID scopeTenantId;
    }
}
