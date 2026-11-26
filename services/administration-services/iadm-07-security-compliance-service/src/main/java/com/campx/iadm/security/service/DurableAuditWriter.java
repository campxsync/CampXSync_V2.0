package com.campx.iadm.security.service;

import com.campx.iadm.security.exception.InstituteAdminException;
import com.campx.iadm.security.security.UserSecurityContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Shared audit via {@code audit.log_event} (IADM-07-US-020).
 * Plain language: privileged analytics views and exports must leave an audit trail;
 * if audit is down, the action is refused. No second audit store. Payload never carries figures.
 */
public final class DurableAuditWriter {

    private DurableAuditWriter() {
    }

    public static UUID append(Connection conn, UserSecurityContext ctx, UUID tenantId,
                              String action, String entityType, String entityId, String message)
            throws SQLException {
        if (SecurityOutageFlags.isAuditDisabled()) {
            throw new InstituteAdminException(503, "IADM07_AUDIT_UNAVAILABLE",
                    "The security governance change cannot be confirmed because audit is unavailable.");
        }
        if (conn == null) {
            throw new InstituteAdminException(503, "IADM07_AUDIT_UNAVAILABLE",
                    "The security governance change cannot be confirmed because audit is unavailable.");
        }
        // Never put metric figures, tokens or credentials in the audit payload.
        String payload = "{\"actor\":\"" + safe(ctx != null ? ctx.getUserId() : null)
                + "\",\"scopeTenant\":\"" + safe(tenantId)
                + "\",\"entityType\":\"" + escape(entityType)
                + "\",\"entityId\":\"" + escape(entityId)
                + "\",\"message\":\"" + escape(message) + "\"}";
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT audit.log_event(?, ?, ?, ?, ?, 'SUCCESS', 'INFO', NULL, ?::jsonb, NULL)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, ctx != null ? ctx.getUserId() : null);
            ps.setString(3, action == null ? "IADM07_VIEW" : action);
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
                    throw new InstituteAdminException(503, "IADM07_AUDIT_UNAVAILABLE",
                            "The security governance change cannot be confirmed because audit is unavailable.");
                }
                return (UUID) rs.getObject(1);
            }
        } catch (InstituteAdminException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new InstituteAdminException(503, "IADM07_AUDIT_UNAVAILABLE",
                    "The security governance change cannot be confirmed because audit is unavailable.");
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
