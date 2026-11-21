package com.campx.iadm.identity.service;

import com.campx.iadm.identity.config.DatabaseConnectionManager;
import com.campx.iadm.identity.exception.InstituteAdminException;
import com.campx.iadm.identity.exception.UserProfileAccessDeniedException;
import com.campx.iadm.identity.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * IADM-03 Platform Identity &amp; Access — keys adm08/adm09. Ceilings = CADM-03 contract.
 * <p>
 * Write operations audit through {@link CampXLogger} per Agreement &#167;3.2.
 */
public class Iadm03DomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm03DomainService.class);

    private final DatabaseConnectionManager db = DatabaseConnectionManager.getInstance();

    public Map<String, Object> listRoles(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm09.view");
            List<Map<String, Object>> roles = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT r.code, r.name, r.id, " +
                            "COALESCE((SELECT string_agg(p.code, ',' ORDER BY p.code) " +
                            " FROM iam.role_permissions rp " +
                            " JOIN iam.permissions p ON p.id = rp.permission_id AND p.deleted_at IS NULL " +
                            " WHERE rp.role_id = r.id AND rp.tenant_id = r.tenant_id), '') AS perm_csv " +
                            "FROM iam.roles r WHERE r.tenant_id = ? AND r.deleted_at IS NULL ORDER BY r.code LIMIT 100")) {
                ps.setObject(1, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("code", rs.getString(1));
                        r.put("name", rs.getString(2));
                        r.put("roleId", rs.getObject(3) == null ? null : rs.getObject(3).toString());
                        String csv = rs.getString(4);
                        List<String> perms = new ArrayList<>();
                        if (csv != null && !csv.isEmpty()) {
                            perms.addAll(Arrays.asList(csv.split(",")));
                        }
                        r.put("permissions", perms);
                        roles.add(r);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("roles", roles);
            return out;
        });
    }

    public Map<String, Object> setCeiling(UserSecurityContext ctx, String roleCode, String keysCsv) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm09.manage");
            UUID id = UUID.randomUUID();
            String[] keys = (keysCsv == null || keysCsv.isEmpty()) ? new String[0] : keysCsv.split(",");
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO iam.role_ceilings (id, tenant_id, role_code, allowed_permission_keys, created_by) VALUES (?,?,?,?::text[],?)")) {
                ps.setObject(1, id);
                ps.setObject(2, ctx.getTenantId());
                ps.setString(3, roleCode);
                ps.setArray(4, conn.createArrayOf("text", keys));
                ps.setObject(5, ctx.getUserId());
                ps.executeUpdate();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ceilingId", id.toString());
            out.put("roleCode", roleCode);
            auditMutation(ctx, "setCeiling", "ROLE_CEILING", id.toString());
            return out;
        });
    }

    public Map<String, Object> getCeiling(UserSecurityContext ctx, String roleCode) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm09.view");
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, role_code, allowed_permission_keys FROM iam.role_ceilings " +
                            "WHERE tenant_id=? AND role_code=? AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 1")) {
                ps.setObject(1, ctx.getTenantId());
                ps.setString(2, roleCode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new InstituteAdminException(404, "IADM03_CEILING_NOT_FOUND", "No ceiling for role");
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("ceilingId", rs.getObject(1).toString());
                    out.put("roleCode", rs.getString(2));
                    Array arr = rs.getArray(3);
                    out.put("allowedPermissionKeys", arr == null ? Collections.emptyList() : Arrays.asList((String[]) arr.getArray()));
                    return out;
                }
            }
        });
    }

    /**
     * IADM-03-US-009 / OWNER-DEC-TP-01: R01 or R02 requests appoint/remove of Institution Admin.
     */
    public Map<String, Object> requestInstitutionAdminQuorum(UserSecurityContext ctx, UUID tenantId,
                                                             UUID targetUserId, String action, String reason) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            if (tenantId == null || targetUserId == null) {
                throw new InstituteAdminException(400, "IADM03_QUORUM_REQUIRED", "tenantId and targetUserId required");
            }
            String act = action == null ? "" : action.trim().toUpperCase();
            if (!"APPOINT".equals(act) && !"REMOVE".equals(act)) {
                throw new InstituteAdminException(400, "IADM03_QUORUM_ACTION", "action must be APPOINT or REMOVE");
            }
            if (reason == null || reason.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM03_QUORUM_REASON", "reason required");
            }
            if (targetUserId.equals(ctx.getUserId())) {
                throw new InstituteAdminException(403, "IADM03_QUORUM_SELF", "Cannot target self as Institution Admin change");
            }
            String track = platformAdminTrack(conn, ctx.getUserId());
            if (track == null) {
                throw new InstituteAdminException(403, "IADM03_QUORUM_TRACK",
                        "Caller must be R01 (SUPER_ADMIN/PLATFORM_ADMIN) or R02 (SYSTEM_ADMIN)");
            }
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO plat.institution_admin_quorum_requests " +
                            "(id, tenant_id, target_user_id, action, status, reason, requested_by, requested_track, created_by) " +
                            "VALUES (?,?,?,?,'PENDING',?,?,?,?)")) {
                ps.setObject(1, id);
                ps.setObject(2, tenantId);
                ps.setObject(3, targetUserId);
                ps.setString(4, act);
                ps.setString(5, reason.trim());
                ps.setObject(6, ctx.getUserId());
                ps.setString(7, track);
                ps.setObject(8, ctx.getUserId());
                ps.executeUpdate();
            } catch (java.sql.SQLException dup) {
                if (dup.getMessage() != null && dup.getMessage().toLowerCase().contains("uq_iaqr_pending")) {
                    throw new InstituteAdminException(409, "IADM03_QUORUM_PENDING",
                            "A pending request already exists for this tenant/target/action");
                }
                throw dup;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requestId", id.toString());
            out.put("tenantId", tenantId.toString());
            out.put("targetUserId", targetUserId.toString());
            out.put("action", act);
            out.put("status", "PENDING");
            out.put("requestedTrack", track);
            auditMutation(ctx, "requestInstitutionAdminQuorum", "IA_QUORUM", id.toString());
            return out;
        });
    }

    /**
     * IADM-03-US-009: complementary track (R01↔R02) decides; on APPROVE executes appoint/remove.
     */
    public Map<String, Object> decideInstitutionAdminQuorum(UserSecurityContext ctx, UUID requestId,
                                                            String decision, String note) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            if (requestId == null) {
                throw new InstituteAdminException(400, "IADM03_QUORUM_REQUIRED", "requestId required");
            }
            String track = platformAdminTrack(conn, ctx.getUserId());
            if (track == null) {
                throw new InstituteAdminException(403, "IADM03_QUORUM_TRACK",
                        "Caller must be R01 or R02 on plat.platform_admins");
            }
            String status = decision != null && decision.toUpperCase().startsWith("APP") ? "APPROVED" : "REJECTED";

            UUID tenantId;
            UUID targetUserId;
            String action;
            String requestedTrack;
            UUID requestedBy;
            try (PreparedStatement sel = conn.prepareStatement(
                    "SELECT tenant_id, target_user_id, action, requested_track, requested_by " +
                            "FROM plat.institution_admin_quorum_requests " +
                            "WHERE id = ? AND status = 'PENDING' AND deleted_at IS NULL")) {
                sel.setObject(1, requestId);
                try (ResultSet rs = sel.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(404, "IADM03_QUORUM_NOT_FOUND", "Pending quorum request not found");
                    }
                    tenantId = (UUID) rs.getObject(1);
                    targetUserId = (UUID) rs.getObject(2);
                    action = rs.getString(3);
                    requestedTrack = rs.getString(4);
                    requestedBy = (UUID) rs.getObject(5);
                }
            }
            if (requestedBy.equals(ctx.getUserId())) {
                throw new InstituteAdminException(403, "IADM03_QUORUM_SELF_APPROVE",
                        "Requestor cannot approve their own Institution Admin change");
            }
            if (track.equals(requestedTrack)) {
                throw new InstituteAdminException(403, "IADM03_QUORUM_SAME_TRACK",
                        "Approver must be the complementary track (R01 requests need R02; R02 requests need R01)");
            }
            if (!("R01".equals(requestedTrack) && "R02".equals(track))
                    && !("R02".equals(requestedTrack) && "R01".equals(track))) {
                throw new InstituteAdminException(403, "IADM03_QUORUM_PAIR", "Quorum requires one R01 and one R02");
            }

            UUID assignmentId = null;
            if ("APPROVED".equals(status)) {
                if ("APPOINT".equals(action)) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT plat.assign_institution_admin(?, ?, ?)")) {
                        ps.setObject(1, tenantId);
                        ps.setObject(2, targetUserId);
                        ps.setObject(3, ctx.getUserId());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new InstituteAdminException(500, "IADM03_QUORUM_APPOINT", "No assignment returned");
                            }
                            assignmentId = (UUID) rs.getObject(1);
                        }
                    }
                } else if ("REMOVE".equals(action)) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE iam.role_assignments ra SET deleted_at = now(), updated_by = ?, updated_at = now() " +
                                    "FROM iam.roles r " +
                                    "WHERE ra.role_id = r.id AND ra.tenant_id = ? AND ra.user_id = ? " +
                                    "AND r.code = 'INSTITUTION_ADMIN' AND ra.deleted_at IS NULL " +
                                    "RETURNING ra.id")) {
                        ps.setObject(1, ctx.getUserId());
                        ps.setObject(2, tenantId);
                        ps.setObject(3, targetUserId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                assignmentId = (UUID) rs.getObject(1);
                            }
                        }
                    }
                }
            }

            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE plat.institution_admin_quorum_requests SET status = ?, decided_by = ?, decided_track = ?, " +
                            "decision_note = ?, decided_at = now(), assignment_id = ?, updated_by = ?, updated_at = now() " +
                            "WHERE id = ? AND status = 'PENDING'")) {
                upd.setString(1, status);
                upd.setObject(2, ctx.getUserId());
                upd.setString(3, track);
                upd.setString(4, note);
                upd.setObject(5, assignmentId);
                upd.setObject(6, ctx.getUserId());
                upd.setObject(7, requestId);
                if (upd.executeUpdate() == 0) {
                    throw new InstituteAdminException(409, "IADM03_QUORUM_RACE", "Request was already decided");
                }
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requestId", requestId.toString());
            out.put("status", status);
            out.put("action", action);
            out.put("requestedTrack", requestedTrack);
            out.put("decidedTrack", track);
            out.put("tenantId", tenantId.toString());
            out.put("targetUserId", targetUserId.toString());
            if (assignmentId != null) out.put("assignmentId", assignmentId.toString());
            auditMutation(ctx, "decideInstitutionAdminQuorum", "IA_QUORUM", requestId.toString());
            return out;
        });
    }

    public Map<String, Object> listInstitutionAdminQuorum(UserSecurityContext ctx, UUID tenantId) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            List<Map<String, Object>> items = new ArrayList<>();
            String sql = "SELECT id, tenant_id, target_user_id, action, status, reason, requested_by, requested_track, " +
                    "decided_by, decided_track, decided_at, assignment_id, created_at " +
                    "FROM plat.institution_admin_quorum_requests WHERE deleted_at IS NULL ";
            if (tenantId != null) sql += "AND tenant_id = ? ";
            sql += "ORDER BY created_at DESC LIMIT 100";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (tenantId != null) ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("requestId", rs.getObject(1).toString());
                        row.put("tenantId", rs.getObject(2).toString());
                        row.put("targetUserId", rs.getObject(3).toString());
                        row.put("action", rs.getString(4));
                        row.put("status", rs.getString(5));
                        row.put("reason", rs.getString(6));
                        row.put("requestedBy", rs.getObject(7) == null ? null : rs.getObject(7).toString());
                        row.put("requestedTrack", rs.getString(8));
                        row.put("decidedBy", rs.getObject(9) == null ? null : rs.getObject(9).toString());
                        row.put("decidedTrack", rs.getString(10));
                        row.put("decidedAt", rs.getTimestamp(11));
                        row.put("assignmentId", rs.getObject(12) == null ? null : rs.getObject(12).toString());
                        row.put("createdAt", rs.getTimestamp(13));
                        items.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requests", items);
            return out;
        });
    }

    private static String platformAdminTrack(Connection conn, UUID userId) throws java.sql.SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT plat.platform_admin_track(?)")) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getString(1);
            }
        }
    }

    /**
     * IADM-03-US-008 / OWNER-DEC-BG-01: stakeholder requests break-glass for a tenant.
     */
    public Map<String, Object> requestBreakGlass(UserSecurityContext ctx, UUID tenantId, String reason, Integer minutes) {
        return ctx.executeInTransaction(db, conn -> {
            if (tenantId == null) {
                throw new InstituteAdminException(400, "IADM03_BG_REQUIRED", "tenantId required");
            }
            if (reason == null || reason.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM03_BG_REASON", "reason required");
            }
            int mins = minutes == null ? 60 : minutes.intValue();
            if (mins < 15 || mins > 240) {
                throw new InstituteAdminException(400, "IADM03_BG_MINUTES", "requestedMinutes must be 15–240");
            }
            String role;
            try (PreparedStatement ps = conn.prepareStatement("SELECT plat.break_glass_requester_role(?, ?)")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, ctx.getUserId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getString(1) == null) {
                        throw new InstituteAdminException(403, "IADM03_BG_REQUESTER",
                                "Only R03/R04/R05/R31 or R01/R02 may request break-glass for this tenant");
                    }
                    role = rs.getString(1);
                }
            }
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO plat.break_glass_requests " +
                            "(id, tenant_id, requested_by, requester_role, reason, requested_minutes, status, created_by) " +
                            "VALUES (?,?,?,?,?,?,'PENDING',?)")) {
                ps.setObject(1, id);
                ps.setObject(2, tenantId);
                ps.setObject(3, ctx.getUserId());
                ps.setString(4, role);
                ps.setString(5, reason.trim());
                ps.setInt(6, mins);
                ps.setObject(7, ctx.getUserId());
                ps.executeUpdate();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requestId", id.toString());
            out.put("tenantId", tenantId.toString());
            out.put("status", "PENDING");
            out.put("requesterRole", role);
            out.put("requestedMinutes", mins);
            auditMutation(ctx, "requestBreakGlass", "BREAK_GLASS", id.toString());
            return out;
        });
    }

    /**
     * Approve (R01/R02) opens a time-boxed session; reject leaves no session.
     */
    public Map<String, Object> decideBreakGlass(UserSecurityContext ctx, UUID requestId, String decision, String note) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            String track = platformAdminTrack(conn, ctx.getUserId());
            if (track == null || (!"R01".equals(track) && !"R02".equals(track))) {
                throw new InstituteAdminException(403, "IADM03_BG_APPROVER", "Only R01 or R02 may approve break-glass");
            }
            if (requestId == null) {
                throw new InstituteAdminException(400, "IADM03_BG_REQUIRED", "requestId required");
            }
            boolean approve = decision != null && decision.toUpperCase().startsWith("APP");
            UUID tenantId;
            String reason;
            int mins;
            UUID requestedBy;
            try (PreparedStatement sel = conn.prepareStatement(
                    "SELECT tenant_id, reason, requested_minutes, requested_by FROM plat.break_glass_requests " +
                            "WHERE id = ? AND status = 'PENDING' AND deleted_at IS NULL")) {
                sel.setObject(1, requestId);
                try (ResultSet rs = sel.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(404, "IADM03_BG_NOT_FOUND", "Pending break-glass request not found");
                    }
                    tenantId = (UUID) rs.getObject(1);
                    reason = rs.getString(2);
                    mins = rs.getInt(3);
                    requestedBy = (UUID) rs.getObject(4);
                }
            }
            // College stakeholders cannot approve; platform only (already checked). Self-request by R01/R02 may self-approve
            // for platform ops — still audited. OWNER-DEC-BG-01: R03/R04 cannot approve (enforced by track).

            UUID sessionId = null;
            Timestamp expiresAt = null;
            String status = approve ? "APPROVED" : "REJECTED";
            if (approve) {
                sessionId = UUID.randomUUID();
                expiresAt = Timestamp.from(Instant.now().plusSeconds(mins * 60L));
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO plat.break_glass_sessions " +
                                "(id, tenant_id, request_id, opened_by, opener_track, reason, expires_at, created_by) " +
                                "VALUES (?,?,?,?,?,?,?,?)")) {
                    ins.setObject(1, sessionId);
                    ins.setObject(2, tenantId);
                    ins.setObject(3, requestId);
                    ins.setObject(4, ctx.getUserId());
                    ins.setString(5, track);
                    ins.setString(6, reason);
                    ins.setTimestamp(7, expiresAt);
                    ins.setObject(8, ctx.getUserId());
                    ins.executeUpdate();
                }
                appendBreakGlassAction(conn, sessionId, tenantId, ctx.getUserId(), "SESSION_OPENED",
                        "{\"requestId\":\"" + requestId + "\",\"expiresAt\":\"" + expiresAt.toInstant() + "\"," +
                                "\"requestedBy\":\"" + requestedBy + "\"}");
            }
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE plat.break_glass_requests SET status = ?, decided_by = ?, decision_note = ?, " +
                            "decided_at = now(), session_id = ?, updated_by = ?, updated_at = now() " +
                            "WHERE id = ? AND status = 'PENDING'")) {
                upd.setString(1, status);
                upd.setObject(2, ctx.getUserId());
                upd.setString(3, note);
                upd.setObject(4, sessionId);
                upd.setObject(5, ctx.getUserId());
                upd.setObject(6, requestId);
                if (upd.executeUpdate() == 0) {
                    throw new InstituteAdminException(409, "IADM03_BG_RACE", "Request already decided");
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requestId", requestId.toString());
            out.put("status", status);
            out.put("tenantId", tenantId.toString());
            out.put("decidedTrack", track);
            if (sessionId != null) {
                out.put("sessionId", sessionId.toString());
                out.put("expiresAt", expiresAt.toInstant().toString());
            }
            auditMutation(ctx, "decideBreakGlass", "BREAK_GLASS", requestId.toString());
            return out;
        });
    }

    public Map<String, Object> closeBreakGlassSession(UserSecurityContext ctx, UUID sessionId, String closeReason) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            String track = platformAdminTrack(conn, ctx.getUserId());
            if (track == null) {
                throw new InstituteAdminException(403, "IADM03_BG_APPROVER", "Only R01/R02 may close break-glass");
            }
            UUID tenantId;
            try (PreparedStatement sel = conn.prepareStatement(
                    "SELECT tenant_id FROM plat.break_glass_sessions " +
                            "WHERE id = ? AND closed_at IS NULL AND deleted_at IS NULL")) {
                sel.setObject(1, sessionId);
                try (ResultSet rs = sel.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(404, "IADM03_BG_SESSION", "Active session not found");
                    }
                    tenantId = (UUID) rs.getObject(1);
                }
            }
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE plat.break_glass_sessions SET closed_at = now(), closed_by = ?, close_reason = ?, " +
                            "updated_by = ?, updated_at = now() WHERE id = ? AND closed_at IS NULL")) {
                upd.setObject(1, ctx.getUserId());
                upd.setString(2, closeReason == null ? "CLOSED" : closeReason);
                upd.setObject(3, ctx.getUserId());
                upd.setObject(4, sessionId);
                upd.executeUpdate();
            }
            appendBreakGlassAction(conn, sessionId, tenantId, ctx.getUserId(), "SESSION_CLOSED",
                    "{\"reason\":\"" + (closeReason == null ? "CLOSED" : closeReason.replace("\"", "'")) + "\"}");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("sessionId", sessionId.toString());
            out.put("status", "CLOSED");
            auditMutation(ctx, "closeBreakGlassSession", "BREAK_GLASS", sessionId.toString());
            return out;
        });
    }

    public Map<String, Object> recordBreakGlassAction(UserSecurityContext ctx, UUID sessionId,
                                                      String actionType, String detailJson) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            String track = platformAdminTrack(conn, ctx.getUserId());
            if (track == null) {
                throw new InstituteAdminException(403, "IADM03_BG_APPROVER", "Only R01/R02 during active session");
            }
            String type = actionType == null ? "" : actionType.trim().toUpperCase();
            if (!"RECOVERY_NOTE".equals(type) && !"EMERGENCY_APPOINT_QUORUM".equals(type) && !"STATUS_CHECK".equals(type)) {
                throw new InstituteAdminException(400, "IADM03_BG_ACTION",
                        "actionType must be RECOVERY_NOTE, EMERGENCY_APPOINT_QUORUM, or STATUS_CHECK");
            }
            UUID tenantId;
            Timestamp expiresAt;
            Timestamp closedAt;
            try (PreparedStatement sel = conn.prepareStatement(
                    "SELECT tenant_id, expires_at, closed_at FROM plat.break_glass_sessions " +
                            "WHERE id = ? AND deleted_at IS NULL")) {
                sel.setObject(1, sessionId);
                try (ResultSet rs = sel.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(404, "IADM03_BG_SESSION", "Session not found");
                    }
                    tenantId = (UUID) rs.getObject(1);
                    expiresAt = rs.getTimestamp(2);
                    closedAt = rs.getTimestamp(3);
                }
            }
            if (closedAt != null || expiresAt.before(Timestamp.from(Instant.now()))) {
                throw new InstituteAdminException(403, "IADM03_BG_EXPIRED", "Break-glass session closed or expired");
            }
            String detail = (detailJson == null || detailJson.trim().isEmpty() || "null".equals(detailJson.trim()))
                    ? "{}" : detailJson.trim();
            if ("EMERGENCY_APPOINT_QUORUM".equals(type)) {
                // Opens US-009 quorum request in-session; does not bypass two-person rule
                String target = extractJsonString(detail, "targetUserId");
                String qReason = extractJsonString(detail, "reason");
                if (target == null || target.isEmpty()) {
                    throw new InstituteAdminException(400, "IADM03_BG_TARGET", "detail.targetUserId required");
                }
                UUID targetId = UUID.fromString(target);
                if (targetId.equals(ctx.getUserId())) {
                    throw new InstituteAdminException(403, "IADM03_QUORUM_SELF", "Cannot target self");
                }
                UUID qid = UUID.randomUUID();
                String qText = (qReason == null || qReason.isEmpty())
                        ? "Break-glass emergency appoint " + sessionId : qReason;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO plat.institution_admin_quorum_requests " +
                                "(id, tenant_id, target_user_id, action, status, reason, requested_by, requested_track, created_by) " +
                                "VALUES (?,?,?,'APPOINT','PENDING',?,?,?,?)")) {
                    ps.setObject(1, qid);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, targetId);
                    ps.setString(4, qText);
                    ps.setObject(5, ctx.getUserId());
                    ps.setString(6, track);
                    ps.setObject(7, ctx.getUserId());
                    ps.executeUpdate();
                }
                detail = "{\"quorumRequestId\":\"" + qid + "\",\"targetUserId\":\"" + target + "\"}";
            }
            UUID actionId = appendBreakGlassAction(conn, sessionId, tenantId, ctx.getUserId(), type, detail);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("actionId", actionId.toString());
            out.put("sessionId", sessionId.toString());
            out.put("actionType", type);
            out.put("tenantId", tenantId.toString());
            auditMutation(ctx, "recordBreakGlassAction", "BREAK_GLASS", actionId.toString());
            return out;
        });
    }

    public Map<String, Object> listBreakGlass(UserSecurityContext ctx, UUID tenantId) {
        return ctx.executeInTransaction(db, conn -> {
            boolean platform;
            try {
                ctx.checkPlatformAdmin(conn);
                platform = true;
            } catch (UserProfileAccessDeniedException e) {
                platform = false;
            }
            if (!platform && (ctx.getTenantId() == null || (tenantId != null && !tenantId.equals(ctx.getTenantId())))) {
                throw new InstituteAdminException(403, "IADM03_BG_TENANT", "Tenant stakeholders may only list own tenant");
            }
            UUID filter = tenantId != null ? tenantId : ctx.getTenantId();
            List<Map<String, Object>> requests = new ArrayList<>();
            String sql = "SELECT id, tenant_id, requested_by, requester_role, reason, requested_minutes, status, " +
                    "session_id, decided_by, decided_at, created_at FROM plat.break_glass_requests " +
                    "WHERE deleted_at IS NULL ";
            if (filter != null) sql += "AND tenant_id = ? ";
            sql += "ORDER BY created_at DESC LIMIT 100";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (filter != null) ps.setObject(1, filter);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("requestId", rs.getObject(1).toString());
                        row.put("tenantId", rs.getObject(2).toString());
                        row.put("requestedBy", rs.getObject(3).toString());
                        row.put("requesterRole", rs.getString(4));
                        row.put("reason", rs.getString(5));
                        row.put("requestedMinutes", rs.getInt(6));
                        row.put("status", rs.getString(7));
                        row.put("sessionId", rs.getObject(8) == null ? null : rs.getObject(8).toString());
                        row.put("decidedBy", rs.getObject(9) == null ? null : rs.getObject(9).toString());
                        row.put("decidedAt", rs.getTimestamp(10));
                        row.put("createdAt", rs.getTimestamp(11));
                        requests.add(row);
                    }
                }
            }
            List<Map<String, Object>> sessions = new ArrayList<>();
            String ssql = "SELECT id, tenant_id, request_id, opened_by, opener_track, reason, expires_at, closed_at " +
                    "FROM plat.break_glass_sessions WHERE deleted_at IS NULL ";
            if (filter != null) ssql += "AND tenant_id = ? ";
            ssql += "ORDER BY created_at DESC LIMIT 50";
            try (PreparedStatement ps = conn.prepareStatement(ssql)) {
                if (filter != null) ps.setObject(1, filter);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("sessionId", rs.getObject(1).toString());
                        row.put("tenantId", rs.getObject(2).toString());
                        row.put("requestId", rs.getObject(3).toString());
                        row.put("openedBy", rs.getObject(4).toString());
                        row.put("openerTrack", rs.getString(5));
                        row.put("reason", rs.getString(6));
                        Timestamp exp = rs.getTimestamp(7);
                        row.put("expiresAt", exp == null ? null : exp.toInstant().toString());
                        Timestamp closed = rs.getTimestamp(8);
                        boolean active = closed == null && exp != null && exp.after(Timestamp.from(Instant.now()));
                        row.put("active", active);
                        row.put("closedAt", closed);
                        sessions.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requests", requests);
            out.put("sessions", sessions);
            return out;
        });
    }

    private static UUID appendBreakGlassAction(Connection conn, UUID sessionId, UUID tenantId, UUID actor,
                                               String actionType, String detailJson) throws java.sql.SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO plat.break_glass_actions (id, session_id, tenant_id, actor_id, action_type, detail, created_by) " +
                        "VALUES (?,?,?,?,?,?::jsonb,?)")) {
            ps.setObject(1, id);
            ps.setObject(2, sessionId);
            ps.setObject(3, tenantId);
            ps.setObject(4, actor);
            ps.setString(5, actionType);
            ps.setString(6, detailJson == null ? "{}" : detailJson);
            ps.setObject(7, actor);
            ps.executeUpdate();
        }
        return id;
    }

    public Map<String, Object> createAccessRequest(UserSecurityContext ctx, String type, String reason, String payloadJson) {
        return ctx.executeInTransaction(db, conn -> {
            UUID id = UUID.randomUUID();
            String payload = (payloadJson == null || payloadJson.trim().isEmpty() || "null".equals(payloadJson.trim()))
                    ? "{}" : payloadJson.trim();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO iam.access_change_requests (id, tenant_id, requested_by, request_type, payload, reason, status, created_by) " +
                            "VALUES (?,?,?,?,?::jsonb,?,'PENDING',?)")) {
                ps.setObject(1, id);
                ps.setObject(2, ctx.getTenantId());
                ps.setObject(3, ctx.getUserId());
                ps.setString(4, type);
                ps.setString(5, payload);
                ps.setString(6, reason);
                ps.setObject(7, ctx.getUserId());
                ps.executeUpdate();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requestId", id.toString());
            out.put("status", "PENDING");
            out.put("requestType", type);
            auditMutation(ctx, "createAccessRequest", "ACCESS_REQUEST", id.toString());
            return out;
        });
    }

    public Map<String, Object> decideAccessRequest(UserSecurityContext ctx, UUID requestId, String decision, String note) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm09.manage");
            String status = decision != null && decision.toUpperCase().startsWith("APP") ? "APPROVED" : "REJECTED";
            String requestType = null;
            String payload = null;
            try (PreparedStatement sel = conn.prepareStatement(
                    "SELECT request_type, payload::text FROM iam.access_change_requests " +
                            "WHERE id=? AND tenant_id=? AND status='PENDING' AND deleted_at IS NULL")) {
                sel.setObject(1, requestId);
                sel.setObject(2, ctx.getTenantId());
                try (ResultSet rs = sel.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(404, "IADM03_REQUEST", "Pending request not found");
                    }
                    requestType = rs.getString(1);
                    payload = rs.getString(2);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE iam.access_change_requests SET status=?, decided_by=?, decided_at=now(), decision_note=?, updated_by=? " +
                            "WHERE id=? AND tenant_id=? AND status='PENDING' AND requested_by <> ?")) {
                ps.setString(1, status);
                ps.setObject(2, ctx.getUserId());
                ps.setString(3, note);
                ps.setObject(4, ctx.getUserId());
                ps.setObject(5, requestId);
                ps.setObject(6, ctx.getTenantId());
                ps.setObject(7, ctx.getUserId());
                if (ps.executeUpdate() == 0) {
                    throw new InstituteAdminException(404, "IADM03_REQUEST", "Pending request not found or self-approve blocked");
                }
            }
            Map<String, Object> applied = null;
            if ("APPROVED".equals(status) && requestType != null && requestType.toUpperCase().contains("ROLE_ASSIGN")) {
                applied = applyRoleAssignFromPayload(ctx, conn, payload);
            } else if ("APPROVED".equals(status) && requestType != null && requestType.toUpperCase().contains("ROLE_REVOKE")) {
                applied = applyRoleRevokeFromPayload(ctx, conn, payload);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requestId", requestId.toString());
            out.put("status", status);
            out.put("requestType", requestType);
            if (applied != null) out.put("applied", applied);
            auditMutation(ctx, "decideAccessRequest", "ACCESS_REQUEST", requestId.toString());
            return out;
        });
    }

    public Map<String, Object> assignRole(UserSecurityContext ctx, UUID userId, String roleCode, UUID collegeId) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm09.manage");
            if (userId == null || roleCode == null || roleCode.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM03_ASSIGN_REQUIRED", "userId and roleCode required");
            }
            if (userId.equals(ctx.getUserId())) {
                throw new InstituteAdminException(403, "IADM03_SELF_GRANT", "Self-grant of roles is forbidden");
            }
            String code = roleCode.trim();
            boolean platformAdmin = isPlatformAdmin(ctx, conn);
            if (!platformAdmin) {
                assertCallerHoldsRole(conn, ctx.getTenantId(), ctx.getUserId(), code);
            }
            assertWithinCeiling(conn, ctx.getTenantId(), code);
            if (collegeId != null) {
                assertCollegeAssignableAfterHandover(ctx, conn, collegeId);
            }
            UUID roleId = lookupRoleId(conn, ctx.getTenantId(), code);
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO iam.role_assignments (id, tenant_id, user_id, role_id, college_id, granted_by, created_by) " +
                            "VALUES (?,?,?,?,?,?,?)")) {
                ps.setObject(1, id);
                ps.setObject(2, ctx.getTenantId());
                ps.setObject(3, userId);
                ps.setObject(4, roleId);
                ps.setObject(5, collegeId);
                ps.setObject(6, ctx.getUserId());
                ps.setObject(7, ctx.getUserId());
                ps.executeUpdate();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("assignmentId", id.toString());
            out.put("userId", userId.toString());
            out.put("roleCode", code);
            out.put("collegeId", collegeId == null ? null : collegeId.toString());
            auditMutation(ctx, "assignRole", "ROLE_ASSIGNMENT", id.toString());
            return out;
        });
    }

    public Map<String, Object> revokeRoleAssignment(UserSecurityContext ctx, UUID assignmentId) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm09.manage");
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE iam.role_assignments SET deleted_at = now(), updated_at = now(), updated_by = ? " +
                            "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL")) {
                ps.setObject(1, ctx.getUserId());
                ps.setObject(2, assignmentId);
                ps.setObject(3, ctx.getTenantId());
                if (ps.executeUpdate() == 0) {
                    throw new InstituteAdminException(404, "IADM03_ASSIGNMENT_NOT_FOUND", "Role assignment not found");
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("assignmentId", assignmentId.toString());
            out.put("status", "REVOKED");
            auditMutation(ctx, "revokeRoleAssignment", "ROLE_ASSIGNMENT", assignmentId.toString());
            return out;
        });
    }

    /** Time-boxed role delegation into {@code iam.delegations} (Wave B / Scripts_V3.0). */
    public Map<String, Object> createDelegation(UserSecurityContext ctx, UUID toUserId, String roleCode,
                                               String validFromIso, String validToIso, String reason) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm09.manage");
            if (toUserId == null || roleCode == null || roleCode.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM03_DELEGATION_REQUIRED", "toUserId and roleCode required");
            }
            UUID roleId = lookupRoleId(conn, ctx.getTenantId(), roleCode.trim());
            Instant from = parseInstant(validFromIso, Instant.now());
            Instant to = parseInstant(validToIso, from.plusSeconds(7L * 24 * 3600));
            if (!to.isAfter(from)) {
                throw new InstituteAdminException(400, "IADM03_DELEGATION_WINDOW", "validTo must be after validFrom");
            }
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO iam.delegations (id, tenant_id, from_user_id, to_user_id, role_id, valid_from, valid_to, reason, status, created_by) " +
                            "VALUES (?,?,?,?,?,?,?,?,'ACTIVE',?)")) {
                ps.setObject(1, id);
                ps.setObject(2, ctx.getTenantId());
                ps.setObject(3, ctx.getUserId());
                ps.setObject(4, toUserId);
                ps.setObject(5, roleId);
                ps.setTimestamp(6, Timestamp.from(from));
                ps.setTimestamp(7, Timestamp.from(to));
                ps.setString(8, reason);
                ps.setObject(9, ctx.getUserId());
                ps.executeUpdate();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("delegationId", id.toString());
            out.put("toUserId", toUserId.toString());
            out.put("roleCode", roleCode.trim());
            out.put("status", "ACTIVE");
            auditMutation(ctx, "createDelegation", "DELEGATION", id.toString());
            return out;
        });
    }

    public Map<String, Object> listAccessReviews(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm09.view");
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id::text, cycle_name, reviewer_id::text, status, due_on::text, created_at::text " +
                            "FROM iam.access_reviews WHERE tenant_id = ? AND deleted_at IS NULL " +
                            "ORDER BY created_at DESC LIMIT 100")) {
                ps.setObject(1, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getString(1));
                        row.put("cycleName", rs.getString(2));
                        row.put("reviewerId", rs.getString(3));
                        row.put("status", rs.getString(4));
                        row.put("dueOn", rs.getString(5));
                        row.put("createdAt", rs.getString(6));
                        items.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("items", items);
            out.put("count", items.size());
            return out;
        });
    }

    public Map<String, Object> listDelegations(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm09.view");
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT d.id::text, d.from_user_id::text, d.to_user_id::text, r.code, d.status, " +
                            "d.valid_from::text, d.valid_to::text, d.created_at::text " +
                            "FROM iam.delegations d " +
                            "JOIN iam.roles r ON r.id = d.role_id " +
                            "WHERE d.tenant_id = ? AND d.deleted_at IS NULL " +
                            "ORDER BY d.created_at DESC LIMIT 200")) {
                ps.setObject(1, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("delegationId", rs.getString(1));
                        row.put("fromUserId", rs.getString(2));
                        row.put("toUserId", rs.getString(3));
                        row.put("roleCode", rs.getString(4));
                        row.put("status", rs.getString(5));
                        row.put("validFrom", rs.getString(6));
                        row.put("validTo", rs.getString(7));
                        row.put("createdAt", rs.getString(8));
                        items.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("items", items);
            out.put("count", items.size());
            return out;
        });
    }

    public Map<String, Object> revokeDelegation(UserSecurityContext ctx, UUID delegationId) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm09.manage");
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE iam.delegations SET status = 'REVOKED', updated_at = now(), updated_by = ? " +
                            "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL AND status = 'ACTIVE'")) {
                ps.setObject(1, ctx.getUserId());
                ps.setObject(2, delegationId);
                ps.setObject(3, ctx.getTenantId());
                if (ps.executeUpdate() == 0) {
                    throw new InstituteAdminException(404, "IADM03_DELEGATION_NOT_FOUND", "Active delegation not found");
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("delegationId", delegationId.toString());
            out.put("status", "REVOKED");
            auditMutation(ctx, "revokeDelegation", "DELEGATION", delegationId.toString());
            return out;
        });
    }

    /** Auditor / admin read of role assignments (adm09.view). */
    public Map<String, Object> listRoleAssignments(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm09.view");
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT ra.id::text, ra.user_id::text, r.code, ra.college_id::text, ra.granted_by::text, ra.created_at::text " +
                            "FROM iam.role_assignments ra " +
                            "JOIN iam.roles r ON r.id = ra.role_id " +
                            "WHERE ra.tenant_id = ? AND ra.deleted_at IS NULL " +
                            "ORDER BY ra.created_at DESC LIMIT 200")) {
                ps.setObject(1, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("assignmentId", rs.getString(1));
                        row.put("userId", rs.getString(2));
                        row.put("roleCode", rs.getString(3));
                        row.put("collegeId", rs.getString(4));
                        row.put("grantedBy", rs.getString(5));
                        row.put("createdAt", rs.getString(6));
                        items.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("items", items);
            out.put("count", items.size());
            return out;
        });
    }

    public Map<String, Object> certifyAccessReview(UserSecurityContext ctx, UUID reviewId, String bodyJson) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm09.manage");
            Set<UUID> revokeIds = new HashSet<>();
            // Body-driven REVOKE decisions (comma-separated revokeAssignmentIds)
            String bodyRevokes = extractJsonString(bodyJson, "revokeAssignmentIds");
            if (bodyRevokes != null && !bodyRevokes.trim().isEmpty()) {
                for (String part : bodyRevokes.split(",")) {
                    String t = part.trim();
                    if (t.isEmpty()) continue;
                    try {
                        UUID aid = UUID.fromString(t);
                        revokeIds.add(aid);
                        try (PreparedStatement up = conn.prepareStatement(
                                "UPDATE iam.access_review_items SET decision = 'REVOKE', decided_at = now(), " +
                                        "updated_at = now(), updated_by = ? " +
                                        "WHERE review_id = ? AND tenant_id = ? AND role_assignment_id = ? AND deleted_at IS NULL")) {
                            up.setObject(1, ctx.getUserId());
                            up.setObject(2, reviewId);
                            up.setObject(3, ctx.getTenantId());
                            up.setObject(4, aid);
                            up.executeUpdate();
                        }
                    } catch (IllegalArgumentException ignored) {
                        // skip malformed ids
                    }
                }
            }
            // Existing items already marked REVOKE
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT role_assignment_id FROM iam.access_review_items " +
                            "WHERE review_id = ? AND tenant_id = ? AND deleted_at IS NULL " +
                            "AND UPPER(COALESCE(decision,'')) = 'REVOKE' AND role_assignment_id IS NOT NULL")) {
                ps.setObject(1, reviewId);
                ps.setObject(2, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Object o = rs.getObject(1);
                        if (o instanceof UUID) revokeIds.add((UUID) o);
                    }
                }
            }
            int revoked = 0;
            for (UUID assignmentId : revokeIds) {
                try (PreparedStatement del = conn.prepareStatement(
                        "UPDATE iam.role_assignments SET deleted_at = now(), updated_at = now(), updated_by = ? " +
                                "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL")) {
                    del.setObject(1, ctx.getUserId());
                    del.setObject(2, assignmentId);
                    del.setObject(3, ctx.getTenantId());
                    revoked += del.executeUpdate();
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE iam.access_reviews SET status = 'COMPLETED', completed_at = now(), reviewer_id = ?, " +
                            "updated_at = now(), updated_by = ? " +
                            "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL AND status IN ('PENDING','IN_PROGRESS')")) {
                ps.setObject(1, ctx.getUserId());
                ps.setObject(2, ctx.getUserId());
                ps.setObject(3, reviewId);
                ps.setObject(4, ctx.getTenantId());
                if (ps.executeUpdate() == 0) {
                    throw new InstituteAdminException(404, "IADM03_REVIEW_NOT_FOUND", "Pending access review not found");
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("reviewId", reviewId.toString());
            out.put("status", "COMPLETED");
            out.put("revokedAssignments", revoked);
            auditMutation(ctx, "certifyAccessReview", "ACCESS_REVIEW", reviewId.toString());
            return out;
        });
    }

    private Map<String, Object> applyRoleAssignFromPayload(UserSecurityContext ctx, Connection conn, String payload)
            throws java.sql.SQLException {
        String userIdStr = extractJsonString(payload, "userId");
        String roleCode = extractJsonString(payload, "roleCode");
        String collegeStr = extractJsonString(payload, "collegeId");
        if (userIdStr == null || roleCode == null || roleCode.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_PAYLOAD",
                    "ROLE_ASSIGN payload requires userId and roleCode");
        }
        UUID userId = UUID.fromString(userIdStr.trim());
        UUID collegeId = (collegeStr == null || collegeStr.trim().isEmpty()) ? null : UUID.fromString(collegeStr.trim());
        if (userId.equals(ctx.getUserId())) {
            throw new InstituteAdminException(403, "IADM03_SELF_GRANT", "Self-grant of roles is forbidden");
        }
        String code = roleCode.trim();
        if (!isPlatformAdmin(ctx, conn)) {
            assertCallerHoldsRole(conn, ctx.getTenantId(), ctx.getUserId(), code);
        }
        assertWithinCeiling(conn, ctx.getTenantId(), code);
        if (collegeId != null) {
            assertCollegeAssignableAfterHandover(ctx, conn, collegeId);
        }
        UUID roleId = lookupRoleId(conn, ctx.getTenantId(), code);
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO iam.role_assignments (id, tenant_id, user_id, role_id, college_id, granted_by, created_by) " +
                        "VALUES (?,?,?,?,?,?,?)")) {
            ps.setObject(1, id);
            ps.setObject(2, ctx.getTenantId());
            ps.setObject(3, userId);
            ps.setObject(4, roleId);
            ps.setObject(5, collegeId);
            ps.setObject(6, ctx.getUserId());
            ps.setObject(7, ctx.getUserId());
            ps.executeUpdate();
        }
        Map<String, Object> applied = new LinkedHashMap<>();
        applied.put("assignmentId", id.toString());
        applied.put("userId", userId.toString());
        applied.put("roleCode", code);
        return applied;
    }

    private Map<String, Object> applyRoleRevokeFromPayload(UserSecurityContext ctx, Connection conn, String payload)
            throws java.sql.SQLException {
        String assignmentIdStr = extractJsonString(payload, "assignmentId");
        if (assignmentIdStr == null || assignmentIdStr.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_PAYLOAD",
                    "ROLE_REVOKE payload requires assignmentId");
        }
        UUID assignmentId = UUID.fromString(assignmentIdStr.trim());
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE iam.role_assignments SET deleted_at = now(), updated_at = now(), updated_by = ? " +
                        "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, ctx.getUserId());
            ps.setObject(2, assignmentId);
            ps.setObject(3, ctx.getTenantId());
            if (ps.executeUpdate() == 0) {
                throw new InstituteAdminException(404, "IADM03_ASSIGNMENT_NOT_FOUND", "Role assignment not found");
            }
        }
        Map<String, Object> applied = new LinkedHashMap<>();
        applied.put("assignmentId", assignmentId.toString());
        applied.put("status", "REVOKED");
        return applied;
    }

    /**
     * Matrix §21.1: caller may grant {@code roleCode} only if one of their roles appears as
     * {@code grantor_role} for that {@code grantable_role} in {@code iam.role_grant_rules}.
     */
    private static void assertCallerHoldsRole(Connection conn, UUID tenantId, UUID callerId, String roleCode)
            throws java.sql.SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM iam.role_assignments ra " +
                        "JOIN iam.roles r ON r.id = ra.role_id AND r.deleted_at IS NULL " +
                        "JOIN iam.role_grant_rules g ON g.grantor_role = r.code AND g.grantable_role = ? " +
                        "  AND g.deleted_at IS NULL " +
                        "WHERE ra.tenant_id = ? AND ra.user_id = ? AND ra.deleted_at IS NULL LIMIT 1")) {
            ps.setString(1, roleCode);
            ps.setObject(2, tenantId);
            ps.setObject(3, callerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new InstituteAdminException(403, "IADM03_ABOVE_OWN",
                            "Caller grant table does not allow granting role " + roleCode);
                }
            }
        }
    }

    private static void assertWithinCeiling(Connection conn, UUID tenantId, String roleCode)
            throws java.sql.SQLException {
        Set<String> ceiling = new HashSet<>();
        boolean hasCeiling = false;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT allowed_permission_keys FROM iam.role_ceilings " +
                        "WHERE tenant_id = ? AND role_code = ? AND deleted_at IS NULL " +
                        "ORDER BY created_at DESC LIMIT 1")) {
            ps.setObject(1, tenantId);
            ps.setString(2, roleCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    hasCeiling = true;
                    Array arr = rs.getArray(1);
                    if (arr != null) {
                        ceiling.addAll(Arrays.asList((String[]) arr.getArray()));
                    }
                }
            }
        }
        if (!hasCeiling) return;
        // Allow if role code itself is listed in the ceiling codes
        if (ceiling.contains(roleCode)) return;
        UUID roleId = lookupRoleId(conn, tenantId, roleCode);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT p.code FROM iam.role_permissions rp " +
                        "JOIN iam.permissions p ON p.id = rp.permission_id AND p.deleted_at IS NULL " +
                        "WHERE rp.role_id = ? AND rp.tenant_id = ?")) {
            ps.setObject(1, roleId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString(1);
                    if (key != null && !ceiling.contains(key)) {
                        throw new InstituteAdminException(403, "IADM03_CEILING_EXCEEDED",
                                "Role " + roleCode + " exceeds ceiling (permission " + key + ")");
                    }
                }
            }
        }
    }

    private static boolean isPlatformAdmin(UserSecurityContext ctx, Connection conn) throws java.sql.SQLException {
        try {
            ctx.checkPlatformAdmin(conn);
            return true;
        } catch (UserProfileAccessDeniedException ignored) {
            return false;
        }
    }

    private static String extractJsonString(String json, String key) {
        if (json == null) return null;
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) return null;
        String rest = json.substring(colon + 1).trim();
        if (rest.startsWith("\"")) {
            int q2 = rest.indexOf('"', 1);
            return q2 < 0 ? null : rest.substring(1, q2);
        }
        int end = rest.length();
        for (int j = 0; j < rest.length(); j++) {
            char c = rest.charAt(j);
            if (c == ',' || c == '}' || c == ']') {
                end = j;
                break;
            }
        }
        String lit = rest.substring(0, end).trim();
        return "null".equalsIgnoreCase(lit) ? null : lit;
    }

    private void assertCollegeAssignableAfterHandover(UserSecurityContext ctx, java.sql.Connection conn, UUID collegeId)
            throws java.sql.SQLException {
        boolean platformAdmin = isPlatformAdmin(ctx, conn);
        if (platformAdmin) return;
        // Prefer SECURITY DEFINER helper (migration 20261121000800); fall back to table read
        boolean handedOver = false;
        try (PreparedStatement ps = conn.prepareStatement("SELECT plat.is_college_handed_over(?)")) {
            ps.setObject(1, collegeId);
            try (ResultSet rs = ps.executeQuery()) {
                handedOver = rs.next() && rs.getBoolean(1);
            }
        } catch (java.sql.SQLException fnMissing) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM plat.college_admin_handovers WHERE college_id = ? AND tenant_id = ? " +
                            "AND status = 'COMPLETED' AND deleted_at IS NULL LIMIT 1")) {
                ps.setObject(1, collegeId);
                ps.setObject(2, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    handedOver = rs.next();
                }
            }
        }
        if (handedOver) {
            throw new InstituteAdminException(403, "IADM03_HANDOVER_COMPLETE",
                    "College admin handover complete; Institution Admin cannot assign college users");
        }
    }

    private static UUID lookupRoleId(java.sql.Connection conn, UUID tenantId, String roleCode) throws java.sql.SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM iam.roles WHERE tenant_id = ? AND code = ? AND deleted_at IS NULL LIMIT 1")) {
            ps.setObject(1, tenantId);
            ps.setString(2, roleCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new InstituteAdminException(404, "IADM03_ROLE_NOT_FOUND", "Role not found: " + roleCode);
                }
                return (UUID) rs.getObject(1);
            }
        }
    }

    private static Instant parseInstant(String iso, Instant fallback) {
        if (iso == null || iso.trim().isEmpty()) return fallback;
        try {
            return Instant.parse(iso.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private void auditMutation(UserSecurityContext ctx, String action, String resourceType, String resourceId) {
        String tenant = ctx.getTenantId() == null ? null : ctx.getTenantId().toString();
        String user = ctx.getUserId() == null ? null : ctx.getUserId().toString();
        logger.info("IADM-03 action={} tenantId={} resourceType={} resourceId={}", action, tenant, resourceType, resourceId);
        logger.audit(AuditEvent.builder().action(action).tenantId(tenant).principalId(user)
                .resourceType(resourceType).resourceId(resourceId == null ? "N/A" : resourceId)
                .status("SUCCESS").severity("INFO").description(action).build());
    }
}
