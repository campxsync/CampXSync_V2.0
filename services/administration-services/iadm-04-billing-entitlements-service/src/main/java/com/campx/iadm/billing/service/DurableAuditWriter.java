package com.campx.iadm.billing.service;

import com.campx.iadm.billing.exception.InstituteAdminException;
import com.campx.iadm.billing.security.UserSecurityContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Durable shared audit via {@code audit.log_event} in the same transaction.
 * If audit cannot be guaranteed, the caller must not report success.
 */
public final class DurableAuditWriter {

    private DurableAuditWriter() {
    }

    public static UUID append(Connection conn, UserSecurityContext ctx, UUID tenantId,
                              String action, String entityType, String entityId, String message)
            throws SQLException {
        if (BillingOutageFlags.isAuditDisabled()) {
            throw new InstituteAdminException(503, "IADM04_AUDIT_UNAVAILABLE",
                    "The billing change cannot be confirmed because audit is unavailable.");
        }
        if (conn == null) {
            throw new InstituteAdminException(503, "IADM04_AUDIT_UNAVAILABLE",
                    "The billing change cannot be confirmed because audit is unavailable.");
        }
        String payload = "{\"actor\":\"" + safe(ctx != null ? ctx.getUserId() : null)
                + "\",\"scopeTenant\":\"" + safe(tenantId)
                + "\",\"entityType\":\"" + escape(entityType)
                + "\",\"entityId\":\"" + escape(entityId)
                + "\",\"message\":\"" + escape(message) + "\"}";
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT audit.log_event(?, ?, ?, ?, ?, 'SUCCESS', 'INFO', NULL, ?::jsonb, NULL)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, ctx != null ? ctx.getUserId() : null);
            ps.setString(3, action == null ? "IADM04_MUTATION" : action);
            ps.setString(4, entityType);
            UUID resourceUuid = null;
            if (entityId != null && !entityId.trim().isEmpty()) {
                try {
                    resourceUuid = UUID.fromString(entityId.trim());
                } catch (IllegalArgumentException ignored) {
                    resourceUuid = null;
                }
            }
            ps.setObject(5, resourceUuid);
            ps.setString(6, payload);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getObject(1) == null) {
                    throw new InstituteAdminException(503, "IADM04_AUDIT_UNAVAILABLE",
                            "The billing change cannot be confirmed because audit is unavailable.");
                }
                return (UUID) rs.getObject(1);
            }
        } catch (InstituteAdminException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new InstituteAdminException(503, "IADM04_AUDIT_UNAVAILABLE",
                    "The billing change cannot be confirmed because audit is unavailable.");
        }
    }

    private static String safe(UUID id) {
        return id == null ? "" : id.toString();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
