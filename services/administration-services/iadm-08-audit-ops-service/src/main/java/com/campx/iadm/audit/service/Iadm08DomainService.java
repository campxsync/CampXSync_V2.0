package com.campx.iadm.audit.service;

import com.campx.iadm.audit.config.DatabaseConnectionManager;
import com.campx.iadm.audit.exception.InstituteAdminException;
import com.campx.iadm.audit.security.UserSecurityContext;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** IADM-08 Audit, Health & Operations — shared audit store only; CADM-07 contract. */
public class Iadm08DomainService {
    private final DatabaseConnectionManager db = DatabaseConnectionManager.getInstance();

    public Map<String, Object> searchAudit(UserSecurityContext ctx, String actor, String action) {
        return ctx.executeInTransaction(db, conn -> {
            // Prefer audit view permission; fall back to platform admin
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
                // If audit.events shape differs, return empty with note (fail soft for read probe)
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
}
