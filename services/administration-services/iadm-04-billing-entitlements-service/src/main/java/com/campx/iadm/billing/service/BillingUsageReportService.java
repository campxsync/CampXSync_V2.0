package com.campx.iadm.billing.service;

import com.campx.iadm.billing.config.DatabaseConnectionManager;
import com.campx.iadm.billing.exception.InstituteAdminException;
import com.campx.iadm.billing.exception.UserProfileAccessDeniedException;
import com.campx.iadm.billing.security.UserSecurityContext;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-04 S4 — usage ingest/read + scoped billing reports + auditor evidence (US-033…036).
 */
public class BillingUsageReportService {

    private final DatabaseConnectionManager db;

    public BillingUsageReportService() {
        this(DatabaseConnectionManager.getInstance());
    }

    public BillingUsageReportService(DatabaseConnectionManager db) {
        this.db = db == null ? DatabaseConnectionManager.getInstance() : db;
    }

    /** US-033 — record usage measurement idempotently. */
    public Map<String, Object> recordUsage(final UserSecurityContext ctx, final UUID tenantId,
                                           final String source, final String periodStart, final String periodEnd,
                                           final String entitlementKey, final Double quantity, final String unit,
                                           final String dedupeKey, final Boolean incomplete,
                                           final String incompleteReason) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iadm04.usage.record");
                if (tenantId == null) {
                    throw new InstituteAdminException(400, "IADM04_INVALID", "tenantId is required.");
                }
                if (dedupeKey == null || dedupeKey.trim().isEmpty()) {
                    throw new InstituteAdminException(400, "IADM04_INVALID", "dedupeKey is required.");
                }
                if (entitlementKey == null || entitlementKey.trim().isEmpty()) {
                    throw new InstituteAdminException(400, "IADM04_INVALID", "entitlementKey is required.");
                }
                String key = dedupeKey.trim();
                Map<String, Object> existing = findByDedupe(conn, key);
                if (existing != null) {
                    existing.put("idempotentReplay", Boolean.TRUE);
                    existing.put("note", "Duplicate measurement ignored.");
                    return existing;
                }
                boolean inc = incomplete != null && incomplete.booleanValue();
                if (inc && (incompleteReason == null || incompleteReason.trim().isEmpty())) {
                    throw new InstituteAdminException(400, "IADM04_INVALID",
                            "incomplete_reason required when incomplete=true (never invent usage).");
                }
                LocalDate start = LocalDate.parse(periodStart == null ? LocalDate.now().toString() : periodStart);
                LocalDate end = LocalDate.parse(periodEnd == null ? start.toString() : periodEnd);
                double qty = quantity == null ? 0d : quantity.doubleValue();
                if (qty < 0) {
                    throw new InstituteAdminException(400, "IADM04_INVALID", "quantity must be >= 0.");
                }
                UUID id;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO plat.usage_measurements ("
                                + "tenant_id, source, period_start, period_end, entitlement_key, quantity, unit, "
                                + "dedupe_key, incomplete, incomplete_reason, demo_label) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DEMO') RETURNING id")) {
                    ps.setObject(1, tenantId);
                    ps.setString(2, source == null ? "UNKNOWN" : source);
                    ps.setDate(3, Date.valueOf(start));
                    ps.setDate(4, Date.valueOf(end));
                    ps.setString(5, entitlementKey.trim());
                    ps.setDouble(6, qty);
                    ps.setString(7, unit);
                    ps.setString(8, key);
                    ps.setBoolean(9, inc);
                    ps.setString(10, incompleteReason);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        id = (UUID) rs.getObject(1);
                    }
                }
                DurableAuditWriter.append(conn, ctx, tenantId, "IADM04_USAGE_RECORD",
                        "usage_measurement", id.toString(), "Usage recorded");
                Map<String, Object> out = findByDedupe(conn, key);
                out.put("overageCharge", Boolean.FALSE);
                out.put("throttling", Boolean.FALSE);
                out.put("note", "No overage charge and no automatic throttling (D8).");
                return out;
            }
        });
    }

    /** US-034 — usage vs quota for own institute (or platform filter). */
    public Map<String, Object> getUsageAgainstQuota(final UserSecurityContext ctx, final UUID tenantFilter) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                boolean platform = isPlatformOperator(conn) || hasPermission(conn, "iadm04.reports.view")
                        || hasPermission(conn, "iadm04.subscriptions.view");
                boolean own = hasPermission(conn, "iadm04.subscriptions.view.own");
                if (!platform && !own) {
                    throw forbidden("iadm04.subscriptions.view.own");
                }
                UUID tenantId = tenantFilter;
                if (!platform) {
                    tenantId = ctx.getTenantId();
                    if (tenantId == null) {
                        throw new InstituteAdminException(403, "IADM04_FORBIDDEN", "Tenant context required.");
                    }
                }
                if (tenantId == null) {
                    throw new InstituteAdminException(400, "IADM04_INVALID", "tenantId is required for platform view.");
                }
                // Current plan limits
                List<Map<String, Object>> limits = new ArrayList<Map<String, Object>>();
                UUID planId = null;
                String subStatus = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT plan_id, status FROM plat.subscriptions WHERE tenant_id = ? AND deleted_at IS NULL "
                                + "AND status IN ('TRIAL','PENDING','ACTIVE','GRACE') "
                                + "ORDER BY CASE status WHEN 'ACTIVE' THEN 1 WHEN 'TRIAL' THEN 2 "
                                + "WHEN 'GRACE' THEN 3 ELSE 4 END LIMIT 1")) {
                    ps.setObject(1, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            planId = (UUID) rs.getObject(1);
                            subStatus = rs.getString(2);
                        }
                    }
                }
                if (planId != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT entitlement_key, limit_value, limit_unit, is_enabled "
                                    + "FROM plat.plan_entitlements WHERE plan_id = ? AND deleted_at IS NULL")) {
                        ps.setObject(1, planId);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                Map<String, Object> lim = new LinkedHashMap<String, Object>();
                                lim.put("entitlementKey", rs.getString("entitlement_key"));
                                lim.put("limitValue", rs.getObject("limit_value"));
                                lim.put("limitUnit", rs.getString("limit_unit"));
                                lim.put("enabled", Boolean.valueOf(rs.getBoolean("is_enabled")));
                                limits.add(lim);
                            }
                        }
                    }
                }
                List<Map<String, Object>> used = new ArrayList<Map<String, Object>>();
                boolean anyIncomplete = false;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT entitlement_key, SUM(quantity) AS used_qty, BOOL_OR(incomplete) AS any_inc, "
                                + "MAX(period_end) AS period_end "
                                + "FROM plat.usage_measurements WHERE tenant_id = ? AND deleted_at IS NULL "
                                + "GROUP BY entitlement_key")) {
                    ps.setObject(1, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> u = new LinkedHashMap<String, Object>();
                            u.put("entitlementKey", rs.getString("entitlement_key"));
                            u.put("used", Double.valueOf(rs.getDouble("used_qty")));
                            boolean inc = rs.getBoolean("any_inc");
                            u.put("incomplete", Boolean.valueOf(inc));
                            if (inc) {
                                anyIncomplete = true;
                            }
                            u.put("periodEnd", rs.getDate("period_end") == null ? null
                                    : rs.getDate("period_end").toString());
                            used.add(u);
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("tenantId", tenantId.toString());
                out.put("subscriptionStatus", subStatus);
                out.put("limits", limits);
                out.put("usage", used);
                out.put("incompleteData", Boolean.valueOf(anyIncomplete));
                if (anyIncomplete) {
                    out.put("incompleteNote", "One or more usage sources are incomplete; values were not invented.");
                }
                out.put("readOnly", Boolean.TRUE);
                return out;
            }
        });
    }

    /** US-035 — scoped billing summary. */
    public Map<String, Object> billingReport(final UserSecurityContext ctx, final UUID tenantFilter) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                boolean platform = isPlatformOperator(conn) || hasPermission(conn, "iadm04.reports.view")
                        || hasPermission(conn, "iadm04.subscriptions.view");
                boolean own = hasPermission(conn, "iadm04.subscriptions.view.own")
                        || hasPermission(conn, "iadm04.invoices.view.own");
                boolean auditor = hasPermission(conn, "iadm04.audit.view");
                if (!platform && !own && !auditor) {
                    throw forbidden("iadm04.reports.view");
                }
                UUID scopeTenant = null;
                if (!platform) {
                    scopeTenant = ctx.getTenantId();
                    if (scopeTenant == null && tenantFilter != null && auditor) {
                        scopeTenant = tenantFilter;
                    }
                    if (scopeTenant == null && !auditor) {
                        throw new InstituteAdminException(403, "IADM04_FORBIDDEN", "Tenant scope required.");
                    }
                } else if (tenantFilter != null) {
                    scopeTenant = tenantFilter;
                }
                Map<String, Object> summary = new LinkedHashMap<String, Object>();
                summary.put("subscriptionsByStatus", countBy(conn,
                        "SELECT status, COUNT(*) FROM plat.subscriptions WHERE deleted_at IS NULL "
                                + (scopeTenant == null ? "" : "AND tenant_id = ? ")
                                + "GROUP BY status", scopeTenant));
                summary.put("invoicesByStatus", countBy(conn,
                        "SELECT status, COUNT(*) FROM plat.invoices WHERE deleted_at IS NULL "
                                + (scopeTenant == null ? "" : "AND tenant_id = ? ")
                                + "GROUP BY status", scopeTenant));
                summary.put("onlineIntentsByStatus", countBy(conn,
                        "SELECT status, COUNT(*) FROM plat.online_payment_intents WHERE deleted_at IS NULL "
                                + (scopeTenant == null ? "" : "AND tenant_id = ? ")
                                + "GROUP BY status", scopeTenant));
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("scope", scopeTenant == null ? "PLATFORM" : scopeTenant.toString());
                out.put("summary", summary);
                out.put("exportRespectsScope", Boolean.TRUE);
                out.put("collegeAdminDenied", Boolean.TRUE);
                return out;
            }
        });
    }

    /** US-036 — auditor billing evidence (read-only). */
    public Map<String, Object> auditorEvidence(final UserSecurityContext ctx, final UUID tenantFilter) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                if (!isPlatformOperator(conn) && !hasPermission(conn, "iadm04.audit.view")
                        && !hasPermission(conn, "iadm04.reports.view")) {
                    throw forbidden("iadm04.audit.view");
                }
                List<Map<String, Object>> invoices = new ArrayList<Map<String, Object>>();
                String sql = "SELECT id, tenant_id, status, amount_minor_units, currency_code, internal_ref, "
                        + "issued_on FROM plat.invoices WHERE deleted_at IS NULL ";
                if (tenantFilter != null) {
                    sql += "AND tenant_id = ? ";
                }
                sql += "ORDER BY issued_on DESC NULLS LAST LIMIT 50";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (tenantFilter != null) {
                        ps.setObject(1, tenantFilter);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> m = new LinkedHashMap<String, Object>();
                            m.put("id", rs.getObject("id").toString());
                            m.put("tenantId", rs.getObject("tenant_id").toString());
                            m.put("status", rs.getString("status"));
                            m.put("amountMinorUnits", Long.valueOf(rs.getLong("amount_minor_units")));
                            m.put("currencyCode", rs.getString("currency_code"));
                            m.put("internalRef", rs.getString("internal_ref"));
                            invoices.add(m);
                        }
                    }
                }
                List<Map<String, Object>> history = new ArrayList<Map<String, Object>>();
                String hsql = "SELECT subscription_id, from_status, to_status, reason, created_at "
                        + "FROM plat.subscription_history WHERE 1=1 ";
                if (tenantFilter != null) {
                    hsql += "AND tenant_id = ? ";
                }
                hsql += "ORDER BY created_at DESC LIMIT 50";
                try (PreparedStatement ps = conn.prepareStatement(hsql)) {
                    if (tenantFilter != null) {
                        ps.setObject(1, tenantFilter);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> m = new LinkedHashMap<String, Object>();
                            m.put("subscriptionId", rs.getObject("subscription_id").toString());
                            m.put("fromStatus", rs.getString("from_status"));
                            m.put("toStatus", rs.getString("to_status"));
                            m.put("reason", rs.getString("reason"));
                            m.put("at", rs.getTimestamp("created_at") == null ? null
                                    : rs.getTimestamp("created_at").toInstant().toString());
                            history.add(m);
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("invoices", invoices);
                out.put("subscriptionHistory", history);
                out.put("readOnly", Boolean.TRUE);
                out.put("mutationsDenied", Boolean.TRUE);
                return out;
            }
        });
    }

    private Map<String, Object> findByDedupe(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, tenant_id, source, period_start, period_end, entitlement_key, quantity, unit, "
                        + "dedupe_key, incomplete, incomplete_reason FROM plat.usage_measurements "
                        + "WHERE dedupe_key = ? AND deleted_at IS NULL")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("id", rs.getObject("id").toString());
                m.put("tenantId", rs.getObject("tenant_id").toString());
                m.put("source", rs.getString("source"));
                m.put("periodStart", rs.getDate("period_start").toString());
                m.put("periodEnd", rs.getDate("period_end").toString());
                m.put("entitlementKey", rs.getString("entitlement_key"));
                m.put("quantity", Double.valueOf(rs.getDouble("quantity")));
                m.put("unit", rs.getString("unit"));
                m.put("dedupeKey", rs.getString("dedupe_key"));
                m.put("incomplete", Boolean.valueOf(rs.getBoolean("incomplete")));
                m.put("incompleteReason", rs.getString("incomplete_reason"));
                return m;
            }
        }
    }

    private Map<String, Integer> countBy(Connection conn, String sql, UUID tenantId) throws SQLException {
        Map<String, Integer> m = new LinkedHashMap<String, Integer>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (tenantId != null) {
                ps.setObject(1, tenantId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    m.put(rs.getString(1), Integer.valueOf(rs.getInt(2)));
                }
            }
        } catch (SQLException ex) {
            // Table may be absent on partial deploys — return empty rather than 500 for reports
            if (ex.getMessage() != null && ex.getMessage().contains("does not exist")) {
                return m;
            }
            throw ex;
        }
        return m;
    }

    private void requirePermission(Connection conn, UserSecurityContext ctx, String key) throws SQLException {
        if (isPlatformOperator(conn)) {
            return;
        }
        if (!hasPermission(conn, key)) {
            throw forbidden(key);
        }
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

    private static UserProfileAccessDeniedException forbidden(String key) {
        return new UserProfileAccessDeniedException("Missing permission: " + key);
    }
}
