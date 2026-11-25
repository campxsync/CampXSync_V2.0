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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-04 S2 — plan-change, invoices, trial conversion, offline SoD payments.
 */
public class BillingFinanceService {

    private final DatabaseConnectionManager db;

    public BillingFinanceService() {
        this(DatabaseConnectionManager.getInstance());
    }

    public BillingFinanceService(DatabaseConnectionManager db) {
        this.db = db == null ? DatabaseConnectionManager.getInstance() : db;
    }

    // -------------------- Plan change (US-017 / US-018) --------------------

    public Map<String, Object> requestPlanChange(final UserSecurityContext ctx, final UUID requestedPlanId,
                                                 final String effectiveOn, final String reason,
                                                 final String idempotencyKey) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iadm04.plan_change.request");
                UUID tenantId = ctx.getTenantId();
                if (tenantId == null) {
                    throw new InstituteAdminException(400, "IADM04_INVALID", "Tenant context required.");
                }
                if (requestedPlanId == null) {
                    throw new InstituteAdminException(400, "IADM04_INVALID", "requestedPlanId is required.");
                }
                String key = blankToNull(idempotencyKey);
                if (key != null) {
                    Map<String, Object> replay = findPlanChangeByIdempotency(conn, key);
                    if (replay != null) {
                        replay.put("idempotentReplay", Boolean.TRUE);
                        return replay;
                    }
                }
                UUID subId = findCurrentSub(conn, tenantId);
                if (subId == null) {
                    throw new InstituteAdminException(409, "IADM04_NO_SUBSCRIPTION",
                            "No current subscription to change.");
                }
                Map<String, Object> sub = loadSub(conn, subId);
                UUID currentPlanId = UUID.fromString(String.valueOf(sub.get("planId")));
                assertPlanActive(conn, requestedPlanId);
                LocalDate effective = parseDateOrToday(effectiveOn);
                UUID id;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO plat.plan_change_requests ("
                                + "tenant_id, subscription_id, current_plan_id, requested_plan_id, effective_on, "
                                + "status, request_reason, requested_by, idempotency_key, demo_label) "
                                + "VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, 'DEMO') RETURNING id")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, subId);
                    ps.setObject(3, currentPlanId);
                    ps.setObject(4, requestedPlanId);
                    ps.setDate(5, Date.valueOf(effective));
                    ps.setString(6, reason);
                    ps.setObject(7, ctx.getUserId());
                    ps.setString(8, key);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        id = (UUID) rs.getObject(1);
                    }
                }
                DurableAuditWriter.append(conn, ctx, tenantId, "IADM04_PLAN_CHANGE_REQUEST",
                        "plan_change_request", id.toString(), "Plan change requested");
                return loadPlanChange(conn, id);
            }
        });
    }

    public Map<String, Object> decidePlanChange(final UserSecurityContext ctx, final UUID requestId,
                                                final String decision, final String reason) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iadm04.plan_change.decide");
                Map<String, Object> req = loadPlanChange(conn, requestId);
                if (!"PENDING".equals(String.valueOf(req.get("status")))) {
                    throw new ResourceConflictException("plan_change_request", "Only PENDING requests can be decided.");
                }
                String dec = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
                if (!"APPROVED".equals(dec) && !"REJECTED".equals(dec)) {
                    throw new InstituteAdminException(400, "IADM04_INVALID", "decision must be APPROVED or REJECTED.");
                }
                UUID requester = UUID.fromString(String.valueOf(req.get("requestedBy")));
                if (requester.equals(ctx.getUserId())) {
                    throw new InstituteAdminException(403, "IADM04_SELF_APPROVE_DENIED",
                            "The requester cannot approve or reject their own plan-change request.");
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE plat.plan_change_requests SET status = ?, decision_reason = ?, decided_by = ?, "
                                + "decided_at = now(), updated_at = now() WHERE id = ? AND deleted_at IS NULL")) {
                    ps.setString(1, dec);
                    ps.setString(2, reason);
                    ps.setObject(3, ctx.getUserId());
                    ps.setObject(4, requestId);
                    ps.executeUpdate();
                }
                UUID tenantId = UUID.fromString(String.valueOf(req.get("tenantId")));
                DurableAuditWriter.append(conn, ctx, tenantId, "IADM04_PLAN_CHANGE_DECIDE",
                        "plan_change_request", requestId.toString(), "Decision " + dec);

                if ("APPROVED".equals(dec)) {
                    LocalDate effective = LocalDate.parse(String.valueOf(req.get("effectiveOn")));
                    if (!effective.isAfter(LocalDate.now())) {
                        applyPlanChangeNow(conn, ctx, requestId, req);
                    }
                }
                Map<String, Object> out = loadPlanChange(conn, requestId);
                out.put("prorationNote", "Proration and credits are NOT calculated (D9 pending).");
                return out;
            }
        });
    }

    public Map<String, Object> listPlanChanges(final UserSecurityContext ctx, final String statusFilter) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                boolean platform = isPlatformOperator(conn)
                        || hasPermission(conn, "iadm04.plan_change.decide")
                        || hasPermission(conn, "iadm04.subscriptions.view");
                boolean own = hasPermission(conn, "iadm04.plan_change.request");
                if (!platform && !own) {
                    throw forbidden("iadm04.plan_change.request");
                }
                StringBuilder sql = new StringBuilder(
                        "SELECT id, tenant_id, subscription_id, current_plan_id, requested_plan_id, "
                                + "effective_on, status, request_reason, decision_reason, requested_by, decided_by, "
                                + "decided_at, applied_at, demo_label "
                                + "FROM plat.plan_change_requests WHERE deleted_at IS NULL ");
                List<Object> params = new ArrayList<Object>();
                if (!platform) {
                    sql.append("AND tenant_id = ? ");
                    params.add(ctx.getTenantId());
                }
                if (statusFilter != null && !statusFilter.trim().isEmpty()) {
                    sql.append("AND status = ? ");
                    params.add(statusFilter.trim().toUpperCase(Locale.ROOT));
                }
                sql.append("ORDER BY created_at DESC LIMIT 100");
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            items.add(mapPlanChange(rs));
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

    // -------------------- Invoices (US-019 / 021 / 022) --------------------

    public Map<String, Object> generateInvoice(final UserSecurityContext ctx, final UUID subscriptionId,
                                               final String idempotencyKey) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, ctx, "iadm04.invoices.manage", "iadm04.subscriptions.attach");
                if (subscriptionId == null) {
                    throw new InstituteAdminException(400, "IADM04_INVALID", "subscriptionId is required.");
                }
                String key = blankToNull(idempotencyKey);
                if (key != null) {
                    UUID existing = findInvoiceByIdempotency(conn, key);
                    if (existing != null) {
                        Map<String, Object> replay = loadInvoice(conn, existing);
                        replay.put("idempotentReplay", Boolean.TRUE);
                        return replay;
                    }
                }
                Map<String, Object> sub = loadSub(conn, subscriptionId);
                UUID tenantId = UUID.fromString(String.valueOf(sub.get("tenantId")));
                UUID planId = UUID.fromString(String.valueOf(sub.get("planId")));
                Map<String, Object> plan = loadPlan(conn, planId);
                long minor = ((Number) plan.get("priceMinorUnits")).longValue();
                String currency = String.valueOf(plan.get("currencyCode"));
                String snapshot = "{\"planId\":\"" + planId + "\",\"planCode\":\"" + plan.get("planCode")
                        + "\",\"priceMinorUnits\":" + minor + ",\"currencyCode\":\"" + currency
                        + "\",\"billingCycle\":\"" + plan.get("billingCycle") + "\"}";
                String internalRef = "INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
                UUID invoiceId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO plat.invoices ("
                                + "tenant_id, subscription_id, invoice_number, issued_on, due_date, amount, "
                                + "currency_code, status, amount_minor_units, pricing_snapshot, idempotency_key, "
                                + "internal_ref, demo_label) "
                                + "VALUES (?, ?, ?, CURRENT_DATE, CURRENT_DATE + 14, ?, ?, 'ISSUED', ?, ?::jsonb, ?, ?, 'DEMO') "
                                + "RETURNING id")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, subscriptionId);
                    ps.setString(3, internalRef);
                    ps.setDouble(4, minor / 100.0d);
                    ps.setString(5, currency);
                    ps.setLong(6, minor);
                    ps.setString(7, snapshot);
                    ps.setString(8, key);
                    ps.setString(9, internalRef);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        invoiceId = (UUID) rs.getObject(1);
                    }
                }
                try (PreparedStatement line = conn.prepareStatement(
                        "INSERT INTO plat.invoice_lines (tenant_id, invoice_id, description, quantity, "
                                + "unit_price, amount, unit_price_minor_units, amount_minor_units, demo_label) "
                                + "VALUES (?, ?, ?, 1, ?, ?, ?, ?, 'DEMO')")) {
                    line.setObject(1, tenantId);
                    line.setObject(2, invoiceId);
                    line.setString(3, "Subscription " + plan.get("planCode"));
                    line.setDouble(4, minor / 100.0d);
                    line.setDouble(5, minor / 100.0d);
                    line.setLong(6, minor);
                    line.setLong(7, minor);
                    line.executeUpdate();
                }
                DurableAuditWriter.append(conn, ctx, tenantId, "IADM04_INVOICE_GENERATE",
                        "invoice", invoiceId.toString(), "Invoice issued " + internalRef);
                Map<String, Object> out = loadInvoice(conn, invoiceId);
                out.put("taxNote", "No GST/tax or invoice numbering scheme (D22 pending).");
                return out;
            }
        });
    }

    public Map<String, Object> voidInvoice(final UserSecurityContext ctx, final UUID invoiceId, final String reason) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iadm04.invoices.manage");
                Map<String, Object> inv = loadInvoice(conn, invoiceId);
                String status = String.valueOf(inv.get("status"));
                if ("PAID".equals(status) || "VOID".equals(status)) {
                    throw new ResourceConflictException("invoice", "Cannot void a " + status + " invoice.");
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE plat.invoices SET status = 'VOID', void_reason = ?, updated_at = now() "
                                + "WHERE id = ? AND deleted_at IS NULL")) {
                    ps.setString(1, reason);
                    ps.setObject(2, invoiceId);
                    ps.executeUpdate();
                }
                UUID tenantId = UUID.fromString(String.valueOf(inv.get("tenantId")));
                DurableAuditWriter.append(conn, ctx, tenantId, "IADM04_INVOICE_VOID",
                        "invoice", invoiceId.toString(), "Invoice voided");
                Map<String, Object> out = loadInvoice(conn, invoiceId);
                out.put("refundNote", "Refunds and credits are not implemented (D9 pending).");
                return out;
            }
        });
    }

    public Map<String, Object> listInvoices(final UserSecurityContext ctx, final UUID tenantFilter) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                boolean platform = isPlatformOperator(conn) || hasPermission(conn, "iadm04.invoices.manage")
                        || hasPermission(conn, "iadm04.subscriptions.view");
                boolean own = hasPermission(conn, "iadm04.invoices.view.own");
                if (!platform && !own) {
                    throw forbidden("iadm04.invoices.view.own");
                }
                StringBuilder sql = new StringBuilder(
                        "SELECT id, tenant_id, subscription_id, invoice_number, internal_ref, status, "
                                + "amount_minor_units, currency_code, issued_on, due_date, paid_at, demo_label "
                                + "FROM plat.invoices WHERE deleted_at IS NULL ");
                List<Object> params = new ArrayList<Object>();
                if (!platform) {
                    sql.append("AND tenant_id = ? ");
                    params.add(ctx.getTenantId());
                } else if (tenantFilter != null) {
                    sql.append("AND tenant_id = ? ");
                    params.add(tenantFilter);
                }
                sql.append("ORDER BY created_at DESC LIMIT 100");
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            items.add(mapInvoice(rs));
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

    public Map<String, Object> getInvoice(final UserSecurityContext ctx, final UUID invoiceId) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                Map<String, Object> inv = loadInvoice(conn, invoiceId);
                UUID tenantId = UUID.fromString(String.valueOf(inv.get("tenantId")));
                boolean platform = isPlatformOperator(conn) || hasPermission(conn, "iadm04.invoices.manage");
                boolean own = hasPermission(conn, "iadm04.invoices.view.own")
                        && ctx.getTenantId() != null && ctx.getTenantId().equals(tenantId);
                if (!platform && !own) {
                    throw forbidden("iadm04.invoices.view.own");
                }
                inv.put("lines", listInvoiceLines(conn, invoiceId));
                return inv;
            }
        });
    }

    // -------------------- Trial conversion (US-020) --------------------

    public Map<String, Object> convertTrial(final UserSecurityContext ctx, final UUID subscriptionId,
                                            final String idempotencyKey) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, ctx, "iadm04.invoices.manage", "iadm04.subscriptions.attach", "iadm04.jobs.run");
                Map<String, Object> sub = loadSub(conn, subscriptionId);
                if (!"TRIAL".equals(String.valueOf(sub.get("status")))) {
                    throw new InstituteAdminException(409, "IADM04_NOT_TRIAL",
                            "Only TRIAL subscriptions can be converted.");
                }
                String key = blankToNull(idempotencyKey);
                if (key == null) {
                    key = "trial-convert-" + subscriptionId;
                }
                UUID existingInv = findInvoiceByIdempotency(conn, key);
                Map<String, Object> invoice;
                if (existingInv != null) {
                    invoice = loadInvoice(conn, existingInv);
                    invoice.put("idempotentReplay", Boolean.TRUE);
                } else {
                    // inline generate to stay in same TX
                    invoice = generateInvoiceInternal(conn, ctx, subscriptionId, key);
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE plat.subscriptions SET status = 'PENDING', status_reason = ?, updated_at = now() "
                                + "WHERE id = ? AND deleted_at IS NULL AND status = 'TRIAL'")) {
                    ps.setString(1, "Trial conversion — payment required");
                    ps.setObject(2, subscriptionId);
                    if (ps.executeUpdate() == 0 && existingInv == null) {
                        throw new ResourceConflictException("subscription", "Trial conversion race.");
                    }
                }
                UUID tenantId = UUID.fromString(String.valueOf(sub.get("tenantId")));
                insertHistory(conn, tenantId, subscriptionId, "TRIAL", "PENDING", "trial_conversion",
                        ctx.getUserId(), key);
                DurableAuditWriter.append(conn, ctx, tenantId, "IADM04_TRIAL_CONVERT",
                        "subscription", subscriptionId.toString(), "TRIAL→PENDING with invoice");
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("subscriptionId", subscriptionId.toString());
                out.put("status", "PENDING");
                out.put("invoice", invoice);
                out.put("entitlementsNote", "PENDING returns zero entitlements immediately");
                return out;
            }
        });
    }

    // -------------------- Offline payments (US-023 / 024 / 025) --------------------

    public Map<String, Object> recordOfflineEvidence(final UserSecurityContext ctx, final UUID invoiceId,
                                                     final String instrumentType, final String reference,
                                                     final Long amountMinor, final String currency,
                                                     final String notes) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iadm04.payments.offline.record");
                Map<String, Object> inv = loadInvoice(conn, invoiceId);
                String status = String.valueOf(inv.get("status"));
                if ("PAID".equals(status) || "VOID".equals(status)) {
                    throw new ResourceConflictException("invoice", "Cannot record evidence on " + status + " invoice.");
                }
                if (amountMinor == null || amountMinor.longValue() <= 0) {
                    throw new InstituteAdminException(400, "IADM04_INVALID", "amountMinorUnits must be > 0.");
                }
                String instrument = instrumentType == null ? "BANK_TRANSFER"
                        : instrumentType.trim().toUpperCase(Locale.ROOT);
                String curr = currency == null || currency.trim().isEmpty()
                        ? String.valueOf(inv.get("currencyCode")) : currency.trim().toUpperCase(Locale.ROOT);
                UUID tenantId = UUID.fromString(String.valueOf(inv.get("tenantId")));
                UUID id;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO plat.offline_payment_evidence ("
                                + "tenant_id, invoice_id, instrument_type, reference_text, amount_minor_units, "
                                + "currency_code, recorded_by, notes, demo_label) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DEMO') RETURNING id")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, invoiceId);
                    ps.setString(3, instrument);
                    ps.setString(4, reference == null ? "" : reference.trim());
                    ps.setLong(5, amountMinor.longValue());
                    ps.setString(6, curr);
                    ps.setObject(7, ctx.getUserId());
                    ps.setString(8, notes);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        id = (UUID) rs.getObject(1);
                    }
                }
                DurableAuditWriter.append(conn, ctx, tenantId, "IADM04_OFFLINE_EVIDENCE",
                        "offline_payment_evidence", id.toString(), "Evidence recorded; invoice not paid yet");
                Map<String, Object> out = loadEvidence(conn, id);
                out.put("invoicePaid", Boolean.FALSE);
                out.put("note", "Evidence is immutable. Invoice does NOT become paid at this step.");
                return out;
            }
        });
    }

    public Map<String, Object> reconcileOffline(final UserSecurityContext ctx, final UUID evidenceId,
                                                final String decision, final String reason) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iadm04.payments.offline.reconcile");
                int r01Count = countActiveSuperAdmins(conn);
                if (r01Count < 2) {
                    throw new InstituteAdminException(409, "IADM04_SOD_BLOCKED",
                            "Offline reconciliation requires two distinct Super Admin accounts. "
                                    + "Only one Super Admin account is available; workflow blocked "
                                    + "(no role substitution).");
                }
                Map<String, Object> evidence = loadEvidence(conn, evidenceId);
                UUID recorder = UUID.fromString(String.valueOf(evidence.get("recordedBy")));
                if (recorder.equals(ctx.getUserId())) {
                    throw new InstituteAdminException(403, "IADM04_SOD_SAME_ACTOR",
                            "The Super Admin who recorded the evidence cannot reconcile it.");
                }
                UUID invoiceId = UUID.fromString(String.valueOf(evidence.get("invoiceId")));
                Map<String, Object> inv = loadInvoice(conn, invoiceId);
                long invMinor = ((Number) inv.get("amountMinorUnits")).longValue();
                long evMinor = ((Number) evidence.get("amountMinorUnits")).longValue();
                String invCur = String.valueOf(inv.get("currencyCode")).trim();
                String evCur = String.valueOf(evidence.get("currencyCode")).trim();
                String dec = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
                if (!"APPROVED".equals(dec) && !"REJECTED".equals(dec)) {
                    throw new InstituteAdminException(400, "IADM04_INVALID", "decision must be APPROVED or REJECTED.");
                }
                if ("APPROVED".equals(dec) && (invMinor != evMinor || !invCur.equalsIgnoreCase(evCur))) {
                    Map<String, Object> hold = new LinkedHashMap<String, Object>();
                    hold.put("held", Boolean.TRUE);
                    hold.put("reason", "AMOUNT_OR_CURRENCY_MISMATCH");
                    hold.put("invoiceAmountMinorUnits", Long.valueOf(invMinor));
                    hold.put("evidenceAmountMinorUnits", Long.valueOf(evMinor));
                    hold.put("invoiceCurrency", invCur);
                    hold.put("evidenceCurrency", evCur);
                    hold.put("note", "Mismatch holds for reconciliation; invoice and subscription unchanged.");
                    DurableAuditWriter.append(conn, ctx,
                            UUID.fromString(String.valueOf(inv.get("tenantId"))),
                            "IADM04_OFFLINE_MISMATCH_HOLD", "invoice", invoiceId.toString(),
                            "Offline reconcile held on mismatch");
                    return hold;
                }
                UUID tenantId = UUID.fromString(String.valueOf(evidence.get("tenantId")));
                UUID reconId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO plat.offline_payment_reconciliations ("
                                + "tenant_id, invoice_id, evidence_id, decision, reason, reconciler_user_id, demo_label) "
                                + "VALUES (?, ?, ?, ?, ?, ?, 'DEMO') RETURNING id")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, invoiceId);
                    ps.setObject(3, evidenceId);
                    ps.setString(4, dec);
                    ps.setString(5, reason);
                    ps.setObject(6, ctx.getUserId());
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        reconId = (UUID) rs.getObject(1);
                    }
                } catch (SQLException ex) {
                    if (ex.getMessage() != null && ex.getMessage().contains("uq_offline_recon_evidence")) {
                        throw new ResourceConflictException("offline_reconciliation",
                                "This evidence was already reconciled.");
                    }
                    throw ex;
                }

                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("reconciliationId", reconId.toString());
                out.put("decision", dec);
                out.put("held", Boolean.FALSE);

                if ("APPROVED".equals(dec)) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE plat.invoices SET status = 'PAID', paid_at = now(), updated_at = now() "
                                    + "WHERE id = ? AND deleted_at IS NULL AND status <> 'PAID'")) {
                        ps.setObject(1, invoiceId);
                        ps.executeUpdate();
                    }
                    UUID subId = UUID.fromString(String.valueOf(inv.get("subscriptionId")));
                    Map<String, Object> sub = loadSub(conn, subId);
                    if ("PENDING".equals(String.valueOf(sub.get("status")))) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE plat.subscriptions SET status = 'ACTIVE', status_reason = ?, updated_at = now() "
                                        + "WHERE id = ? AND deleted_at IS NULL AND status = 'PENDING'")) {
                            ps.setString(1, "Offline reconciliation approved");
                            ps.setObject(2, subId);
                            ps.executeUpdate();
                        }
                        insertHistory(conn, tenantId, subId, "PENDING", "ACTIVE", "offline_reconcile",
                                ctx.getUserId(), reconId.toString());
                        out.put("subscriptionStatus", "ACTIVE");
                    } else {
                        out.put("subscriptionStatus", sub.get("status"));
                    }
                    out.put("invoiceStatus", "PAID");
                }
                DurableAuditWriter.append(conn, ctx, tenantId, "IADM04_OFFLINE_RECONCILE",
                        "offline_payment_reconciliation", reconId.toString(), "Reconcile " + dec);
                return out;
            }
        });
    }

    public Map<String, Object> listOfflineEvidence(final UserSecurityContext ctx, final UUID invoiceId) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, ctx, "iadm04.payments.offline.record", "iadm04.payments.offline.reconcile",
                        "iadm04.invoices.manage", "iadm04.audit.view");
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                String sql = "SELECT id, tenant_id, invoice_id, instrument_type, reference_text, "
                        + "amount_minor_units, currency_code, recorded_by, recorded_at, notes, demo_label "
                        + "FROM plat.offline_payment_evidence WHERE deleted_at IS NULL ";
                if (invoiceId != null) {
                    sql += "AND invoice_id = ? ";
                }
                sql += "ORDER BY recorded_at DESC LIMIT 100";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (invoiceId != null) {
                        ps.setObject(1, invoiceId);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            items.add(mapEvidence(rs));
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

    // -------------------- helpers --------------------

    private void applyPlanChangeNow(Connection conn, UserSecurityContext ctx, UUID requestId,
                                    Map<String, Object> req) throws SQLException {
        UUID subId = UUID.fromString(String.valueOf(req.get("subscriptionId")));
        UUID newPlan = UUID.fromString(String.valueOf(req.get("requestedPlanId")));
        UUID tenantId = UUID.fromString(String.valueOf(req.get("tenantId")));
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE plat.subscriptions SET plan_id = ?, updated_at = now() WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, newPlan);
            ps.setObject(2, subId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE plat.plan_change_requests SET status = 'APPLIED', applied_at = now(), updated_at = now() "
                        + "WHERE id = ?")) {
            ps.setObject(1, requestId);
            ps.executeUpdate();
        }
        DurableAuditWriter.append(conn, ctx, tenantId, "IADM04_PLAN_CHANGE_APPLIED",
                "subscription", subId.toString(), "Plan changed prospectively; past invoices unchanged");
    }

    private Map<String, Object> generateInvoiceInternal(Connection conn, UserSecurityContext ctx,
                                                        UUID subscriptionId, String key) throws SQLException {
        Map<String, Object> sub = loadSub(conn, subscriptionId);
        UUID tenantId = UUID.fromString(String.valueOf(sub.get("tenantId")));
        UUID planId = UUID.fromString(String.valueOf(sub.get("planId")));
        Map<String, Object> plan = loadPlan(conn, planId);
        long minor = ((Number) plan.get("priceMinorUnits")).longValue();
        String currency = String.valueOf(plan.get("currencyCode"));
        String snapshot = "{\"planId\":\"" + planId + "\",\"planCode\":\"" + plan.get("planCode")
                + "\",\"priceMinorUnits\":" + minor + ",\"currencyCode\":\"" + currency + "\"}";
        String internalRef = "INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        UUID invoiceId;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO plat.invoices ("
                        + "tenant_id, subscription_id, invoice_number, issued_on, due_date, amount, "
                        + "currency_code, status, amount_minor_units, pricing_snapshot, idempotency_key, "
                        + "internal_ref, demo_label) "
                        + "VALUES (?, ?, ?, CURRENT_DATE, CURRENT_DATE + 14, ?, ?, 'ISSUED', ?, ?::jsonb, ?, ?, 'DEMO') "
                        + "RETURNING id")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, subscriptionId);
            ps.setString(3, internalRef);
            ps.setDouble(4, minor / 100.0d);
            ps.setString(5, currency);
            ps.setLong(6, minor);
            ps.setString(7, snapshot);
            ps.setString(8, key);
            ps.setString(9, internalRef);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                invoiceId = (UUID) rs.getObject(1);
            }
        }
        DurableAuditWriter.append(conn, ctx, tenantId, "IADM04_INVOICE_GENERATE",
                "invoice", invoiceId.toString(), "Invoice issued " + internalRef);
        return loadInvoice(conn, invoiceId);
    }

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

    private int countActiveSuperAdmins(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT count(*) FROM plat.platform_admins "
                        + "WHERE role_code = 'SUPER_ADMIN' AND status = 'ACTIVE' AND deleted_at IS NULL");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private UUID findCurrentSub(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM plat.subscriptions WHERE tenant_id = ? AND deleted_at IS NULL "
                        + "AND status IN ('TRIAL','PENDING','ACTIVE') ORDER BY created_at DESC LIMIT 1")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject(1);
                }
            }
        }
        return null;
    }

    private Map<String, Object> loadSub(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, tenant_id, plan_id, status FROM plat.subscriptions WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("Subscription not found.");
                }
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("id", rs.getObject("id").toString());
                m.put("tenantId", rs.getObject("tenant_id").toString());
                m.put("planId", rs.getObject("plan_id").toString());
                m.put("status", rs.getString("status"));
                return m;
            }
        }
    }

    private Map<String, Object> loadPlan(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, plan_code, billing_cycle, currency_code, price_minor_units, plan_status "
                        + "FROM plat.subscription_plans WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("Plan not found.");
                }
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("id", rs.getObject("id").toString());
                m.put("planCode", rs.getString("plan_code"));
                m.put("billingCycle", rs.getString("billing_cycle"));
                m.put("currencyCode", rs.getString("currency_code"));
                m.put("priceMinorUnits", Long.valueOf(rs.getLong("price_minor_units")));
                m.put("planStatus", rs.getString("plan_status"));
                return m;
            }
        }
    }

    private void assertPlanActive(Connection conn, UUID planId) throws SQLException {
        Map<String, Object> plan = loadPlan(conn, planId);
        if (!"ACTIVE".equals(String.valueOf(plan.get("planStatus")))) {
            throw new InstituteAdminException(400, "IADM04_PLAN_INACTIVE",
                    "Only ACTIVE plans can be requested.");
        }
    }

    private Map<String, Object> loadPlanChange(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, tenant_id, subscription_id, current_plan_id, requested_plan_id, effective_on, "
                        + "status, request_reason, decision_reason, requested_by, decided_by, decided_at, "
                        + "applied_at, demo_label FROM plat.plan_change_requests WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("Plan-change request not found.");
                }
                return mapPlanChange(rs);
            }
        }
    }

    private Map<String, Object> findPlanChangeByIdempotency(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM plat.plan_change_requests WHERE idempotency_key = ? AND deleted_at IS NULL LIMIT 1")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return loadPlanChange(conn, (UUID) rs.getObject(1));
                }
            }
        }
        return null;
    }

    private Map<String, Object> mapPlanChange(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", rs.getObject("id").toString());
        m.put("tenantId", rs.getObject("tenant_id").toString());
        m.put("subscriptionId", rs.getObject("subscription_id").toString());
        m.put("currentPlanId", rs.getObject("current_plan_id").toString());
        m.put("requestedPlanId", rs.getObject("requested_plan_id").toString());
        m.put("effectiveOn", rs.getDate("effective_on").toString());
        m.put("status", rs.getString("status"));
        m.put("requestReason", rs.getString("request_reason"));
        m.put("decisionReason", rs.getString("decision_reason"));
        m.put("requestedBy", rs.getObject("requested_by").toString());
        Object decidedBy = rs.getObject("decided_by");
        m.put("decidedBy", decidedBy == null ? null : decidedBy.toString());
        m.put("demoLabel", rs.getString("demo_label"));
        return m;
    }

    private Map<String, Object> loadInvoice(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, tenant_id, subscription_id, invoice_number, internal_ref, status, "
                        + "amount_minor_units, currency_code, issued_on, due_date, paid_at, pricing_snapshot, "
                        + "void_reason, demo_label FROM plat.invoices WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("Invoice not found.");
                }
                return mapInvoice(rs);
            }
        }
    }

    private Map<String, Object> mapInvoice(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", rs.getObject("id").toString());
        m.put("tenantId", rs.getObject("tenant_id").toString());
        Object sub = rs.getObject("subscription_id");
        m.put("subscriptionId", sub == null ? null : sub.toString());
        m.put("invoiceNumber", rs.getString("invoice_number"));
        try {
            m.put("internalRef", rs.getString("internal_ref"));
        } catch (SQLException ignored) {
        }
        m.put("status", rs.getString("status"));
        m.put("amountMinorUnits", Long.valueOf(rs.getLong("amount_minor_units")));
        m.put("currencyCode", rs.getString("currency_code"));
        Date issued = rs.getDate("issued_on");
        Date due = rs.getDate("due_date");
        m.put("issuedOn", issued == null ? null : issued.toString());
        m.put("dueDate", due == null ? null : due.toString());
        Object paid = null;
        try {
            paid = rs.getTimestamp("paid_at");
        } catch (SQLException ignored) {
        }
        m.put("paidAt", paid == null ? null : paid.toString());
        try {
            Object snap = rs.getObject("pricing_snapshot");
            m.put("pricingSnapshot", snap == null ? null : snap.toString());
            m.put("voidReason", rs.getString("void_reason"));
            m.put("demoLabel", rs.getString("demo_label"));
        } catch (SQLException ignored) {
        }
        return m;
    }

    private List<Map<String, Object>> listInvoiceLines(Connection conn, UUID invoiceId) throws SQLException {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, description, quantity, amount_minor_units, unit_price_minor_units "
                        + "FROM plat.invoice_lines WHERE invoice_id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getObject("id").toString());
                    row.put("description", rs.getString("description"));
                    row.put("quantity", rs.getBigDecimal("quantity"));
                    row.put("amountMinorUnits", Long.valueOf(rs.getLong("amount_minor_units")));
                    row.put("unitPriceMinorUnits", Long.valueOf(rs.getLong("unit_price_minor_units")));
                    items.add(row);
                }
            }
        }
        return items;
    }

    private UUID findInvoiceByIdempotency(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM plat.invoices WHERE idempotency_key = ? AND deleted_at IS NULL LIMIT 1")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject(1);
                }
            }
        }
        return null;
    }

    private Map<String, Object> loadEvidence(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, tenant_id, invoice_id, instrument_type, reference_text, amount_minor_units, "
                        + "currency_code, recorded_by, recorded_at, notes, demo_label "
                        + "FROM plat.offline_payment_evidence WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("Offline evidence not found.");
                }
                return mapEvidence(rs);
            }
        }
    }

    private Map<String, Object> mapEvidence(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", rs.getObject("id").toString());
        m.put("tenantId", rs.getObject("tenant_id").toString());
        m.put("invoiceId", rs.getObject("invoice_id").toString());
        m.put("instrumentType", rs.getString("instrument_type"));
        m.put("reference", rs.getString("reference_text"));
        m.put("amountMinorUnits", Long.valueOf(rs.getLong("amount_minor_units")));
        m.put("currencyCode", rs.getString("currency_code"));
        m.put("recordedBy", rs.getObject("recorded_by").toString());
        m.put("recordedAt", rs.getTimestamp("recorded_at").toInstant().toString());
        m.put("notes", rs.getString("notes"));
        m.put("demoLabel", rs.getString("demo_label"));
        return m;
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

    private LocalDate parseDateOrToday(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return LocalDate.now();
        }
        return LocalDate.parse(raw.trim());
    }

    private String blankToNull(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        return s.trim();
    }
}
