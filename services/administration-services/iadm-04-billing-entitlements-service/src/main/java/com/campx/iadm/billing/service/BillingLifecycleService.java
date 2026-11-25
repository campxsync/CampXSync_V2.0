package com.campx.iadm.billing.service;

import com.campx.iadm.billing.config.DatabaseConnectionManager;
import com.campx.iadm.billing.exception.InstituteAdminException;
import com.campx.iadm.billing.exception.ResourceNotFoundException;
import com.campx.iadm.billing.exception.UserProfileAccessDeniedException;
import com.campx.iadm.billing.security.UserSecurityContext;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-04 S4 — scheduled renewal/trial/expiry jobs + Super Admin renewal desk (US-031/032).
 */
public class BillingLifecycleService {

    private final DatabaseConnectionManager db;
    private final BillingFinanceService finance;

    public BillingLifecycleService() {
        this(DatabaseConnectionManager.getInstance(), new BillingFinanceService());
    }

    public BillingLifecycleService(DatabaseConnectionManager db, BillingFinanceService finance) {
        this.db = db == null ? DatabaseConnectionManager.getInstance() : db;
        this.finance = finance == null ? new BillingFinanceService() : finance;
    }

    /** US-031 — run idempotent jobs; fail closed if billing.timezone absent. */
    public Map<String, Object> runScheduledJobs(final UserSecurityContext ctx) {
        Map<String, Object> gate = ctx.executeInTransaction(db,
                new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
                    public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                        requirePermission(conn, ctx, "iadm04.jobs.run");
                        String tz = readSetting(conn, "billing.timezone");
                        Map<String, Object> gateOut = new LinkedHashMap<String, Object>();
                        if (tz == null || tz.trim().isEmpty() || "null".equalsIgnoreCase(tz.trim())) {
                            gateOut.put("ran", Boolean.FALSE);
                            gateOut.put("errorCode", "IADM04_BILLING_TZ_MISSING");
                            gateOut.put("detail",
                                    "Expiry/renewal jobs did not run: billing.timezone is not configured (fail closed).");
                            recordCheckpoint(conn, ctx, "ALL", "FAIL_CLOSED_NO_TZ", "SKIPPED",
                                    "billing.timezone missing");
                            return gateOut;
                        }
                        try {
                            ZoneId zone = ZoneId.of(tz.replace("\"", "").trim());
                            gateOut.put("ran", Boolean.TRUE);
                            gateOut.put("zone", zone.getId());
                            gateOut.put("today", LocalDate.now(zone).toString());
                            gateOut.put("graceDays", Integer.valueOf(readIntSetting(conn, "billing.grace.days", -1)));
                            gateOut.put("reminderDays",
                                    Integer.valueOf(readIntSetting(conn, "billing.renewal.reminder_days", -1)));
                            return gateOut;
                        } catch (Exception ex) {
                            gateOut.put("ran", Boolean.FALSE);
                            gateOut.put("errorCode", "IADM04_BILLING_TZ_INVALID");
                            gateOut.put("detail",
                                    "Expiry/renewal jobs did not run: billing.timezone is invalid (fail closed).");
                            return gateOut;
                        }
                    }
                });
        if (!Boolean.TRUE.equals(gate.get("ran"))) {
            return gate;
        }
        final LocalDate today = LocalDate.parse(String.valueOf(gate.get("today")));
        final int graceDays = ((Number) gate.get("graceDays")).intValue();
        final int reminderDays = ((Number) gate.get("reminderDays")).intValue();

        Map<String, Object> trial = runTrialConversionsOuter(ctx, today);
        Map<String, Object> reminders = ctx.executeInTransaction(db,
                new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
                    public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                        requirePermission(conn, ctx, "iadm04.jobs.run");
                        return runRenewalReminders(conn, ctx, today, reminderDays);
                    }
                });
        Map<String, Object> expiry = ctx.executeInTransaction(db,
                new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
                    public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                        requirePermission(conn, ctx, "iadm04.jobs.run");
                        return runExpiryTransitions(conn, ctx, today, graceDays);
                    }
                });
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("ran", Boolean.TRUE);
        out.put("billingTimezone", gate.get("zone"));
        out.put("asOf", today.toString());
        out.put("trialConversions", trial);
        out.put("renewalReminders", reminders);
        out.put("expiryTransitions", expiry);
        return out;
    }

    private Map<String, Object> runTrialConversionsOuter(final UserSecurityContext ctx, final LocalDate today) {
        List<UUID> ids = ctx.executeInTransaction(db,
                new UserSecurityContext.TransactionCallback<List<UUID>>() {
                    public List<UUID> execute(Connection conn) throws SQLException, InstituteAdminException {
                        requirePermission(conn, ctx, "iadm04.jobs.run");
                        String ck = "trial-" + today;
                        if (checkpointExists(conn, "TRIAL_CONVERT", ck)) {
                            return null;
                        }
                        List<UUID> list = new ArrayList<UUID>();
                        try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT id FROM plat.subscriptions WHERE deleted_at IS NULL AND status = 'TRIAL' "
                                        + "AND trial_ends_on IS NOT NULL AND trial_ends_on <= ?")) {
                            ps.setDate(1, Date.valueOf(today));
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    list.add((UUID) rs.getObject(1));
                                }
                            }
                        }
                        return list;
                    }
                });
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (ids == null) {
            out.put("idempotentReplay", Boolean.TRUE);
            out.put("converted", Integer.valueOf(0));
            return out;
        }
        int converted = 0;
        int skipped = 0;
        for (UUID id : ids) {
            try {
                finance.convertTrial(ctx, id, "job-trial-" + id + "-" + today);
                converted++;
            } catch (Exception ex) {
                skipped++;
            }
        }
        final int c = converted;
        final int s = skipped;
        ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Void>() {
            public Void execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iadm04.jobs.run");
                recordCheckpoint(conn, ctx, "TRIAL_CONVERT", "trial-" + today, "SUCCEEDED",
                        "converted=" + c + ";skipped=" + s);
                return null;
            }
        });
        out.put("converted", Integer.valueOf(converted));
        out.put("skipped", Integer.valueOf(skipped));
        return out;
    }

    /** US-032 — list renewals/expiries for desk. */
    public Map<String, Object> listRenewals(final UserSecurityContext ctx, final String bucket) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, ctx, "iadm04.subscriptions.view", "iadm04.plans.manage");
                String tz = readSetting(conn, "billing.timezone");
                LocalDate today = LocalDate.now(ZoneId.of(
                        tz == null || tz.trim().isEmpty() ? "UTC" : tz.replace("\"", "").trim()));
                int reminderDays = Math.max(0, readIntSetting(conn, "billing.renewal.reminder_days", 14));
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                String sql = "SELECT s.id, s.tenant_id, t.name AS tenant_name, s.status, s.end_date, "
                        + "s.trial_ends_on, s.status_reason, p.plan_code "
                        + "FROM plat.subscriptions s "
                        + "JOIN core.tenants t ON t.id = s.tenant_id "
                        + "JOIN plat.subscription_plans p ON p.id = s.plan_id "
                        + "WHERE s.deleted_at IS NULL "
                        + "AND s.status IN ('TRIAL','ACTIVE','GRACE','EXPIRED','SUSPENDED') "
                        + "ORDER BY COALESCE(s.end_date, s.trial_ends_on) NULLS LAST LIMIT 200";
                try (PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = mapSubBrief(rs);
                        String status = String.valueOf(row.get("status"));
                        Date end = rs.getDate("end_date");
                        Date trialEnd = rs.getDate("trial_ends_on");
                        String computed = computeBucket(status, end, trialEnd, today, reminderDays);
                        row.put("bucket", computed);
                        if (bucket == null || bucket.trim().isEmpty()
                                || computed.equalsIgnoreCase(bucket.trim())) {
                            items.add(row);
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                out.put("count", Integer.valueOf(items.size()));
                out.put("asOf", today.toString());
                return out;
            }
        });
    }

    /** US-032 — Super Admin acts on renewal/expiry under policy. */
    public Map<String, Object> actOnRenewal(final UserSecurityContext ctx, final UUID subscriptionId,
                                            final String action, final String reason) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                // Commercial desk: platform subscriptions.view (R01); not R02/R03/R28 alone
                if (!isPlatformOperator(conn) && !hasPermission(conn, "iadm04.subscriptions.view")) {
                    throw forbidden("iadm04.subscriptions.view");
                }
                if (!isPlatformOperator(conn) && hasPermission(conn, "iadm04.payments.callback.retry")
                        && !hasPermission(conn, "iadm04.plans.manage")
                        && !hasPermission(conn, "iadm04.invoices.manage")) {
                    // System Admin operational view only — deny commercial renew actions
                    throw new UserProfileAccessDeniedException(
                            "System Admin cannot apply renewal or expiry commercial actions.");
                }
                Map<String, Object> sub = loadSub(conn, subscriptionId);
                String act = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
                UUID tenantId = UUID.fromString(String.valueOf(sub.get("tenantId")));
                String from = String.valueOf(sub.get("status"));
                String to = from;
                if ("MARK_GRACE".equals(act)) {
                    to = "GRACE";
                } else if ("MARK_EXPIRED".equals(act)) {
                    to = "EXPIRED";
                } else if ("SUSPEND".equals(act)) {
                    to = "SUSPENDED";
                } else if ("MARK_RENEWED".equals(act)) {
                    // Renewal applies only after payment checks — open unpaid invoice blocks
                    if (hasOpenUnpaidInvoice(conn, tenantId, subscriptionId)) {
                        throw new InstituteAdminException(409, "IADM04_RENEWAL_PAYMENT_REQUIRED",
                                "Renewal blocked: unpaid invoice exists for this subscription.");
                    }
                    to = "ACTIVE";
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE plat.subscriptions SET end_date = CURRENT_DATE + 365, status = 'ACTIVE', "
                                    + "status_reason = ?, updated_at = now() WHERE id = ? AND deleted_at IS NULL")) {
                        ps.setString(1, reason == null ? "Renewed by Super Admin" : reason);
                        ps.setObject(2, subscriptionId);
                        ps.executeUpdate();
                    }
                } else if ("REMINDER_NOTED".equals(act)) {
                    // no status change
                } else {
                    throw new InstituteAdminException(400, "IADM04_INVALID",
                            "action must be MARK_GRACE, MARK_EXPIRED, MARK_RENEWED, SUSPEND, or REMINDER_NOTED.");
                }
                if (!"REMINDER_NOTED".equals(act) && !"MARK_RENEWED".equals(act)) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE plat.subscriptions SET status = ?, status_reason = ?, updated_at = now() "
                                    + "WHERE id = ? AND deleted_at IS NULL")) {
                        ps.setString(1, to);
                        ps.setString(2, reason);
                        ps.setObject(3, subscriptionId);
                        ps.executeUpdate();
                    }
                }
                if (!from.equals(to) && !"REMINDER_NOTED".equals(act)) {
                    insertHistory(conn, tenantId, subscriptionId, from, to, act, ctx.getUserId());
                }
                UUID actionId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO plat.renewal_actions "
                                + "(tenant_id, subscription_id, action, reason, actor_user_id, demo_label) "
                                + "VALUES (?, ?, ?, ?, ?, 'DEMO') RETURNING id")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, subscriptionId);
                    ps.setString(3, act);
                    ps.setString(4, reason);
                    ps.setObject(5, ctx.getUserId());
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        actionId = (UUID) rs.getObject(1);
                    }
                }
                DurableAuditWriter.append(conn, ctx, tenantId, "IADM04_RENEWAL_ACTION",
                        "renewal_action", actionId.toString(), act);
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("actionId", actionId.toString());
                out.put("action", act);
                out.put("fromStatus", from);
                out.put("toStatus", to);
                out.put("subscription", loadSub(conn, subscriptionId));
                return out;
            }
        });
    }

    // -------------------- job helpers --------------------

    private Map<String, Object> runRenewalReminders(Connection conn, UserSecurityContext ctx,
                                                    LocalDate today, int reminderDays) throws SQLException {
        String ck = "remind-" + today;
        if (checkpointExists(conn, "RENEWAL_REMINDER", ck)) {
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("idempotentReplay", Boolean.TRUE);
            return out;
        }
        if (reminderDays < 0) {
            recordCheckpoint(conn, ctx, "RENEWAL_REMINDER", ck, "SKIPPED",
                    "billing.renewal.reminder_days not configured");
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("due", Integer.valueOf(0));
            out.put("skipped", Boolean.TRUE);
            return out;
        }
        int due = 0;
        LocalDate until = today.plusDays(reminderDays);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM plat.subscriptions WHERE deleted_at IS NULL AND status = 'ACTIVE' "
                        + "AND end_date IS NOT NULL AND end_date >= ? AND end_date <= ?")) {
            ps.setDate(1, Date.valueOf(today));
            ps.setDate(2, Date.valueOf(until));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                due = rs.getInt(1);
            }
        }
        recordCheckpoint(conn, ctx, "RENEWAL_REMINDER", ck, "SUCCEEDED", "due=" + due);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("due", Integer.valueOf(due));
        out.put("windowDays", Integer.valueOf(reminderDays));
        return out;
    }

    private Map<String, Object> runExpiryTransitions(Connection conn, UserSecurityContext ctx,
                                                     LocalDate today, int graceDays) throws SQLException {
        String ck = "expiry-" + today;
        if (checkpointExists(conn, "EXPIRY", ck)) {
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("idempotentReplay", Boolean.TRUE);
            return out;
        }
        if (graceDays < 0) {
            recordCheckpoint(conn, ctx, "EXPIRY", ck, "SKIPPED", "billing.grace.days not configured");
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("toGrace", Integer.valueOf(0));
            out.put("toExpired", Integer.valueOf(0));
            out.put("skipped", Boolean.TRUE);
            out.put("reason", "billing.grace.days missing — fail closed for expiry transitions");
            return out;
        }
        int toGrace = 0;
        int toExpired = 0;
        // ACTIVE past end_date → GRACE
        List<UUID[]> activePast = new ArrayList<UUID[]>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, tenant_id FROM plat.subscriptions WHERE deleted_at IS NULL AND status = 'ACTIVE' "
                        + "AND end_date IS NOT NULL AND end_date < ?")) {
            ps.setDate(1, Date.valueOf(today));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    activePast.add(new UUID[]{(UUID) rs.getObject(1), (UUID) rs.getObject(2)});
                }
            }
        }
        for (UUID[] pair : activePast) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE plat.subscriptions SET status = 'GRACE', status_reason = ?, updated_at = now() "
                            + "WHERE id = ? AND status = 'ACTIVE'")) {
                ps.setString(1, "Entered grace after end_date (job)");
                ps.setObject(2, pair[0]);
                if (ps.executeUpdate() > 0) {
                    insertHistory(conn, pair[1], pair[0], "ACTIVE", "GRACE", "job_expiry", ctx.getUserId());
                    toGrace++;
                }
            }
        }
        // GRACE past end_date + grace → EXPIRED
        LocalDate graceCutoff = today.minusDays(graceDays);
        List<UUID[]> gracePast = new ArrayList<UUID[]>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, tenant_id FROM plat.subscriptions WHERE deleted_at IS NULL AND status = 'GRACE' "
                        + "AND end_date IS NOT NULL AND end_date < ?")) {
            ps.setDate(1, Date.valueOf(graceCutoff));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    gracePast.add(new UUID[]{(UUID) rs.getObject(1), (UUID) rs.getObject(2)});
                }
            }
        }
        for (UUID[] pair : gracePast) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE plat.subscriptions SET status = 'EXPIRED', status_reason = ?, updated_at = now() "
                            + "WHERE id = ? AND status = 'GRACE'")) {
                ps.setString(1, "Grace elapsed (job)");
                ps.setObject(2, pair[0]);
                if (ps.executeUpdate() > 0) {
                    insertHistory(conn, pair[1], pair[0], "GRACE", "EXPIRED", "job_expiry", ctx.getUserId());
                    toExpired++;
                }
            }
        }
        recordCheckpoint(conn, ctx, "EXPIRY", ck, "SUCCEEDED",
                "toGrace=" + toGrace + ";toExpired=" + toExpired);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("toGrace", Integer.valueOf(toGrace));
        out.put("toExpired", Integer.valueOf(toExpired));
        out.put("graceDays", Integer.valueOf(graceDays));
        return out;
    }

    private static String computeBucket(String status, Date end, Date trialEnd, LocalDate today, int reminderDays) {
        if ("EXPIRED".equals(status)) {
            return "EXPIRED";
        }
        if ("SUSPENDED".equals(status)) {
            return "SUSPENDED";
        }
        if ("GRACE".equals(status)) {
            return "GRACE";
        }
        if ("TRIAL".equals(status) && trialEnd != null) {
            LocalDate te = trialEnd.toLocalDate();
            if (!te.isAfter(today)) {
                return "DUE";
            }
            if (!te.isAfter(today.plusDays(reminderDays))) {
                return "DUE";
            }
        }
        if ("ACTIVE".equals(status) && end != null) {
            LocalDate ed = end.toLocalDate();
            if (ed.isBefore(today)) {
                return "DUE";
            }
            if (!ed.isAfter(today.plusDays(reminderDays))) {
                return "DUE";
            }
            return "RENEWED";
        }
        return status;
    }

    private boolean hasOpenUnpaidInvoice(Connection conn, UUID tenantId, UUID subscriptionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM plat.invoices WHERE tenant_id = ? AND subscription_id = ? "
                        + "AND deleted_at IS NULL AND status IN ('ISSUED','DUE','DRAFT') LIMIT 1")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, subscriptionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean checkpointExists(Connection conn, String job, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM plat.billing_job_checkpoints WHERE job_name = ? AND checkpoint_key = ? "
                        + "AND deleted_at IS NULL AND status = 'SUCCEEDED'")) {
            ps.setString(1, job);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void recordCheckpoint(Connection conn, UserSecurityContext ctx, String job, String key,
                                  String status, String detail) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO plat.billing_job_checkpoints "
                        + "(job_name, checkpoint_key, status, detail, actor_user_id, demo_label) "
                        + "VALUES (?, ?, ?, ?, ?, 'DEMO') "
                        + "ON CONFLICT (job_name, checkpoint_key) DO UPDATE SET "
                        + "status = EXCLUDED.status, detail = EXCLUDED.detail, ran_at = now()")) {
            ps.setString(1, job);
            ps.setString(2, key);
            ps.setString(3, status);
            ps.setString(4, detail);
            ps.setObject(5, ctx.getUserId());
            ps.executeUpdate();
        }
    }

    private Map<String, Object> loadSub(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, tenant_id, plan_id, status, start_date, end_date, trial_ends_on, status_reason "
                        + "FROM plat.subscriptions WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("subscription", id.toString());
                }
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("id", rs.getObject("id").toString());
                m.put("tenantId", rs.getObject("tenant_id").toString());
                m.put("planId", rs.getObject("plan_id").toString());
                m.put("status", rs.getString("status"));
                m.put("startDate", rs.getDate("start_date") == null ? null : rs.getDate("start_date").toString());
                m.put("endDate", rs.getDate("end_date") == null ? null : rs.getDate("end_date").toString());
                m.put("trialEndsOn", rs.getDate("trial_ends_on") == null ? null
                        : rs.getDate("trial_ends_on").toString());
                m.put("statusReason", rs.getString("status_reason"));
                return m;
            }
        }
    }

    private Map<String, Object> mapSubBrief(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", rs.getObject("id").toString());
        m.put("tenantId", rs.getObject("tenant_id").toString());
        m.put("tenantName", rs.getString("tenant_name"));
        m.put("status", rs.getString("status"));
        m.put("endDate", rs.getDate("end_date") == null ? null : rs.getDate("end_date").toString());
        m.put("trialEndsOn", rs.getDate("trial_ends_on") == null ? null
                : rs.getDate("trial_ends_on").toString());
        m.put("statusReason", rs.getString("status_reason"));
        m.put("planCode", rs.getString("plan_code"));
        return m;
    }

    private void insertHistory(Connection conn, UUID tenantId, UUID subId, String from, String to,
                               String reason, UUID actor) throws SQLException {
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
            ps.setString(7, reason);
            ps.executeUpdate();
        }
    }

    private String readSetting(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT default_value::text FROM cfg.setting_definitions "
                        + "WHERE setting_key = ? AND deleted_at IS NULL")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return rs.getString(1);
            }
        }
    }

    private int readIntSetting(Connection conn, String key, int missingDefault) throws SQLException {
        String raw = readSetting(conn, key);
        if (raw == null) {
            return missingDefault;
        }
        try {
            return Integer.parseInt(raw.replace("\"", "").trim());
        } catch (NumberFormatException ex) {
            return missingDefault;
        }
    }

    private void requirePermission(Connection conn, UserSecurityContext ctx, String key) throws SQLException {
        if (isPlatformOperator(conn)) {
            return;
        }
        if (!hasPermission(conn, key)) {
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
        throw forbidden(keys[0]);
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
