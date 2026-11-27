package com.campx.iadm.access.service;

import com.campx.iadm.access.config.DatabaseConnectionManager;
import com.campx.iadm.access.config.Iadm02DelegationDefaultsClient;
import com.campx.iadm.access.exception.InstituteAdminException;
import com.campx.iadm.access.security.AccessPermissionMatrix;
import com.campx.iadm.access.security.UserSecurityContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-03-US-009 / US-010 — Recorded, scoped, time-bound delegation.
 * Expiry is computed on read; no existing service consumes delegation yet (partial by design).
 */
public class V2DelegationService {

    private static final int DEMO_DEFAULT_MAX_DAYS = 90;

    public Map<String, Object> createDelegation(UserSecurityContext ctx,
                                                UUID delegateUserId,
                                                String allowedAction,
                                                String targetService,
                                                String scopeTypeRaw,
                                                UUID scopeId,
                                                String startsAtIso,
                                                String expiresAtIso,
                                                String reason,
                                                String correlationId) {
        if (delegateUserId == null || allowedAction == null || allowedAction.trim().isEmpty()
                || targetService == null || targetService.trim().isEmpty()
                || reason == null || reason.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION",
                    "delegateUserId, allowedAction, targetService, and reason are required.");
        }
        if (allowedAction.contains("*") || "*".equals(targetService.trim())) {
            throw new InstituteAdminException(400, "IADM03_WILDCARD_DENIED",
                    "Wildcard delegation is not permitted.");
        }
        if (expiresAtIso == null || expiresAtIso.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_OPEN_ENDED_DENIED",
                    "Open-ended delegation without expiresAt is not permitted.");
        }

        Instant startsAt = Instant.now();
        if (startsAtIso != null && !startsAtIso.trim().isEmpty()) {
            try {
                startsAt = Instant.parse(startsAtIso.trim());
            } catch (Exception ex) {
                throw new InstituteAdminException(400, "IADM03_VALIDATION", "startsAt must be ISO-8601.");
            }
        }
        Instant expiresAt;
        try {
            expiresAt = Instant.parse(expiresAtIso.trim());
        } catch (Exception ex) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "expiresAt must be ISO-8601.");
        }
        if (!expiresAt.isAfter(startsAt)) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "expiresAt must be after startsAt.");
        }

        String scopeType = (scopeTypeRaw == null || scopeTypeRaw.trim().isEmpty())
                ? (ctx.getTenantId() != null ? "INSTITUTE" : "PLATFORM")
                : scopeTypeRaw.trim().toUpperCase(Locale.ROOT);

        // Max validity from IADM-02 if available, else DEMO ONLY default 90 days
        int maxDays = DEMO_DEFAULT_MAX_DAYS;
        String validitySource = "DEMO_ONLY_DEFAULT_90D";
        try {
            maxDays = new Iadm02DelegationDefaultsClient().requireMaxValidityDays();
            validitySource = "IADM-02:iam.delegation.max_validity_days";
        } catch (Exception ignored) {
            maxDays = DEMO_DEFAULT_MAX_DAYS;
        }
        if (expiresAt.isAfter(startsAt.plus(maxDays, ChronoUnit.DAYS))) {
            throw new InstituteAdminException(400, "IADM03_VALIDITY_EXCEEDED",
                    "Delegation validity exceeds maximum of " + maxDays + " days (" + validitySource + ").");
        }

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                String persona = CatalogueReadService.resolveCallerPersona(conn, ctx);
                if (!AccessPermissionMatrix.allowsIadm03(persona, "iadm03.delegation.manage")) {
                    DurableAuditWriter.appendWithRefs(conn, ctx, ctx.getTenantId(),
                            "IADM03_DELEGATION_DENIED", scopeLabel(ctx), "DELEGATION", null,
                            allowedAction, "403", correlationId, "Delegation create denied for " + persona);
                    conn.commit();
                    throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                            "Role " + persona + " cannot create delegations.");
                }
                if (delegateUserId.equals(ctx.getUserId())) {
                    DurableAuditWriter.appendWithRefs(conn, ctx, ctx.getTenantId(),
                            "IADM03_SELF_DELEGATION_DENIED", scopeLabel(ctx), "DELEGATION", null,
                            allowedAction, "403", correlationId, "Self-delegation refused");
                    conn.commit();
                    throw new InstituteAdminException(403, "IADM03_SELF_GRANT_DENIED",
                            "Nobody may delegate to themselves.");
                }

                UUID effectiveTenantId = ctx.getTenantId();
                if ("R03".equals(persona)) {
                    if (effectiveTenantId == null) {
                        throw new InstituteAdminException(403, "IADM03_SCOPE_REQUIRED",
                                "Institution Admin requires an institute scope.");
                    }
                    if ("PLATFORM".equals(scopeType) || (scopeId != null && !scopeId.equals(effectiveTenantId))) {
                        DurableAuditWriter.appendWithRefs(conn, ctx, effectiveTenantId,
                                "IADM03_SCOPE_ESCALATION_DENIED", "INSTITUTE:" + effectiveTenantId,
                                "DELEGATION", null, scopeType, "403", correlationId,
                                "R03 cannot delegate outside own institute");
                        conn.commit();
                        throw new InstituteAdminException(403, "IADM03_SCOPE_ESCALATION_DENIED",
                                "Delegation scope cannot be wider than delegator's institute.");
                    }
                } else if (scopeId != null && "INSTITUTE".equals(scopeType)) {
                    effectiveTenantId = scopeId;
                }

                UUID delegationId = UUID.randomUUID();
                UUID auditId = DurableAuditWriter.appendWithRefs(conn, ctx, effectiveTenantId,
                        "IADM03_DELEGATION_CREATED",
                        effectiveTenantId != null ? "INSTITUTE:" + effectiveTenantId : "PLATFORM",
                        "DELEGATION", delegationId.toString(),
                        null, "ACTIVE:" + allowedAction.trim(), correlationId,
                        "Recorded scoped delegation created");

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO iadm03.delegations " +
                                "(id, tenant_id, delegator_user_id, delegate_user_id, allowed_action, target_service, " +
                                "scope_type, scope_id, starts_at, expires_at, reason, status, audit_event_id) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)")) {
                    ps.setObject(1, delegationId);
                    ps.setObject(2, effectiveTenantId);
                    ps.setObject(3, ctx.getUserId());
                    ps.setObject(4, delegateUserId);
                    ps.setString(5, allowedAction.trim());
                    ps.setString(6, targetService.trim());
                    ps.setString(7, scopeType);
                    ps.setObject(8, scopeId != null ? scopeId : effectiveTenantId);
                    ps.setTimestamp(9, Timestamp.from(startsAt));
                    ps.setTimestamp(10, Timestamp.from(expiresAt));
                    ps.setString(11, reason.trim());
                    ps.setObject(12, auditId);
                    ps.executeUpdate();
                }

                conn.commit();
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", delegationId.toString());
                out.put("status", "ACTIVE");
                out.put("validitySource", validitySource);
                out.put("maxValidityDays", maxDays);
                out.put("startsAt", startsAt.toString());
                out.put("expiresAt", expiresAt.toString());
                out.put("auditEventId", auditId.toString());
                return out;
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new InstituteAdminException(500, "IADM03_DB_ERROR", "Unable to create delegation.");
        }
    }

    public Map<String, Object> revokeDelegation(UserSecurityContext ctx,
                                                UUID delegationId,
                                                String reason,
                                                String correlationId) {
        if (delegationId == null) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "delegationId is required.");
        }
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                String persona = CatalogueReadService.resolveCallerPersona(conn, ctx);
                if (!("R01".equals(persona) || "R03".equals(persona))) {
                    DurableAuditWriter.appendWithRefs(conn, ctx, ctx.getTenantId(),
                            "IADM03_DELEGATION_REVOKE_DENIED", scopeLabel(ctx), "DELEGATION", delegationId.toString(),
                            null, "403", correlationId, "Delegation revoke denied for " + persona);
                    conn.commit();
                    throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                            "Only R01 or the delegator may revoke a delegation.");
                }

                UUID delegator = null;
                UUID rowTenant = null;
                String currentStatus = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT delegator_user_id, tenant_id, status FROM iadm03.delegations WHERE id = ?")) {
                    ps.setObject(1, delegationId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new InstituteAdminException(404, "IADM03_NOT_FOUND", "Delegation not found.");
                        }
                        delegator = (UUID) rs.getObject("delegator_user_id");
                        rowTenant = (UUID) rs.getObject("tenant_id");
                        currentStatus = rs.getString("status");
                    }
                }
                if ("R03".equals(persona) && !ctx.getUserId().equals(delegator)) {
                    throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                            "R03 can only revoke their own delegation.");
                }

                UUID auditId = DurableAuditWriter.appendWithRefs(conn, ctx, rowTenant,
                        "IADM03_DELEGATION_REVOKED",
                        rowTenant != null ? "INSTITUTE:" + rowTenant : "PLATFORM",
                        "DELEGATION", delegationId.toString(),
                        currentStatus, "REVOKED", correlationId,
                        reason == null ? "Delegation revoked" : reason.trim());

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE iadm03.delegations SET status = 'REVOKED', revoked_by = ?, revoked_at = now(), " +
                                "revoke_reason = ? WHERE id = ?")) {
                    ps.setObject(1, ctx.getUserId());
                    ps.setString(2, reason == null ? "Revoked by operator" : reason.trim());
                    ps.setObject(3, delegationId);
                    ps.executeUpdate();
                }

                conn.commit();
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", delegationId.toString());
                out.put("status", "REVOKED");
                out.put("valid", Boolean.FALSE);
                out.put("auditEventId", auditId.toString());
                return out;
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new InstituteAdminException(500, "IADM03_DB_ERROR", "Unable to revoke delegation.");
        }
    }

    public Map<String, Object> listDelegations(UserSecurityContext ctx) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                String persona = CatalogueReadService.resolveCallerPersona(conn, ctx);
                if (!("R01".equals(persona) || "R02".equals(persona) || "R03".equals(persona) || "R28".equals(persona))) {
                    throw new InstituteAdminException(403, "IADM03_FORBIDDEN", "Not permitted to view delegations.");
                }
                StringBuilder sql = new StringBuilder(
                        "SELECT id, tenant_id, delegator_user_id, delegate_user_id, allowed_action, target_service, " +
                                "scope_type, scope_id, starts_at, expires_at, reason, status, revoked_at " +
                                "FROM iadm03.delegations WHERE 1=1 ");
                if ("R03".equals(persona)) {
                    sql.append("AND tenant_id = ? ");
                }
                sql.append("ORDER BY created_at DESC LIMIT 100");
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    if ("R03".equals(persona)) {
                        ps.setObject(1, ctx.getTenantId());
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            items.add(mapDelegation(rs));
                        }
                    }
                }
                conn.commit();
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("count", items.size());
                out.put("items", items);
                return out;
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new InstituteAdminException(500, "IADM03_DB_ERROR", "Unable to list delegations.");
        }
    }

    public Map<String, Object> getDelegationStatus(UserSecurityContext ctx, UUID delegationId) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, tenant_id, delegator_user_id, delegate_user_id, allowed_action, target_service, " +
                                "scope_type, scope_id, starts_at, expires_at, reason, status, revoked_at " +
                                "FROM iadm03.delegations WHERE id = ?")) {
                    ps.setObject(1, delegationId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new InstituteAdminException(404, "IADM03_NOT_FOUND", "Delegation not found.");
                        }
                        Map<String, Object> row = mapDelegation(rs);
                        conn.commit();
                        return row;
                    }
                }
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new InstituteAdminException(500, "IADM03_DB_ERROR", "Unable to check delegation status.");
        }
    }

    private static Map<String, Object> mapDelegation(ResultSet rs) throws SQLException {
        String rawStatus = rs.getString("status");
        Timestamp exp = rs.getTimestamp("expires_at");
        Timestamp starts = rs.getTimestamp("starts_at");
        Instant now = Instant.now();
        String effectiveStatus = rawStatus;
        if ("ACTIVE".equals(rawStatus) && exp != null && !exp.toInstant().isAfter(now)) {
            effectiveStatus = "EXPIRED";
        }
        boolean valid = "ACTIVE".equals(effectiveStatus) && (starts == null || !starts.toInstant().isAfter(now));

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", String.valueOf(rs.getObject("id")));
        m.put("tenantId", rs.getObject("tenant_id") == null ? null : String.valueOf(rs.getObject("tenant_id")));
        m.put("delegatorUserId", String.valueOf(rs.getObject("delegator_user_id")));
        m.put("delegateUserId", String.valueOf(rs.getObject("delegate_user_id")));
        m.put("allowedAction", rs.getString("allowed_action"));
        m.put("targetService", rs.getString("target_service"));
        m.put("scopeType", rs.getString("scope_type"));
        m.put("startsAt", starts == null ? null : starts.toInstant().toString());
        m.put("expiresAt", exp == null ? null : exp.toInstant().toString());
        m.put("reason", rs.getString("reason"));
        m.put("status", effectiveStatus);
        m.put("valid", Boolean.valueOf(valid));
        return m;
    }

    private static String scopeLabel(UserSecurityContext ctx) {
        return ctx != null && ctx.getTenantId() != null ? "INSTITUTE:" + ctx.getTenantId() : "PLATFORM";
    }
}
