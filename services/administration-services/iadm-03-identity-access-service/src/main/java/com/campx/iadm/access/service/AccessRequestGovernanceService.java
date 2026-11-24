package com.campx.iadm.access.service;

import com.campx.iadm.access.config.DatabaseConnectionManager;
import com.campx.iadm.access.exception.InstituteAdminException;
import com.campx.iadm.access.exception.UserProfileAccessDeniedException;
import com.campx.iadm.access.security.GrantorCeiling;
import com.campx.iadm.access.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.Connection;
import java.sql.Date;
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
 * IADM-03 S3 — access requests (US-023…025). Store only; never grants on submit.
 */
public class AccessRequestGovernanceService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AccessRequestGovernanceService.class);
    private static final String CLAIMS_NOTE =
            "Authoritative checks use DB + iam.has_permission immediately. Gateway identity cache may lag up to 5 minutes (US-021 DEV SLA).";

    private final DatabaseConnectionManager db;

    public AccessRequestGovernanceService() {
        this(DatabaseConnectionManager.getInstance());
    }

    public AccessRequestGovernanceService(DatabaseConnectionManager db) {
        this.db = db == null ? DatabaseConnectionManager.getInstance() : db;
    }

    public Map<String, Object> submit(final UserSecurityContext ctx,
                                      final UUID subjectUserId,
                                      final String requestedRole,
                                      final UUID collegeId,
                                      final UUID tenantId,
                                      final String justification,
                                      final String validFrom,
                                      final String validTo,
                                      final String idempotencyKey,
                                      final String correlationId) {
        if (requestedRole == null || requestedRole.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "requestedRole is required");
        }
        if (justification == null || justification.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "A business justification is required.");
        }
        final UUID subject = subjectUserId != null ? subjectUserId : ctx.getUserId();
        if (subject == null) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "subjectUserId is required");
        }
        final String role = requestedRole.trim().toUpperCase(Locale.ROOT);
        final String key = idempotencyKey == null || idempotencyKey.trim().isEmpty()
                ? UUID.randomUUID().toString() : idempotencyKey.trim();
        final String corr = correlationId == null || correlationId.trim().isEmpty()
                ? UUID.randomUUID().toString() : correlationId.trim();

        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iam.request.submit");
                boolean platform = isPlatformOperator(conn, ctx);
                String persona = resolvePersona(conn, ctx, platform);

                // Direct grant attempt by R04 is refused — request-only
                if ("R04".equals(persona)) {
                    String directDeny = GrantorCeiling.denyReason("R04", role, false);
                    if (directDeny == null) {
                        // should not happen for R04
                    } else {
                        // expected — they submit request instead; continue
                    }
                }

                UUID scopeTenant = tenantId != null ? tenantId : ctx.getTenantId();
                if (scopeTenant == null) {
                    throw new InstituteAdminException(400, "IADM03_VALIDATION", "tenantId is required.");
                }
                if (!platform && (ctx.getTenantId() == null || !ctx.getTenantId().equals(scopeTenant))) {
                    throw new InstituteAdminException(403, "IADM03_CROSS_INSTITUTE",
                            "The requested institute is outside your permitted scope.");
                }
                if (collegeId != null) {
                    assertCollegeInTenant(conn, collegeId, scopeTenant);
                }

                Map<String, Object> replay = findIdempotent(conn, key);
                if (replay != null) {
                    return replay;
                }

                UUID id = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO iam.access_requests "
                                + "(id, tenant_id, college_id, requester_user_id, subject_user_id, requested_role_code, "
                                + "justification, status, valid_from, valid_to, idempotency_key, correlation_id, "
                                + "created_by, updated_by) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'SUBMITTED', ?, ?, ?, ?, ?, ?)")) {
                    ps.setObject(1, id);
                    ps.setObject(2, scopeTenant);
                    ps.setObject(3, collegeId);
                    ps.setObject(4, ctx.getUserId());
                    ps.setObject(5, subject);
                    ps.setString(6, role);
                    ps.setString(7, justification.trim());
                    ps.setDate(8, parseDate(validFrom));
                    ps.setDate(9, parseDate(validTo));
                    ps.setString(10, key);
                    ps.setString(11, corr);
                    ps.setObject(12, ctx.getUserId());
                    ps.setObject(13, ctx.getUserId());
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

                DurableAuditWriter.append(conn, ctx.withTenant(scopeTenant), scopeTenant,
                        "IADM03_ACCESS_REQUEST_SUBMITTED", "ACCESS_REQUEST", id.toString(),
                        "role=" + role + "; persona=" + persona + "; " + justification.trim());

                Map<String, Object> out = baseOut(id, scopeTenant, subject, role, "SUBMITTED", key, corr);
                out.put("collegeId", collegeId == null ? null : collegeId.toString());
                out.put("requesterUserId", ctx.getUserId().toString());
                out.put("message", "Request stored and routed. No grant was applied.");
                out.put("replay", Boolean.FALSE);
                saveIdempotent(conn, key, "ACCESS_REQUEST_SUBMIT", out, corr, scopeTenant, ctx.getUserId());
                logger.info("IADM-03 access request submitted id={} role={}", id, role);
                return out;
            }
        });
    }

    public Map<String, Object> list(final UserSecurityContext ctx, final String statusFilter) {
        final boolean[] platformHolder = new boolean[]{false};
        ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException, InstituteAdminException {
                platformHolder[0] = isPlatformOperator(conn, ctx);
                if (!platformHolder[0]) {
                    try {
                        ctx.checkPermission(conn, "iam.request.submit");
                    } catch (UserProfileAccessDeniedException ex1) {
                        try {
                            ctx.checkPermission(conn, "iam.request.decide");
                        } catch (UserProfileAccessDeniedException ex2) {
                            throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                                    "You are not authorized to view access requests.");
                        }
                    }
                }
                return null;
            }
        });
        final boolean platform = platformHolder[0];
        UserSecurityContext.TransactionCallback<Map<String, Object>> load =
                new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
                    @Override
                    public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                        StringBuilder sql = new StringBuilder(
                                "SELECT id, tenant_id, college_id, requester_user_id, subject_user_id, requested_role_code, "
                                        + "justification, status, valid_from, valid_to, decided_by, decided_at, decision_reason, "
                                        + "resulting_assignment_id, created_at "
                                        + "FROM iam.access_requests WHERE deleted_at IS NULL ");
                        if (!platform) {
                            sql.append("AND tenant_id = ? ");
                        }
                        if (statusFilter != null && !statusFilter.trim().isEmpty()
                                && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
                            sql.append("AND status = ? ");
                        }
                        sql.append("ORDER BY created_at DESC LIMIT 100");
                        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                            int i = 1;
                            if (!platform) {
                                ps.setObject(i++, ctx.getTenantId());
                            }
                            if (statusFilter != null && !statusFilter.trim().isEmpty()
                                    && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
                                ps.setString(i, statusFilter.trim().toUpperCase(Locale.ROOT));
                            }
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    items.add(mapRow(rs));
                                }
                            }
                        }
                        Map<String, Object> out = new LinkedHashMap<String, Object>();
                        out.put("items", items);
                        out.put("scope", platform ? "PLATFORM" : "INSTITUTE");
                        if (platform) {
                            out.put("rlsNote", "Platform list uses pool-role read after permission check (interim).");
                        }
                        return out;
                    }
                };
        if (platform) {
            return withPool(load);
        }
        return ctx.executeInTransaction(db, load);
    }

    public Map<String, Object> decide(final UserSecurityContext ctx,
                                      final UUID requestId,
                                      final String decision,
                                      final String reason) {
        if (requestId == null) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "requestId is required");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "A reason is required for the decision.");
        }
        final String dec = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        if (!"APPROVE".equals(dec) && !"REJECT".equals(dec)) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "decision must be APPROVE or REJECT");
        }

        // Authz probe in tenant TX; platform load/mutate may use pool (interim RLS gap)
        final boolean[] platformHolder = new boolean[]{false};
        ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iam.request.decide");
                platformHolder[0] = isPlatformOperator(conn, ctx);
                return null;
            }
        });
        final boolean platform = platformHolder[0];

        UserSecurityContext.TransactionCallback<Map<String, Object>> action =
                new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                Map<String, Object> req = loadRequest(conn, requestId, platform ? null : ctx.getTenantId());
                if (req == null) {
                    throw new InstituteAdminException(404, "IADM03_REQUEST_NOT_FOUND", "Access request not found.");
                }
                String status = String.valueOf(req.get("status"));
                if (!"SUBMITTED".equalsIgnoreCase(status) && !"PENDING".equalsIgnoreCase(status)
                        && !"DRAFT".equalsIgnoreCase(status)) {
                    throw new InstituteAdminException(409, "IADM03_REQUEST_NOT_OPEN",
                            "That access request is not open for decision.");
                }
                UUID requester = UUID.fromString(req.get("requesterUserId").toString());
                if (requester.equals(ctx.getUserId())) {
                    throw new InstituteAdminException(403, "IADM03_SELF_APPROVE",
                            "The requester cannot approve or reject their own access request.");
                }
                UUID tenant = UUID.fromString(req.get("tenantId").toString());
                if (!platform && (ctx.getTenantId() == null || !ctx.getTenantId().equals(tenant))) {
                    throw new InstituteAdminException(403, "IADM03_CROSS_INSTITUTE",
                            "The requested institute is outside your permitted scope.");
                }

                if ("REJECT".equals(dec)) {
                    markDecision(conn, requestId, "REJECTED", reason.trim(), ctx.getUserId(), null);
                    DurableAuditWriter.append(conn, ctx.withTenant(tenant), tenant,
                            "IADM03_ACCESS_REQUEST_REJECTED", "ACCESS_REQUEST", requestId.toString(), reason.trim());
                    Map<String, Object> out = new LinkedHashMap<String, Object>();
                    out.put("requestId", requestId.toString());
                    out.put("decision", "REJECTED");
                    out.put("reason", reason.trim());
                    return out;
                }

                // APPROVE — revalidate authority then apply grant
                String role = String.valueOf(req.get("requestedRoleCode"));
                String persona = resolvePersona(conn, ctx, platform);
                // R01 is the escalation approver (US-025) and may apply institute roles from requests.
                // Direct assign ceiling still blocks R01→FACULTY outside this path.
                if (!platform) {
                    String deny = GrantorCeiling.denyReason(persona, role, false);
                    if (deny != null) {
                        auditDenial(conn, ctx, tenant, deny);
                        throw new InstituteAdminException(403, "IADM03_CEILING",
                                "Approver cannot apply this grant: " + deny);
                    }
                } else if ("R02".equals(persona)) {
                    throw new InstituteAdminException(403, "IADM03_CEILING",
                            "System Admin (R02) cannot decide access requests for tenant roles.");
                }
                UUID subject = UUID.fromString(req.get("subjectUserId").toString());
                UUID collegeId = req.get("collegeId") == null ? null
                        : UUID.fromString(req.get("collegeId").toString());

                // Apply via grant service path (nested TX risk) — do inline assign for same conn
                UUID assignmentId = applyApprovedGrant(conn, ctx, tenant, subject, role, collegeId,
                        req.get("validFrom") == null ? null : String.valueOf(req.get("validFrom")),
                        req.get("validTo") == null ? null : String.valueOf(req.get("validTo")),
                        reason.trim());

                markDecision(conn, requestId, "APPROVED", reason.trim(), ctx.getUserId(), assignmentId);
                DurableAuditWriter.append(conn, ctx.withTenant(tenant), tenant,
                        "IADM03_ACCESS_REQUEST_APPROVED", "ACCESS_REQUEST", requestId.toString(),
                        "role=" + role + "; assignment=" + assignmentId + "; " + reason.trim());

                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("requestId", requestId.toString());
                out.put("decision", "APPROVED");
                out.put("assignmentId", assignmentId == null ? null : assignmentId.toString());
                out.put("roleCode", role);
                out.put("claimsNote", CLAIMS_NOTE);
                return out;
            }
        };
        if (platform) {
            return withPool(action);
        }
        return ctx.executeInTransaction(db, action);
    }

    private UUID applyApprovedGrant(Connection conn, UserSecurityContext ctx, UUID tenant, UUID subject,
                                    String role, UUID collegeId, String validFrom, String validTo, String reason)
            throws SQLException {
        if ("INSTITUTION_ADMIN".equals(role) || "R03".equals(role)) {
            if (!isPlatformOperator(conn, ctx)) {
                throw new InstituteAdminException(403, "IADM03_CEILING",
                        "Only platform operators may approve Institution Admin grants.");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT plat.assign_institution_admin(?, ?, ?)")) {
                ps.setObject(1, tenant);
                ps.setObject(2, subject);
                ps.setObject(3, ctx.getUserId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String result = rs.getString(1);
                        if (result != null && result.toUpperCase(Locale.ROOT).startsWith("REFUSED")) {
                            throw new InstituteAdminException(403, "IADM03_APPOINT_REFUSED", result);
                        }
                    }
                }
            }
            DurableAuditWriter.append(conn, ctx.withTenant(tenant), tenant, "IADM03_ROLE_GRANTED",
                    "USER_PROFILE", subject.toString(), "Via access request approve; role=INSTITUTION_ADMIN");
            return null;
        }
        UUID roleId;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM iam.roles WHERE tenant_id = ? AND upper(code) = ? AND deleted_at IS NULL LIMIT 1")) {
            ps.setObject(1, tenant);
            ps.setString(2, role);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new InstituteAdminException(404, "IADM03_ROLE_NOT_FOUND",
                            "Role " + role + " is not available in this institute.");
                }
                roleId = (UUID) rs.getObject(1);
            }
        }
        UUID assignmentId = UUID.randomUUID();
        UserSecurityContext scoped = ctx.withTenant(tenant);
        scoped.applyClaimsAndRole(conn);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO iam.role_assignments "
                        + "(id, tenant_id, role_id, user_id, college_id, valid_from, valid_to, granted_by, "
                        + "created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, assignmentId);
            ps.setObject(2, tenant);
            ps.setObject(3, roleId);
            ps.setObject(4, subject);
            ps.setObject(5, collegeId);
            ps.setDate(6, parseDate(validFrom));
            ps.setDate(7, parseDate(validTo));
            ps.setObject(8, ctx.getUserId());
            ps.setObject(9, ctx.getUserId());
            ps.setObject(10, ctx.getUserId());
            ps.executeUpdate();
        } catch (SQLException ex) {
            if ("23505".equals(ex.getSQLState())) {
                throw new InstituteAdminException(409, "IADM03_GRANT_EXISTS",
                        "An equivalent grant already exists.");
            }
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            if (msg.contains("institute-wide administrator") || msg.contains("guard_role_assignment")) {
                throw new InstituteAdminException(403, "IADM03_ASSIGNMENT_GUARD",
                        "Database assignment guard refused this grant. An institute-wide administrator "
                                + "session is required to apply institute roles; reject the request or use a peer R03.");
            }
            throw ex;
        }
        DurableAuditWriter.append(conn, scoped, tenant, "IADM03_ROLE_GRANTED",
                "ROLE_ASSIGNMENT", assignmentId.toString(),
                "Via access request; role=" + role + "; reason=" + reason);
        return assignmentId;
    }

    private Map<String, Object> loadRequest(Connection conn, UUID id, UUID tenantFilter) throws SQLException {
        String sql = "SELECT id, tenant_id, college_id, requester_user_id, subject_user_id, requested_role_code, "
                + "justification, status, valid_from, valid_to, decided_by, decided_at, decision_reason, "
                + "resulting_assignment_id, created_at FROM iam.access_requests "
                + "WHERE id = ? AND deleted_at IS NULL "
                + (tenantFilter == null ? "" : "AND tenant_id = ?");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            if (tenantFilter != null) {
                ps.setObject(2, tenantFilter);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapRow(rs);
            }
        }
    }

    private Map<String, Object> loadRequestPool(final UUID id) {
        return withPool(new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                return loadRequest(conn, id, null);
            }
        });
    }

    private void markDecision(Connection conn, UUID id, String status, String reason, UUID actor, UUID assignmentId)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE iam.access_requests SET status = ?, decided_by = ?, decided_at = now(), "
                        + "decision_reason = ?, resulting_assignment_id = ?, updated_at = now(), updated_by = ? "
                        + "WHERE id = ? AND deleted_at IS NULL")) {
            ps.setString(1, status);
            ps.setObject(2, actor);
            ps.setString(3, reason);
            ps.setObject(4, assignmentId);
            ps.setObject(5, actor);
            ps.setObject(6, id);
            ps.executeUpdate();
        }
    }

    private Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("requestId", rs.getObject("id").toString());
        row.put("tenantId", rs.getObject("tenant_id").toString());
        Object college = rs.getObject("college_id");
        row.put("collegeId", college == null ? null : college.toString());
        row.put("requesterUserId", rs.getObject("requester_user_id").toString());
        row.put("subjectUserId", rs.getObject("subject_user_id").toString());
        row.put("requestedRoleCode", rs.getString("requested_role_code"));
        row.put("justification", rs.getString("justification"));
        row.put("status", rs.getString("status"));
        Date vf = rs.getDate("valid_from");
        Date vt = rs.getDate("valid_to");
        row.put("validFrom", vf == null ? null : vf.toString());
        row.put("validTo", vt == null ? null : vt.toString());
        Object decidedBy = rs.getObject("decided_by");
        row.put("decidedBy", decidedBy == null ? null : decidedBy.toString());
        row.put("decidedAt", rs.getTimestamp("decided_at") == null ? null
                : rs.getTimestamp("decided_at").toInstant().toString());
        row.put("decisionReason", rs.getString("decision_reason"));
        Object asg = rs.getObject("resulting_assignment_id");
        row.put("resultingAssignmentId", asg == null ? null : asg.toString());
        row.put("createdAt", rs.getTimestamp("created_at") == null ? null
                : rs.getTimestamp("created_at").toInstant().toString());
        return row;
    }

    private Map<String, Object> baseOut(UUID id, UUID tenant, UUID subject, String role, String status,
                                        String key, String corr) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("requestId", id.toString());
        out.put("tenantId", tenant.toString());
        out.put("subjectUserId", subject.toString());
        out.put("requestedRoleCode", role);
        out.put("status", status);
        out.put("idempotencyKey", key);
        out.put("correlationId", corr);
        return out;
    }

    private void assertCollegeInTenant(Connection conn, UUID collegeId, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM core.colleges WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, collegeId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new InstituteAdminException(400, "IADM03_COLLEGE_SCOPE",
                            "College must belong to the same institute as the request.");
                }
            }
        }
    }

    private Map<String, Object> findIdempotent(Connection conn, String key) throws SQLException {
        try (PreparedStatement find = conn.prepareStatement(
                "SELECT result_payload::text, status FROM iam.governance_idempotency "
                        + "WHERE idempotency_key = ? AND deleted_at IS NULL")) {
            find.setString(1, key);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next() && "SUCCEEDED".equalsIgnoreCase(rs.getString("status"))) {
                    Map<String, Object> replay = new LinkedHashMap<String, Object>();
                    replay.put("replay", Boolean.TRUE);
                    replay.put("resultPayload", rs.getString(1));
                    replay.put("idempotencyKey", key);
                    return replay;
                }
            }
        }
        return null;
    }

    private void saveIdempotent(Connection conn, String key, String op, Map<String, Object> out,
                                String corr, UUID tenantId, UUID actor) throws SQLException {
        String payload = "{\"requestId\":\"" + String.valueOf(out.get("requestId"))
                + "\",\"status\":\"" + String.valueOf(out.get("status")) + "\"}";
        try (PreparedStatement idem = conn.prepareStatement(
                "INSERT INTO iam.governance_idempotency "
                        + "(idempotency_key, operation, status, result_payload, correlation_id, tenant_id, actor_user_id, created_by) "
                        + "VALUES (?, ?, 'SUCCEEDED', ?::jsonb, ?, ?, ?, ?) "
                        + "ON CONFLICT (idempotency_key) DO UPDATE SET status = 'SUCCEEDED', "
                        + "result_payload = EXCLUDED.result_payload, updated_at = now()")) {
            idem.setString(1, key);
            idem.setString(2, op);
            idem.setString(3, payload);
            idem.setString(4, corr);
            idem.setObject(5, tenantId);
            idem.setObject(6, actor);
            idem.setObject(7, actor);
            idem.executeUpdate();
        }
    }

    private void auditDenial(Connection conn, UserSecurityContext ctx, UUID tenantId, String message)
            throws SQLException {
        try {
            DurableAuditWriter.append(conn, ctx, tenantId, "IADM03_GRANT_DENIED", "SECURITY",
                    ctx.getUserId() == null ? null : ctx.getUserId().toString(), message);
        } catch (InstituteAdminException ex) {
            logger.warn("IADM-03 denial audit unavailable: {}", ex.getMessage());
        }
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

    private String resolvePersona(Connection conn, UserSecurityContext ctx, boolean platform) throws SQLException {
        if (platform) {
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
        // Distinguish R03 vs R04 from highest assignment
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT r.code FROM iam.role_assignments ra "
                        + "JOIN iam.roles r ON r.id = ra.role_id "
                        + "WHERE ra.user_id = ? AND ra.tenant_id = ? AND ra.deleted_at IS NULL "
                        + "ORDER BY CASE r.code WHEN 'INSTITUTION_ADMIN' THEN 1 WHEN 'COLLEGE_ADMIN' THEN 2 ELSE 3 END "
                        + "LIMIT 1")) {
            ps.setObject(1, ctx.getUserId());
            ps.setObject(2, ctx.getTenantId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && "COLLEGE_ADMIN".equalsIgnoreCase(rs.getString(1))) {
                    return "R04";
                }
            }
        }
        return "R03";
    }

    private Date parseDate(String raw) {
        if (raw == null || raw.trim().isEmpty() || "null".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        try {
            return Date.valueOf(raw.trim().substring(0, Math.min(10, raw.trim().length())));
        } catch (Exception ex) {
            return null;
        }
    }

    private <T> T withPool(UserSecurityContext.TransactionCallback<T> action) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = action.execute(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                    /* ignore */
                }
                if (e instanceof InstituteAdminException) {
                    throw (InstituteAdminException) e;
                }
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException(e.getMessage(), e);
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                    /* ignore */
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
