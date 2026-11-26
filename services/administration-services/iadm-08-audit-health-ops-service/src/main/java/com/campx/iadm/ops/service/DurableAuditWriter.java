package com.campx.iadm.ops.service;

import com.campx.iadm.ops.exception.InstituteAdminException;
import com.campx.iadm.ops.security.UserSecurityContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Shared audit via {@code audit.log_event} (IADM-08-US-019 / decision D3).
 * Plain language: every IADM-08 read must leave a filter-summary trail; if audit is down, refuse the read.
 * Never write result payloads, tokens or credentials.
 */
public final class DurableAuditWriter {

    private DurableAuditWriter() {
    }

    public static UUID append(Connection conn, UserSecurityContext ctx, UUID tenantId,
                              String action, String entityType, String entityId, String filterSummary)
            throws SQLException {
        if (OpsOutageFlags.isAuditDisabled()) {
            throw new InstituteAdminException(503, "IADM08_AUDIT_UNAVAILABLE",
                    "The request could not be recorded and was not processed.");
        }
        if (conn == null) {
            throw new InstituteAdminException(503, "IADM08_AUDIT_UNAVAILABLE",
                    "The request could not be recorded and was not processed.");
        }
        String payload = "{\"actor\":\"" + safe(ctx != null ? ctx.getUserId() : null)
                + "\",\"scopeTenant\":\"" + safe(tenantId)
                + "\",\"entityType\":\"" + escape(entityType)
                + "\",\"entityId\":\"" + escape(entityId)
                + "\",\"filterSummary\":\"" + escape(filterSummary) + "\"}";
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT audit.log_event(?, ?, ?, ?, ?, 'SUCCESS', 'INFO', NULL, ?::jsonb, NULL)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, ctx != null ? ctx.getUserId() : null);
            ps.setString(3, action == null ? "IADM08_READ" : action);
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
                    throw new InstituteAdminException(503, "IADM08_AUDIT_UNAVAILABLE",
                            "The request could not be recorded and was not processed.");
                }
                return (UUID) rs.getObject(1);
            }
        } catch (InstituteAdminException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new InstituteAdminException(503, "IADM08_AUDIT_UNAVAILABLE",
                    "The request could not be recorded and was not processed.");
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
