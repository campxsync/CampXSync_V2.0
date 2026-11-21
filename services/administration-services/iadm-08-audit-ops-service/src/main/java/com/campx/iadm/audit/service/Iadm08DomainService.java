package com.campx.iadm.audit.service;

import com.campx.iadm.audit.config.DatabaseConnectionManager;
import com.campx.iadm.audit.exception.InstituteAdminException;
import com.campx.iadm.audit.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IADM-08 Audit, Health &amp; Operations — shared audit store and ops probes (CADM-07 contract).
 * <p>
 * Platform-admin reads with {@link CampXLogger} debug tracing per Agreement §3.2.
 * Never log tokens, passwords, or JWTs.
 */
public class Iadm08DomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm08DomainService.class);

    private final DatabaseConnectionManager db = DatabaseConnectionManager.getInstance();

    public Map<String, Object> searchAudit(UserSecurityContext ctx, String actor, String action) {
        return ctx.executeInTransaction(db, conn -> {
            logger.debug("IADM-08 searchAudit tenantId={}", ctx.getTenantId());
            try {
                ctx.checkPermission(conn, "adm01.view");
            } catch (Exception e) {
                ctx.checkPlatformAdmin(conn);
            }
            List<Map<String, Object>> items = new ArrayList<>();
            String sql = "SELECT id::text, action, actor_id::text, created_at::text FROM audit.events " +
                    "WHERE tenant_id = ? AND deleted_at IS NULL ";
            if (actor != null && !actor.isEmpty()) sql += "AND actor_id::text = ? ";
            if (action != null && !action.isEmpty()) sql += "AND action = ? ";
            sql += "ORDER BY created_at DESC LIMIT 100";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int i = 1;
                ps.setObject(i++, ctx.getTenantId());
                if (actor != null && !actor.isEmpty()) ps.setString(i++, actor);
                if (action != null && !action.isEmpty()) ps.setString(i, action);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getString(1));
                        row.put("action", rs.getString(2));
                        row.put("actorId", rs.getString(3));
                        row.put("createdAt", rs.getString(4));
                        items.add(row);
                    }
                }
            } catch (Exception ex) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("items", items);
                out.put("warning", ex.getMessage());
                return out;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("items", items);
            return out;
        });
    }

    public Map<String, Object> health(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            logger.debug("IADM-08 ops health probe");
            ctx.checkPlatformAdmin(conn);
            List<Map<String, Object>> services = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT service_name, status, last_heartbeat::text FROM plat.service_health WHERE deleted_at IS NULL ORDER BY last_heartbeat DESC NULLS LAST LIMIT 50")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("serviceName", rs.getString(1));
                        row.put("status", rs.getString(2));
                        row.put("lastHeartbeat", rs.getString(3));
                        services.add(row);
                    }
                }
            } catch (Exception ex) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("services", services);
                out.put("warning", ex.getMessage());
                return out;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("services", services);
            return out;
        });
    }

    public Map<String, Object> auditSelfCheck(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            logger.debug("IADM-08 auditSelfCheck");
            ctx.checkPlatformAdmin(conn);
            List<Map<String, Object>> checks = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT check_name, passed, detail FROM audit.self_check_immutable()")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("checkName", rs.getString(1));
                        row.put("passed", rs.getBoolean(2));
                        row.put("detail", rs.getString(3));
                        checks.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("checks", checks);
            return out;
        });
    }

    private void auditMutation(UserSecurityContext ctx, String action, String resourceType, String resourceId) {
        String tenant = ctx.getTenantId() == null ? null : ctx.getTenantId().toString();
        String user = ctx.getUserId() == null ? null : ctx.getUserId().toString();
        logger.info("IADM-08 action={} tenantId={} resourceType={} resourceId={}", action, tenant, resourceType, resourceId);
        logger.audit(AuditEvent.builder().action(action).tenantId(tenant).principalId(user)
                .resourceType(resourceType).resourceId(resourceId == null ? "N/A" : resourceId)
                .status("SUCCESS").severity("INFO").description(action).build());
    }
}