package com.campx.iadm.governance.service;

import com.campx.iadm.governance.config.DatabaseConnectionManager;
import com.campx.iadm.governance.exception.InstituteAdminException;
import com.campx.iadm.governance.exception.ResourceNotFoundException;
import com.campx.iadm.governance.exception.UserProfileAccessDeniedException;
import com.campx.iadm.governance.security.UserSecurityContext;
import com.campx.iadm.governance.simulator.DomainExecutionSimulator;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Governance request engine (US-027) for export, erasure and sharing.
 * Plain language: IADM-05 records requests and decisions; domain services (or DEMO simulator) execute.
 */
public class GovernanceRequestService {

    private final DatabaseConnectionManager db = DatabaseConnectionManager.getInstance();
    private final DomainExecutionSimulator simulator = new DomainExecutionSimulator();

    public Map<String, Object> submitExport(UserSecurityContext ctx, Map<String, Object> body) {
        return submit(ctx, "EXPORT", body, "iadm05.export.request");
    }

    public Map<String, Object> submitErasure(UserSecurityContext ctx, Map<String, Object> body) {
        return submit(ctx, "ERASURE", body, "iadm05.erasure.request");
    }

    public Map<String, Object> submitSharing(UserSecurityContext ctx, Map<String, Object> body) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, "iadm05.sharing.propose");
                String purpose = str(body, "purpose");
                String provider = str(body, "provider");
                String consumer = str(body, "consumer");
                String validityEnd = str(body, "validityEnd");
                if (purpose.isEmpty() || provider.isEmpty() || consumer.isEmpty() || validityEnd.isEmpty()) {
                    throw new InstituteAdminException(400, "IADM05_VALIDATION",
                            "purpose, provider, consumer and validityEnd are required.");
                }
                if ("*".equals(provider) || "*".equals(consumer)) {
                    throw new InstituteAdminException(400, "IADM05_VALIDATION",
                            "Wildcard provider/consumer is forbidden (default deny).");
                }
                String idem = str(body, "idempotencyKey");
                Map<String, Object> existing = findByIdempotency(conn, idem);
                if (existing != null) {
                    return existing;
                }
                UUID scopeTenant = resolveTrustedScopeTenant(conn, ctx, parseUuid(str(body, "scopeTenantId")));
                UUID id = UUID.randomUUID();
                UUID correlation = UUID.randomUUID();
                String legal = strOr(body, "legalBasisRef", "TBC");
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO gov.governance_requests "
                                + "(id, request_type, status, correlation_id, idempotency_key, requester_user_id, "
                                + "purpose, domain_codes, fields_requested, scope_tenant_id, "
                                + "sharing_provider, sharing_consumer, sharing_validity_start, sharing_validity_end, "
                                + "legal_basis_ref, onward_sharing_restriction, revocation_conditions, "
                                + "agreement_status, demo_label) "
                                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,now(),?::timestamptz,?,?,?,NULL,?)")) {
                    ps.setObject(1, id);
                    ps.setString(2, "SHARING");
                    ps.setString(3, "SUBMITTED");
                    ps.setObject(4, correlation);
                    if (idem.isEmpty()) {
                        ps.setNull(5, Types.VARCHAR);
                    } else {
                        ps.setString(5, idem);
                    }
                    ps.setObject(6, ctx.getUserId());
                    ps.setString(7, purpose);
                    ps.setArray(8, conn.createArrayOf("text", toArray(body.get("domainCodes"))));
                    ps.setArray(9, conn.createArrayOf("text", toArray(body.get("fieldsRequested"))));
                    setUuid(ps, 10, scopeTenant, null);
                    ps.setString(11, provider);
                    ps.setString(12, consumer);
                    ps.setString(13, validityEnd);
                    ps.setString(14, legal);
                    ps.setString(15, strOr(body, "onwardSharingRestriction", "NO_ONWARD_SHARING"));
                    ps.setString(16, strOr(body, "revocationConditions", "TBC"));
                    ps.setString(17, "DEMO ONLY");
                    ps.executeUpdate();
                }
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM05_SHARING_SUBMIT", "governance_request", id.toString(),
                        "Sharing agreement submitted");
                return loadRequest(conn, id);
            }
        });
    }

    private Map<String, Object> submit(UserSecurityContext ctx, final String type,
                                       final Map<String, Object> body, final String perm) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, perm);
                String purpose = str(body, "purpose");
                if (purpose.isEmpty() && !"ERASURE".equals(type)) {
                    throw new InstituteAdminException(400, "IADM05_VALIDATION", "purpose is required.");
                }
                // All request types: non-platform callers forced to trusted claims (same as EXPORT).
                UUID scopeTenant = resolveTrustedScopeTenant(conn, ctx, parseUuid(str(body, "scopeTenantId")));
                // Fail closed: require applicable published policy when policyId not provided
                UUID policyId = parseUuid(str(body, "applicablePolicyId"));
                Integer policyVersion = null;
                if (policyId == null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT id, version_no FROM gov.policies "
                                    + "WHERE policy_type = ? AND status = 'PUBLISHED' LIMIT 2")) {
                        ps.setString(1, "EXPORT".equals(type) ? "RETENTION" : "RETENTION");
                        // Prefer any published classification/retention as governance baseline
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new InstituteAdminException(404, "IADM05_NO_APPLICABLE_POLICY",
                                        "No applicable published policy (fail closed).");
                            }
                            policyId = (UUID) rs.getObject("id");
                            policyVersion = Integer.valueOf(rs.getInt("version_no"));
                            if (rs.next()) {
                                // multiple OK for baseline — use first; conflict handled at resolve
                            }
                        }
                    }
                } else {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT version_no, status FROM gov.policies WHERE id = ?")) {
                        ps.setObject(1, policyId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next() || !"PUBLISHED".equals(rs.getString("status"))) {
                                throw new InstituteAdminException(404, "IADM05_NO_APPLICABLE_POLICY",
                                        "Applicable policy missing or not published (fail closed).");
                            }
                            policyVersion = Integer.valueOf(rs.getInt("version_no"));
                        }
                    }
                }
                String idem = str(body, "idempotencyKey");
                Map<String, Object> existing = findByIdempotency(conn, idem);
                if (existing != null) {
                    return existing;
                }
                UUID id = UUID.randomUUID();
                UUID correlation = UUID.randomUUID();
                String subjectRef = str(body, "subjectRefOpaque");
                if ("ERASURE".equals(type) && subjectRef.isEmpty()) {
                    subjectRef = "opaque-" + UUID.randomUUID().toString().substring(0, 8);
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO gov.governance_requests "
                                + "(id, request_type, status, correlation_id, idempotency_key, requester_user_id, "
                                + "purpose, domain_codes, fields_requested, scope_tenant_id, destination_note, "
                                + "applicable_policy_id, applicable_policy_version, subject_ref_opaque, demo_label) "
                                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                    ps.setObject(1, id);
                    ps.setString(2, type);
                    ps.setString(3, "SUBMITTED");
                    ps.setObject(4, correlation);
                    if (idem.isEmpty()) {
                        ps.setNull(5, Types.VARCHAR);
                    } else {
                        ps.setString(5, idem);
                    }
                    ps.setObject(6, ctx.getUserId());
                    ps.setString(7, purpose.isEmpty() ? "erasure-governance" : purpose);
                    ps.setArray(8, conn.createArrayOf("text", toArray(body.get("domainCodes"))));
                    ps.setArray(9, conn.createArrayOf("text", toArray(body.get("fieldsRequested"))));
                    if (scopeTenant == null) {
                        ps.setNull(10, Types.OTHER);
                    } else {
                        ps.setObject(10, scopeTenant);
                    }
                    ps.setString(11, str(body, "destinationNote"));
                    ps.setObject(12, policyId);
                    ps.setInt(13, policyVersion.intValue());
                    ps.setString(14, "ERASURE".equals(type) ? subjectRef : null);
                    ps.setString(15, "DEMO ONLY");
                    ps.executeUpdate();
                }
                // Seed pending domain outcomes
                String[] domains = toArray(body.get("domainCodes"));
                if (domains.length == 0) {
                    domains = new String[]{"DEMO_DOMAIN_A", "DEMO_DOMAIN_B"};
                }
                for (int i = 0; i < domains.length; i++) {
                    insertOutcome(conn, id, domains[i], "PENDING", correlation, null, null);
                }
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM05_" + type + "_SUBMIT", "governance_request", id.toString(),
                        type + " request submitted");
                return loadRequest(conn, id);
            }
        });
    }

    public Map<String, Object> decideRequest(UserSecurityContext ctx, UUID requestId,
                                             String decision, String rationaleRef) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                Map<String, Object> req = loadRequest(conn, requestId);
                String type = String.valueOf(req.get("requestType"));
                String decideKey = "EXPORT".equals(type) ? "iadm05.export.decide"
                        : "SHARING".equals(type) ? "iadm05.sharing.decide" : "iadm05.erasure.decide";
                assertSuperAdminApprover(conn, ctx, decideKey);
                String requester = String.valueOf(req.get("requesterUserId"));
                if (requester.equals(ctx.getUserId().toString())) {
                    throw new InstituteAdminException(403, "IADM05_SOD_VIOLATION",
                            "Reviewer must differ from requester.");
                }
                String d = decision == null ? "" : decision.trim().toUpperCase();
                if (!"APPROVE".equals(d) && !"REJECT".equals(d)) {
                    throw new InstituteAdminException(400, "IADM05_VALIDATION", "APPROVE or REJECT required.");
                }
                if ("REJECT".equals(d) && (rationaleRef == null || rationaleRef.trim().isEmpty())) {
                    throw new InstituteAdminException(400, "IADM05_VALIDATION", "Rejection requires rationale.");
                }
                String newStatus = "APPROVE".equals(d) ? "APPROVED" : "REJECTED";
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE gov.governance_requests SET status = ?, reviewer_user_id = ?, "
                                + "updated_at = now() WHERE id = ?")) {
                    ps.setString(1, newStatus);
                    ps.setObject(2, ctx.getUserId());
                    ps.setObject(3, requestId);
                    ps.executeUpdate();
                }
                if ("SHARING".equals(type) && "APPROVE".equals(d)) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE gov.governance_requests SET agreement_status = 'ACTIVE', "
                                    + "status = 'COMPLETED', updated_at = now() WHERE id = ?")) {
                        ps.setObject(1, requestId);
                        ps.executeUpdate();
                    }
                }
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM05_REQUEST_DECIDE", "governance_request", requestId.toString(),
                        d + " " + type);
                // Dispatch approved export/erasure via DEMO simulator (real domain contract path)
                if ("APPROVE".equals(d) && !"SHARING".equals(type)) {
                    dispatchInternal(conn, ctx, requestId);
                }
                return loadRequest(conn, requestId);
            }
        });
    }

    public Map<String, Object> dispatch(UserSecurityContext ctx, UUID requestId) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, "iadm05.requests.operate", "iadm05.export.decide", "iadm05.erasure.decide");
                return dispatchInternal(conn, ctx, requestId);
            }
        });
    }

    private Map<String, Object> dispatchInternal(Connection conn, UserSecurityContext ctx, UUID requestId)
            throws SQLException {
        Map<String, Object> req = loadRequest(conn, requestId);
        String status = String.valueOf(req.get("status"));
        if (!"APPROVED".equals(status) && !"DISPATCHED".equals(status)
                && !"PARTIALLY_COMPLETED".equals(status) && !"IN_PROGRESS".equals(status)
                && !"FAILED".equals(status)) {
            throw new InstituteAdminException(409, "IADM05_INVALID_STATE",
                    "Request must be approved (or partial/failed) before dispatch.");
        }
        UUID correlation = UUID.fromString(String.valueOf(req.get("correlationId")));
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE gov.governance_requests SET status = 'DISPATCHED', updated_at = now() WHERE id = ?")) {
            ps.setObject(1, requestId);
            ps.executeUpdate();
        }
        // Only unresolved domains
        List<String> pending = new ArrayList<String>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT domain_code, outcome FROM gov.domain_outcomes WHERE request_id = ?")) {
            ps.setObject(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String outcome = rs.getString("outcome");
                    if (!"COMPLETED".equals(outcome) && !"REJECTED".equals(outcome)) {
                        pending.add(rs.getString("domain_code"));
                    }
                }
            }
        }
        for (int i = 0; i < pending.size(); i++) {
            Map<String, Object> sim = simulator.execute(String.valueOf(req.get("requestType")),
                    pending.get(i), correlation, req);
            String outcome = String.valueOf(sim.get("outcome"));
            String receipt = sim.get("receiptRef") == null ? null : String.valueOf(sim.get("receiptRef"));
            String reason = sim.get("reasonCode") == null ? null : String.valueOf(sim.get("reasonCode"));
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE gov.domain_outcomes SET outcome = ?, receipt_ref = ?, reason_code = ?, "
                            + "updated_at = now() WHERE request_id = ? AND domain_code = ?")) {
                ps.setString(1, outcome);
                ps.setString(2, receipt);
                ps.setString(3, reason);
                ps.setObject(4, requestId);
                ps.setString(5, pending.get(i));
                ps.executeUpdate();
            }
        }
        refreshAggregateStatus(conn, requestId);
        DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                "IADM05_REQUEST_DISPATCH", "governance_request", requestId.toString(),
                "Dispatched unresolved domains via DEMO ONLY simulator");
        return loadRequest(conn, requestId);
    }

    public Map<String, Object> closeErasure(UserSecurityContext ctx, UUID requestId) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                assertSuperAdminApprover(conn, ctx, "iadm05.erasure.close");
                Map<String, Object> req = loadRequest(conn, requestId);
                if (!"ERASURE".equals(String.valueOf(req.get("requestType")))) {
                    throw new InstituteAdminException(400, "IADM05_VALIDATION", "Only erasure requests can be closed.");
                }
                // Technical closure rule only (G-05 legal criteria TBC)
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FILTER (WHERE outcome NOT IN ('COMPLETED','REJECTED')) AS open_count "
                                + "FROM gov.domain_outcomes WHERE request_id = ?")) {
                    ps.setObject(1, requestId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        if (rs.getInt("open_count") > 0) {
                            throw new InstituteAdminException(409, "IADM05_CLOSURE_BLOCKED",
                                    "Closure blocked: one or more required domains are unresolved "
                                            + "(technical rule only; legal closure criteria TBC — G-05).");
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE gov.governance_requests SET status = 'CLOSED', updated_at = now() WHERE id = ?")) {
                    ps.setObject(1, requestId);
                    ps.executeUpdate();
                }
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM05_ERASURE_CLOSE", "governance_request", requestId.toString(),
                        "Erasure closed under technical rule (legal criteria TBC)");
                return loadRequest(conn, requestId);
            }
        });
    }

    public Map<String, Object> sharingLifecycle(UserSecurityContext ctx, UUID requestId, String action) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                if (!isPlatform(conn)) {
                    requireAny(conn, "iadm05.sharing.lifecycle");
                }
                String a = action == null ? "" : action.trim().toUpperCase();
                String agreement;
                if ("SUSPEND".equals(a)) {
                    agreement = "SUSPENDED";
                } else if ("REVOKE".equals(a)) {
                    agreement = "REVOKED";
                } else if ("EXPIRE".equals(a)) {
                    agreement = "EXPIRED";
                } else {
                    throw new InstituteAdminException(400, "IADM05_VALIDATION",
                            "action must be SUSPEND, REVOKE or EXPIRE.");
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE gov.governance_requests SET agreement_status = ?, updated_at = now() "
                                + "WHERE id = ? AND request_type = 'SHARING'")) {
                    ps.setString(1, agreement);
                    ps.setObject(2, requestId);
                    if (ps.executeUpdate() == 0) {
                        throw new ResourceNotFoundException("Sharing agreement", requestId.toString());
                    }
                }
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM05_SHARING_LIFECYCLE", "governance_request", requestId.toString(), a);
                return loadRequest(conn, requestId);
            }
        });
    }

    public Map<String, Object> applyExpiryJob(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                if (!isPlatform(conn)) {
                    requireAny(conn, "iadm05.sharing.lifecycle");
                }
                int n;
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE gov.governance_requests SET agreement_status = 'EXPIRED', updated_at = now() "
                                + "WHERE request_type = 'SHARING' AND agreement_status = 'ACTIVE' "
                                + "AND sharing_validity_end IS NOT NULL AND sharing_validity_end < now()")) {
                    n = ps.executeUpdate();
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("expiredCount", Integer.valueOf(n));
                return out;
            }
        });
    }

    public Map<String, Object> listRequests(UserSecurityContext ctx, String type, String status) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, "iadm05.export.request", "iadm05.export.decide", "iadm05.erasure.request",
                        "iadm05.erasure.decide", "iadm05.sharing.view", "iadm05.sharing.propose",
                        "iadm05.requests.operate", "iadm05.audit.view", "iadm05.adherence.view");
                StringBuilder sql = new StringBuilder(
                        "SELECT id FROM gov.governance_requests WHERE 1=1");
                List<Object> params = new ArrayList<Object>();
                if (type != null && !type.trim().isEmpty()) {
                    sql.append(" AND request_type = ?");
                    params.add(type.trim().toUpperCase());
                }
                if (status != null && !status.trim().isEmpty()) {
                    sql.append(" AND status = ?");
                    params.add(status.trim().toUpperCase());
                }
                if (!isPlatform(conn) && ctx.getTenantId() != null) {
                    sql.append(" AND scope_tenant_id = ?");
                    params.add(ctx.getTenantId());
                }
                sql.append(" ORDER BY created_at DESC");
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            items.add(loadRequest(conn, (UUID) rs.getObject("id")));
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

    public Map<String, Object> reportOutcome(UserSecurityContext ctx, UUID requestId, Map<String, Object> body) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, "iadm05.domain.contract");
                String domain = str(body, "domainCode");
                String outcome = str(body, "outcome").toUpperCase();
                if (domain.isEmpty() || outcome.isEmpty()) {
                    throw new InstituteAdminException(400, "IADM05_VALIDATION",
                            "domainCode and outcome required.");
                }
                // Idempotent by correlation + domain
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE gov.domain_outcomes SET outcome = ?, receipt_ref = ?, reason_code = ?, "
                                + "updated_at = now() WHERE request_id = ? AND domain_code = ?")) {
                    ps.setString(1, outcome);
                    ps.setString(2, str(body, "receiptRef"));
                    ps.setString(3, str(body, "reasonCode"));
                    ps.setObject(4, requestId);
                    ps.setString(5, domain);
                    if (ps.executeUpdate() == 0) {
                        UUID corr = parseUuid(str(body, "correlationId"));
                        if (corr == null) {
                            corr = UUID.randomUUID();
                        }
                        insertOutcome(conn, requestId, domain, outcome, corr,
                                str(body, "receiptRef"), str(body, "reasonCode"));
                    }
                }
                refreshAggregateStatus(conn, requestId);
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM05_DOMAIN_OUTCOME", "governance_request", requestId.toString(),
                        domain + "=" + outcome);
                return loadRequest(conn, requestId);
            }
        });
    }

    private void refreshAggregateStatus(Connection conn, UUID requestId) throws SQLException {
        int total = 0;
        int completed = 0;
        int rejected = 0;
        int pending = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT outcome FROM gov.domain_outcomes WHERE request_id = ?")) {
            ps.setObject(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    total++;
                    String o = rs.getString(1);
                    if ("COMPLETED".equals(o)) {
                        completed++;
                    } else if ("REJECTED".equals(o)) {
                        rejected++;
                    } else {
                        pending++;
                    }
                }
            }
        }
        String agg;
        if (total == 0) {
            agg = "DISPATCHED";
        } else if (pending > 0 && (completed > 0 || rejected > 0)) {
            agg = "PARTIALLY_COMPLETED";
        } else if (pending > 0) {
            agg = "IN_PROGRESS";
        } else if (completed == total) {
            agg = "COMPLETED";
        } else if (rejected == total) {
            agg = "FAILED";
        } else {
            agg = "PARTIALLY_COMPLETED";
        }
        // Never global COMPLETED while unresolved
        if (pending > 0 && "COMPLETED".equals(agg)) {
            agg = "PARTIALLY_COMPLETED";
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE gov.governance_requests SET status = ?, updated_at = now() WHERE id = ?")) {
            ps.setString(1, agg);
            ps.setObject(2, requestId);
            ps.executeUpdate();
        }
    }

    private void insertOutcome(Connection conn, UUID requestId, String domain, String outcome,
                               UUID correlation, String receipt, String reason) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO gov.domain_outcomes "
                        + "(request_id, domain_code, outcome, receipt_ref, reason_code, correlation_id) "
                        + "VALUES (?,?,?,?,?,?) "
                        + "ON CONFLICT (request_id, domain_code) DO UPDATE SET "
                        + "outcome = EXCLUDED.outcome, receipt_ref = EXCLUDED.receipt_ref, "
                        + "reason_code = EXCLUDED.reason_code, updated_at = now()")) {
            ps.setObject(1, requestId);
            ps.setString(2, domain);
            ps.setString(3, outcome);
            ps.setString(4, receipt);
            ps.setString(5, reason);
            ps.setObject(6, correlation);
            ps.executeUpdate();
        }
    }

    private Map<String, Object> findByIdempotency(Connection conn, String idem) throws SQLException {
        if (idem == null || idem.trim().isEmpty()) {
            return null;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM gov.governance_requests WHERE idempotency_key = ?")) {
            ps.setString(1, idem.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return loadRequest(conn, (UUID) rs.getObject(1));
                }
            }
        }
        return null;
    }

    public Map<String, Object> loadRequest(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM gov.governance_requests WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("Governance request", id.toString());
                }
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("requestId", rs.getObject("id").toString());
                m.put("requestType", rs.getString("request_type"));
                m.put("status", rs.getString("status"));
                m.put("correlationId", rs.getObject("correlation_id").toString());
                m.put("requesterUserId", rs.getObject("requester_user_id").toString());
                Object rev = rs.getObject("reviewer_user_id");
                m.put("reviewerUserId", rev == null ? null : rev.toString());
                m.put("purpose", rs.getString("purpose"));
                m.put("demoLabel", rs.getString("demo_label"));
                Object st = rs.getObject("scope_tenant_id");
                m.put("scopeTenantId", st == null ? null : st.toString());
                Object ap = rs.getObject("applicable_policy_id");
                m.put("applicablePolicyId", ap == null ? null : ap.toString());
                m.put("applicablePolicyVersion", rs.getObject("applicable_policy_version"));
                m.put("subjectRefOpaque", rs.getString("subject_ref_opaque"));
                m.put("sharingProvider", rs.getString("sharing_provider"));
                m.put("sharingConsumer", rs.getString("sharing_consumer"));
                m.put("agreementStatus", rs.getString("agreement_status"));
                m.put("legalBasisRef", rs.getString("legal_basis_ref"));
                Timestamp ve = rs.getTimestamp("sharing_validity_end");
                m.put("validityEnd", ve == null ? null : ve.toInstant().toString());
                List<Map<String, Object>> outcomes = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ops = conn.prepareStatement(
                        "SELECT domain_code, outcome, receipt_ref, reason_code, correlation_id, updated_at "
                                + "FROM gov.domain_outcomes WHERE request_id = ? ORDER BY domain_code")) {
                    ops.setObject(1, id);
                    try (ResultSet ors = ops.executeQuery()) {
                        while (ors.next()) {
                            Map<String, Object> o = new LinkedHashMap<String, Object>();
                            o.put("domainCode", ors.getString("domain_code"));
                            o.put("outcome", ors.getString("outcome"));
                            o.put("receiptRef", ors.getString("receipt_ref"));
                            o.put("reasonCode", ors.getString("reason_code"));
                            o.put("correlationId", ors.getObject("correlation_id").toString());
                            outcomes.add(o);
                        }
                    }
                }
                m.put("domainOutcomes", outcomes);
                return m;
            }
        }
    }

    private void setUuid(PreparedStatement ps, int idx, UUID primary, UUID fallback) throws SQLException {
        UUID v = primary != null ? primary : fallback;
        if (v == null) {
            ps.setNull(idx, Types.OTHER);
        } else {
            ps.setObject(idx, v);
        }
    }

    /**
     * Trusted institute for governance requests (close-out).
     * Plain language: Super Admin (R01) / platform may pass scopeTenantId; everyone else is forced
     * to the institute from verified claims; a different one is refused (403).
     */
    public static UUID resolveScopeTenant(boolean platformOperator, UUID trustedFromClaims, UUID clientRequested) {
        if (platformOperator) {
            return clientRequested != null ? clientRequested : trustedFromClaims;
        }
        if (trustedFromClaims == null) {
            throw new InstituteAdminException(403, "IADM05_SCOPE_REQUIRED",
                    "Institute context could not be verified.");
        }
        if (clientRequested != null && !trustedFromClaims.equals(clientRequested)) {
            throw new InstituteAdminException(403, "IADM05_CROSS_TENANT_DENIED",
                    "Cross-tenant request requires platform authority (R01).");
        }
        return trustedFromClaims;
    }

    private UUID resolveTrustedScopeTenant(Connection conn, UserSecurityContext ctx, UUID clientRequested)
            throws SQLException {
        return resolveScopeTenant(isPlatform(conn), ctx.getTenantId(), clientRequested);
    }

    private boolean isPlatform(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT iam.is_platform_admin()");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getBoolean(1);
        }
    }

    /** G-15: System Admin in plat.platform_admins must not decide/close. */
    private void assertSuperAdminApprover(Connection conn, UserSecurityContext ctx, String permissionKey)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT role_code FROM plat.platform_admins "
                        + "WHERE user_id = ? AND deleted_at IS NULL LIMIT 1")) {
            ps.setObject(1, ctx.getUserId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String code = rs.getString(1);
                    if (code != null && ("SYSTEM_ADMIN".equalsIgnoreCase(code) || "R02".equalsIgnoreCase(code))) {
                        throw new UserProfileAccessDeniedException(
                                "System Admin (R02) cannot approve, decide, or close governance requests (G-15).");
                    }
                }
            }
        }
        if (!isPlatform(conn)) {
            requireAny(conn, permissionKey);
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

    private static String[] toArray(Object raw) {
        if (raw == null) {
            return new String[0];
        }
        if (raw instanceof List) {
            List<?> list = (List<?>) raw;
            String[] a = new String[list.size()];
            for (int i = 0; i < list.size(); i++) {
                a[i] = String.valueOf(list.get(i));
            }
            return a;
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            return new String[0];
        }
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
        }
        String[] parts = s.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim().replace("\"", "");
        }
        return parts;
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String strOr(Map<String, Object> body, String key, String def) {
        String s = str(body, key);
        return s.isEmpty() ? def : s;
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
