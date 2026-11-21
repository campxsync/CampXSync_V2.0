package com.campx.iadm.analytics.service;

import com.campx.iadm.analytics.config.DatabaseConnectionManager;
import com.campx.iadm.analytics.exception.InstituteAdminException;
import com.campx.iadm.analytics.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    /** Groups usage by {@code plat.usage_metrics.college_id}. */
    public Map<String, Object> usageByCollege(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm06.view");
            List<Map<String, Object>> rows = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT college_id::text, metric_name, COALESCE(SUM(metric_value),0) AS total " +
                            "FROM plat.usage_metrics WHERE tenant_id=? AND deleted_at IS NULL " +
                            "GROUP BY college_id, metric_name " +
                            "ORDER BY college_id NULLS LAST, metric_name LIMIT 200")) {
                ps.setObject(1, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("collegeId", rs.getString(1));
                        row.put("metricName", rs.getString(2));
                        row.put("total", rs.getBigDecimal(3));
                        rows.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("tenantId", ctx.getTenantId().toString());
            out.put("items", rows);
            out.put("count", rows.size());
            return out;
        });
    }

    /**
     * Metering feed: insert {@code plat.usage_metrics} row for later billing/entitlement used_value.
     */
    public Map<String, Object> ingestUsage(UserSecurityContext ctx, String metricName, BigDecimal value,
                                           UUID collegeId, String recordedOn) {
        return ctx.executeInTransaction(db, conn -> {
            try {
                ctx.checkPermission(conn, "iadm06.write");
            } catch (Exception e) {
                ctx.checkPlatformAdmin(conn);
            }
            if (metricName == null || metricName.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM06_METRIC_REQUIRED", "metricName required");
            }
            if (value == null) {
                throw new InstituteAdminException(400, "IADM06_VALUE_REQUIRED", "value required");
            }
            LocalDate on;
            try {
                on = recordedOn == null || recordedOn.trim().isEmpty()
                        ? LocalDate.now() : LocalDate.parse(recordedOn.trim());
            } catch (Exception e) {
                throw new InstituteAdminException(400, "IADM06_RECORDED_ON", "recordedOn must be YYYY-MM-DD");
            }
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO plat.usage_metrics " +
                            "(id, tenant_id, metric_name, metric_value, recorded_on, college_id, created_by, row_version) " +
                            "VALUES (?,?,?,?,?,?,?,1)")) {
                ps.setObject(1, id);
                ps.setObject(2, ctx.getTenantId());
                ps.setString(3, metricName.trim());
                ps.setBigDecimal(4, value);
                ps.setDate(5, Date.valueOf(on));
                ps.setObject(6, collegeId);
                ps.setObject(7, ctx.getUserId());
                ps.executeUpdate();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id.toString());
            out.put("metricName", metricName.trim());
            out.put("value", value);
            out.put("collegeId", collegeId == null ? null : collegeId.toString());
            out.put("recordedOn", on.toString());
            auditMutation(ctx, "iadm06.usage.ingest", "USAGE_METRIC", id.toString());
            return out;
        });
    }

    public Map<String, Object> usageExport(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            try {
                ctx.checkPermission(conn, "iadm06.export");
            } catch (Exception e) {
                ctx.checkPermission(conn, "iadm06.view");
            }
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
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("tenantId", ctx.getTenantId().toString());
            summary.put("metrics", rows);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("format", "json");
            out.put("usageSummary", summary);
            out.put("contentDisposition", "attachment; filename=\"usage-export.json\"");
            return out;
        });
    }

    public Map<String, Object> usagePlatform(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            List<Map<String, Object>> rows = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT tenant_id::text, metric_name, COALESCE(SUM(metric_value),0) AS total " +
                            "FROM plat.usage_metrics WHERE deleted_at IS NULL " +
                            "GROUP BY tenant_id, metric_name ORDER BY tenant_id, metric_name LIMIT 500")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("tenantId", rs.getString(1));
                        row.put("metricName", rs.getString(2));
                        row.put("total", rs.getBigDecimal(3));
                        rows.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("items", rows);
            out.put("count", rows.size());
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