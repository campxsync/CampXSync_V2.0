package com.campx.iadm.access.service;

import com.campx.iadm.access.exception.InstituteAdminException;
import com.campx.iadm.access.security.UserSecurityContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * IADM-03-US-020 — durable shared audit via {@code audit.log_event} in the same transaction.
 * Never builds a second audit store. Refuses any governed operation if audit cannot be recorded.
 */
public final class DurableAuditWriter {

    private static final ThreadLocal<Boolean> DEMO_AUDIT_DISABLED = new ThreadLocal<Boolean>();

    private DurableAuditWriter() {
    }

    public static void setDemoAuditDisabled(boolean disabled) {
        if (disabled) {
            DEMO_AUDIT_DISABLED.set(Boolean.TRUE);
        } else {
            DEMO_AUDIT_DISABLED.remove();
        }
    }

    public static void clearDemoAuditDisabled() {
        DEMO_AUDIT_DISABLED.remove();
    }

    public static boolean isDemoAuditDisabled() {
        if (Boolean.TRUE.equals(DEMO_AUDIT_DISABLED.get())) {
            return true;
        }
        if ("true".equalsIgnoreCase(System.getProperty("iadm03.demo.audit.disabled"))) {
            return true;
        }
        return "true".equalsIgnoreCase(System.getenv("IADM03_DEMO_AUDIT_DISABLED"));
    }

    public static UUID append(Connection conn, UserSecurityContext ctx, UUID tenantId,
                              String action, String entityType, String entityId, String message)
            throws SQLException {
        return appendWithRefs(conn, ctx, tenantId, action,
                tenantId != null ? "INSTITUTE:" + tenantId : "PLATFORM",
                entityType, entityId, null, null, null, message);
    }

    public static UUID appendWithRefs(Connection conn, UserSecurityContext ctx, UUID tenantId,
                                      String action, String scope, String entityType, String entityId,
                                      String beforeRef, String afterRef, String correlationId,
                                      String message)
            throws SQLException {
        if (isDemoAuditDisabled() || conn == null) {
            throw new InstituteAdminException(503, "IADM03_AUDIT_UNAVAILABLE",
                    "Governed operation refused because shared audit is unavailable.");
        }
        String payload = "{\"actor\":\"" + safe(ctx != null ? ctx.getUserId() : null)
                + "\",\"action\":\"" + escape(action)
                + "\",\"scope\":\"" + escape(scope)
                + "\",\"scopeTenant\":\"" + safe(tenantId)
                + "\",\"entityType\":\"" + escape(entityType)
                + "\",\"entityId\":\"" + escape(entityId)
                + "\",\"beforeRef\":\"" + escape(beforeRef)
                + "\",\"afterRef\":\"" + escape(afterRef)
                + "\",\"correlationId\":\"" + escape(correlationId)
                + "\",\"message\":\"" + escape(message) + "\"}";
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT audit.log_event(?, ?, ?, ?, ?, 'SUCCESS', 'INFO', NULL, ?::jsonb, NULL)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, ctx != null ? ctx.getUserId() : null);
            ps.setString(3, action == null ? "IADM03_MUTATION" : action);
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
                    throw new InstituteAdminException(503, "IADM03_AUDIT_UNAVAILABLE",
                            "Governed operation refused because shared audit is unavailable.");
                }
                return (UUID) rs.getObject(1);
            }
        } catch (InstituteAdminException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new InstituteAdminException(503, "IADM03_AUDIT_UNAVAILABLE",
                    "Governed operation refused because shared audit is unavailable.");
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
