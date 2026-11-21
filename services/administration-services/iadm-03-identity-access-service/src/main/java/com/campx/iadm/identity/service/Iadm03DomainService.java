package com.campx.iadm.identity.service;

import com.campx.iadm.identity.config.DatabaseConnectionManager;
import com.campx.iadm.identity.exception.InstituteAdminException;
import com.campx.iadm.identity.exception.UserProfileAccessDeniedException;
import com.campx.iadm.identity.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                    "SELECT code, name FROM iam.roles WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY code LIMIT 100")) {
                ps.setObject(1, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("code", rs.getString(1));
                        r.put("name", rs.getString(2));
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

    public Map<String, Object> createAccessRequest(UserSecurityContext ctx, String type, String reason) {
        return ctx.executeInTransaction(db, conn -> {
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO iam.access_change_requests (id, tenant_id, requested_by, request_type, reason, status, created_by) " +
                            "VALUES (?,?,?,?,?,'PENDING',?)")) {
                ps.setObject(1, id);
                ps.setObject(2, ctx.getTenantId());
                ps.setObject(3, ctx.getUserId());
                ps.setString(4, type);
                ps.setString(5, reason);
                ps.setObject(6, ctx.getUserId());
                ps.executeUpdate();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requestId", id.toString());
            out.put("status", "PENDING");
            auditMutation(ctx, "createAccessRequest", "ACCESS_REQUEST", id.toString());
            return out;
        });
    }

    public Map<String, Object> decideAccessRequest(UserSecurityContext ctx, UUID requestId, String decision, String note) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm09.manage");
            String status = decision != null && decision.toUpperCase().startsWith("APP") ? "APPROVED" : "REJECTED";
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
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requestId", requestId.toString());
            out.put("status", status);
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
            if (collegeId != null) {
                assertCollegeAssignableAfterHandover(ctx, conn, collegeId);
            }
            UUID roleId = lookupRoleId(conn, ctx.getTenantId(), roleCode.trim());
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
            out.put("roleCode", roleCode.trim());
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
                            "AND status IN ('PENDING','IN_PROGRESS') ORDER BY created_at DESC LIMIT 100")) {
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

    public Map<String, Object> certifyAccessReview(UserSecurityContext ctx, UUID reviewId) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm09.manage");
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
            auditMutation(ctx, "certifyAccessReview", "ACCESS_REVIEW", reviewId.toString());
            return out;
        });
    }

    private void assertCollegeAssignableAfterHandover(UserSecurityContext ctx, java.sql.Connection conn, UUID collegeId)
            throws java.sql.SQLException {
        boolean platformAdmin = false;
        try {
            ctx.checkPlatformAdmin(conn);
            platformAdmin = true;
        } catch (UserProfileAccessDeniedException ignored) {
            // Institution Admin path — enforce handover gate
        }
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
