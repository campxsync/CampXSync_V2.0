package com.campx.iadm.analytics.service;

import com.campx.iadm.analytics.config.DatabaseConnectionManager;
import com.campx.iadm.analytics.security.UserSecurityContext;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IADM-06 Platform Analytics & Usage (plain language: usage metrics / aggregates).
 */
public class Iadm06DomainService {
    private final DatabaseConnectionManager db = DatabaseConnectionManager.getInstance();

    public Map<String, Object> health(UserSecurityContext ctx) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("service", "IADM-06");
        out.put("status", "UP");
        return out;
    }

    public Map<String, Object> usageSummary(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm06.view");
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
}
