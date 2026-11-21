package com.campx.iadm.securitygov.service;

import com.campx.iadm.securitygov.config.DatabaseConnectionManager;
import com.campx.iadm.securitygov.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;

import java.sql.PreparedStatement;
import java.util.LinkedHashMap;
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

    private void auditMutation(UserSecurityContext ctx, String action, String resourceType, String resourceId) {
        String tenant = ctx.getTenantId() == null ? null : ctx.getTenantId().toString();
        String user = ctx.getUserId() == null ? null : ctx.getUserId().toString();
        logger.info("IADM-07 action={} tenantId={} resourceType={} resourceId={}", action, tenant, resourceType, resourceId);
        logger.audit(AuditEvent.builder().action(action).tenantId(tenant).principalId(user)
                .resourceType(resourceType).resourceId(resourceId == null ? "N/A" : resourceId)
                .status("SUCCESS").severity("INFO").description(action).build());
    }
}