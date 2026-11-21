package com.campx.iadm.analytics.service;

import com.campx.iadm.analytics.config.DatabaseConnectionManager;
import com.campx.iadm.analytics.security.UserSecurityContext;
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
 * Domain service for IADM-06 Platform Analytics &amp; Usage (usage metrics / aggregates).
 * <p>
 * Permission-key checks, tenant isolation, and {@link CampXLogger} diagnostics per Agreement §3.2.
 * Read-only paths use debug logging; never log tokens, passwords, or JWTs.
 */
public class Iadm06DomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm06DomainService.class);

    private final DatabaseConnectionManager db = DatabaseConnectionManager.getInstance();

    public Map<String, Object> health(UserSecurityContext ctx) {
        logger.debug("IADM-06 health probe tenantId={}", ctx.getTenantId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("service", "IADM-06");
        out.put("status", "UP");
        return out;
    }

    public Map<String, Object> usageSummary(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm06.view");
            logger.debug("IADM-06 usageSummary tenantId={}", ctx.getTenantId());
            List<Map<String, Object>> rows = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT metric_name, COALESCE(SUM(metric_value),0) AS total " +
                            "FROM plat.usage_metrics WHERE tenant_id=? AND deleted_at IS NULL " +
                            "GROUP BY metric_name ORDER BY metric_name LIMIT 100")) {
                ps.setObject(1, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("metricName", rs.getString(1));
                        row.put("total", rs.getBigDecimal(2));
                        rows.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("tenantId", ctx.getTenantId().toString());
            out.put("metrics", rows);
            return out;
        });
    }

    private void auditMutation(UserSecurityContext ctx, String action, String resourceType, String resourceId) {
        String tenant = ctx.getTenantId() == null ? null : ctx.getTenantId().toString();
        String user = ctx.getUserId() == null ? null : ctx.getUserId().toString();
        logger.info("IADM-06 action={} tenantId={} resourceType={} resourceId={}", action, tenant, resourceType, resourceId);
        logger.audit(AuditEvent.builder().action(action).tenantId(tenant).principalId(user)
                .resourceType(resourceType).resourceId(resourceId == null ? "N/A" : resourceId)
                .status("SUCCESS").severity("INFO").description(action).build());
    }
}