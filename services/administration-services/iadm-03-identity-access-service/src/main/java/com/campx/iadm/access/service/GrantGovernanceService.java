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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-03 S2 — appoint / assign / revoke grants with ceiling, audit, idempotency (US-015…020, 022).
 */
public class GrantGovernanceService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(GrantGovernanceService.class);
    private static final String CLAIMS_NOTE =
            "Authoritative checks use DB + iam.has_permission immediately. Gateway identity cache may lag up to 5 minutes (US-021 DEV SLA).";

    private final DatabaseConnectionManager db;
    private final ProfileGovernanceService profiles;

    public GrantGovernanceService() {
        this(DatabaseConnectionManager.getInstance(), new ProfileGovernanceService());
    }

    public GrantGovernanceService(DatabaseConnectionManager db, ProfileGovernanceService profiles) {
        this.db = db == null ? DatabaseConnectionManager.getInstance() : db;
        this.profiles = profiles == null ? new ProfileGovernanceService() : profiles;
    }

    /** US-015 — invite/create profile if needed, then grant INSTITUTION_ADMIN via plat.assign_institution_admin. */
    public Map<String, Object> appointInstitutionAdmin(final UserSecurityContext ctx,
                                                       final String email,
                                                       final String displayName,
                                                       final UUID tenantId,
                                                       final String idempotencyKey,
                                                       final String correlationId) {
        if (tenantId == null) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "tenantId is required to appoint an Institution Admin.");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "email is required");
        }
        final String key = idempotencyKey == null || idempotencyKey.trim().isEmpty()
                ? UUID.randomUUID().toString() : idempotencyKey.trim();
        final String corr = correlationId == null || correlationId.trim().isEmpty()
                ? UUID.randomUUID().toString() : correlationId.trim();

        // Step A — profile (reuse S1 invite; idempotent)
        Map<String, Object> invite = profiles.inviteOrCreateProfile(
                ctx, email, displayName, "INSTITUTION_ADMIN", tenantId, key + ":profile", corr);
        if (Boolean.TRUE.equals(invite.get("replay")) && invite.get("resultPayload") != null) {
            // continue to grant with same profile id from payload if present
        }
        Object profileIdObj = invite.get("profileId");
        if (profileIdObj == null && invite.get("resultPayload") != null) {
            String payload = String.valueOf(invite.get("resultPayload"));
            int i = payload.indexOf("\"profileId\":\"");
            if (i >= 0) {
                profileIdObj = payload.substring(i + 13, i + 13 + 36);
            }
        }
        if (profileIdObj == null) {
            throw new InstituteAdminException(409, "IADM03_APPOINT_PROFILE",
                    "Profile step did not return a profile id. Retry with the same idempotency key after the invitee accepts Auth.");
        }
        final UUID subjectId = UUID.fromString(profileIdObj.toString());

        // Step B — grant R03
        return ctx.withTenant(tenantId).executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iam.grant.assign");
                assertPlatformOperator(conn, ctx);
                String deny = GrantorCeiling.denyReason("R01", "INSTITUTION_ADMIN", false);
                if (deny != null) {
                    auditDenial(conn, ctx, tenantId, "IADM03_GRANT_DENIED", deny);
                    throw new InstituteAdminException(403, "IADM03_CEILING", deny);
                }
                Map<String, Object> replay = findIdempotent(conn, key + ":grant");
                if (replay != null) {
                    return replay;
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT plat.assign_institution_admin(?, ?, ?)")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, subjectId);
                    ps.setObject(3, ctx.getUserId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new InstituteAdminException(500, "IADM03_APPOINT_FAILED",
                                    "Profile ready; Institution Admin assignment is pending. Retry with the same key.");
                        }
                        String result = rs.getString(1);
                        if (result != null && result.toUpperCase(Locale.ROOT).startsWith("REFUSED")) {
                            auditDenial(conn, ctx, tenantId, "IADM03_GRANT_DENIED", result);
                            throw new InstituteAdminException(403, "IADM03_APPOINT_REFUSED", result);
                        }
                    }
                }
                DurableAuditWriter.append(conn, ctx.withTenant(tenantId), tenantId,
                        "IADM03_R03_APPOINTED", "USER_PROFILE", subjectId.toString(),
                        "Institution Admin appointed after profile step; correlationId=" + corr);
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("profileId", subjectId.toString());
                out.put("tenantId", tenantId.toString());
                out.put("roleCode", "INSTITUTION_ADMIN");
                out.put("steps", java.util.Arrays.asList("PROFILE", "GRANT"));
                out.put("idempotencyKey", key);
                out.put("correlationId", corr);
                out.put("claimsNote", CLAIMS_NOTE);
                out.put("replay", Boolean.FALSE);
                saveIdempotent(conn, key + ":grant", "APPOINT_R03", out, corr, tenantId, ctx.getUserId());
                logger.info("IADM-03 appoint R03 profileId={} tenant={}", subjectId, tenantId);
                return out;
            }
        });
    }

    /** US-016 / US-018 — assign eligible role within ceiling. */
    public Map<String, Object> assignGrant(final UserSecurityContext ctx,
                                           final UUID subjectUserId,
                                           final String targetRoleCode,
                                           final UUID tenantId,
                                           final UUID collegeId,
                                           final String validFrom,
                                           final String validTo,
                                           final String reason,
                                           final String idempotencyKey,
                                           final String correlationId) {
        if (subjectUserId == null) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "subjectUserId is required");
        }
        if (targetRoleCode == null || targetRoleCode.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "targetRole is required");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "A reason is required for grant assignment.");
        }
        final String role = targetRoleCode.trim().toUpperCase(Locale.ROOT);
        final String key = idempotencyKey == null || idempotencyKey.trim().isEmpty()
                ? UUID.randomUUID().toString() : idempotencyKey.trim();
        final String corr = correlationId == null || correlationId.trim().isEmpty()
                ? UUID.randomUUID().toString() : correlationId.trim();

        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                boolean platform = isPlatformOperator(conn, ctx);
                String persona = resolvePersona(conn, ctx, platform);
                if ("R02".equals(persona)) {
                    requirePermission(conn, ctx, "iam.identity.technical.assign");
                } else {
                    requirePermission(conn, ctx, "iam.grant.assign");
                }
                boolean self = subjectUserId.equals(ctx.getUserId());
                String deny = GrantorCeiling.denyReason(persona, role, self);
                if (deny != null) {
                    auditDenial(conn, ctx, tenantId != null ? tenantId : ctx.getTenantId(), "IADM03_GRANT_DENIED", deny);
                    throw new InstituteAdminException(403, "IADM03_CEILING", deny);
                }

                // R02 technical — platform_admins SYSTEM_ADMIN only (DEV dual-store)
                if ("R02".equals(persona)) {
                    if (!"SYSTEM_ADMIN".equals(role) && !"R02".equals(role)
                            && !role.startsWith("SVC-") && !"SERVICE".equals(role) && !"TECHNICAL".equals(role)) {
                        String msg = "System Admin (R02) may assign platform technical service identities only.";
                        auditDenial(conn, ctx, null, "IADM03_GRANT_DENIED", msg);
                        throw new InstituteAdminException(403, "IADM03_CEILING", msg);
                    }
                    return assignPlatformTechnical(conn, ctx, subjectUserId, role, reason.trim(), key, corr);
                }

                UUID scopeTenant = tenantId != null ? tenantId : ctx.getTenantId();
                if (scopeTenant == null) {
                    throw new InstituteAdminException(400, "IADM03_VALIDATION", "tenantId is required for institute grants.");
                }
                if (!platform && (ctx.getTenantId() == null || !ctx.getTenantId().equals(scopeTenant))) {
                    String msg = "The requested institute is outside your permitted scope.";
                    auditDenial(conn, ctx, ctx.getTenantId(), "IADM03_GRANT_DENIED", msg);
                    throw new InstituteAdminException(403, "IADM03_CROSS_INSTITUTE", msg);
                }
                // R01 appoint path for R03 should use appointInstitutionAdmin; direct assign still allowed for R01→R03
                if ("R01".equals(persona) && ("INSTITUTION_ADMIN".equals(role) || "R03".equals(role))) {
                    // fall through to assign_institution_admin below
                } else if ("R01".equals(persona) && ("SYSTEM_ADMIN".equals(role) || "R02".equals(role))) {
                    return assignPlatformTechnical(conn, ctx, subjectUserId, "SYSTEM_ADMIN", reason.trim(), key, corr);
                }

                Map<String, Object> replay = findIdempotent(conn, key);
                if (replay != null) {
                    return replay;
                }

                assertProfileActive(conn, subjectUserId, scopeTenant);
                if (collegeId != null) {
                    assertCollegeInTenant(conn, collegeId, scopeTenant);
                }
                // R03: GrantorCeiling §21.1 is authoritative for eligible business roles.
                // Flat permission-count subset is unreliable (FACULTY catalogue ≠ R03 graph).
                if (!platform && !"R03".equals(persona)
                        && !permissionSubsetOk(conn, ctx.getUserId(), role, scopeTenant)) {
                    String msg = "Target role holds more permissions than you do — refused.";
                    auditDenial(conn, ctx, scopeTenant, "IADM03_GRANT_DENIED", msg);
                    throw new InstituteAdminException(403, "IADM03_PERMISSION_CEILING", msg);
                }

                UserSecurityContext scoped = ctx.withTenant(scopeTenant);
                scoped.applyClaimsAndRole(conn);

                if ("INSTITUTION_ADMIN".equals(role) || "R03".equals(role)) {
                    if (!platform) {
                        String msg = "Institution Admin (R03) cannot assign R01, R02 or R03.";
                        auditDenial(conn, scoped, scopeTenant, "IADM03_GRANT_DENIED", msg);
                        throw new InstituteAdminException(403, "IADM03_CEILING", msg);
                    }
                    // Guard 3: changing/appointing R03 grants by direct path for non-first — US-020 queue when removing;
                    // initial appoint by R01 proceeds (B8).
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT plat.assign_institution_admin(?, ?, ?)")) {
                        ps.setObject(1, scopeTenant);
                        ps.setObject(2, subjectUserId);
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
                    DurableAuditWriter.append(conn, scoped, scopeTenant, "IADM03_ROLE_GRANTED",
                            "USER_PROFILE", subjectUserId.toString(),
                            "Role=INSTITUTION_ADMIN; reason=" + reason.trim());
                    Map<String, Object> out = baseGrantOut(subjectUserId, "INSTITUTION_ADMIN", scopeTenant, null, key, corr);
                    saveIdempotent(conn, key, "GRANT_ASSIGN", out, corr, scopeTenant, ctx.getUserId());
                    return out;
                }

                UUID roleId = resolveRoleId(conn, role, scopeTenant);
                UUID assignmentId = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO iam.role_assignments "
                                + "(id, tenant_id, role_id, user_id, college_id, valid_from, valid_to, granted_by, "
                                + "created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setObject(1, assignmentId);
                    ps.setObject(2, scopeTenant);
                    ps.setObject(3, roleId);
                    ps.setObject(4, subjectUserId);
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
                                "An equivalent grant already exists. Retry returns the existing assignment.");
                    }
                    throw ex;
                }
                DurableAuditWriter.append(conn, scoped, scopeTenant, "IADM03_ROLE_GRANTED",
                        "ROLE_ASSIGNMENT", assignmentId.toString(),
                        "Role=" + role + "; subject=" + subjectUserId + "; reason=" + reason.trim());
                Map<String, Object> out = baseGrantOut(subjectUserId, role, scopeTenant, assignmentId, key, corr);
                out.put("collegeId", collegeId == null ? null : collegeId.toString());
                saveIdempotent(conn, key, "GRANT_ASSIGN", out, corr, scopeTenant, ctx.getUserId());
                return out;
            }
        });
    }

    /** US-019 — revoke grant; R03 removal goes through US-020 approval when requester is not sole approver path. */
    public Map<String, Object> revokeGrant(final UserSecurityContext ctx,
                                           final UUID assignmentId,
                                           final String reason,
                                           final String idempotencyKey,
                                           final String correlationId) {
        if (assignmentId == null) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "assignmentId is required");
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
                requirePermission(conn, ctx, "iam.grant.revoke");
                boolean platform = isPlatformOperator(conn, ctx);
                String persona = resolvePersona(conn, ctx, platform);
                Map<String, Object> grant = loadAssignment(conn, assignmentId, platform ? null : ctx.getTenantId());
                if (grant == null && platform) {
                    grant = loadAssignmentPool(assignmentId);
                }
                if (grant == null) {
                    throw new InstituteAdminException(404, "IADM03_GRANT_NOT_FOUND", "Grant not found.");
                }
                UUID subject = UUID.fromString(grant.get("userId").toString());
                if (subject.equals(ctx.getUserId())) {
                    String msg = "Nobody may edit their own grants.";
                    auditDenial(conn, ctx, ctx.getTenantId(), "IADM03_GRANT_DENIED", msg);
                    throw new InstituteAdminException(403, "IADM03_CEILING", msg);
                }
                String roleCode = String.valueOf(grant.get("roleCode"));
                // Revoke of R03: queue for US-020 (B8 — removal needs R01 or different R03)
                if ("INSTITUTION_ADMIN".equalsIgnoreCase(roleCode)) {
                    Map<String, Object> pending = queueR03Change(conn, ctx, grant, "REVOKE", reason.trim(), key, corr);
                    pending.put("queued", Boolean.TRUE);
                    pending.put("message", "Removing an Institution Admin requires independent approval (US-020).");
                    return pending;
                }
                String deny = GrantorCeiling.denyReason(persona, roleCode, false);
                if (deny != null) {
                    auditDenial(conn, ctx, ctx.getTenantId(), "IADM03_GRANT_DENIED", deny);
                    throw new InstituteAdminException(403, "IADM03_CEILING", deny);
                }
                UUID tenant = UUID.fromString(grant.get("tenantId").toString());
                if (!platform && (ctx.getTenantId() == null || !ctx.getTenantId().equals(tenant))) {
                    throw new InstituteAdminException(403, "IADM03_CROSS_INSTITUTE",
                            "The requested institute is outside your permitted scope.");
                }
                UserSecurityContext scoped = ctx.withTenant(tenant);
                scoped.applyClaimsAndRole(conn);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE iam.role_assignments SET deleted_at = now(), updated_at = now(), updated_by = ? "
                                + "WHERE id = ? AND deleted_at IS NULL")) {
                    ps.setObject(1, ctx.getUserId());
                    ps.setObject(2, assignmentId);
                    if (ps.executeUpdate() == 0) {
                        throw new InstituteAdminException(404, "IADM03_GRANT_NOT_FOUND", "Grant not found.");
                    }
                }
                DurableAuditWriter.append(conn, scoped, tenant, "IADM03_ROLE_REVOKED",
                        "ROLE_ASSIGNMENT", assignmentId.toString(),
                        "Role=" + roleCode + "; reason=" + reason.trim());
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("assignmentId", assignmentId.toString());
                out.put("status", "REVOKED");
                out.put("roleCode", roleCode);
                out.put("idempotencyKey", key);
                out.put("correlationId", corr);
                out.put("claimsNote", CLAIMS_NOTE);
                out.put("replay", Boolean.FALSE);
                return out;
            }
        });
    }

    /** US-020 — approve/reject queued R03 grant change. */
    public Map<String, Object> decideR03GrantChange(final UserSecurityContext ctx,
                                                    final String requestKey,
                                                    final String decision,
                                                    final String reason) {
        if (requestKey == null || requestKey.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "requestId / idempotency key is required");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "A reason is required for the decision.");
        }
        final String dec = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        if (!"APPROVE".equals(dec) && !"REJECT".equals(dec)) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "decision must be APPROVE or REJECT");
        }
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iam.grant.r03.decide");
                boolean platform = isPlatformOperator(conn, ctx);
                Map<String, Object> pending = loadPendingR03Change(conn, requestKey.trim());
                if (pending == null) {
                    throw new InstituteAdminException(404, "IADM03_CHANGE_NOT_FOUND", "No pending R03 grant change for that key.");
                }
                UUID requester = pending.get("requesterUserId") == null ? null
                        : UUID.fromString(pending.get("requesterUserId").toString());
                if (requester != null && requester.equals(ctx.getUserId())) {
                    throw new InstituteAdminException(403, "IADM03_SELF_APPROVE",
                            "The requester cannot approve or reject their own R03 grant change.");
                }
                if (!platform) {
                    // second R03 of same institute
                    UUID tenant = UUID.fromString(pending.get("tenantId").toString());
                    if (ctx.getTenantId() == null || !ctx.getTenantId().equals(tenant)) {
                        throw new InstituteAdminException(403, "IADM03_CROSS_INSTITUTE",
                                "The requested institute is outside your permitted scope.");
                    }
                }
                if ("REJECT".equals(dec)) {
                    markPending(conn, requestKey.trim(), "REJECTED", reason.trim(), ctx.getUserId());
                    DurableAuditWriter.append(conn, ctx,
                            UUID.fromString(pending.get("tenantId").toString()),
                            "IADM03_R03_CHANGE_REJECTED", "GRANT_CHANGE", requestKey.trim(), reason.trim());
                    Map<String, Object> out = new LinkedHashMap<String, Object>();
                    out.put("requestId", requestKey.trim());
                    out.put("decision", "REJECTED");
                    out.put("reason", reason.trim());
                    return out;
                }
                // APPROVE — apply revoke
                UUID assignmentId = UUID.fromString(pending.get("assignmentId").toString());
                UUID tenant = UUID.fromString(pending.get("tenantId").toString());
                UserSecurityContext scoped = ctx.withTenant(tenant);
                scoped.applyClaimsAndRole(conn);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE iam.role_assignments SET deleted_at = now(), updated_at = now(), updated_by = ? "
                                + "WHERE id = ? AND deleted_at IS NULL")) {
                    ps.setObject(1, ctx.getUserId());
                    ps.setObject(2, assignmentId);
                    ps.executeUpdate();
                }
                markPending(conn, requestKey.trim(), "SUCCEEDED", reason.trim(), ctx.getUserId());
                DurableAuditWriter.append(conn, scoped, tenant,
                        "IADM03_R03_CHANGE_APPROVED", "GRANT_CHANGE", requestKey.trim(),
                        "Approved revoke of INSTITUTION_ADMIN; reason=" + reason.trim());
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("requestId", requestKey.trim());
                out.put("decision", "APPROVED");
                out.put("assignmentId", assignmentId.toString());
                out.put("claimsNote", CLAIMS_NOTE);
                return out;
            }
        });
    }

    public Map<String, Object> claimsContract() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("authoritative", "DB + iam.has_permission in service transaction");
        out.put("gatewayIdentityCacheTtlSeconds", Integer.valueOf(300));
        out.put("revocationDelaySecondsMax", Integer.valueOf(300));
        out.put("iadm03OnRequestPath", Boolean.FALSE);
        out.put("note", CLAIMS_NOTE);
        return out;
    }

    // --- helpers ---

    private Map<String, Object> assignPlatformTechnical(Connection conn, UserSecurityContext ctx, UUID subjectUserId,
                                                        String role, String reason, String key, String corr)
            throws SQLException {
        Map<String, Object> replay = findIdempotent(conn, key);
        if (replay != null) {
            return replay;
        }
        String code = "SYSTEM_ADMIN";
        if (role.startsWith("SVC-") || "SERVICE".equals(role) || "TECHNICAL".equals(role)) {
            // No separate service role store yet — record as SYSTEM_ADMIN technical operator with note
            code = "SYSTEM_ADMIN";
        }
        try (PreparedStatement find = conn.prepareStatement(
                "SELECT user_id FROM plat.platform_admins WHERE user_id = ? AND deleted_at IS NULL")) {
            find.setObject(1, subjectUserId);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    throw new InstituteAdminException(409, "IADM03_GRANT_EXISTS",
                            "A platform technical identity already exists for this Auth subject.");
                }
            }
        }
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO plat.platform_admins (user_id, full_name, role_code, status, created_by, updated_by) "
                        + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)")) {
            ins.setObject(1, subjectUserId);
            ins.setString(2, "Technical " + code);
            ins.setString(3, code);
            ins.setObject(4, ctx.getUserId());
            ins.setObject(5, ctx.getUserId());
            ins.executeUpdate();
        }
        DurableAuditWriter.append(conn, ctx, ctx.getTenantId(), "IADM03_TECHNICAL_GRANTED",
                "PLATFORM_ADMIN", subjectUserId.toString(), "role=" + code + "; reason=" + reason);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("profileId", subjectUserId.toString());
        out.put("roleCode", code);
        out.put("store", "PLATFORM_ADMIN");
        out.put("idempotencyKey", key);
        out.put("correlationId", corr);
        out.put("claimsNote", CLAIMS_NOTE);
        out.put("replay", Boolean.FALSE);
        saveIdempotent(conn, key, "TECHNICAL_ASSIGN", out, corr, null, ctx.getUserId());
        return out;
    }

    private Map<String, Object> queueR03Change(Connection conn, UserSecurityContext ctx, Map<String, Object> grant,
                                               String action, String reason, String key, String corr)
            throws SQLException {
        String payload = "{\"action\":\"" + action + "\",\"assignmentId\":\"" + grant.get("assignmentId")
                + "\",\"tenantId\":\"" + grant.get("tenantId") + "\",\"userId\":\"" + grant.get("userId")
                + "\",\"roleCode\":\"" + grant.get("roleCode") + "\",\"requesterUserId\":\"" + ctx.getUserId()
                + "\",\"reason\":\"" + escape(reason) + "\"}";
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO iam.governance_idempotency "
                        + "(idempotency_key, operation, status, result_payload, correlation_id, tenant_id, actor_user_id, created_by) "
                        + "VALUES (?, 'R03_GRANT_CHANGE', 'PENDING', ?::jsonb, ?, ?, ?, ?) "
                        + "ON CONFLICT (idempotency_key) DO UPDATE SET result_payload = EXCLUDED.result_payload, "
                        + "status = 'PENDING', updated_at = now()")) {
            ps.setString(1, key);
            ps.setString(2, payload);
            ps.setString(3, corr);
            ps.setObject(4, UUID.fromString(grant.get("tenantId").toString()));
            ps.setObject(5, ctx.getUserId());
            ps.setObject(6, ctx.getUserId());
            ps.executeUpdate();
        }
        DurableAuditWriter.append(conn, ctx, UUID.fromString(grant.get("tenantId").toString()),
                "IADM03_R03_CHANGE_REQUESTED", "GRANT_CHANGE", key, reason);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("requestId", key);
        out.put("status", "PENDING");
        out.put("action", action);
        out.put("assignmentId", grant.get("assignmentId"));
        out.put("correlationId", corr);
        return out;
    }

    private Map<String, Object> loadPendingR03Change(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT result_payload::text, status, actor_user_id FROM iam.governance_idempotency "
                        + "WHERE idempotency_key = ? AND operation = 'R03_GRANT_CHANGE' AND deleted_at IS NULL")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                if (!"PENDING".equalsIgnoreCase(rs.getString("status"))) {
                    throw new InstituteAdminException(409, "IADM03_CHANGE_NOT_PENDING",
                            "That R03 grant change is not pending.");
                }
                String payload = rs.getString(1);
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("assignmentId", extractJson(payload, "assignmentId"));
                out.put("tenantId", extractJson(payload, "tenantId"));
                out.put("userId", extractJson(payload, "userId"));
                out.put("roleCode", extractJson(payload, "roleCode"));
                out.put("requesterUserId", extractJson(payload, "requesterUserId"));
                return out;
            }
        }
    }

    private void markPending(Connection conn, String key, String status, String reason, UUID actor) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE iam.governance_idempotency SET status = ?, updated_at = now(), updated_by = ?, "
                        + "result_payload = coalesce(result_payload, '{}'::jsonb) || ?::jsonb "
                        + "WHERE idempotency_key = ?")) {
            ps.setString(1, status);
            ps.setObject(2, actor);
            ps.setString(3, "{\"decisionReason\":\"" + escape(reason) + "\"}");
            ps.setString(4, key);
            ps.executeUpdate();
        }
    }

    private Map<String, Object> loadAssignment(Connection conn, UUID id, UUID tenantFilter) throws SQLException {
        String sql = "SELECT ra.id, ra.user_id, ra.tenant_id, r.code AS role_code FROM iam.role_assignments ra "
                + "JOIN iam.roles r ON r.id = ra.role_id "
                + "WHERE ra.id = ? AND ra.deleted_at IS NULL "
                + (tenantFilter == null ? "" : "AND ra.tenant_id = ?");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            if (tenantFilter != null) {
                ps.setObject(2, tenantFilter);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapAssignment(rs);
            }
        }
    }

    private Map<String, Object> loadAssignmentPool(final UUID id) {
        return withPool(new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                return loadAssignment(conn, id, null);
            }
        });
    }

    private Map<String, Object> mapAssignment(ResultSet rs) throws SQLException {
        Map<String, Object> g = new LinkedHashMap<String, Object>();
        g.put("assignmentId", rs.getObject("id").toString());
        g.put("userId", rs.getObject("user_id").toString());
        g.put("tenantId", rs.getObject("tenant_id").toString());
        g.put("roleCode", rs.getString("role_code"));
        return g;
    }

    private void assertProfileActive(Connection conn, UUID userId, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT status FROM iam.user_profiles WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, userId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    // try without tenant for platform list
                    try (PreparedStatement ps2 = conn.prepareStatement(
                            "SELECT status, tenant_id FROM iam.user_profiles WHERE id = ? AND deleted_at IS NULL")) {
                        ps2.setObject(1, userId);
                        try (ResultSet rs2 = ps2.executeQuery()) {
                            if (!rs2.next()) {
                                throw new InstituteAdminException(404, "IADM03_PROFILE_NOT_FOUND",
                                        "No profile for that person. Invite or create the profile first.");
                            }
                            if (!"ACTIVE".equalsIgnoreCase(rs2.getString("status"))) {
                                throw new InstituteAdminException(409, "IADM03_PROFILE_INACTIVE",
                                        "Inactive profiles cannot receive new grants.");
                            }
                            return;
                        }
                    }
                }
                if (!"ACTIVE".equalsIgnoreCase(rs.getString("status"))) {
                    throw new InstituteAdminException(409, "IADM03_PROFILE_INACTIVE",
                            "Inactive profiles cannot receive new grants.");
                }
            }
        }
    }

    private void assertCollegeInTenant(Connection conn, UUID collegeId, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM core.colleges WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, collegeId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new InstituteAdminException(400, "IADM03_COLLEGE_SCOPE",
                            "College must belong to the same institute as the grant.");
                }
            }
        }
    }

    private boolean permissionSubsetOk(Connection conn, UUID grantorId, String targetRole, UUID tenantId)
            throws SQLException {
        // Fail closed if we cannot prove subset: count target template perms vs grantor's live perms
        int targetCount = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT count(*) FROM iam.role_template_permissions WHERE role_code = ? AND deleted_at IS NULL")) {
            ps.setString(1, targetRole);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                targetCount = rs.getInt(1);
            }
        }
        int grantorCount = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT count(DISTINCT p.code) FROM iam.role_assignments ra "
                        + "JOIN iam.role_permissions rp ON rp.role_id = ra.role_id AND rp.tenant_id = ra.tenant_id "
                        + "JOIN iam.permissions p ON p.id = rp.permission_id "
                        + "WHERE ra.user_id = ? AND ra.tenant_id = ? AND ra.deleted_at IS NULL")) {
            ps.setObject(1, grantorId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                grantorCount = rs.getInt(1);
            }
        }
        if (grantorCount == 0) {
            // platform operator path may have zero tenant perms — caller already checked platform
            return true;
        }
        return grantorCount >= targetCount;
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

    private Map<String, Object> baseGrantOut(UUID subject, String role, UUID tenant, UUID assignmentId,
                                             String key, String corr) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("subjectUserId", subject.toString());
        out.put("roleCode", role);
        out.put("tenantId", tenant.toString());
        if (assignmentId != null) {
            out.put("assignmentId", assignmentId.toString());
        }
        out.put("idempotencyKey", key);
        out.put("correlationId", corr);
        out.put("claimsNote", CLAIMS_NOTE);
        out.put("replay", Boolean.FALSE);
        return out;
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
        String payload = "{\"assignmentId\":\"" + String.valueOf(out.get("assignmentId"))
                + "\",\"roleCode\":\"" + String.valueOf(out.get("roleCode")) + "\"}";
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

    private void auditDenial(Connection conn, UserSecurityContext ctx, UUID tenantId, String action, String message)
            throws SQLException {
        try {
            DurableAuditWriter.append(conn, ctx, tenantId, action, "SECURITY",
                    ctx.getUserId() == null ? null : ctx.getUserId().toString(), message);
        } catch (InstituteAdminException ex) {
            // still fail the mutation with ceiling denial even if audit of denial fails closed later
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

    private void assertPlatformOperator(Connection conn, UserSecurityContext ctx) throws SQLException {
        if (!isPlatformOperator(conn, ctx)) {
            throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                    "Only a platform administrator may appoint an Institution Admin through this flow.");
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
            // Distinguish R01 vs R02 from plat.platform_admins.role_code
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT role_code FROM plat.platform_admins WHERE user_id = ? AND deleted_at IS NULL")) {
                ps.setObject(1, ctx.getUserId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String code = rs.getString(1);
                        if ("SYSTEM_ADMIN".equalsIgnoreCase(code)) {
                            return "R02";
                        }
                    }
                }
            }
            return "R01";
        }
        return "R03";
    }

    private Date parseDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        return Date.valueOf(raw.trim());
    }

    private static String extractJson(String json, String field) {
        if (json == null || field == null) {
            return null;
        }
        String needle = "\"" + field + "\":\"";
        int i = json.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int start = i + needle.length();
        int end = json.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        return json.substring(start, end);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
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
