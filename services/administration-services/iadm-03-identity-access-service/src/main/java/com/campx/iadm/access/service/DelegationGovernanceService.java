package com.campx.iadm.access.service;

import com.campx.iadm.access.config.DatabaseConnectionManager;
import com.campx.iadm.access.config.Iadm02DelegationDefaultsClient;
import com.campx.iadm.access.exception.InstituteAdminException;
import com.campx.iadm.access.exception.UserProfileAccessDeniedException;
import com.campx.iadm.access.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * IADM-03 S3 — create/revoke delegations on existing {@code iam.delegations} (US-026/027/029).
 * Active-check for STM keys remains on Path C {@code :8081} (US-028 reuse).
 */
public class DelegationGovernanceService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(DelegationGovernanceService.class);
    private static final String CLAIMS_NOTE =
            "Authoritative checks use DB + iam.has_permission immediately. Gateway identity cache may lag up to 5 minutes (US-021 DEV SLA).";

    private final DatabaseConnectionManager db;
    private final Iadm02DelegationDefaultsClient defaultsClient;

    public DelegationGovernanceService() {
        this(DatabaseConnectionManager.getInstance(), new Iadm02DelegationDefaultsClient());
    }

    public DelegationGovernanceService(DatabaseConnectionManager db, Iadm02DelegationDefaultsClient defaultsClient) {
        this.db = db == null ? DatabaseConnectionManager.getInstance() : db;
        this.defaultsClient = defaultsClient == null ? new Iadm02DelegationDefaultsClient() : defaultsClient;
    }

    public Map<String, Object> createDelegation(final UserSecurityContext ctx,
                                                final UUID toUserId,
                                                final String roleCode,
                                                final String validFromIso,
                                                final String validToIso,
                                                final String reason,
                                                final String idempotencyKey,
                                                final String correlationId) {
        if (ctx.getTenantId() == null) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "tenantId is required for institute delegations.");
        }
        if (toUserId == null) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "toUserId (delegate) is required");
        }
        if (roleCode == null || roleCode.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "roleCode is required");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "A reason is required for delegation.");
        }
        if (toUserId.equals(ctx.getUserId())) {
            throw new InstituteAdminException(403, "IADM03_CEILING", "Nobody may edit their own grants or self-delegate.");
        }
        final String role = roleCode.trim().toUpperCase(Locale.ROOT);
        final String key = idempotencyKey == null || idempotencyKey.trim().isEmpty()
                ? UUID.randomUUID().toString() : idempotencyKey.trim();
        final String corr = correlationId == null || correlationId.trim().isEmpty()
                ? UUID.randomUUID().toString() : correlationId.trim();

        // US-029 — fail closed if IADM-02 default unavailable
        final int maxDays = defaultsClient.requireMaxValidityDays();

        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iam.delegation.create");
                Map<String, Object> replay = findIdempotent(conn, key);
                if (replay != null) {
                    return replay;
                }

                long now = System.currentTimeMillis();
                long fromMs = parseInstant(validFromIso, now);
                long toMs = parseInstant(validToIso, now + TimeUnit.DAYS.toMillis(maxDays));
                if (toMs <= fromMs) {
                    throw new InstituteAdminException(400, "IADM03_VALIDATION", "validTo must be after validFrom.");
                }
                long maxMs = fromMs + TimeUnit.DAYS.toMillis(maxDays);
                if (toMs > maxMs) {
                    throw new InstituteAdminException(400, "IADM03_DELEGATION_VALIDITY",
                            "Delegation validity cannot exceed the platform default of " + maxDays
                                    + " days (iam.delegation.max_validity_days).");
                }

                assertDelegatorHoldsRole(conn, ctx.getUserId(), role, ctx.getTenantId());
                UUID roleId = resolveRoleId(conn, role, ctx.getTenantId());
                UUID id = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO iam.delegations "
                                + "(id, tenant_id, from_user_id, to_user_id, role_id, valid_from, valid_to, reason, status, "
                                + "created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)")) {
                    ps.setObject(1, id);
                    ps.setObject(2, ctx.getTenantId());
                    ps.setObject(3, ctx.getUserId());
                    ps.setObject(4, toUserId);
                    ps.setObject(5, roleId);
                    ps.setTimestamp(6, new Timestamp(fromMs));
                    ps.setTimestamp(7, new Timestamp(toMs));
                    ps.setString(8, reason.trim());
                    ps.setObject(9, ctx.getUserId());
                    ps.setObject(10, ctx.getUserId());
                    ps.executeUpdate();
                } catch (SQLException ex) {
                    String msg = ex.getMessage() == null ? "" : ex.getMessage();
                    if (msg.contains("you can only delegate a role you hold")) {
                        throw new InstituteAdminException(403, "IADM03_DELEGATION_AUTHORITY",
                                "You can only delegate a role you currently hold (database guard).");
                    }
                    throw ex;
                }

                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(), "IADM03_DELEGATION_CREATED",
                        "DELEGATION", id.toString(),
                        "role=" + role + "; to=" + toUserId + "; maxDays=" + maxDays + "; reason=" + reason.trim());

                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("delegationId", id.toString());
                out.put("roleCode", role);
                out.put("toUserId", toUserId.toString());
                out.put("fromUserId", ctx.getUserId().toString());
                out.put("status", "ACTIVE");
                out.put("maxValidityDays", Integer.valueOf(maxDays));
                out.put("validFrom", new Timestamp(fromMs).toInstant().toString());
                out.put("validTo", new Timestamp(toMs).toInstant().toString());
                out.put("idempotencyKey", key);
                out.put("correlationId", corr);
                out.put("claimsNote", CLAIMS_NOTE);
                out.put("activeCheckNote",
                        "Consumers continue Path C GET /api/v1/admin/internal/delegations/active-check on :8081 (US-028); IADM-03 is not on the per-request path.");
                out.put("replay", Boolean.FALSE);
                saveIdempotent(conn, key, "DELEGATION_CREATE", out, corr, ctx.getTenantId(), ctx.getUserId());
                logger.info("IADM-03 delegation created id={} role={}", id, role);
                return out;
            }
        });
    }

    public Map<String, Object> revokeDelegation(final UserSecurityContext ctx,
                                                final UUID delegationId,
                                                final String reason,
                                                final String idempotencyKey,
                                                final String correlationId) {
        if (delegationId == null) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "delegationId is required");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "A reason is required for revoke.");
        }
        final String key = idempotencyKey == null || idempotencyKey.trim().isEmpty()
                ? UUID.randomUUID().toString() : idempotencyKey.trim();
        final String corr = correlationId == null || correlationId.trim().isEmpty()
                ? UUID.randomUUID().toString() : correlationId.trim();

        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iam.delegation.revoke");
                Map<String, Object> replay = findIdempotent(conn, key);
                if (replay != null) {
                    return replay;
                }
                boolean platform = isPlatformOperator(conn, ctx);
                try (PreparedStatement find = conn.prepareStatement(
                        "SELECT from_user_id, tenant_id, status FROM iam.delegations "
                                + "WHERE id = ? AND deleted_at IS NULL"
                                + (platform ? "" : " AND tenant_id = ?"))) {
                    find.setObject(1, delegationId);
                    if (!platform) {
                        find.setObject(2, ctx.getTenantId());
                    }
                    try (ResultSet rs = find.executeQuery()) {
                        if (!rs.next()) {
                            throw new InstituteAdminException(404, "IADM03_DELEGATION_NOT_FOUND", "Delegation not found.");
                        }
                        UUID from = (UUID) rs.getObject("from_user_id");
                        if (!platform && (from == null || !from.equals(ctx.getUserId()))) {
                            throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                                    "Only the delegator (or platform) may revoke this delegation.");
                        }
                        if ("REVOKED".equalsIgnoreCase(rs.getString("status"))) {
                            throw new InstituteAdminException(409, "IADM03_DELEGATION_REVOKED",
                                    "Delegation is already revoked.");
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE iam.delegations SET status = 'REVOKED', updated_at = now(), updated_by = ? "
                                + "WHERE id = ? AND deleted_at IS NULL")) {
                    ps.setObject(1, ctx.getUserId());
                    ps.setObject(2, delegationId);
                    if (ps.executeUpdate() == 0) {
                        throw new InstituteAdminException(404, "IADM03_DELEGATION_NOT_FOUND", "Delegation not found.");
                    }
                }
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(), "IADM03_DELEGATION_REVOKED",
                        "DELEGATION", delegationId.toString(), reason.trim());
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("delegationId", delegationId.toString());
                out.put("status", "REVOKED");
                out.put("idempotencyKey", key);
                out.put("correlationId", corr);
                out.put("claimsNote", CLAIMS_NOTE);
                out.put("replay", Boolean.FALSE);
                saveIdempotent(conn, key, "DELEGATION_REVOKE", out, corr, ctx.getTenantId(), ctx.getUserId());
                return out;
            }
        });
    }

    public Map<String, Object> listDelegations(final UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iam.delegation.create");
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                boolean platform = isPlatformOperator(conn, ctx);
                String sql = "SELECT d.id, d.from_user_id, d.to_user_id, r.code AS role_code, d.valid_from, d.valid_to, "
                        + "d.reason, d.status, d.tenant_id FROM iam.delegations d "
                        + "JOIN iam.roles r ON r.id = d.role_id "
                        + "WHERE d.deleted_at IS NULL "
                        + (platform ? "" : "AND d.tenant_id = ? ")
                        + "ORDER BY d.created_at DESC LIMIT 100";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (!platform) {
                        ps.setObject(1, ctx.getTenantId());
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<String, Object>();
                            row.put("delegationId", rs.getObject("id").toString());
                            row.put("fromUserId", rs.getObject("from_user_id").toString());
                            row.put("toUserId", rs.getObject("to_user_id").toString());
                            row.put("roleCode", rs.getString("role_code"));
                            row.put("validFrom", rs.getTimestamp("valid_from") == null ? null
                                    : rs.getTimestamp("valid_from").toInstant().toString());
                            row.put("validTo", rs.getTimestamp("valid_to") == null ? null
                                    : rs.getTimestamp("valid_to").toInstant().toString());
                            row.put("reason", rs.getString("reason"));
                            row.put("status", rs.getString("status"));
                            row.put("tenantId", rs.getObject("tenant_id").toString());
                            items.add(row);
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                out.put("activeCheckPath", "/api/v1/admin/internal/delegations/active-check");
                return out;
            }
        });
    }

    public Map<String, Object> delegationContract() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("store", "iam.delegations");
        out.put("maxValidityKey", Iadm02DelegationDefaultsClient.KEY);
        out.put("activeCheck", "Path C /api/v1/admin/internal/delegations/active-check on institute-admin :8081");
        out.put("iadm03OnRequestPath", Boolean.FALSE);
        try {
            out.put("maxValidityDays", Integer.valueOf(defaultsClient.requireMaxValidityDays()));
            out.put("defaultsStatus", "AVAILABLE");
        } catch (InstituteAdminException ex) {
            out.put("maxValidityDays", null);
            out.put("defaultsStatus", "UNAVAILABLE");
            out.put("defaultsError", ex.getErrorCode());
        }
        return out;
    }

    private void assertDelegatorHoldsRole(Connection conn, UUID grantorId, String roleCode, UUID tenantId)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM iam.role_assignments ra "
                        + "JOIN iam.roles r ON r.id = ra.role_id "
                        + "WHERE ra.user_id = ? AND ra.tenant_id = ? AND upper(r.code) = ? "
                        + "AND ra.deleted_at IS NULL "
                        + "AND (ra.valid_to IS NULL OR ra.valid_to >= CURRENT_DATE) LIMIT 1")) {
            ps.setObject(1, grantorId);
            ps.setObject(2, tenantId);
            ps.setString(3, roleCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }
        // R03 may delegate REGISTRAR / R31-style roles they govern without holding REGISTRAR themselves
        // if they hold INSTITUTION_ADMIN — ceiling already limited role codes at UI; require IA grant.
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM iam.role_assignments ra "
                        + "JOIN iam.roles r ON r.id = ra.role_id "
                        + "WHERE ra.user_id = ? AND ra.tenant_id = ? AND r.code = 'INSTITUTION_ADMIN' "
                        + "AND ra.deleted_at IS NULL LIMIT 1")) {
            ps.setObject(1, grantorId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && isDelegatableByR03(roleCode)) {
                    return;
                }
            }
        }
        throw new InstituteAdminException(403, "IADM03_DELEGATION_AUTHORITY",
                "Delegator must hold the authority being delegated (or INSTITUTION_ADMIN for eligible campus roles).");
    }

    private static boolean isDelegatableByR03(String role) {
        return "REGISTRAR".equals(role) || "R31".equals(role) || "COLLEGE_ADMIN".equals(role)
                || "FACULTY".equals(role) || "HOD".equals(role);
    }

    private UUID resolveRoleId(Connection conn, String roleCode, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM iam.roles WHERE tenant_id = ? AND upper(code) = ? AND deleted_at IS NULL LIMIT 1")) {
            ps.setObject(1, tenantId);
            ps.setString(2, roleCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new InstituteAdminException(404, "IADM03_ROLE_NOT_FOUND",
                            "Role " + roleCode + " is not available in this institute.");
                }
                return (UUID) rs.getObject(1);
            }
        }
    }

    private long parseInstant(String raw, long fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        try {
            if (raw.length() <= 10) {
                return java.sql.Date.valueOf(raw.trim()).getTime();
            }
            return Timestamp.from(java.time.Instant.parse(raw.trim())).getTime();
        } catch (Exception ex) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "Invalid date/time: " + raw);
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
        String payload = "{\"delegationId\":\"" + String.valueOf(out.get("delegationId"))
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
}
