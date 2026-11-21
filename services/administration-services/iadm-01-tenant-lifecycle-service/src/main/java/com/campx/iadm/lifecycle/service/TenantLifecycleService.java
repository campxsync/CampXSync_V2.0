package com.campx.iadm.lifecycle.service;

import com.campx.iadm.lifecycle.config.DatabaseConnectionManager;
import com.campx.iadm.lifecycle.exception.InstituteAdminException;
import com.campx.iadm.lifecycle.exception.UserProfileAccessDeniedException;
import com.campx.iadm.lifecycle.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-01 Institute &amp; Tenant Lifecycle domain service.
 * <p>
 * Permission keys: adm01.manage / adm01.write / adm01.view (never role names). Successful mutations
 * are audited through {@link CampXLogger} per Agreement &#167;3.2.
 */
public class TenantLifecycleService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(TenantLifecycleService.class);

    private final DatabaseConnectionManager db = DatabaseConnectionManager.getInstance();

    public Map<String, Object> registerInstitute(UserSecurityContext ctx, String code, String name, String region) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            if (code == null || code.trim().isEmpty() || name == null || name.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM01_VALIDATION", "code and name are required");
            }
            try (PreparedStatement dup = conn.prepareStatement(
                    "SELECT 1 FROM core.tenants WHERE code = ? AND deleted_at IS NULL")) {
                dup.setString(1, code.trim());
                try (ResultSet rs = dup.executeQuery()) {
                    if (rs.next()) {
                        throw new InstituteAdminException(409, "IADM01_DUPLICATE_CODE", "Institute code already exists");
                    }
                }
            }
            UUID id = UUID.randomUUID();
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO core.tenants (id, code, name, status, created_by) VALUES (?, ?, ?, 'ACTIVE', ?)")) {
                ins.setObject(1, id);
                ins.setString(2, code.trim());
                ins.setString(3, name.trim());
                ins.setObject(4, ctx.getUserId());
                ins.executeUpdate();
            }
            appendEvent(conn, id, "CREATE", ctx.getUserId(), "Institute registered",
                    "{\"code\":\"" + escape(code) + "\",\"region\":\"" + escape(region == null ? "" : region) + "\"}");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("tenantId", id.toString());
            out.put("code", code.trim());
            out.put("name", name.trim());
            out.put("status", "ACTIVE");
            out.put("region", region);
            auditMutation(ctx, "registerInstitute", "TENANT", id.toString());
            return out;
        });
    }

    public Map<String, Object> appointInstitutionAdmin(UserSecurityContext ctx, UUID tenantId, UUID userId) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            if (tenantId == null || userId == null) {
                throw new InstituteAdminException(400, "IADM01_VALIDATION", "tenantId and userId required");
            }
            if (userId.equals(ctx.getUserId())) {
                throw new InstituteAdminException(403, "IADM01_SELF_APPOINT", "Cannot appoint self as Institution Admin");
            }
            // Caller identity from auth.uid() via SET LOCAL ROLE + jwt claims; p_assigned_by ignored by DB
            UUID assignmentId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT plat.assign_institution_admin(?, ?, ?)")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, userId);
                ps.setObject(3, ctx.getUserId()); // ignored by hardened function
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new InstituteAdminException(500, "IADM01_APPOINT", "No assignment returned");
                    assignmentId = (UUID) rs.getObject(1);
                }
            }
            appendEvent(conn, tenantId, "APPOINT", ctx.getUserId(), "Appointed Institution Admin (R03)",
                    "{\"userId\":\"" + userId + "\",\"assignmentId\":\"" + assignmentId + "\"}");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("assignmentId", assignmentId.toString());
            out.put("tenantId", tenantId.toString());
            out.put("userId", userId.toString());
            auditMutation(ctx, "appointInstitutionAdmin", "TENANT", tenantId.toString());
            return out;
        });
    }

    public Map<String, Object> getInstitute(UserSecurityContext ctx, UUID tenantId) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm01.view");
            UUID requested = tenantId != null ? tenantId : ctx.getTenantId();
            // IADM-01-US-005: Institution Admin may only view own institute (platform admins may cross-read)
            if (!ctx.isPlatformScope() && ctx.getTenantId() != null && requested != null
                    && !requested.equals(ctx.getTenantId())) {
                boolean platform = false;
                try {
                    ctx.checkPlatformAdmin(conn);
                    platform = true;
                } catch (UserProfileAccessDeniedException ignored) {
                    // not platform
                }
                if (!platform) {
                    throw new InstituteAdminException(403, "IADM01_OWN_TENANT_ONLY",
                            "Institution Admin may only view own institute profile");
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, code, name, status, created_at, updated_at FROM core.tenants WHERE id = ? AND deleted_at IS NULL")) {
                ps.setObject(1, requested);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new InstituteAdminException(404, "IADM01_NOT_FOUND", "Institute not found");
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("tenantId", rs.getObject("id").toString());
                    out.put("code", rs.getString("code"));
                    out.put("name", rs.getString("name"));
                    out.put("status", rs.getString("status"));
                    out.put("createdAt", rs.getTimestamp("created_at"));
                    out.put("updatedAt", rs.getTimestamp("updated_at"));
                    return out;
                }
            }
        });
    }

    public Map<String, Object> onboardCollege(UserSecurityContext ctx, String collegeCode, String collegeName,
                                              UUID firstCollegeAdminUserId) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm01.manage");
            UUID tenantId = ctx.getTenantId();
            if (tenantId == null) throw new InstituteAdminException(400, "IADM01_VALIDATION", "tenant required");
            if (collegeCode == null || collegeCode.trim().isEmpty() || collegeName == null || collegeName.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM01_VALIDATION", "collegeCode and collegeName are required");
            }
            // SEC-11 / FINDING-IADM-ARCH-01: first College Admin (R04) must be Auth-linked
            if (firstCollegeAdminUserId == null) {
                throw new InstituteAdminException(400, "IADM01_AUTH_USER_REQUIRED",
                        "First College Admin userId is required and must exist in auth.users");
            }
            assertAuthLinkedUser(conn, firstCollegeAdminUserId);
            assertTenantActive(conn, tenantId);
            UUID collegeId = UUID.randomUUID();
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO core.colleges (id, tenant_id, code, name, status, provisioning_status, created_by) " +
                            "VALUES (?, ?, ?, ?, 'ACTIVE', 'ACTIVE', ?)")) {
                ins.setObject(1, collegeId);
                ins.setObject(2, tenantId);
                ins.setString(3, collegeCode.trim());
                ins.setString(4, collegeName.trim());
                ins.setObject(5, ctx.getUserId());
                ins.executeUpdate();
            }
            UUID roleId = lookupRole(conn, tenantId, "COLLEGE_ADMIN");
            try (PreparedStatement ra = conn.prepareStatement(
                    "INSERT INTO iam.role_assignments (tenant_id, user_id, role_id, college_id, created_by) " +
                            "VALUES (?, ?, ?, ?, ?)")) {
                ra.setObject(1, tenantId);
                ra.setObject(2, firstCollegeAdminUserId);
                ra.setObject(3, roleId);
                ra.setObject(4, collegeId);
                ra.setObject(5, ctx.getUserId());
                ra.executeUpdate();
            }
            UUID handoverId = insertHandover(conn, tenantId, collegeId, firstCollegeAdminUserId, ctx.getUserId(), "PENDING");
            appendEvent(conn, tenantId, "HANDOVER", ctx.getUserId(), "College onboarded",
                    "{\"collegeId\":\"" + collegeId + "\"}");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("collegeId", collegeId.toString());
            out.put("tenantId", tenantId.toString());
            out.put("handoverId", handoverId != null ? handoverId.toString() : null);
            out.put("firstCollegeAdminUserId", firstCollegeAdminUserId != null ? firstCollegeAdminUserId.toString() : null);
            auditMutation(ctx, "onboardCollege", "COLLEGE", collegeId.toString());
            return out;
        });
    }

    public Map<String, Object> completeHandover(UserSecurityContext ctx, UUID collegeId, String notes) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm01.manage");
            UUID tenantId = ctx.getTenantId();
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE plat.college_admin_handovers SET status = 'COMPLETED', notes = ?, updated_at = now(), updated_by = ? " +
                            "WHERE tenant_id = ? AND college_id = ? AND deleted_at IS NULL RETURNING id, first_college_admin_user_id")) {
                ps.setString(1, notes);
                ps.setObject(2, ctx.getUserId());
                ps.setObject(3, tenantId);
                ps.setObject(4, collegeId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(404, "IADM01_HANDOVER_NOT_FOUND", "No handover record for college");
                    }
                    // IADM-01-US-004: after COMPLETED, R03 loses direct college user-admin
                    // (enforced by plat.is_college_handed_over / IADM-03 assignRole gate)
                    appendEvent(conn, tenantId, "HANDOVER", ctx.getUserId(), "Handover completed to College Admin (R04)",
                            "{\"collegeId\":\"" + collegeId + "\",\"r03CollegeUserAdmin\":\"REVOKED\"}");
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("handoverId", rs.getObject("id").toString());
                    out.put("collegeId", collegeId.toString());
                    out.put("firstCollegeAdminUserId", rs.getObject("first_college_admin_user_id").toString());
                    out.put("status", "COMPLETED");
                    out.put("r03CollegeUserAdmin", "REVOKED");
                    auditMutation(ctx, "completeHandover", "COLLEGE", collegeId.toString());
                    return out;
                }
            }
        });
    }

    /**
     * IADM-01-US-004 read-back: whether Institution Admin may still assign college-scoped users.
     */
    public Map<String, Object> collegeUserAdminBoundary(UserSecurityContext ctx, UUID collegeId) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm01.view");
            boolean handedOver;
            try (PreparedStatement ps = conn.prepareStatement("SELECT plat.is_college_handed_over(?)")) {
                ps.setObject(1, collegeId);
                try (ResultSet rs = ps.executeQuery()) {
                    handedOver = rs.next() && rs.getBoolean(1);
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("collegeId", collegeId.toString());
            out.put("handedOver", handedOver);
            out.put("r03CollegeUserAdminAllowed", !handedOver);
            return out;
        });
    }

    public Map<String, Object> suspendOrReactivate(UserSecurityContext ctx, UUID tenantId, String action, String reason) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            if (reason == null || reason.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM01_REASON_REQUIRED", "Reason is required");
            }
            boolean suspending = "suspend".equalsIgnoreCase(action);
            String status = suspending ? "SUSPENDED" : "ACTIVE";
            String event = suspending ? "SUSPEND" : "REACTIVATE";
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE core.tenants SET status = ?, updated_at = now(), updated_by = ? WHERE id = ? AND deleted_at IS NULL")) {
                ps.setString(1, status);
                ps.setObject(2, ctx.getUserId());
                ps.setObject(3, tenantId);
                if (ps.executeUpdate() == 0) {
                    throw new InstituteAdminException(404, "IADM01_NOT_FOUND", "Institute not found");
                }
            }
            // IADM-01-US-006: record session block so suspended tenants fail closed on subsequent ops
            try (PreparedStatement blk = conn.prepareStatement(
                    "INSERT INTO plat.tenant_session_blocks (tenant_id, blocked, reason, actor_id, created_by) VALUES (?,?,?,?,?)")) {
                blk.setObject(1, tenantId);
                blk.setBoolean(2, suspending);
                blk.setString(3, reason);
                blk.setObject(4, ctx.getUserId());
                blk.setObject(5, ctx.getUserId());
                blk.executeUpdate();
            }
            appendEvent(conn, tenantId, event, ctx.getUserId(), reason,
                    "{\"sessionsBlocked\":" + suspending + "}");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("tenantId", tenantId.toString());
            out.put("status", status);
            out.put("reason", reason);
            out.put("sessionsBlocked", suspending);
            auditMutation(ctx, action, "TENANT", tenantId.toString());
            return out;
        });
    }

    public Map<String, Object> createLifecycleRequest(UserSecurityContext ctx, String requestType, String reason) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm01.write");
            if (reason == null || reason.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM01_REASON_REQUIRED", "Reason is required");
            }
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO plat.institute_lifecycle_requests " +
                            "(id, tenant_id, request_type, requested_by, status, reason, created_by) " +
                            "VALUES (?, ?, ?, ?, 'PENDING', ?, ?)")) {
                ps.setObject(1, id);
                ps.setObject(2, ctx.getTenantId());
                ps.setString(3, requestType);
                ps.setObject(4, ctx.getUserId());
                ps.setString(5, reason);
                ps.setObject(6, ctx.getUserId());
                ps.executeUpdate();
            }
            String eventType = "EXPORT".equals(requestType) ? "EXPORT_REQUEST" : "DECOMMISSION_REQUEST";
            if ("SUSPEND".equals(requestType)) eventType = "DECOMMISSION_REQUEST";
            appendEvent(conn, ctx.getTenantId(), eventType, ctx.getUserId(), reason, "{\"requestId\":\"" + id + "\"}");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requestId", id.toString());
            out.put("status", "PENDING");
            out.put("requestType", requestType);
            auditMutation(ctx, "createLifecycleRequest", "LIFECYCLE_REQUEST", id.toString());
            return out;
        });
    }

    public Map<String, Object> decideLifecycleRequest(UserSecurityContext ctx, UUID requestId, String decision, String note) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            String status = "APPROVE".equalsIgnoreCase(decision) || "APPROVED".equalsIgnoreCase(decision) ? "APPROVED" : "REJECTED";
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE plat.institute_lifecycle_requests SET status = ?, decided_by = ?, decided_at = now(), " +
                            "decision_note = ?, updated_at = now(), updated_by = ? " +
                            "WHERE id = ? AND status = 'PENDING' AND deleted_at IS NULL " +
                            "RETURNING tenant_id, request_type")) {
                ps.setString(1, status);
                ps.setObject(2, ctx.getUserId());
                ps.setString(3, note);
                ps.setObject(4, ctx.getUserId());
                ps.setObject(5, requestId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(404, "IADM01_REQUEST_NOT_FOUND", "Pending request not found");
                    }
                    UUID tenantId = (UUID) rs.getObject("tenant_id");
                    String type = rs.getString("request_type");
                    if ("APPROVED".equals(status) && "SUSPEND".equals(type)) {
                        try (PreparedStatement up = conn.prepareStatement(
                                "UPDATE core.tenants SET status = 'SUSPENDED', updated_at = now(), updated_by = ? WHERE id = ?")) {
                            up.setObject(1, ctx.getUserId());
                            up.setObject(2, tenantId);
                            up.executeUpdate();
                        }
                        appendEvent(conn, tenantId, "SUSPEND", ctx.getUserId(), note == null ? "Approved suspend request" : note, "{}");
                    }
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("requestId", requestId.toString());
                    out.put("status", status);
                    out.put("tenantId", tenantId.toString());
                    auditMutation(ctx, "decideLifecycleRequest", "LIFECYCLE_REQUEST", requestId.toString());
                    return out;
                }
            }
        });
    }

    public List<Map<String, Object>> lifecycleHistory(UserSecurityContext ctx, UUID tenantId) {
        return ctx.executeInTransaction(db, conn -> {
            // IADM-01-US-008: Auditor (R28) via adm01.view; platform admin fallback
            try {
                ctx.checkPermission(conn, "adm01.view");
            } catch (UserProfileAccessDeniedException ex) {
                ctx.checkPlatformAdmin(conn);
            }
            UUID tid = tenantId != null ? tenantId : ctx.getTenantId();
            List<Map<String, Object>> rows = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, event_type, actor_id, reason, payload, created_at " +
                            "FROM plat.institute_lifecycle_events WHERE tenant_id = ? ORDER BY created_at DESC LIMIT 200")) {
                ps.setObject(1, tid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getObject("id").toString());
                        row.put("eventType", rs.getString("event_type"));
                        Object actor = rs.getObject("actor_id");
                        row.put("actorId", actor != null ? actor.toString() : null);
                        row.put("reason", rs.getString("reason"));
                        row.put("payload", rs.getString("payload"));
                        Timestamp ts = rs.getTimestamp("created_at");
                        row.put("createdAt", ts != null ? ts.toInstant().toString() : null);
                        rows.add(row);
                    }
                }
            }
            return rows;
        });
    }

    /**
     * SEC-11: first College Admin must already exist in Supabase Auth ({@code auth.users}).
     * Fail closed — never mint a college admin profile without a login identity.
     */
    private static void assertAuthLinkedUser(Connection conn, UUID userId) throws SQLException {
        // authenticated cannot SELECT auth.users — use SECURITY DEFINER helper (migration 20261121000700)
        try (PreparedStatement ps = conn.prepareStatement("SELECT plat.is_auth_linked_user(?)")) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !rs.getBoolean(1)) {
                    throw new InstituteAdminException(400, "IADM01_AUTH_USER_REQUIRED",
                            "First College Admin must be a linked Auth user (SEC-11)");
                }
            }
        }
    }

    private static void assertTenantActive(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT status FROM core.tenants WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new InstituteAdminException(404, "IADM01_NOT_FOUND", "Institute not found");
                if ("SUSPENDED".equals(rs.getString("status"))) {
                    throw new InstituteAdminException(403, "IADM01_SUSPENDED", "Institute is suspended");
                }
            }
        }
    }

    private static UUID lookupRole(Connection conn, UUID tenantId, String code) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM iam.roles WHERE tenant_id = ? AND code = ? AND deleted_at IS NULL LIMIT 1")) {
            ps.setObject(1, tenantId);
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new InstituteAdminException(500, "IADM01_ROLE_MISSING", code + " role missing");
                return (UUID) rs.getObject(1);
            }
        }
    }

    private static UUID insertHandover(Connection conn, UUID tenantId, UUID collegeId, UUID adminId, UUID by, String status)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO plat.college_admin_handovers " +
                        "(id, tenant_id, college_id, first_college_admin_user_id, handed_over_by, status, created_by) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, collegeId);
            ps.setObject(4, adminId);
            ps.setObject(5, by);
            ps.setString(6, status);
            ps.setObject(7, by);
            ps.executeUpdate();
        }
        return id;
    }

    private static void appendEvent(Connection conn, UUID tenantId, String type, UUID actor, String reason, String payloadJson)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO plat.institute_lifecycle_events (tenant_id, event_type, actor_id, reason, payload, created_by) " +
                        "VALUES (?, ?, ?, ?, ?::jsonb, ?)")) {
            ps.setObject(1, tenantId);
            ps.setString(2, type);
            ps.setObject(3, actor);
            ps.setString(4, reason);
            ps.setString(5, payloadJson == null ? "{}" : payloadJson);
            ps.setObject(6, actor);
            ps.executeUpdate();
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void auditMutation(UserSecurityContext ctx, String action, String resourceType, String resourceId) {
        String tenant = ctx.getTenantId() == null ? null : ctx.getTenantId().toString();
        String user = ctx.getUserId() == null ? null : ctx.getUserId().toString();
        logger.info("IADM-01 action={} tenantId={} resourceType={} resourceId={}", action, tenant, resourceType, resourceId);
        logger.audit(AuditEvent.builder().action(action).tenantId(tenant).principalId(user)
                .resourceType(resourceType).resourceId(resourceId == null ? "N/A" : resourceId)
                .status("SUCCESS").severity("INFO").description(action).build());
    }
}
