package com.campx.iadm.access.service;

import com.campx.iadm.access.config.DatabaseConnectionManager;
import com.campx.iadm.access.exception.InstituteAdminException;
import com.campx.iadm.access.exception.UserProfileAccessDeniedException;
import com.campx.iadm.access.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.Connection;
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
 * IADM-03 US-032 — manual access review campaigns. No scheduled cadence. No auto-revoke.
 * Decisions: KEEP (certify), MODIFY (flag), REVOKE (request revocation only).
 */
public class AccessReviewGovernanceService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AccessReviewGovernanceService.class);
    private static final String NOTIFICATION_NOTE =
            "Access change is audited durably. App notification is best-effort and may show as delayed; "
                    + "notifications never decide whether access is granted (US-034).";

    private final DatabaseConnectionManager db;

    public AccessReviewGovernanceService() {
        this(DatabaseConnectionManager.getInstance());
    }

    public AccessReviewGovernanceService(DatabaseConnectionManager db) {
        this.db = db == null ? DatabaseConnectionManager.getInstance() : db;
    }

    public Map<String, Object> list(final UserSecurityContext ctx, final UUID tenantFilter) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireReviewManage(conn, ctx);
                boolean platform = isPlatformOperator(conn, ctx);
                UUID tenant = resolveTenant(ctx, tenantFilter, platform);
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                String sql = "SELECT id, tenant_id, cycle_name, reviewer_id, status, due_on, completed_at, created_at "
                        + "FROM iam.access_reviews WHERE deleted_at IS NULL ";
                if (!platform) {
                    sql += "AND tenant_id = ? ";
                } else if (tenant != null) {
                    sql += "AND tenant_id = ? ";
                }
                sql += "ORDER BY created_at DESC LIMIT 50";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (!platform || tenant != null) {
                        ps.setObject(1, platform ? tenant : ctx.getTenantId());
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<String, Object>();
                            row.put("id", rs.getObject("id").toString());
                            row.put("tenantId", rs.getObject("tenant_id").toString());
                            row.put("cycleName", rs.getString("cycle_name"));
                            Object rev = rs.getObject("reviewer_id");
                            row.put("reviewerId", rev == null ? null : rev.toString());
                            row.put("status", rs.getString("status"));
                            row.put("dueOn", rs.getDate("due_on") == null ? null : rs.getDate("due_on").toString());
                            row.put("completedAt", rs.getTimestamp("completed_at") == null ? null
                                    : rs.getTimestamp("completed_at").toInstant().toString());
                            row.put("createdAt", rs.getTimestamp("created_at") == null ? null
                                    : rs.getTimestamp("created_at").toInstant().toString());
                            items.add(row);
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                out.put("count", Integer.valueOf(items.size()));
                out.put("cadence", "MANUAL_ONLY");
                out.put("autoRevoke", Boolean.FALSE);
                return out;
            }
        });
    }

    public Map<String, Object> start(final UserSecurityContext ctx,
                                     final UUID tenantId,
                                     final String cycleName,
                                     final String idempotencyKey,
                                     final String correlationId) {
        if (cycleName == null || cycleName.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "cycleName is required");
        }
        final String key = idempotencyKey == null || idempotencyKey.trim().isEmpty()
                ? UUID.randomUUID().toString() : idempotencyKey.trim();
        final String corr = correlationId == null || correlationId.trim().isEmpty()
                ? UUID.randomUUID().toString() : correlationId.trim();

        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireReviewManage(conn, ctx);
                boolean platform = isPlatformOperator(conn, ctx);
                UUID tenant = resolveTenant(ctx, tenantId, platform);
                if (tenant == null) {
                    throw new InstituteAdminException(400, "IADM03_VALIDATION", "tenantId is required");
                }
                if (!platform && (ctx.getTenantId() == null || !ctx.getTenantId().equals(tenant))) {
                    throw new InstituteAdminException(403, "IADM03_CROSS_INSTITUTE",
                            "You cannot start a review outside your institute.");
                }

                Map<String, Object> replay = findIdempotent(conn, key);
                if (replay != null) {
                    return replay;
                }

                UUID reviewerProfile = findProfileInTenant(conn, tenant, ctx.getUserId());
                UUID reviewId = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO iam.access_reviews "
                                + "(id, tenant_id, cycle_name, reviewer_id, status, created_by, updated_by) "
                                + "VALUES (?, ?, ?, ?, 'IN_PROGRESS', ?, ?)")) {
                    ps.setObject(1, reviewId);
                    ps.setObject(2, tenant);
                    ps.setString(3, cycleName.trim());
                    ps.setObject(4, reviewerProfile);
                    ps.setObject(5, ctx.getUserId());
                    ps.setObject(6, ctx.getUserId());
                    ps.executeUpdate();
                }

                int seeded = 0;
                try (PreparedStatement sel = conn.prepareStatement(
                        "SELECT ra.id FROM iam.role_assignments ra "
                                + "WHERE ra.tenant_id = ? AND ra.deleted_at IS NULL "
                                + "ORDER BY ra.created_at DESC LIMIT 100");
                     PreparedStatement ins = conn.prepareStatement(
                             "INSERT INTO iam.access_review_items "
                                     + "(id, tenant_id, review_id, role_assignment_id, created_by, updated_by) "
                                     + "VALUES (?, ?, ?, ?, ?, ?)")) {
                    sel.setObject(1, tenant);
                    try (ResultSet rs = sel.executeQuery()) {
                        while (rs.next()) {
                            UUID assignmentId = (UUID) rs.getObject(1);
                            ins.setObject(1, UUID.randomUUID());
                            ins.setObject(2, tenant);
                            ins.setObject(3, reviewId);
                            ins.setObject(4, assignmentId);
                            ins.setObject(5, ctx.getUserId());
                            ins.setObject(6, ctx.getUserId());
                            ins.addBatch();
                            seeded++;
                        }
                    }
                    if (seeded > 0) {
                        ins.executeBatch();
                    }
                }

                DurableAuditWriter.append(conn, ctx.withTenant(tenant), tenant,
                        "IADM03_ACCESS_REVIEW_STARTED", "ACCESS_REVIEW", reviewId.toString(),
                        "cycle=" + cycleName.trim() + "; items=" + seeded);

                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", reviewId.toString());
                out.put("tenantId", tenant.toString());
                out.put("cycleName", cycleName.trim());
                out.put("status", "IN_PROGRESS");
                out.put("itemCount", Integer.valueOf(seeded));
                out.put("autoRevoke", Boolean.FALSE);
                out.put("cadence", "MANUAL_ONLY");
                out.put("notificationDeferred", Boolean.TRUE);
                out.put("notificationMessage", NOTIFICATION_NOTE);
                out.put("idempotencyKey", key);
                out.put("correlationId", corr);
                out.put("replay", Boolean.FALSE);
                saveIdempotent(conn, key, "ACCESS_REVIEW_START", out, corr, tenant, ctx.getUserId());
                logger.info("IADM-03 access review started id={} items={}", reviewId, seeded);
                return out;
            }
        });
    }

    public Map<String, Object> get(final UserSecurityContext ctx, final UUID reviewId) {
        if (reviewId == null) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "reviewId is required");
        }
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireReviewManage(conn, ctx);
                Map<String, Object> review = loadReview(conn, ctx, reviewId);
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT i.id, i.role_assignment_id, i.decision, i.decided_at, i.note, "
                                + "r.code AS role_code, ra.user_id, ra.valid_from, ra.valid_to, ra.granted_by "
                                + "FROM iam.access_review_items i "
                                + "JOIN iam.role_assignments ra ON ra.id = i.role_assignment_id AND ra.tenant_id = i.tenant_id "
                                + "JOIN iam.roles r ON r.id = ra.role_id "
                                + "WHERE i.review_id = ? AND i.deleted_at IS NULL "
                                + "ORDER BY r.code, ra.user_id")) {
                    ps.setObject(1, reviewId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<String, Object>();
                            row.put("id", rs.getObject("id").toString());
                            row.put("roleAssignmentId", rs.getObject("role_assignment_id").toString());
                            row.put("roleCode", rs.getString("role_code"));
                            row.put("subjectUserId", rs.getObject("user_id").toString());
                            Object gb = rs.getObject("granted_by");
                            row.put("grantedBy", gb == null ? null : gb.toString());
                            row.put("validFrom", rs.getDate("valid_from") == null ? null : rs.getDate("valid_from").toString());
                            row.put("validTo", rs.getDate("valid_to") == null ? null : rs.getDate("valid_to").toString());
                            row.put("decision", rs.getString("decision"));
                            row.put("decidedAt", rs.getTimestamp("decided_at") == null ? null
                                    : rs.getTimestamp("decided_at").toInstant().toString());
                            row.put("note", rs.getString("note"));
                            row.put("source", "ROLE_ASSIGNMENT");
                            items.add(row);
                        }
                    }
                }
                // Attach active delegations in same tenant for reviewer context (no auto action)
                List<Map<String, Object>> delegations = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT d.id, d.from_user_id, d.to_user_id, r.code AS role_code, d.status, "
                                + "d.valid_from, d.valid_to "
                                + "FROM iam.delegations d "
                                + "LEFT JOIN iam.roles r ON r.id = d.role_id "
                                + "WHERE d.tenant_id = ? AND d.deleted_at IS NULL "
                                + "AND d.status = 'ACTIVE' LIMIT 50")) {
                    ps.setObject(1, UUID.fromString(String.valueOf(review.get("tenantId"))));
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> d = new LinkedHashMap<String, Object>();
                            d.put("id", rs.getObject("id").toString());
                            d.put("fromUserId", rs.getObject("from_user_id").toString());
                            d.put("toUserId", rs.getObject("to_user_id").toString());
                            d.put("roleCode", rs.getString("role_code"));
                            d.put("status", rs.getString("status"));
                            d.put("validFrom", rs.getDate("valid_from") == null ? null : rs.getDate("valid_from").toString());
                            d.put("validTo", rs.getDate("valid_to") == null ? null : rs.getDate("valid_to").toString());
                            delegations.add(d);
                        }
                    }
                } catch (SQLException ex) {
                    logger.warn("IADM-03 review delegation attach skipped: {}", ex.getMessage());
                }
                review.put("items", items);
                review.put("itemCount", Integer.valueOf(items.size()));
                review.put("delegations", delegations);
                review.put("autoRevoke", Boolean.FALSE);
                review.put("cadence", "MANUAL_ONLY");
                return review;
            }
        });
    }

    public Map<String, Object> decideItem(final UserSecurityContext ctx,
                                          final UUID reviewId,
                                          final UUID itemId,
                                          final String decision,
                                          final String note) {
        if (reviewId == null || itemId == null) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "reviewId and itemId are required");
        }
        if (decision == null || decision.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "decision is required");
        }
        final String dec = normalizeDecision(decision);
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireReviewManage(conn, ctx);
                Map<String, Object> review = loadReview(conn, ctx, reviewId);
                String status = String.valueOf(review.get("status"));
                if ("COMPLETED".equals(status) || "EXPIRED".equals(status)) {
                    throw new InstituteAdminException(409, "IADM03_STATE",
                            "This review is closed (" + status + ").");
                }
                UUID tenant = UUID.fromString(String.valueOf(review.get("tenantId")));

                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE iam.access_review_items SET decision = ?, decided_at = now(), note = ?, "
                                + "updated_at = now(), updated_by = ?, row_version = row_version + 1 "
                                + "WHERE id = ? AND review_id = ? AND deleted_at IS NULL")) {
                    upd.setString(1, dec);
                    upd.setString(2, note == null ? null : note.trim());
                    upd.setObject(3, ctx.getUserId());
                    upd.setObject(4, itemId);
                    upd.setObject(5, reviewId);
                    if (upd.executeUpdate() == 0) {
                        throw new InstituteAdminException(404, "IADM03_NOT_FOUND", "Review item not found.");
                    }
                }

                // Never auto-revoke — REVOKE is a request recorded only
                DurableAuditWriter.append(conn, ctx.withTenant(tenant), tenant,
                        "IADM03_ACCESS_REVIEW_DECIDED", "ACCESS_REVIEW_ITEM", itemId.toString(),
                        "decision=" + dec + "; review=" + reviewId
                                + (note == null || note.trim().isEmpty() ? "" : "; " + note.trim())
                                + "; autoRevoke=false");

                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("reviewId", reviewId.toString());
                out.put("itemId", itemId.toString());
                out.put("decision", dec);
                out.put("autoRevoke", Boolean.FALSE);
                out.put("revocationApplied", Boolean.FALSE);
                out.put("notificationDeferred", Boolean.TRUE);
                out.put("notificationMessage", NOTIFICATION_NOTE);
                if ("REVOKE".equals(dec)) {
                    out.put("message", "Revocation requested and audited. No automatic revoke was applied.");
                } else if ("KEEP".equals(dec)) {
                    out.put("message", "Grant certified (KEEP).");
                } else {
                    out.put("message", "Grant flagged (MODIFY).");
                }
                return out;
            }
        });
    }

    public Map<String, Object> complete(final UserSecurityContext ctx, final UUID reviewId, final String reason) {
        if (reviewId == null) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "reviewId is required");
        }
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireReviewManage(conn, ctx);
                Map<String, Object> review = loadReview(conn, ctx, reviewId);
                UUID tenant = UUID.fromString(String.valueOf(review.get("tenantId")));
                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE iam.access_reviews SET status = 'COMPLETED', completed_at = now(), "
                                + "updated_at = now(), updated_by = ?, row_version = row_version + 1 "
                                + "WHERE id = ? AND deleted_at IS NULL")) {
                    upd.setObject(1, ctx.getUserId());
                    upd.setObject(2, reviewId);
                    upd.executeUpdate();
                }
                DurableAuditWriter.append(conn, ctx.withTenant(tenant), tenant,
                        "IADM03_ACCESS_REVIEW_COMPLETED", "ACCESS_REVIEW", reviewId.toString(),
                        reason == null || reason.trim().isEmpty() ? "completed" : reason.trim());
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", reviewId.toString());
                out.put("status", "COMPLETED");
                out.put("autoRevoke", Boolean.FALSE);
                out.put("notificationDeferred", Boolean.TRUE);
                out.put("notificationMessage", NOTIFICATION_NOTE);
                return out;
            }
        });
    }

    public Map<String, Object> notificationContract() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("audit", "REQUIRED_SAME_TX — failure blocks success (US-004/US-034)");
        out.put("notification", "BEST_EFFORT — may be delayed; never grants or denies access");
        out.put("stoppedNotificationService", "Change still succeeds; UI shows notification is delayed");
        out.put("stoppedAuditService", "Change must not report success");
        out.put("message", NOTIFICATION_NOTE);
        return out;
    }

    private String normalizeDecision(String decision) {
        String d = decision.trim().toUpperCase(Locale.ROOT);
        if ("CERTIFY".equals(d) || "KEEP".equals(d)) {
            return "KEEP";
        }
        if ("FLAG".equals(d) || "MODIFY".equals(d)) {
            return "MODIFY";
        }
        if ("REQUEST_REVOCATION".equals(d) || "REVOKE".equals(d)) {
            return "REVOKE";
        }
        throw new InstituteAdminException(400, "IADM03_VALIDATION",
                "decision must be CERTIFY/KEEP, FLAG/MODIFY, or REQUEST_REVOCATION/REVOKE");
    }

    private Map<String, Object> loadReview(Connection conn, UserSecurityContext ctx, UUID reviewId)
            throws SQLException {
        boolean platform = isPlatformOperator(conn, ctx);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, tenant_id, cycle_name, reviewer_id, status, due_on, completed_at "
                        + "FROM iam.access_reviews WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, reviewId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new InstituteAdminException(404, "IADM03_NOT_FOUND", "Access review not found.");
                }
                UUID tenant = (UUID) rs.getObject("tenant_id");
                if (!platform && (ctx.getTenantId() == null || !ctx.getTenantId().equals(tenant))) {
                    throw new InstituteAdminException(403, "IADM03_CROSS_INSTITUTE",
                            "This review is outside your institute scope.");
                }
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("id", rs.getObject("id").toString());
                row.put("tenantId", tenant.toString());
                row.put("cycleName", rs.getString("cycle_name"));
                Object rev = rs.getObject("reviewer_id");
                row.put("reviewerId", rev == null ? null : rev.toString());
                row.put("status", rs.getString("status"));
                row.put("dueOn", rs.getDate("due_on") == null ? null : rs.getDate("due_on").toString());
                row.put("completedAt", rs.getTimestamp("completed_at") == null ? null
                        : rs.getTimestamp("completed_at").toInstant().toString());
                return row;
            }
        }
    }

    private UUID findProfileInTenant(Connection conn, UUID tenant, UUID userId) throws SQLException {
        if (userId == null) {
            return null;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM iam.user_profiles WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, tenant);
            ps.setObject(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject(1);
                }
            }
        }
        return null;
    }

    private UUID resolveTenant(UserSecurityContext ctx, UUID tenantFilter, boolean platform) {
        if (tenantFilter != null) {
            return tenantFilter;
        }
        return ctx.getTenantId();
    }

    private void requireReviewManage(Connection conn, UserSecurityContext ctx) throws SQLException {
        if (isPlatformOperator(conn, ctx)) {
            return;
        }
        try {
            ctx.checkPermission(conn, "iam.review.manage");
        } catch (UserProfileAccessDeniedException ex) {
            throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                    "You are not authorized for this action (iam.review.manage).");
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

    private Map<String, Object> findIdempotent(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT result_payload::text FROM iam.governance_idempotency "
                        + "WHERE idempotency_key = ? AND deleted_at IS NULL")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("replay", Boolean.TRUE);
                out.put("raw", rs.getString(1));
                out.put("message", "Idempotent replay of access review start.");
                return out;
            }
        }
    }

    private void saveIdempotent(Connection conn, String key, String op, Map<String, Object> result,
                                String corr, UUID tenant, UUID actor) throws SQLException {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : result.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(e.getKey().replace("\"", "")).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Boolean || v instanceof Number) {
                sb.append(v.toString());
            } else {
                sb.append('"').append(String.valueOf(v).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            }
        }
        sb.append('}');
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO iam.governance_idempotency "
                        + "(idempotency_key, operation, status, result_payload, correlation_id, actor_user_id, "
                        + "tenant_id, created_by, updated_by) "
                        + "VALUES (?, ?, 'COMPLETED', ?::jsonb, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (idempotency_key) DO NOTHING")) {
            ps.setString(1, key);
            ps.setString(2, op);
            ps.setString(3, sb.toString());
            ps.setString(4, corr);
            ps.setObject(5, actor);
            ps.setObject(6, tenant);
            ps.setObject(7, actor);
            ps.setObject(8, actor);
            ps.executeUpdate();
        }
    }
}
