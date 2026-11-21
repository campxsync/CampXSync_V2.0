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
            UUID tenantId = null;
            String requestedPlan = null;
            try (PreparedStatement sel = conn.prepareStatement(
                    "SELECT tenant_id, requested_plan_code FROM plat.plan_change_requests " +
                            "WHERE id=? AND status='PENDING' AND deleted_at IS NULL")) {
                sel.setObject(1, requestId);
                try (ResultSet rs = sel.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(404, "IADM04_PLAN_CHANGE_NOT_FOUND",
                                "Pending plan-change request not found");
                    }
                    tenantId = (UUID) rs.getObject(1);
                    requestedPlan = rs.getString(2);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE plat.plan_change_requests SET status=?, decided_by=?, decided_at=now(), decision_note=?, " +
                            "updated_at=now(), updated_by=? " +
                            "WHERE id=? AND status='PENDING' AND deleted_at IS NULL")) {
                ps.setString(1, status);
                ps.setObject(2, ctx.getUserId());
                ps.setString(3, note);
                ps.setObject(4, ctx.getUserId());
                ps.setObject(5, requestId);
                if (ps.executeUpdate() == 0) {
                    throw new InstituteAdminException(404, "IADM04_PLAN_CHANGE_NOT_FOUND",
                            "Pending plan-change request not found");
                }
            }
            String appliedSubscriptionId = null;
            if ("APPROVED".equals(status) && requestedPlan != null) {
                UUID planId = lookupPublishedPlanId(conn, requestedPlan.trim().toUpperCase(Locale.ROOT));
                try (PreparedStatement cancel = conn.prepareStatement(
                        "UPDATE plat.subscriptions SET status='CANCELLED', updated_at=now(), updated_by=? " +
                                "WHERE tenant_id=? AND deleted_at IS NULL AND status IN ('TRIAL','ACTIVE','PAST_DUE')")) {
                    cancel.setObject(1, ctx.getUserId());
                    cancel.setObject(2, tenantId);
                    cancel.executeUpdate();
                }
                UUID subId = UUID.randomUUID();
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO plat.subscriptions (id, tenant_id, plan_id, start_date, status, auto_renew, created_by) " +
                                "VALUES (?,?,?, CURRENT_DATE, 'ACTIVE', true, ?)")) {
                    ins.setObject(1, subId);
                    ins.setObject(2, tenantId);
                    ins.setObject(3, planId);
                    ins.setObject(4, ctx.getUserId());
                    ins.executeUpdate();
                }
                appliedSubscriptionId = subId.toString();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requestId", requestId.toString());
            out.put("status", status);
            out.put("decision", dec);
            if (appliedSubscriptionId != null) out.put("subscriptionId", appliedSubscriptionId);
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

    /**
     * IADM-04-US-002: assign a published plan to a tenant as TRIAL or ACTIVE.
     */
    public Map<String, Object> assignSubscription(UserSecurityContext ctx, UUID tenantId, String planCode,
                                                  String status, String startDateIso) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            if (tenantId == null) {
                throw new InstituteAdminException(400, "IADM04_TENANT_REQUIRED", "tenantId required");
            }
            if (planCode == null || planCode.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM04_PLAN_CODE_REQUIRED", "planCode required");
            }
            String st = status == null || status.trim().isEmpty() ? "ACTIVE" : status.trim().toUpperCase(Locale.ROOT);
            if (!"TRIAL".equals(st) && !"ACTIVE".equals(st)) {
                throw new InstituteAdminException(400, "IADM04_SUB_STATUS",
                        "Initial status must be TRIAL or ACTIVE");
            }
            UUID planId = lookupPublishedPlanId(conn, planCode.trim().toUpperCase(Locale.ROOT));
            java.sql.Date start = parseDateOrToday(startDateIso);
            // Soft-cancel any prior ACTIVE/TRIAL for tenant before assign
            try (PreparedStatement cancel = conn.prepareStatement(
                    "UPDATE plat.subscriptions SET status='CANCELLED', updated_at=now(), updated_by=? " +
                            "WHERE tenant_id=? AND deleted_at IS NULL AND status IN ('TRIAL','ACTIVE','PAST_DUE')")) {
                cancel.setObject(1, ctx.getUserId());
                cancel.setObject(2, tenantId);
                cancel.executeUpdate();
            }
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO plat.subscriptions (id, tenant_id, plan_id, start_date, status, auto_renew, created_by) " +
                            "VALUES (?,?,?,?,?, true, ?)")) {
                ps.setObject(1, id);
                ps.setObject(2, tenantId);
                ps.setObject(3, planId);
                ps.setDate(4, start);
                ps.setString(5, st);
                ps.setObject(6, ctx.getUserId());
                ps.executeUpdate();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("subscriptionId", id.toString());
            out.put("tenantId", tenantId.toString());
            out.put("planCode", planCode.trim().toUpperCase(Locale.ROOT));
            out.put("status", st);
            out.put("startDate", start.toString());
            auditMutation(ctx, "iadm04.subscription.assign", "SUBSCRIPTION", id.toString());
            return out;
        });
    }

    /**
     * IADM-04-US-002: legal status transitions on an existing subscription.
     * Allowed: TRIAL→ACTIVE, ACTIVE→PAST_DUE|SUSPENDED|CANCELLED,
     * PAST_DUE→ACTIVE|SUSPENDED|CANCELLED, SUSPENDED→ACTIVE|CANCELLED.
     */
    public Map<String, Object> transitionSubscription(UserSecurityContext ctx, UUID subscriptionId, String newStatus) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            if (subscriptionId == null || newStatus == null || newStatus.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM04_TRANSITION_REQUIRED",
                        "subscriptionId and status required");
            }
            String target = newStatus.trim().toUpperCase(Locale.ROOT);
            String current;
            UUID tenantId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT status, tenant_id FROM plat.subscriptions WHERE id=? AND deleted_at IS NULL")) {
                ps.setObject(1, subscriptionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(404, "IADM04_SUB_NOT_FOUND", "Subscription not found");
                    }
                    current = rs.getString(1);
                    tenantId = (UUID) rs.getObject(2);
                }
            }
            if (!isAllowedSubscriptionTransition(current, target)) {
                throw new InstituteAdminException(400, "IADM04_ILLEGAL_TRANSITION",
                        "Cannot transition " + current + " → " + target);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE plat.subscriptions SET status=?, updated_at=now(), updated_by=? " +
                            "WHERE id=? AND deleted_at IS NULL")) {
                ps.setString(1, target);
                ps.setObject(2, ctx.getUserId());
                ps.setObject(3, subscriptionId);
                ps.executeUpdate();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("subscriptionId", subscriptionId.toString());
            out.put("tenantId", tenantId.toString());
            out.put("fromStatus", current);
            out.put("status", target);
            auditMutation(ctx, "iadm04.subscription.transition", "SUBSCRIPTION", subscriptionId.toString());
            return out;
        });
    }

    /**
     * IADM-04-US-005: issue DRAFT→ISSUED invoice (or create ISSUED) for a subscription.
     */
    public Map<String, Object> issueInvoice(UserSecurityContext ctx, UUID tenantId, UUID subscriptionId,
                                            BigDecimal amount, String currencyCode, String invoiceNumber) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            if (tenantId == null || subscriptionId == null) {
                throw new InstituteAdminException(400, "IADM04_INVOICE_REQUIRED",
                        "tenantId and subscriptionId required");
            }
            BigDecimal amt = amount == null ? BigDecimal.ZERO : amount;
            if (amt.compareTo(BigDecimal.ZERO) < 0) {
                throw new InstituteAdminException(400, "IADM04_INVOICE_AMOUNT", "amount must be >= 0");
            }
            // Verify subscription belongs to tenant
            try (PreparedStatement chk = conn.prepareStatement(
                    "SELECT 1 FROM plat.subscriptions WHERE id=? AND tenant_id=? AND deleted_at IS NULL")) {
                chk.setObject(1, subscriptionId);
                chk.setObject(2, tenantId);
                try (ResultSet rs = chk.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(404, "IADM04_SUB_NOT_FOUND",
                                "Subscription not found for tenant");
                    }
                }
            }
            String currency = currencyCode == null || currencyCode.trim().isEmpty()
                    ? "INR" : currencyCode.trim().toUpperCase(Locale.ROOT);
            if (currency.length() > 3) currency = currency.substring(0, 3);
            String number = invoiceNumber == null || invoiceNumber.trim().isEmpty()
                    ? "INV-" + System.currentTimeMillis() : invoiceNumber.trim();
            UUID id = UUID.randomUUID();
            java.sql.Date today = new java.sql.Date(System.currentTimeMillis());
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO plat.invoices " +
                            "(id, tenant_id, subscription_id, invoice_number, issued_on, due_date, amount, " +
                            "currency_code, status, created_by) VALUES (?,?,?,?,?,?,?,?, 'ISSUED', ?)")) {
                ps.setObject(1, id);
                ps.setObject(2, tenantId);
                ps.setObject(3, subscriptionId);
                ps.setString(4, number);
                ps.setDate(5, today);
                ps.setDate(6, today);
                ps.setBigDecimal(7, amt);
                ps.setString(8, currency);
                ps.setObject(9, ctx.getUserId());
                ps.executeUpdate();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("invoiceId", id.toString());
            out.put("invoiceNumber", number);
            out.put("status", "ISSUED");
            out.put("amount", amt);
            out.put("currencyCode", currency);
            out.put("tenantId", tenantId.toString());
            out.put("subscriptionId", subscriptionId.toString());
            auditMutation(ctx, "iadm04.invoice.issue", "INVOICE", id.toString());
            return out;
        });
    }

    /**
     * IADM-04-US-005: reconcile — mark ISSUED invoice PAID (or VOID).
     */
    public Map<String, Object> reconcileInvoice(UserSecurityContext ctx, UUID invoiceId, String action) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            if (invoiceId == null) {
                throw new InstituteAdminException(400, "IADM04_INVOICE_ID", "invoiceId required");
            }
            String act = action == null ? "PAID" : action.trim().toUpperCase(Locale.ROOT);
            if (!"PAID".equals(act) && !"VOID".equals(act)) {
                throw new InstituteAdminException(400, "IADM04_RECONCILE_ACTION",
                        "action must be PAID or VOID");
            }
            String current;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT status FROM plat.invoices WHERE id=? AND deleted_at IS NULL")) {
                ps.setObject(1, invoiceId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(404, "IADM04_INVOICE_NOT_FOUND", "Invoice not found");
                    }
                    current = rs.getString(1);
                }
            }
            if ("PAID".equals(act) && !"ISSUED".equals(current) && !"DRAFT".equals(current)) {
                throw new InstituteAdminException(400, "IADM04_ILLEGAL_INVOICE_TRANSITION",
                        "Cannot mark " + current + " as PAID");
            }
            if ("VOID".equals(act) && "PAID".equals(current)) {
                throw new InstituteAdminException(400, "IADM04_ILLEGAL_INVOICE_TRANSITION",
                        "Cannot VOID a PAID invoice");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE plat.invoices SET status=?, paid_at=CASE WHEN ?='PAID' THEN now() ELSE paid_at END, " +
                            "updated_at=now(), updated_by=? WHERE id=? AND deleted_at IS NULL")) {
                ps.setString(1, act);
                ps.setString(2, act);
                ps.setObject(3, ctx.getUserId());
                ps.setObject(4, invoiceId);
                ps.executeUpdate();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("invoiceId", invoiceId.toString());
            out.put("fromStatus", current);
            out.put("status", act);
            auditMutation(ctx, "iadm04.invoice.reconcile", "INVOICE", invoiceId.toString());
            return out;
        });
    }

    private static UUID lookupPublishedPlanId(java.sql.Connection conn, String planCode) throws java.sql.SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM plat.subscription_plans WHERE plan_code=? AND deleted_at IS NULL AND is_published=true")) {
            ps.setString(1, planCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new InstituteAdminException(404, "IADM04_PLAN_NOT_FOUND",
                            "Published plan not found: " + planCode);
                }
                return (UUID) rs.getObject(1);
            }
        }
    }

    private static boolean isAllowedSubscriptionTransition(String from, String to) {
        if (from == null || to == null || from.equals(to)) return false;
        switch (from) {
            case "TRIAL":
                return "ACTIVE".equals(to) || "CANCELLED".equals(to);
            case "ACTIVE":
                return "PAST_DUE".equals(to) || "SUSPENDED".equals(to) || "CANCELLED".equals(to);
            case "PAST_DUE":
                return "ACTIVE".equals(to) || "SUSPENDED".equals(to) || "CANCELLED".equals(to);
            case "SUSPENDED":
                return "ACTIVE".equals(to) || "CANCELLED".equals(to);
            default:
                return false;
        }
    }

    private static java.sql.Date parseDateOrToday(String iso) {
        if (iso == null || iso.trim().isEmpty()) {
            return new java.sql.Date(System.currentTimeMillis());
        }
        try {
            return java.sql.Date.valueOf(iso.trim().substring(0, Math.min(10, iso.trim().length())));
        } catch (IllegalArgumentException e) {
            throw new InstituteAdminException(400, "IADM04_DATE", "startDate must be YYYY-MM-DD");
        }
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
