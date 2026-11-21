package com.campx.iadm.billing.service;

import com.campx.iadm.billing.config.DatabaseConnectionManager;
import com.campx.iadm.billing.exception.InstituteAdminException;
import com.campx.iadm.billing.security.UserSecurityContext;
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
 * IADM-04 — entitlement check is the CADM/all-services contract.
 * <p>
 * Read-only domain surface; {@link CampXLogger} is available for future mutation audit (Agreement &#167;3.2).
 */
public class Iadm04DomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm04DomainService.class);

    private final DatabaseConnectionManager db = DatabaseConnectionManager.getInstance();

    public Map<String, Object> health(UserSecurityContext ctx) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("service", "IADM-04");
        out.put("status", "UP");
        return out;
    }

    public Map<String, Object> checkEntitlement(UserSecurityContext ctx, UUID tenantId, String key) {
        return ctx.executeInTransaction(db, conn -> {
            try {
                ctx.checkPermission(conn, "adm01.manage");
            } catch (Exception e) {
                try { ctx.checkPermission(conn, "iadm04.view"); }
                catch (Exception e2) { ctx.checkPlatformAdmin(conn); }
            }
            UUID tid = tenantId != null ? tenantId : ctx.getTenantId();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT allowed, limit_value, used_value, deny_reason FROM plat.check_entitlement(?, ?)")) {
                ps.setObject(1, tid);
                ps.setString(2, key);
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, Object> out = new LinkedHashMap<>();
                    if (!rs.next()) {
                        out.put("allowed", false);
                        out.put("denyReason", "UNKNOWN_ENTITLEMENT");
                        return out;
                    }
                    out.put("allowed", rs.getBoolean(1));
                    out.put("limitValue", rs.getBigDecimal(2));
                    out.put("usedValue", rs.getBigDecimal(3));
                    out.put("denyReason", rs.getString(4));
                    out.put("entitlementKey", key);
                    out.put("tenantId", tid.toString());
                    return out;
                }
            }
        });
    }

    private void requireBillingView(UserSecurityContext ctx, java.sql.Connection conn) throws java.sql.SQLException {
        try {
            ctx.checkPermission(conn, "iadm04.view");
        } catch (Exception e) {
            try {
                ctx.checkPermission(conn, "adm01.manage");
            } catch (Exception e2) {
                ctx.checkPlatformAdmin(conn);
            }
        }
    }

    /** Lists {@code plat.subscription_plans} (no {@code plat.billing_plans} table in DEV). */
    public Map<String, Object> listPlans(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            requireBillingView(ctx, conn);
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id::text, plan_code, name, billing_cycle, price, currency_code, is_published " +
                            "FROM plat.subscription_plans WHERE deleted_at IS NULL ORDER BY plan_code LIMIT 100")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getString(1));
                        row.put("planCode", rs.getString(2));
                        row.put("name", rs.getString(3));
                        row.put("billingCycle", rs.getString(4));
                        row.put("price", rs.getBigDecimal(5));
                        row.put("currencyCode", rs.getString(6));
                        row.put("published", rs.getBoolean(7));
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

    public Map<String, Object> listSubscriptions(UserSecurityContext ctx, UUID tenantId) {
        return ctx.executeInTransaction(db, conn -> {
            requireBillingView(ctx, conn);
            UUID tid = tenantId != null ? tenantId : ctx.getTenantId();
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id::text, tenant_id::text, plan_id::text, status, start_date::text, end_date::text " +
                            "FROM plat.subscriptions WHERE tenant_id = ? AND deleted_at IS NULL " +
                            "ORDER BY updated_at DESC NULLS LAST LIMIT 100")) {
                ps.setObject(1, tid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getString(1));
                        row.put("tenantId", rs.getString(2));
                        row.put("planId", rs.getString(3));
                        row.put("status", rs.getString(4));
                        row.put("startDate", rs.getString(5));
                        row.put("endDate", rs.getString(6));
                        items.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("tenantId", tid.toString());
            out.put("items", items);
            out.put("count", items.size());
            return out;
        });
    }

    public Map<String, Object> listInvoices(UserSecurityContext ctx, UUID tenantId) {
        return ctx.executeInTransaction(db, conn -> {
            requireBillingView(ctx, conn);
            UUID tid = tenantId != null ? tenantId : ctx.getTenantId();
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id::text, invoice_number, status, amount, currency_code, issued_on::text, due_date::text " +
                            "FROM plat.invoices WHERE tenant_id = ? AND deleted_at IS NULL " +
                            "ORDER BY created_at DESC NULLS LAST LIMIT 100")) {
                ps.setObject(1, tid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getString(1));
                        row.put("invoiceNumber", rs.getString(2));
                        row.put("status", rs.getString(3));
                        row.put("amount", rs.getBigDecimal(4));
                        row.put("currencyCode", rs.getString(5));
                        row.put("issuedOn", rs.getString(6));
                        row.put("dueDate", rs.getString(7));
                        items.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("tenantId", tid.toString());
            out.put("items", items);
            out.put("count", items.size());
            return out;
        });
    }

    /**
     * PARTIAL: {@code plat.plan_change_requests} does not exist in DEV.
     * {@code plat.institute_lifecycle_requests} CHECK rejects PLAN_CHANGE — return 501.
     */
    public Map<String, Object> createPlanChangeRequest(UserSecurityContext ctx, String reason, String requestedPlanCode) {
        return ctx.executeInTransaction(db, conn -> {
            try {
                ctx.checkPermission(conn, "iadm04.write");
            } catch (Exception e) {
                ctx.checkPermission(conn, "adm01.write");
            }
            throw new InstituteAdminException(501, "IADM04_PLAN_CHANGE_UNAVAILABLE",
                    "plat.plan_change_requests missing; institute_lifecycle_requests cannot store PLAN_CHANGE");
        });
    }

    private void auditMutation(UserSecurityContext ctx, String action, String resourceType, String resourceId) {
        String tenant = ctx.getTenantId() == null ? null : ctx.getTenantId().toString();
        String user = ctx.getUserId() == null ? null : ctx.getUserId().toString();
        logger.info("IADM-04 action={} tenantId={} resourceType={} resourceId={}", action, tenant, resourceType, resourceId);
        logger.audit(AuditEvent.builder().action(action).tenantId(tenant).principalId(user)
                .resourceType(resourceType).resourceId(resourceId == null ? "N/A" : resourceId)
                .status("SUCCESS").severity("INFO").description(action).build());
    }
}
