package com.campx.iadm.securitygov.service;

import com.campx.iadm.securitygov.config.DatabaseConnectionManager;
import com.campx.iadm.securitygov.security.UserSecurityContext;
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
 * Domain service for IADM-07 Security &amp; Compliance Governance (policies and findings).
 * <p>
 * Permission-key checks, tenant-scoped writes, and {@link CampXLogger} audit events per
 * Agreement §3.2. Never log tokens, passwords, or JWTs.
 */
public class Iadm07DomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm07DomainService.class);

    private final DatabaseConnectionManager db = DatabaseConnectionManager.getInstance();

    public Map<String, Object> health(UserSecurityContext ctx) {
        logger.debug("IADM-07 health probe tenantId={}", ctx.getTenantId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("service", "IADM-07");
        out.put("status", "UP");
        return out;
    }

    public Map<String, Object> upsertPolicy(UserSecurityContext ctx, String key, String valueJson) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm07.write");
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE plat.institute_security_policies SET policy_value=?::jsonb, updated_at=now(), updated_by=? " +
                            "WHERE tenant_id=? AND policy_key=? AND deleted_at IS NULL")) {
                upd.setString(1, valueJson == null ? "{}" : valueJson);
                upd.setObject(2, ctx.getUserId());
                upd.setObject(3, ctx.getTenantId());
                upd.setString(4, key);
                if (upd.executeUpdate() == 0) {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO plat.institute_security_policies (tenant_id, policy_key, policy_value, created_by) VALUES (?,?,?::jsonb,?)")) {
                        ins.setObject(1, ctx.getTenantId());
                        ins.setString(2, key);
                        ins.setString(3, valueJson == null ? "{}" : valueJson);
                        ins.setObject(4, ctx.getUserId());
                        ins.executeUpdate();
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("policyKey", key);
            auditMutation(ctx, "iadm07.policy.upsert", "SECURITY_POLICY", key);
            return out;
        });
    }

    public Map<String, Object> createFinding(UserSecurityContext ctx, String title, String severity) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm07.write");
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO plat.security_findings (id, tenant_id, title, severity, status, created_by) " +
                            "VALUES (?,?,?,?, 'OPEN', ?)")) {
                ps.setObject(1, id);
                ps.setObject(2, ctx.getTenantId());
                ps.setString(3, title);
                ps.setString(4, severity == null ? "MEDIUM" : severity);
                ps.setObject(5, ctx.getUserId());
                ps.executeUpdate();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("findingId", id.toString());
            out.put("status", "OPEN");
            auditMutation(ctx, "iadm07.finding.create", "SECURITY_FINDING", id.toString());
            return out;
        });
    }

    public Map<String, Object> listPolicies(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm07.view");
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id::text, policy_key, policy_value::text, updated_at::text " +
                            "FROM plat.institute_security_policies WHERE tenant_id=? AND deleted_at IS NULL " +
                            "ORDER BY policy_key LIMIT 100")) {
                ps.setObject(1, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getString(1));
                        row.put("policyKey", rs.getString(2));
                        row.put("policyValue", rs.getString(3));
                        row.put("updatedAt", rs.getString(4));
                        items.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("items", items);
            out.put("count", items.size());
            return out;
        });
    }

    public Map<String, Object> listFindings(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm07.view");
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id::text, title, severity, status, owner_user_id::text, created_at::text " +
                            "FROM plat.security_findings WHERE tenant_id=? AND deleted_at IS NULL " +
                            "ORDER BY created_at DESC NULLS LAST LIMIT 100")) {
                ps.setObject(1, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getString(1));
                        row.put("title", rs.getString(2));
                        row.put("severity", rs.getString(3));
                        row.put("status", rs.getString(4));
                        row.put("ownerUserId", rs.getString(5));
                        row.put("createdAt", rs.getString(6));
                        items.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("items", items);
            out.put("count", items.size());
            return out;
        });
    }

    public Map<String, Object> posture(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm07.view");
            Map<String, Object> openBySeverity = new LinkedHashMap<>();
            openBySeverity.put("LOW", 0);
            openBySeverity.put("MEDIUM", 0);
            openBySeverity.put("HIGH", 0);
            openBySeverity.put("CRITICAL", 0);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT severity, count(*) FROM plat.security_findings " +
                            "WHERE tenant_id=? AND deleted_at IS NULL AND status='OPEN' GROUP BY severity")) {
                ps.setObject(1, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        openBySeverity.put(rs.getString(1), rs.getInt(2));
                    }
                }
            }
            int policyCount = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM plat.institute_security_policies WHERE tenant_id=? AND deleted_at IS NULL")) {
                ps.setObject(1, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) policyCount = rs.getInt(1);
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("openFindingsBySeverity", openBySeverity);
            out.put("policyCount", policyCount);
            return out;
        });
    }

    /** PARTIAL: {@code plat.platform_security_minimums} absent in DEV — empty catalogue. */
    public Map<String, Object> platformMinimums(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id::text, minimum_key, minimum_value::text FROM plat.platform_security_minimums " +
                            "WHERE deleted_at IS NULL ORDER BY minimum_key LIMIT 100")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getString(1));
                        row.put("minimumKey", rs.getString(2));
                        row.put("minimumValue", rs.getString(3));
                        items.add(row);
                    }
                }
            } catch (Exception ignored) {
                // table missing
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("items", items);
            out.put("count", items.size());
            out.put("partial", true);
            out.put("note", "plat.platform_security_minimums not present in DEV migrations");
            return out;
        });
    }

    private void auditMutation(UserSecurityContext ctx, String action, String resourceType, String resourceId) {
        String tenant = ctx.getTenantId() == null ? null : ctx.getTenantId().toString();
        String user = ctx.getUserId() == null ? null : ctx.getUserId().toString();
        logger.info("IADM-07 action={} tenantId={} resourceType={} resourceId={}", action, tenant, resourceType, resourceId);
        logger.audit(AuditEvent.builder().action(action).tenantId(tenant).principalId(user)
                .resourceType(resourceType).resourceId(resourceId == null ? "N/A" : resourceId)
                .status("SUCCESS").severity("INFO").description(action).build());
    }
}