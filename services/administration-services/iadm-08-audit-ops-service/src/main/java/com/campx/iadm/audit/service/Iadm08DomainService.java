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
import java.util.UUID;

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
            // CADM-07 / IADM-08-US-001: fail closed — view key or platform admin only
            try {
                ctx.checkPermission(conn, "adm01.view");
            } catch (Exception e) {
                ctx.checkPlatformAdmin(conn);
            }
            List<Map<String, Object>> items = new ArrayList<>();
            boolean platformWide = ctx.getTenantId() == null;
            // audit.events columns: principal_id, event_time (no actor_id / deleted_at)
            String sql = "SELECT id::text, action, principal_id::text, COALESCE(event_time, created_at)::text, tenant_id::text " +
                    "FROM audit.events WHERE ";
            if (platformWide) {
                sql += "1=1 ";
            } else {
                sql += "tenant_id = ? ";
            }
            if (actor != null && !actor.isEmpty()) sql += "AND principal_id::text = ? ";
            if (action != null && !action.isEmpty()) sql += "AND action = ? ";
            sql += "ORDER BY COALESCE(event_time, created_at) DESC NULLS LAST LIMIT 100";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int i = 1;
                if (!platformWide) {
                    ps.setObject(i++, ctx.getTenantId());
                }
                if (actor != null && !actor.isEmpty()) ps.setString(i++, actor);
                if (action != null && !action.isEmpty()) ps.setString(i, action);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getString(1));
                        row.put("action", rs.getString(2));
                        row.put("actorId", rs.getString(3));
                        row.put("createdAt", rs.getString(4));
                        row.put("tenantId", rs.getString(5));
                        items.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("items", items);
            out.put("count", items.size());
            out.put("scope", platformWide ? "PLATFORM" : "TENANT");
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

    public Map<String, Object> exportAudit(UserSecurityContext ctx, String actor, String action) {
        Map<String, Object> search = searchAudit(ctx, actor, action);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("format", "json");
        out.put("items", search.get("items"));
        out.put("count", search.get("count"));
        out.put("contentDisposition", "attachment; filename=\"audit-export.json\"");
        return out;
    }

    public Map<String, Object> instituteStatus(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm01.view");
            Map<String, Object> out = new LinkedHashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id::text, code, name, status, updated_at::text FROM core.tenants WHERE id = ? AND deleted_at IS NULL")) {
                ps.setObject(1, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(404, "IADM08_TENANT_NOT_FOUND", "Tenant not found");
                    }
                    out.put("tenantId", rs.getString(1));
                    out.put("code", rs.getString(2));
                    out.put("name", rs.getString(3));
                    out.put("status", rs.getString(4));
                    out.put("updatedAt", rs.getString(5));
                }
            }
            int recentEvents = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM plat.institute_lifecycle_events WHERE tenant_id = ? " +
                            "AND created_at > now() - interval '30 days'")) {
                ps.setObject(1, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) recentEvents = rs.getInt(1);
                }
            }
            out.put("recentLifecycleEventCount", recentEvents);
            return out;
        });
    }

    /**
     * Read audit retention rows from {@code audit.retention_policies} (Wave F — not plat.audit_retention_policy).
     */
    public Map<String, Object> auditRetention(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            try {
                ctx.checkPermission(conn, "adm01.view");
            } catch (Exception e) {
                ctx.checkPlatformAdmin(conn);
            }
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id::text, entity_type, retention_days, action, legal_hold, updated_at::text " +
                            "FROM audit.retention_policies WHERE tenant_id = ? AND deleted_at IS NULL " +
                            "ORDER BY entity_type LIMIT 100")) {
                ps.setObject(1, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getString(1));
                        row.put("entityType", rs.getString(2));
                        row.put("retentionDays", rs.getInt(3));
                        row.put("action", rs.getString(4));
                        row.put("legalHold", rs.getBoolean(5));
                        row.put("updatedAt", rs.getString(6));
                        items.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("items", items);
            out.put("count", items.size());
            out.put("source", "audit.retention_policies");
            return out;
        });
    }

    /** Platform-admin upsert into {@code audit.retention_policies} by tenant + entity_type. */
    public Map<String, Object> upsertAuditRetention(UserSecurityContext ctx, String entityType, Integer retentionDays,
                                                   String action, Boolean legalHold) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            if (entityType == null || entityType.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM08_RETENTION_ENTITY", "entityType required");
            }
            if (retentionDays == null || retentionDays <= 0) {
                throw new InstituteAdminException(400, "IADM08_RETENTION_DAYS", "retentionDays must be > 0");
            }
            String act = (action == null || action.trim().isEmpty()) ? "ARCHIVE" : action.trim().toUpperCase();
            if (!("ARCHIVE".equals(act) || "DELETE".equals(act) || "ANONYMIZE".equals(act))) {
                throw new InstituteAdminException(400, "IADM08_RETENTION_ACTION",
                        "action must be ARCHIVE, DELETE, or ANONYMIZE");
            }
            boolean hold = legalHold != null && legalHold;
            String entity = entityType.trim();
            UUID id = null;
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE audit.retention_policies SET retention_days = ?, action = ?, legal_hold = ?, " +
                            "updated_at = now(), updated_by = ? " +
                            "WHERE tenant_id = ? AND entity_type = ? AND deleted_at IS NULL RETURNING id")) {
                upd.setInt(1, retentionDays);
                upd.setString(2, act);
                upd.setBoolean(3, hold);
                upd.setObject(4, ctx.getUserId());
                upd.setObject(5, ctx.getTenantId());
                upd.setString(6, entity);
                try (ResultSet rs = upd.executeQuery()) {
                    if (rs.next()) id = (UUID) rs.getObject(1);
                }
            }
            if (id == null) {
                id = UUID.randomUUID();
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO audit.retention_policies (id, tenant_id, entity_type, retention_days, action, legal_hold, created_by) " +
                                "VALUES (?,?,?,?,?,?,?)")) {
                    ins.setObject(1, id);
                    ins.setObject(2, ctx.getTenantId());
                    ins.setString(3, entity);
                    ins.setInt(4, retentionDays);
                    ins.setString(5, act);
                    ins.setBoolean(6, hold);
                    ins.setObject(7, ctx.getUserId());
                    ins.executeUpdate();
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id.toString());
            out.put("entityType", entity);
            out.put("retentionDays", retentionDays);
            out.put("action", act);
            out.put("legalHold", hold);
            out.put("source", "audit.retention_policies");
            auditMutation(ctx, "upsertAuditRetention", "AUDIT_RETENTION", id.toString());
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