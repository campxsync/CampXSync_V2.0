package com.campx.iadm.billing.service;

import com.campx.iadm.billing.security.UserSecurityContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Best-effort billing notices (US-038). Billing state never depends on notice delivery.
 * When notifications are disabled, notice is stored as DELAYED; flush delivers on re-enable.
 */
public final class BillingNoticeService {

    private BillingNoticeService() {
    }

    /** Record a notice for a billing event. Does not change billing rows. */
    public static Map<String, Object> enqueue(Connection conn, UserSecurityContext ctx, UUID tenantId,
                                              String noticeType, String entityType, String entityId,
                                              String message) throws SQLException {
        String status = BillingOutageFlags.isNotificationsDisabled() ? "DELAYED" : "DELIVERED";
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO plat.billing_notices ("
                        + "tenant_id, notice_type, entity_type, entity_id, message, status, demo_label, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'DEMO', ?) RETURNING id, status, created_at")) {
            ps.setObject(1, tenantId);
            ps.setString(2, noticeType);
            ps.setString(3, entityType);
            ps.setString(4, entityId);
            ps.setString(5, message);
            ps.setString(6, status);
            ps.setObject(7, ctx != null ? ctx.getUserId() : null);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", String.valueOf(rs.getObject("id")));
                out.put("status", rs.getString("status"));
                out.put("noticeType", noticeType);
                out.put("notificationDeferred", Boolean.valueOf("DELAYED".equals(status)));
                out.put("billingStateUnchangedByNotice", Boolean.TRUE);
                if ("DELAYED".equals(status)) {
                    out.put("notificationMessage",
                            "Billing notice is delayed because notifications are disabled. Billing state is unchanged.");
                } else {
                    out.put("notificationMessage", "Billing notice delivered (DEMO).");
                }
                return out;
            }
        }
    }

    /** Deliver any DELAYED notices (called when notifications are re-enabled). */
    public static Map<String, Object> flushDelayed(Connection conn, UserSecurityContext ctx) throws SQLException {
        int updated;
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE plat.billing_notices SET status = 'DELIVERED', delivered_at = now(), updated_at = now() "
                        + "WHERE status = 'DELAYED' AND deleted_at IS NULL")) {
            updated = ps.executeUpdate();
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("flushedCount", Integer.valueOf(updated));
        out.put("status", "DELIVERED");
        out.put("actor", ctx != null && ctx.getUserId() != null ? ctx.getUserId().toString() : null);
        return out;
    }

    public static Map<String, Object> listRecent(Connection conn, UUID tenantFilter, int limit)
            throws SQLException {
        String sql = "SELECT id, tenant_id, notice_type, entity_type, entity_id, message, status, "
                + "created_at, delivered_at FROM plat.billing_notices "
                + "WHERE deleted_at IS NULL "
                + (tenantFilter != null ? "AND tenant_id = ? " : "")
                + "ORDER BY created_at DESC LIMIT ?";
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            if (tenantFilter != null) {
                ps.setObject(i++, tenantFilter);
            }
            ps.setInt(i, Math.max(1, Math.min(limit, 100)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", String.valueOf(rs.getObject("id")));
                    row.put("tenantId", String.valueOf(rs.getObject("tenant_id")));
                    row.put("noticeType", rs.getString("notice_type"));
                    row.put("entityType", rs.getString("entity_type"));
                    row.put("entityId", rs.getString("entity_id"));
                    row.put("message", rs.getString("message"));
                    row.put("status", rs.getString("status"));
                    row.put("createdAt", rs.getTimestamp("created_at") != null
                            ? rs.getTimestamp("created_at").toInstant().toString() : null);
                    row.put("deliveredAt", rs.getTimestamp("delivered_at") != null
                            ? rs.getTimestamp("delivered_at").toInstant().toString() : null);
                    items.add(row);
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("items", items);
        out.put("count", Integer.valueOf(items.size()));
        return out;
    }
}
