package com.campx.iadm.billing.service;

import com.campx.iadm.billing.config.DatabaseConnectionManager;
import com.campx.iadm.billing.exception.InstituteAdminException;
import com.campx.iadm.billing.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-04 — subscription, billing, and entitlement domain (Agreement &#167;3.2).
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

    private void requireBillingWrite(UserSecurityContext ctx, java.sql.Connection conn) throws java.sql.SQLException {
        try {
            ctx.checkPermission(conn, "iadm04.write");
        } catch (Exception e) {
            try {
                ctx.checkPermission(conn, "adm01.write");
            } catch (Exception e2) {
                ctx.checkPermission(conn, "adm01.manage");
            }
        }
    }

    /**
     * Persists a PENDING plan-change / add-on request into {@code plat.plan_change_requests}.
     */
    public Map<String, Object> createPlanChangeRequest(UserSecurityContext ctx, String reason,
                                                       String requestedPlanCode, String requestType) {
        return ctx.executeInTransaction(db, conn -> {
            requireBillingWrite(ctx, conn);
            if (requestedPlanCode == null || requestedPlanCode.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM04_PLAN_CODE_REQUIRED", "requestedPlanCode required");
            }
            String type = requestType == null || requestType.trim().isEmpty()
                    ? "PLAN_CHANGE" : requestType.trim().toUpperCase(Locale.ROOT);
            if (!"PLAN_CHANGE".equals(type) && !"ADD_ON".equals(type)) {
                throw new InstituteAdminException(400, "IADM04_REQUEST_TYPE", "requestType must be PLAN_CHANGE or ADD_ON");
            }
            String currentPlan = null;
            try (PreparedStatement cur = conn.prepareStatement(
                    "SELECT sp.plan_code FROM plat.subscriptions s " +
                            "JOIN plat.subscription_plans sp ON sp.id = s.plan_id AND sp.deleted_at IS NULL " +
                            "WHERE s.tenant_id=? AND s.deleted_at IS NULL " +
                            "ORDER BY s.updated_at DESC NULLS LAST LIMIT 1")) {
                cur.setObject(1, ctx.getTenantId());
                try (ResultSet rs = cur.executeQuery()) {
                    if (rs.next()) currentPlan = rs.getString(1);
                }
            }
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO plat.plan_change_requests " +
                            "(id, tenant_id, requested_by, current_plan_code, requested_plan_code, request_type, status, reason, created_by) " +
                            "VALUES (?,?,?,?,?,?, 'PENDING', ?, ?)")) {
                ps.setObject(1, id);
                ps.setObject(2, ctx.getTenantId());
                ps.setObject(3, ctx.getUserId());
                ps.setString(4, currentPlan);
                ps.setString(5, requestedPlanCode.trim());
                ps.setString(6, type);
                ps.setString(7, reason);
                ps.setObject(8, ctx.getUserId());
                ps.executeUpdate();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requestId", id.toString());
            out.put("status", "PENDING");
            out.put("requestedPlanCode", requestedPlanCode.trim());
            out.put("requestType", type);
            out.put("currentPlanCode", currentPlan);
            auditMutation(ctx, "iadm04.plan_change.create", "PLAN_CHANGE_REQUEST", id.toString());
            return out;
        });
    }

    /** Platform admin APPROVE / REJECT for a pending plan-change request. */
    public Map<String, Object> decidePlanChange(UserSecurityContext ctx, UUID requestId, String decision, String note) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            if (requestId == null) {
                throw new InstituteAdminException(400, "IADM04_REQUEST_ID", "requestId required");
            }
            String dec = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
            if (!"APPROVE".equals(dec) && !"REJECT".equals(dec)) {
                throw new InstituteAdminException(400, "IADM04_DECISION", "decision must be APPROVE or REJECT");
            }
            String status = "APPROVE".equals(dec) ? "APPROVED" : "REJECTED";
            int updated;
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE plat.plan_change_requests SET status=?, decided_by=?, decided_at=now(), decision_note=?, " +
                            "updated_at=now(), updated_by=? " +
                            "WHERE id=? AND status='PENDING' AND deleted_at IS NULL")) {
                ps.setString(1, status);
                ps.setObject(2, ctx.getUserId());
                ps.setString(3, note);
                ps.setObject(4, ctx.getUserId());
                ps.setObject(5, requestId);
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                throw new InstituteAdminException(404, "IADM04_PLAN_CHANGE_NOT_FOUND",
                        "Pending plan-change request not found");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requestId", requestId.toString());
            out.put("status", status);
            out.put("decision", dec);
            auditMutation(ctx, "iadm04.plan_change.decide", "PLAN_CHANGE_REQUEST", requestId.toString());
            return out;
        });
    }

    /** Aggregate: own subscription + plan + entitlement keys for the caller tenant. */
    public Map<String, Object> getSubscriptionMine(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            requireBillingView(ctx, conn);
            UUID tid = ctx.getTenantId();
            Map<String, Object> subscription = null;
            UUID planId = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id::text, plan_id, status, start_date::text, end_date::text " +
                            "FROM plat.subscriptions WHERE tenant_id=? AND deleted_at IS NULL " +
                            "ORDER BY updated_at DESC NULLS LAST LIMIT 1")) {
                ps.setObject(1, tid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        subscription = new LinkedHashMap<>();
                        subscription.put("id", rs.getString(1));
                        planId = (UUID) rs.getObject(2);
                        subscription.put("planId", planId == null ? null : planId.toString());
                        subscription.put("status", rs.getString(3));
                        subscription.put("startDate", rs.getString(4));
                        subscription.put("endDate", rs.getString(5));
                    }
                }
            }
            Map<String, Object> plan = null;
            List<String> entitlementKeys = new ArrayList<>();
            if (planId != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id::text, plan_code, name, billing_cycle, price, currency_code, is_published " +
                                "FROM plat.subscription_plans WHERE id=? AND deleted_at IS NULL")) {
                    ps.setObject(1, planId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            plan = new LinkedHashMap<>();
                            plan.put("id", rs.getString(1));
                            plan.put("planCode", rs.getString(2));
                            plan.put("name", rs.getString(3));
                            plan.put("billingCycle", rs.getString(4));
                            plan.put("price", rs.getBigDecimal(5));
                            plan.put("currencyCode", rs.getString(6));
                            plan.put("published", rs.getBoolean(7));
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT entitlement_key FROM plat.plan_entitlements " +
                                "WHERE plan_id=? AND deleted_at IS NULL AND is_enabled=true ORDER BY entitlement_key")) {
                    ps.setObject(1, planId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) entitlementKeys.add(rs.getString(1));
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("tenantId", tid.toString());
            out.put("subscription", subscription);
            out.put("plan", plan);
            out.put("entitlementKeys", entitlementKeys);
            return out;
        });
    }

    /** Platform admin: create draft plan in {@code plat.subscription_plans}. */
    public Map<String, Object> createPlan(UserSecurityContext ctx, String planCode, String name,
                                          String billingCycle, BigDecimal price, String currencyCode) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            if (planCode == null || planCode.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM04_PLAN_CODE_REQUIRED", "planCode required");
            }
            if (name == null || name.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM04_PLAN_NAME_REQUIRED", "name required");
            }
            String code = planCode.trim().toUpperCase(Locale.ROOT);
            String cycle = billingCycle == null || billingCycle.trim().isEmpty()
                    ? "MONTHLY" : billingCycle.trim().toUpperCase(Locale.ROOT);
            if (!"MONTHLY".equals(cycle) && !"QUARTERLY".equals(cycle) && !"ANNUALLY".equals(cycle)) {
                cycle = "MONTHLY";
            }
            String currency = currencyCode == null || currencyCode.trim().isEmpty()
                    ? "INR" : currencyCode.trim().toUpperCase(Locale.ROOT);
            if (currency.length() > 3) currency = currency.substring(0, 3);
            BigDecimal amt = price == null ? BigDecimal.ZERO : price;
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT 1 FROM plat.subscription_plans WHERE plan_code=? AND deleted_at IS NULL")) {
                check.setString(1, code);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        throw new InstituteAdminException(409, "IADM04_PLAN_EXISTS", "planCode already exists");
                    }
                }
            }
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO plat.subscription_plans " +
                            "(id, plan_code, name, billing_cycle, price, currency_code, is_published, created_by, row_version) " +
                            "VALUES (?,?,?,?,?,?, false, ?, 1)")) {
                ps.setObject(1, id);
                ps.setString(2, code);
                ps.setString(3, name.trim());
                ps.setString(4, cycle);
                ps.setBigDecimal(5, amt);
                ps.setString(6, currency);
                ps.setObject(7, ctx.getUserId());
                ps.executeUpdate();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id.toString());
            out.put("planCode", code);
            out.put("name", name.trim());
            out.put("billingCycle", cycle);
            out.put("price", amt);
            out.put("currencyCode", currency);
            out.put("published", false);
            auditMutation(ctx, "iadm04.plan.create", "SUBSCRIPTION_PLAN", code);
            return out;
        });
    }

    public Map<String, Object> publishPlan(UserSecurityContext ctx, String planCode) {
        return setPlanPublished(ctx, planCode, true, "iadm04.plan.publish");
    }

    public Map<String, Object> retirePlan(UserSecurityContext ctx, String planCode) {
        return setPlanPublished(ctx, planCode, false, "iadm04.plan.retire");
    }

    private Map<String, Object> setPlanPublished(UserSecurityContext ctx, String planCode,
                                                 boolean published, String auditAction) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            if (planCode == null || planCode.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM04_PLAN_CODE_REQUIRED", "planCode required");
            }
            String code = planCode.trim().toUpperCase(Locale.ROOT);
            int updated;
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE plat.subscription_plans SET is_published=?, updated_at=now(), updated_by=?, " +
                            "row_version = row_version + 1 WHERE plan_code=? AND deleted_at IS NULL")) {
                ps.setBoolean(1, published);
                ps.setObject(2, ctx.getUserId());
                ps.setString(3, code);
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                throw new InstituteAdminException(404, "IADM04_PLAN_NOT_FOUND", "Plan not found: " + code);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("planCode", code);
            out.put("published", published);
            auditMutation(ctx, auditAction, "SUBSCRIPTION_PLAN", code);
            return out;
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
