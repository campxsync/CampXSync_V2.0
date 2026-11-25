package com.campx.iadm.billing.service;

import com.campx.iadm.billing.config.DatabaseConnectionManager;
import com.campx.iadm.billing.exception.InstituteAdminException;
import com.campx.iadm.billing.exception.ResourceConflictException;
import com.campx.iadm.billing.exception.ResourceNotFoundException;
import com.campx.iadm.billing.exception.UserProfileAccessDeniedException;
import com.campx.iadm.billing.security.UserSecurityContext;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-04 Subscription, Billing & Entitlements — S1 domain operations.
 */
public class BillingDomainService {

    private final DatabaseConnectionManager db;

    public BillingDomainService() {
        this(DatabaseConnectionManager.getInstance());
    }

    public BillingDomainService(DatabaseConnectionManager db) {
        this.db = db == null ? DatabaseConnectionManager.getInstance() : db;
    }

    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("status", "UP");
        out.put("service", "IADM-04");
        out.put("prefix", "/api/v1/iadm/04");
        out.put("port", Integer.valueOf(8102));
        out.put("scope", "Subscription, Billing and Entitlements");
        out.put("dbInitialized", Boolean.valueOf(db != null));
        return out;
    }

    public Map<String, Object> listPlans(final UserSecurityContext ctx, final String statusFilter) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, ctx, "iadm04.plans.view", "iadm04.plans.manage", "iadm04.subscriptions.view",
                        "iadm04.subscriptions.view.own");
                String sql = "SELECT id, plan_code, name, billing_cycle, currency_code, price_minor_units, "
                        + "version_no, plan_status, is_published, demo_label, created_at "
                        + "FROM plat.subscription_plans WHERE deleted_at IS NULL";
                if (statusFilter != null && !statusFilter.trim().isEmpty()) {
                    sql += " AND plan_status = ?";
                }
                sql += " ORDER BY plan_code, version_no DESC";
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (statusFilter != null && !statusFilter.trim().isEmpty()) {
                        ps.setString(1, statusFilter.trim().toUpperCase(Locale.ROOT));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            items.add(mapPlan(rs));
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                out.put("count", Integer.valueOf(items.size()));
                return out;
            }
        });
    }

    public Map<String, Object> getPlan(final UserSecurityContext ctx, final UUID planId) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, ctx, "iadm04.plans.view", "iadm04.plans.manage");
                Map<String, Object> plan = loadPlan(conn, planId);
                plan.put("entitlements", listPlanEntitlementsInternal(conn, planId));
                return plan;
            }
        });
    }

    public Map<String, Object> createPlan(final UserSecurityContext ctx, final String planCode, final String name,
                                         final String billingCycle, final long priceMinorUnits,
                                         final String currencyCode, final String idempotencyKey) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iadm04.plans.manage");
                if (planCode == null || planCode.trim().isEmpty()) {
                    throw new InstituteAdminException(400, "IADM04_INVALID", "planCode is required.");
                }
                if (priceMinorUnits < 0) {
                    throw new InstituteAdminException(400, "IADM04_INVALID", "priceMinorUnits must be >= 0.");
                }
                String code = planCode.trim().toUpperCase(Locale.ROOT);
                String cycle = billingCycle == null || billingCycle.trim().isEmpty()
                        ? "MONTHLY" : billingCycle.trim().toUpperCase(Locale.ROOT);
                String currency = currencyCode == null || currencyCode.trim().isEmpty()
                        ? "INR" : currencyCode.trim().toUpperCase(Locale.ROOT);
                double legacyPrice = priceMinorUnits / 100.0d;

                String sql = "INSERT INTO plat.subscription_plans ("
                        + "plan_code, name, billing_cycle, price, currency_code, is_published, "
                        + "version_no, plan_status, price_minor_units, demo_label) "
                        + "VALUES (?, ?, ?, ?, ?, true, 1, 'ACTIVE', ?, 'DEMO') RETURNING id";
                UUID id;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, code);
                    ps.setString(2, name == null || name.trim().isEmpty() ? code : name.trim());
                    ps.setString(3, cycle);
                    ps.setDouble(4, legacyPrice);
                    ps.setString(5, currency);
                    ps.setLong(6, priceMinorUnits);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new InstituteAdminException(500, "IADM04_INTERNAL", "Plan insert failed.");
                        }
                        id = (UUID) rs.getObject(1);
                    }
                } catch (SQLException ex) {
                    if (ex.getMessage() != null && ex.getMessage().contains("uq_subscription_plans_code_version")) {
                        throw new ResourceConflictException("subscription_plan", "Plan code already exists for this version.");
                    }
                    throw ex;
                }
                DurableAuditWriter.append(conn, ctx, null, "IADM04_PLAN_CREATE", "subscription_plan",
                        id.toString(), "Created plan " + code);
                Map<String, Object> out = loadPlan(conn, id);
                out.put("idempotencyKey", idempotencyKey);
                return out;
            }
        });
    }

    public Map<String, Object> updatePlan(final UserSecurityContext ctx, final UUID planId, final String name,
                                          final Long priceMinorUnits, final String planStatus) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iadm04.plans.manage");
                Map<String, Object> existing = loadPlan(conn, planId);
                String newName = name != null && !name.trim().isEmpty() ? name.trim() : String.valueOf(existing.get("name"));
                long minor = priceMinorUnits != null ? priceMinorUnits.longValue()
                        : ((Number) existing.get("priceMinorUnits")).longValue();
                if (minor < 0) {
                    throw new InstituteAdminException(400, "IADM04_INVALID", "priceMinorUnits must be >= 0.");
                }
                String status = planStatus != null && !planStatus.trim().isEmpty()
                        ? planStatus.trim().toUpperCase(Locale.ROOT)
                        : String.valueOf(existing.get("planStatus"));
                if (!"DRAFT".equals(status) && !"ACTIVE".equals(status) && !"DEPRECATED".equals(status)) {
                    throw new InstituteAdminException(400, "IADM04_INVALID", "Invalid planStatus.");
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE plat.subscription_plans SET name = ?, price_minor_units = ?, price = ?, "
                                + "plan_status = ?, updated_at = now() WHERE id = ? AND deleted_at IS NULL")) {
                    ps.setString(1, newName);
                    ps.setLong(2, minor);
                    ps.setDouble(3, minor / 100.0d);
                    ps.setString(4, status);
                    ps.setObject(5, planId);
                    if (ps.executeUpdate() == 0) {
                        throw new ResourceNotFoundException("Plan not found.");
                    }
                }
                DurableAuditWriter.append(conn, ctx, null, "IADM04_PLAN_UPDATE", "subscription_plan",
                        planId.toString(), "Updated plan status=" + status);
                return loadPlan(conn, planId);
            }
        });
    }

    public Map<String, Object> mapEntitlement(final UserSecurityContext ctx, final UUID planId,
                                              final String entitlementKey, final Long limitValue,
                                              final Boolean enabled, final String limitUnit) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iadm04.entitlements.map");
                loadPlan(conn, planId);
                if (entitlementKey == null || entitlementKey.trim().isEmpty()) {
                    throw new InstituteAdminException(400, "IADM04_INVALID", "entitlementKey is required.");
                }
                String key = entitlementKey.trim();
                if (!featureFlagExists(conn, key)) {
                    throw new InstituteAdminException(400, "IADM04_UNKNOWN_FLAG",
                            "Unknown feature flag: " + key);
                }
                long limit = limitValue == null ? 1L : limitValue.longValue();
                boolean isEnabled = enabled == null || enabled.booleanValue();
                String unit = limitUnit == null || limitUnit.trim().isEmpty() ? "flag" : limitUnit.trim();

                UUID existingId = null;
                try (PreparedStatement find = conn.prepareStatement(
                        "SELECT id FROM plat.plan_entitlements WHERE plan_id = ? AND entitlement_key = ? "
                                + "AND deleted_at IS NULL LIMIT 1")) {
                    find.setObject(1, planId);
                    find.setString(2, key);
                    try (ResultSet rs = find.executeQuery()) {
                        if (rs.next()) {
                            existingId = (UUID) rs.getObject(1);
                        }
                    }
                }
                if (existingId != null) {
                    try (PreparedStatement upd = conn.prepareStatement(
                            "UPDATE plat.plan_entitlements SET limit_value = ?, is_enabled = ?, limit_unit = ?, "
                                    + "demo_label = 'DEMO', updated_at = now() WHERE id = ?")) {
                        upd.setLong(1, limit);
                        upd.setBoolean(2, isEnabled);
                        upd.setString(3, unit);
                        upd.setObject(4, existingId);
                        upd.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO plat.plan_entitlements (plan_id, entitlement_key, limit_value, is_enabled, "
                                    + "limit_unit, demo_label) VALUES (?, ?, ?, ?, ?, 'DEMO') RETURNING id")) {
                        ins.setObject(1, planId);
                        ins.setString(2, key);
                        ins.setLong(3, limit);
                        ins.setBoolean(4, isEnabled);
                        ins.setString(5, unit);
                        try (ResultSet rs = ins.executeQuery()) {
                            rs.next();
                            existingId = (UUID) rs.getObject(1);
                        }
                    }
                }
                DurableAuditWriter.append(conn, ctx, null, "IADM04_ENTITLEMENT_MAP", "plan_entitlement",
                        existingId.toString(), "Mapped " + key + " on plan " + planId);
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", existingId.toString());
                out.put("planId", planId.toString());
                out.put("entitlementKey", key);
                out.put("limitValue", Long.valueOf(limit));
                out.put("enabled", Boolean.valueOf(isEnabled));
                out.put("limitUnit", unit);
                return out;
            }
        });
    }

    public Map<String, Object> listFeatureFlags(final UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, ctx, "iadm04.entitlements.map", "iadm04.plans.view", "iadm04.plans.manage");
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT flag_key, name, description, enabled FROM cfg.feature_flags "
                                + "WHERE deleted_at IS NULL ORDER BY flag_key");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<String, Object>();
                        row.put("flagKey", rs.getString("flag_key"));
                        row.put("name", rs.getString("name"));
                        row.put("description", rs.getString("description"));
                        row.put("enabled", Boolean.valueOf(rs.getBoolean("enabled")));
                        items.add(row);
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                out.put("count", Integer.valueOf(items.size()));
                return out;
            }
        });
    }

    public Map<String, Object> listTrialPolicy(final UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, ctx, "iadm04.plans.view", "iadm04.plans.manage", "iadm04.subscriptions.attach");
                Map<String, Object> policy = new LinkedHashMap<String, Object>();
                String[] keys = new String[]{
                        "billing.timezone",
                        "billing.trial.eligibility",
                        "billing.trial.duration_days",
                        "billing.trial.conversion_mode",
                        "billing.grace.days",
                        "billing.renewal.reminder_days",
                        "billing.renewal.auto_invoice"
                };
                for (String key : keys) {
                    policy.put(key, readSettingDefault(conn, key));
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("policy", policy);
                out.put("source", "cfg.setting_definitions");
                out.put("label", "DEMO");
                return out;
            }
        });
    }

    public Map<String, Object> listSubscriptions(final UserSecurityContext ctx, final String statusFilter,
                                                 final UUID tenantFilter) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                boolean platformView = hasPermission(conn, "iadm04.subscriptions.view") || isPlatformOperator(conn);
                boolean ownView = hasPermission(conn, "iadm04.subscriptions.view.own");
                if (!platformView && !ownView) {
                    throw forbidden("iadm04.subscriptions.view");
                }
                StringBuilder sql = new StringBuilder(
                        "SELECT s.id, s.tenant_id, t.name AS tenant_name, t.status AS tenant_status, "
                                + "s.plan_id, p.plan_code, p.name AS plan_name, s.status, s.start_date, s.end_date, "
                                + "s.trial_ends_on, s.auto_renew, s.demo_label, s.status_reason "
                                + "FROM plat.subscriptions s "
                                + "JOIN core.tenants t ON t.id = s.tenant_id "
                                + "JOIN plat.subscription_plans p ON p.id = s.plan_id "
                                + "WHERE s.deleted_at IS NULL ");
                List<Object> params = new ArrayList<Object>();
                if (!platformView) {
                    UUID scope = ctx.getTenantId();
                    if (scope == null) {
                        throw new InstituteAdminException(403, "IADM04_FORBIDDEN",
                                "Institution scope required for own-subscription view.");
                    }
                    sql.append("AND s.tenant_id = ? ");
                    params.add(scope);
                } else if (tenantFilter != null) {
                    sql.append("AND s.tenant_id = ? ");
                    params.add(tenantFilter);
                }
                if (statusFilter != null && !statusFilter.trim().isEmpty()) {
                    sql.append("AND s.status = ? ");
                    params.add(statusFilter.trim().toUpperCase(Locale.ROOT));
                }
                sql.append("ORDER BY t.name, s.created_at DESC");
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            items.add(mapSubscription(rs));
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                out.put("count", Integer.valueOf(items.size()));
                out.put("scope", platformView ? "PLATFORM" : "OWN");
                return out;
            }
        });
    }

    public Map<String, Object> getSubscription(final UserSecurityContext ctx, final UUID subscriptionId) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                Map<String, Object> sub = loadSubscription(conn, subscriptionId);
                UUID tenantId = UUID.fromString(String.valueOf(sub.get("tenantId")));
                assertSubscriptionReadable(conn, ctx, tenantId);
                UUID planId = UUID.fromString(String.valueOf(sub.get("planId")));
                String status = String.valueOf(sub.get("status"));
                if ("PENDING".equals(status) || "EXPIRED".equals(status) || "CANCELLED".equals(status)
                        || "SUSPENDED".equals(status)) {
                    sub.put("entitlements", new ArrayList<Map<String, Object>>());
                    sub.put("entitlementsNote", "No entitlements while subscription is " + status);
                } else {
                    sub.put("entitlements", listPlanEntitlementsInternal(conn, planId));
                }
                sub.put("history", listHistory(conn, subscriptionId));
                return sub;
            }
        });
    }

    public Map<String, Object> getOwnSubscription(final UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, ctx, "iadm04.subscriptions.view.own", "iadm04.subscriptions.view");
                UUID tenantId = ctx.getTenantId();
                if (tenantId == null) {
                    throw new InstituteAdminException(400, "IADM04_INVALID", "Tenant context required.");
                }
                UUID subId = findCurrentSubscriptionId(conn, tenantId);
                if (subId == null) {
                    Map<String, Object> empty = new LinkedHashMap<String, Object>();
                    empty.put("subscription", null);
                    empty.put("entitlements", new ArrayList<Object>());
                    empty.put("accessAllowed", Boolean.FALSE);
                    empty.put("reason", "NO_CURRENT_SUBSCRIPTION");
                    return empty;
                }
                Map<String, Object> sub = loadSubscription(conn, subId);
                String status = String.valueOf(sub.get("status"));
                boolean access = "TRIAL".equals(status) || "ACTIVE".equals(status);
                List<Map<String, Object>> ents;
                if (access) {
                    ents = listPlanEntitlementsInternal(conn, UUID.fromString(String.valueOf(sub.get("planId"))));
                } else {
                    ents = new ArrayList<Map<String, Object>>();
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("subscription", sub);
                out.put("entitlements", ents);
                out.put("accessAllowed", Boolean.valueOf(access && instituteActive(conn, tenantId)));
                out.put("history", listHistory(conn, subId));
                return out;
            }
        });
    }

    public Map<String, Object> attachSubscription(final UserSecurityContext ctx, final UUID tenantId,
                                                  final String planCodeOrId, final String mode,
                                                  final String idempotencyKey) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iadm04.subscriptions.attach");
                if (tenantId == null) {
                    throw new InstituteAdminException(400, "IADM04_INVALID", "tenantId is required.");
                }
                if (!instituteExists(conn, tenantId)) {
                    throw new ResourceNotFoundException("Institute not found.");
                }
                String key = idempotencyKey == null || idempotencyKey.trim().isEmpty()
                        ? ("attach-" + tenantId + "-" + System.currentTimeMillis())
                        : idempotencyKey.trim();
                UUID existingByKey = findByIdempotency(conn, key);
                if (existingByKey != null) {
                    Map<String, Object> replay = loadSubscription(conn, existingByKey);
                    replay.put("idempotentReplay", Boolean.TRUE);
                    return replay;
                }
                if (findCurrentSubscriptionId(conn, tenantId) != null) {
                    throw new ResourceConflictException("subscription",
                            "Institute already has a current TRIAL, PENDING or ACTIVE subscription.");
                }
                UUID planId = resolveActivePlan(conn, planCodeOrId);
                Map<String, Object> plan = loadPlan(conn, planId);
                if ("DEPRECATED".equals(String.valueOf(plan.get("planStatus")))
                        || "DRAFT".equals(String.valueOf(plan.get("planStatus")))) {
                    throw new InstituteAdminException(400, "IADM04_PLAN_INACTIVE",
                            "Deprecated or draft plans cannot be newly attached.");
                }
                String attachMode = mode == null ? "PENDING" : mode.trim().toUpperCase(Locale.ROOT);
                String status;
                Date trialEnds = null;
                if ("TRIAL".equals(attachMode)) {
                    status = "TRIAL";
                    int days = readIntSetting(conn, "billing.trial.duration_days", 30);
                    trialEnds = Date.valueOf(LocalDate.now().plusDays(days));
                } else {
                    status = "PENDING";
                }
                UUID subId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO plat.subscriptions (tenant_id, plan_id, start_date, end_date, auto_renew, "
                                + "status, demo_label, idempotency_key, trial_ends_on, status_reason) "
                                + "VALUES (?, ?, CURRENT_DATE, NULL, true, ?, 'DEMO', ?, ?, ?) RETURNING id")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, planId);
                    ps.setString(3, status);
                    ps.setString(4, key);
                    if (trialEnds == null) {
                        ps.setNull(5, Types.DATE);
                    } else {
                        ps.setDate(5, trialEnds);
                    }
                    ps.setString(6, "IADM-04 attach " + status);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new InstituteAdminException(500, "IADM04_INTERNAL", "Attach failed.");
                        }
                        subId = (UUID) rs.getObject(1);
                    }
                } catch (SQLException ex) {
                    if (ex.getMessage() != null && ex.getMessage().contains("uq_subscriptions_one_current")) {
                        throw new ResourceConflictException("subscription",
                                "Institute already has a current TRIAL, PENDING or ACTIVE subscription.");
                    }
                    if (ex.getMessage() != null && ex.getMessage().contains("uq_subscriptions_idempotency")) {
                        UUID again = findByIdempotency(conn, key);
                        if (again != null) {
                            Map<String, Object> replay = loadSubscription(conn, again);
                            replay.put("idempotentReplay", Boolean.TRUE);
                            return replay;
                        }
                    }
                    throw ex;
                }
                insertHistory(conn, tenantId, subId, null, status, "attach", ctx.getUserId(), key);
                DurableAuditWriter.append(conn, ctx, tenantId, "IADM04_SUBSCRIPTION_ATTACH", "subscription",
                        subId.toString(), "Attached " + status + " plan " + plan.get("planCode"));
                Map<String, Object> out = loadSubscription(conn, subId);
                out.put("instituteActivated", Boolean.FALSE);
                out.put("note", "IADM-04 never activates the institute");
                return out;
            }
        });
    }

    /**
     * Dual access gate (US-016): institute ACTIVE ∧ subscription TRIAL|ACTIVE; PENDING = deny.
     * Authoritative DB — no cache.
     */
    public Map<String, Object> checkEntitlement(final UserSecurityContext ctx, final UUID instituteId,
                                                final String featureKey) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iadm04.entitlements.check");
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("instituteId", instituteId == null ? null : instituteId.toString());
                out.put("featureKey", featureKey);
                out.put("allowed", Boolean.FALSE);
                out.put("cached", Boolean.FALSE);
                if (instituteId == null || featureKey == null || featureKey.trim().isEmpty()) {
                    out.put("reason", "INVALID_REQUEST");
                    return out;
                }
                if (!instituteExists(conn, instituteId)) {
                    out.put("reason", "UNKNOWN_INSTITUTE");
                    return out;
                }
                boolean instituteOk = instituteActive(conn, instituteId);
                UUID subId = findCurrentSubscriptionId(conn, instituteId);
                if (subId == null) {
                    // also consider GRACE as current for display, but gate only TRIAL/ACTIVE
                    subId = findSubscriptionByStatuses(conn, instituteId,
                            new String[]{"TRIAL", "PENDING", "ACTIVE", "GRACE", "EXPIRED"});
                }
                String subStatus = null;
                UUID planId = null;
                if (subId != null) {
                    Map<String, Object> sub = loadSubscription(conn, subId);
                    subStatus = String.valueOf(sub.get("status"));
                    planId = UUID.fromString(String.valueOf(sub.get("planId")));
                }
                out.put("subscriptionStatus", subStatus);
                out.put("instituteStatus", instituteOk ? "ACTIVE" : "NOT_ACTIVE");
                if (!instituteOk) {
                    out.put("reason", "INSTITUTE_NOT_ACTIVE");
                    return out;
                }
                if (subStatus == null) {
                    out.put("reason", "NO_SUBSCRIPTION");
                    return out;
                }
                if ("PENDING".equals(subStatus)) {
                    out.put("reason", "PENDING_ZERO_ENTITLEMENTS");
                    return out;
                }
                if (!"TRIAL".equals(subStatus) && !"ACTIVE".equals(subStatus)) {
                    out.put("reason", "SUBSCRIPTION_NOT_ACCESS_GRANTING");
                    return out;
                }
                if (!featureFlagExists(conn, featureKey.trim())) {
                    out.put("reason", "UNKNOWN_FEATURE");
                    return out;
                }
                boolean entitled = false;
                Long limit = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT is_enabled, limit_value FROM plat.plan_entitlements "
                                + "WHERE plan_id = ? AND entitlement_key = ? AND deleted_at IS NULL LIMIT 1")) {
                    ps.setObject(1, planId);
                    ps.setString(2, featureKey.trim());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            entitled = rs.getBoolean("is_enabled");
                            limit = Long.valueOf(rs.getLong("limit_value"));
                        }
                    }
                }
                out.put("limitValue", limit);
                if (!entitled) {
                    out.put("reason", "NOT_IN_PLAN");
                    return out;
                }
                out.put("allowed", Boolean.TRUE);
                out.put("reason", "OK");
                return out;
            }
        });
    }

    public Map<String, Object> accessGateProbe(final UserSecurityContext ctx, final UUID instituteId) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, ctx, "iadm04.entitlements.check", "iadm04.subscriptions.view",
                        "iadm04.subscriptions.view.own");
                UUID id = instituteId != null ? instituteId : ctx.getTenantId();
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                if (id == null) {
                    out.put("allowed", Boolean.FALSE);
                    out.put("reason", "MISSING_INSTITUTE");
                    return out;
                }
                boolean allowed = false;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT plat.subscription_allows_access(?)")) {
                    ps.setObject(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        allowed = rs.next() && rs.getBoolean(1);
                    }
                }
                out.put("instituteId", id.toString());
                out.put("allowed", Boolean.valueOf(allowed));
                out.put("reason", allowed ? "ACTIVE_AND_TRIAL_OR_ACTIVE_SUB" : "GATE_DENIED");
                out.put("cached", Boolean.FALSE);
                return out;
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    private void requirePermission(Connection conn, UserSecurityContext ctx, String key) throws SQLException {
        if (isPlatformOperator(conn)) {
            return;
        }
        try {
            ctx.checkPermission(conn, key);
        } catch (UserProfileAccessDeniedException ex) {
            throw forbidden(key);
        }
    }

    private void requireAny(Connection conn, UserSecurityContext ctx, String... keys) throws SQLException {
        if (isPlatformOperator(conn)) {
            return;
        }
        for (String key : keys) {
            if (hasPermission(conn, key)) {
                return;
            }
        }
        throw forbidden(keys.length > 0 ? keys[0] : "iadm04");
    }

    private boolean isPlatformOperator(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT iam.is_platform_admin()");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getBoolean(1);
        }
    }

    private boolean hasPermission(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT iam.has_permission(?)")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private InstituteAdminException forbidden(String key) {
        return new InstituteAdminException(403, "IADM04_FORBIDDEN",
                "You are not authorized for this action (" + key + ").");
    }

    private void assertSubscriptionReadable(Connection conn, UserSecurityContext ctx, UUID tenantId)
            throws SQLException {
        if (isPlatformOperator(conn) || hasPermission(conn, "iadm04.subscriptions.view")) {
            return;
        }
        if (hasPermission(conn, "iadm04.subscriptions.view.own")
                && ctx.getTenantId() != null && ctx.getTenantId().equals(tenantId)) {
            return;
        }
        throw forbidden("iadm04.subscriptions.view");
    }

    private Map<String, Object> mapPlan(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", rs.getObject("id").toString());
        m.put("planCode", rs.getString("plan_code"));
        m.put("name", rs.getString("name"));
        m.put("billingCycle", rs.getString("billing_cycle"));
        m.put("currencyCode", rs.getString("currency_code"));
        m.put("priceMinorUnits", Long.valueOf(rs.getLong("price_minor_units")));
        m.put("versionNo", Integer.valueOf(rs.getInt("version_no")));
        m.put("planStatus", rs.getString("plan_status"));
        m.put("published", Boolean.valueOf(rs.getBoolean("is_published")));
        m.put("demoLabel", rs.getString("demo_label"));
        return m;
    }

    private Map<String, Object> loadPlan(Connection conn, UUID planId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, plan_code, name, billing_cycle, currency_code, price_minor_units, "
                        + "version_no, plan_status, is_published, demo_label, created_at "
                        + "FROM plat.subscription_plans WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("Plan not found.");
                }
                return mapPlan(rs);
            }
        }
    }

    private List<Map<String, Object>> listPlanEntitlementsInternal(Connection conn, UUID planId)
            throws SQLException {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, entitlement_key, limit_value, is_enabled, limit_unit, demo_label "
                        + "FROM plat.plan_entitlements WHERE plan_id = ? AND deleted_at IS NULL "
                        + "ORDER BY entitlement_key")) {
            ps.setObject(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getObject("id").toString());
                    row.put("entitlementKey", rs.getString("entitlement_key"));
                    row.put("limitValue", Long.valueOf(rs.getLong("limit_value")));
                    row.put("enabled", Boolean.valueOf(rs.getBoolean("is_enabled")));
                    row.put("limitUnit", rs.getString("limit_unit"));
                    row.put("demoLabel", rs.getString("demo_label"));
                    items.add(row);
                }
            }
        }
        return items;
    }

    private Map<String, Object> mapSubscription(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", rs.getObject("id").toString());
        m.put("tenantId", rs.getObject("tenant_id").toString());
        try {
            m.put("tenantName", rs.getString("tenant_name"));
            m.put("tenantStatus", rs.getString("tenant_status"));
            m.put("planCode", rs.getString("plan_code"));
            m.put("planName", rs.getString("plan_name"));
        } catch (SQLException ignored) {
            // narrower selects omit joins
        }
        m.put("planId", rs.getObject("plan_id").toString());
        m.put("status", rs.getString("status"));
        Date start = rs.getDate("start_date");
        Date end = rs.getDate("end_date");
        Date trial = null;
        try {
            trial = rs.getDate("trial_ends_on");
        } catch (SQLException ignored) {
        }
        m.put("startDate", start == null ? null : start.toString());
        m.put("endDate", end == null ? null : end.toString());
        m.put("trialEndsOn", trial == null ? null : trial.toString());
        try {
            m.put("autoRenew", Boolean.valueOf(rs.getBoolean("auto_renew")));
            m.put("demoLabel", rs.getString("demo_label"));
            m.put("statusReason", rs.getString("status_reason"));
        } catch (SQLException ignored) {
        }
        return m;
    }

    private Map<String, Object> loadSubscription(Connection conn, UUID subscriptionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT s.id, s.tenant_id, t.name AS tenant_name, t.status AS tenant_status, "
                        + "s.plan_id, p.plan_code, p.name AS plan_name, s.status, s.start_date, s.end_date, "
                        + "s.trial_ends_on, s.auto_renew, s.demo_label, s.status_reason "
                        + "FROM plat.subscriptions s "
                        + "JOIN core.tenants t ON t.id = s.tenant_id "
                        + "JOIN plat.subscription_plans p ON p.id = s.plan_id "
                        + "WHERE s.id = ? AND s.deleted_at IS NULL")) {
            ps.setObject(1, subscriptionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("Subscription not found.");
                }
                return mapSubscription(rs);
            }
        }
    }

    private List<Map<String, Object>> listHistory(Connection conn, UUID subscriptionId) throws SQLException {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, from_status, to_status, reason, actor_user_id, created_at "
                        + "FROM plat.subscription_history WHERE subscription_id = ? "
                        + "ORDER BY created_at DESC LIMIT 50")) {
            ps.setObject(1, subscriptionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getObject("id").toString());
                    row.put("fromStatus", rs.getString("from_status"));
                    row.put("toStatus", rs.getString("to_status"));
                    row.put("reason", rs.getString("reason"));
                    Object actor = rs.getObject("actor_user_id");
                    row.put("actorUserId", actor == null ? null : actor.toString());
                    row.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
                    items.add(row);
                }
            }
        }
        return items;
    }

    private void insertHistory(Connection conn, UUID tenantId, UUID subId, String from, String to,
                               String reason, UUID actor, String correlation) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO plat.subscription_history "
                        + "(tenant_id, subscription_id, from_status, to_status, reason, actor_user_id, correlation_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, subId);
            ps.setString(3, from);
            ps.setString(4, to);
            ps.setString(5, reason);
            ps.setObject(6, actor);
            ps.setString(7, correlation);
            ps.executeUpdate();
        }
    }

    private UUID findCurrentSubscriptionId(Connection conn, UUID tenantId) throws SQLException {
        return findSubscriptionByStatuses(conn, tenantId, new String[]{"TRIAL", "PENDING", "ACTIVE"});
    }

    private UUID findSubscriptionByStatuses(Connection conn, UUID tenantId, String[] statuses)
            throws SQLException {
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < statuses.length; i++) {
            if (i > 0) {
                in.append(',');
            }
            in.append('?');
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM plat.subscriptions WHERE tenant_id = ? AND deleted_at IS NULL "
                        + "AND status IN (" + in + ") ORDER BY created_at DESC LIMIT 1")) {
            ps.setObject(1, tenantId);
            for (int i = 0; i < statuses.length; i++) {
                ps.setString(i + 2, statuses[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject(1);
                }
            }
        }
        return null;
    }

    private UUID findByIdempotency(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM plat.subscriptions WHERE idempotency_key = ? AND deleted_at IS NULL LIMIT 1")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject(1);
                }
            }
        }
        return null;
    }

    private UUID resolveActivePlan(Connection conn, String planCodeOrId) throws SQLException {
        if (planCodeOrId == null || planCodeOrId.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM04_INVALID", "planCode or planId is required.");
        }
        String raw = planCodeOrId.trim();
        try {
            UUID asId = UUID.fromString(raw);
            loadPlan(conn, asId);
            return asId;
        } catch (IllegalArgumentException ignored) {
            // treat as plan code
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM plat.subscription_plans WHERE plan_code = ? AND deleted_at IS NULL "
                        + "AND plan_status = 'ACTIVE' ORDER BY version_no DESC LIMIT 1")) {
            ps.setString(1, raw.toUpperCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("Active plan not found: " + raw);
                }
                return (UUID) rs.getObject(1);
            }
        }
    }

    private boolean instituteExists(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM core.tenants WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean instituteActive(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT status FROM core.tenants WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && "ACTIVE".equals(rs.getString(1));
            }
        }
    }

    private boolean featureFlagExists(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM cfg.feature_flags WHERE flag_key = ? AND deleted_at IS NULL")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Object readSettingDefault(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT default_value FROM cfg.setting_definitions WHERE setting_key = ? AND deleted_at IS NULL")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return null;
    }

    private int readIntSetting(Connection conn, String key, int fallback) throws SQLException {
        Object raw = readSettingDefault(conn, key);
        if (raw == null) {
            return fallback;
        }
        String s = String.valueOf(raw).replace("\"", "").trim();
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
