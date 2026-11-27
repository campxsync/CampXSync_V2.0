package com.campx.iadm.access.service;

import com.campx.iadm.access.config.DatabaseConnectionManager;
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
 * IADM-03-US-008 — Access review campaigns.
 * R01 creates; R01 and R02 review (ATTEST / REVOKE -> governed request / EXCEPTION);
 * R03 sees own-institute status read-only; R28 reads all read-only.
 */
public class V2AccessReviewService {

    private static final int DEMO_REVIEW_CADENCE_DAYS = 90;

    public Map<String, Object> createCampaign(UserSecurityContext ctx,
                                              String title,
                                              String scopeTypeRaw,
                                              UUID scopeId,
                                              String dueAtIso,
                                              String correlationId) {
        if (title == null || title.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "Campaign title is required.");
        }
        String scopeType = (scopeTypeRaw == null || scopeTypeRaw.trim().isEmpty())
                ? (scopeId != null ? "INSTITUTE" : "PLATFORM")
                : scopeTypeRaw.trim().toUpperCase(Locale.ROOT);
        if (!"PLATFORM".equals(scopeType) && !"INSTITUTE".equals(scopeType)) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "scopeType must be PLATFORM or INSTITUTE.");
        }

        String cadenceSource = "EXPLICIT_DUE_DATE";
        Timestamp dueAt;
        if (dueAtIso != null && !dueAtIso.trim().isEmpty()) {
            try {
                dueAt = Timestamp.from(Instant.parse(dueAtIso.trim()));
            } catch (Exception ex) {
                throw new InstituteAdminException(400, "IADM03_VALIDATION", "dueAt must be ISO-8601.");
            }
        } else {
            cadenceSource = "DEMO_DEFAULT_" + DEMO_REVIEW_CADENCE_DAYS + "D";
            dueAt = Timestamp.from(Instant.now().plus(DEMO_REVIEW_CADENCE_DAYS, ChronoUnit.DAYS));
        }

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                String persona = CatalogueReadService.resolveCallerPersona(conn, ctx);
                if (!"R01".equals(persona)) {
                    DurableAuditWriter.appendWithRefs(conn, ctx, ctx.getTenantId(),
                            "IADM03_REVIEW_CREATE_DENIED", scopeLabel(ctx), "ACCESS_REVIEW_CAMPAIGN", null,
                            title, "403", correlationId, "Only R01 may create access review campaigns");
                    conn.commit();
                    throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                            "Only Super Admin (R01) may create access review campaigns.");
                }

                UUID campaignId = UUID.randomUUID();
                UUID tenantId = "INSTITUTE".equals(scopeType) ? scopeId : null;

                UUID auditId = DurableAuditWriter.appendWithRefs(conn, ctx, tenantId,
                        "IADM03_REVIEW_CAMPAIGN_CREATED",
                        tenantId != null ? "INSTITUTE:" + tenantId : "PLATFORM",
                        "ACCESS_REVIEW_CAMPAIGN", campaignId.toString(),
                        null, title.trim(), correlationId,
                        "Access review campaign created");

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO iadm03.access_review_campaigns " +
                                "(id, tenant_id, title, scope_type, scope_id, reviewers, due_at, cadence_source, status, created_by, audit_event_id) " +
                                "VALUES (?, ?, ?, ?, ?, '[\"R01\",\"R02\"]'::jsonb, ?, ?, 'OPEN', ?, ?)")) {
                    ps.setObject(1, campaignId);
                    ps.setObject(2, tenantId);
                    ps.setString(3, title.trim());
                    ps.setString(4, scopeType);
                    ps.setObject(5, scopeId);
                    ps.setTimestamp(6, dueAt);
                    ps.setString(7, cadenceSource);
                    ps.setObject(8, ctx.getUserId());
                    ps.setObject(9, auditId);
                    ps.executeUpdate();
                }

                int seededItems = populateItemsFromLiveGrants(conn, campaignId, tenantId);
                conn.commit();

                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", campaignId.toString());
                out.put("title", title.trim());
                out.put("scopeType", scopeType);
                out.put("cadenceSource", cadenceSource);
                out.put("dueAt", dueAt.toInstant().toString());
                out.put("itemCount", seededItems);
                out.put("status", "OPEN");
                out.put("auditEventId", auditId.toString());
                return out;
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new InstituteAdminException(500, "IADM03_DB_ERROR", "Unable to create access review campaign.");
        }
    }

    public Map<String, Object> listCampaigns(UserSecurityContext ctx, UUID campaignId, String correlationId) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                String persona = CatalogueReadService.resolveCallerPersona(conn, ctx);
                if (!("R01".equals(persona) || "R02".equals(persona) || "R03".equals(persona) || "R28".equals(persona))) {
                    throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                            "Role " + persona + " cannot view access reviews.");
                }
                if ("R03".equals(persona) && ctx.getTenantId() == null) {
                    throw new InstituteAdminException(403, "IADM03_SCOPE_REQUIRED",
                            "Institution Admin requires an authenticated institute scope.");
                }

                StringBuilder sql = new StringBuilder(
                        "SELECT id, tenant_id, title, scope_type, scope_id, reviewers::text, due_at, cadence_source, status, created_at " +
                                "FROM iadm03.access_review_campaigns WHERE 1=1 ");
                List<Object> params = new ArrayList<Object>();
                if ("R03".equals(persona)) {
                    sql.append("AND tenant_id = ? ");
                    params.add(ctx.getTenantId());
                }
                if (campaignId != null) {
                    sql.append("AND id = ? ");
                    params.add(campaignId);
                }
                sql.append("ORDER BY created_at DESC LIMIT 50");

                List<Map<String, Object>> campaigns = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> c = new LinkedHashMap<String, Object>();
                            UUID cid = (UUID) rs.getObject("id");
                            Timestamp dueAt = rs.getTimestamp("due_at");
                            c.put("id", cid.toString());
                            c.put("tenantId", rs.getObject("tenant_id") == null ? null : String.valueOf(rs.getObject("tenant_id")));
                            c.put("title", rs.getString("title"));
                            c.put("scopeType", rs.getString("scope_type"));
                            c.put("dueAt", dueAt.toInstant().toString());
                            c.put("cadenceSource", rs.getString("cadence_source"));
                            c.put("status", rs.getString("status"));
                            c.put("items", loadItems(conn, cid, dueAt));
                            campaigns.add(c);
                        }
                    }
                }
                conn.commit();
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("count", campaigns.size());
                out.put("canDecide", Boolean.valueOf("R01".equals(persona) || "R02".equals(persona)));
                out.put("campaigns", campaigns);
                return out;
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new InstituteAdminException(500, "IADM03_DB_ERROR", "Unable to list access reviews.");
        }
    }

    public Map<String, Object> decideItem(UserSecurityContext ctx,
                                          UUID campaignId,
                                          UUID itemId,
                                          String decisionRaw,
                                          String reason,
                                          String exceptionOwner,
                                          String exceptionExpiresAtIso,
                                          String correlationId) {
        if (campaignId == null || itemId == null || decisionRaw == null) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "campaignId, itemId, and decision are required.");
        }
        String d = decisionRaw.trim().toUpperCase(Locale.ROOT);
        if (!"ATTEST".equals(d) && !"REVOKE".equals(d) && !"EXCEPTION".equals(d)) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "decision must be ATTEST, REVOKE, or EXCEPTION.");
        }
        if ("EXCEPTION".equals(d)) {
            if (reason == null || reason.trim().isEmpty()
                    || exceptionOwner == null || exceptionOwner.trim().isEmpty()
                    || exceptionExpiresAtIso == null || exceptionExpiresAtIso.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM03_VALIDATION",
                        "EXCEPTION requires reason, exceptionOwner, and exceptionExpiresAt.");
            }
        }

        Timestamp excExp = null;
        if (exceptionExpiresAtIso != null && !exceptionExpiresAtIso.trim().isEmpty()) {
            try {
                excExp = Timestamp.from(Instant.parse(exceptionExpiresAtIso.trim()));
            } catch (Exception ex) {
                throw new InstituteAdminException(400, "IADM03_VALIDATION", "exceptionExpiresAt must be ISO-8601.");
            }
        }

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                String persona = CatalogueReadService.resolveCallerPersona(conn, ctx);
                if (!AccessPermissionMatrix.allowsIadm03(persona, "iadm03.review.manage")) {
                    DurableAuditWriter.appendWithRefs(conn, ctx, ctx.getTenantId(),
                            "IADM03_REVIEW_DECIDE_DENIED", scopeLabel(ctx), "ACCESS_REVIEW_ITEM", itemId.toString(),
                            d, "403", correlationId, "Review decision denied for " + persona);
                    conn.commit();
                    throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                            "Role " + persona + " cannot decide access review items.");
                }

                UUID subjectUserId = null;
                UUID itemTenantId = null;
                String roleCode = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT subject_user_id, tenant_id, role_code FROM iadm03.access_review_items WHERE id = ? AND campaign_id = ?")) {
                    ps.setObject(1, itemId);
                    ps.setObject(2, campaignId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new InstituteAdminException(404, "IADM03_NOT_FOUND", "Access review item not found.");
                        }
                        subjectUserId = (UUID) rs.getObject("subject_user_id");
                        itemTenantId = (UUID) rs.getObject("tenant_id");
                        roleCode = rs.getString("role_code");
                    }
                }

                if (ctx.getUserId().equals(subjectUserId)) {
                    DurableAuditWriter.appendWithRefs(conn, ctx, itemTenantId,
                            "IADM03_SELF_REVIEW_DENIED", scopeLabel(ctx), "ACCESS_REVIEW_ITEM", itemId.toString(),
                            d, "403", correlationId, "Reviewer cannot be the subject");
                    conn.commit();
                    throw new InstituteAdminException(403, "IADM03_SELF_REVIEW_DENIED",
                            "Reviewer cannot attest or decide their own access.");
                }

                UUID revocationRequestId = null;
                if ("REVOKE".equals(d)) {
                    revocationRequestId = UUID.randomUUID();
                    String reqReason = (reason == null || reason.trim().isEmpty())
                            ? "Access review revocation for " + roleCode : reason.trim();
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO iadm03.grant_requests " +
                                    "(id, tenant_id, requester_user_id, subject_user_id, target_kind, target_code, " +
                                    "scope_type, scope_id, reason, status, correlation_id) " +
                                    "VALUES (?, ?, ?, ?, 'ROLE', ?, ?, ?, ?, 'REQUESTED', ?)")) {
                        ps.setObject(1, revocationRequestId);
                        ps.setObject(2, itemTenantId);
                        ps.setObject(3, ctx.getUserId());
                        ps.setObject(4, subjectUserId);
                        ps.setString(5, roleCode + ":REVOKE");
                        ps.setString(6, itemTenantId != null ? "INSTITUTE" : "PLATFORM");
                        ps.setObject(7, itemTenantId);
                        ps.setString(8, reqReason);
                        ps.setString(9, correlationId);
                        ps.executeUpdate();
                    }
                }

                UUID auditId = DurableAuditWriter.appendWithRefs(conn, ctx, itemTenantId,
                        "IADM03_REVIEW_ITEM_DECIDED",
                        itemTenantId != null ? "INSTITUTE:" + itemTenantId : "PLATFORM",
                        "ACCESS_REVIEW_ITEM", itemId.toString(),
                        "PENDING", d, correlationId,
                        "Review item decided: " + d);

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE iadm03.access_review_items SET decision = ?, reviewer_user_id = ?, reason = ?, " +
                                "exception_owner = ?, exception_expires_at = ?, revocation_request_id = ?, " +
                                "decided_at = now(), audit_event_id = ? WHERE id = ?")) {
                    ps.setString(1, d);
                    ps.setObject(2, ctx.getUserId());
                    ps.setString(3, reason == null ? null : reason.trim());
                    ps.setString(4, exceptionOwner == null ? null : exceptionOwner.trim());
                    ps.setTimestamp(5, excExp);
                    ps.setObject(6, revocationRequestId);
                    ps.setObject(7, auditId);
                    ps.setObject(8, itemId);
                    ps.executeUpdate();
                }

                conn.commit();
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("itemId", itemId.toString());
                out.put("decision", d);
                out.put("revocationRequestId", revocationRequestId == null ? null : revocationRequestId.toString());
                out.put("auditEventId", auditId.toString());
                return out;
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new InstituteAdminException(500, "IADM03_DB_ERROR", "Unable to record review decision.");
        }
    }

    private int populateItemsFromLiveGrants(Connection conn, UUID campaignId, UUID tenantId) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT ra.tenant_id, ra.user_id, r.code AS role_code, ra.granted_by, ra.valid_to " +
                        "FROM iam.role_assignments ra " +
                        "JOIN iam.roles r ON r.id = ra.role_id AND r.deleted_at IS NULL " +
                        "WHERE ra.deleted_at IS NULL ");
        if (tenantId != null) {
            sql.append("AND ra.tenant_id = ? ");
        }
        sql.append("ORDER BY ra.created_at DESC LIMIT 25");

        int count = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (tenantId != null) {
                ps.setObject(1, tenantId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO iadm03.access_review_items " +
                                    "(campaign_id, tenant_id, subject_user_id, role_code, owning_service, scope_type, scope_id, grantor_user_id, valid_to, decision) " +
                                    "VALUES (?, ?, ?, ?, 'IAM-ROLE', 'INSTITUTE', ?, ?, ?, 'PENDING')")) {
                        UUID rowTenant = (UUID) rs.getObject("tenant_id");
                        ins.setObject(1, campaignId);
                        ins.setObject(2, rowTenant);
                        ins.setObject(3, rs.getObject("user_id"));
                        ins.setString(4, rs.getString("role_code"));
                        ins.setObject(5, rowTenant);
                        ins.setObject(6, rs.getObject("granted_by"));
                        ins.setTimestamp(7, rs.getTimestamp("valid_to"));
                        ins.executeUpdate();
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private List<Map<String, Object>> loadItems(Connection conn, UUID campaignId, Timestamp dueAt) throws SQLException {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        Instant now = Instant.now();
        boolean pastDue = dueAt != null && dueAt.toInstant().isBefore(now);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, tenant_id, subject_user_id, role_code, permission_key, owning_service, " +
                        "scope_type, scope_id, grantor_user_id, valid_to, decision, reviewer_user_id, " +
                        "reason, exception_owner, exception_expires_at, revocation_request_id, decided_at " +
                        "FROM iadm03.access_review_items WHERE campaign_id = ? ORDER BY created_at LIMIT 50")) {
            ps.setObject(1, campaignId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String dec = rs.getString("decision");
                    String effectiveDecision = ("PENDING".equals(dec) && pastDue) ? "OVERDUE" : dec;
                    Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("id", String.valueOf(rs.getObject("id")));
                    m.put("subjectUserId", String.valueOf(rs.getObject("subject_user_id")));
                    m.put("roleCode", rs.getString("role_code"));
                    m.put("owningService", rs.getString("owning_service"));
                    m.put("scopeType", rs.getString("scope_type"));
                    m.put("grantorUserId", rs.getObject("grantor_user_id") == null ? null : String.valueOf(rs.getObject("grantor_user_id")));
                    m.put("validTo", rs.getObject("valid_to") == null ? null : String.valueOf(rs.getObject("valid_to")));
                    m.put("lastUsed", null); // No trusted last-used source exists
                    m.put("decision", effectiveDecision);
                    m.put("reason", rs.getString("reason"));
                    m.put("exceptionOwner", rs.getString("exception_owner"));
                    m.put("revocationRequestId", rs.getObject("revocation_request_id") == null ? null : String.valueOf(rs.getObject("revocation_request_id")));
                    items.add(m);
                }
            }
        }
        return items;
    }

    private static String scopeLabel(UserSecurityContext ctx) {
        return ctx != null && ctx.getTenantId() != null ? "INSTITUTE:" + ctx.getTenantId() : "PLATFORM";
    }
}
