package com.campx.iadm.billing.service;

import com.campx.iadm.billing.config.DatabaseConnectionManager;
import com.campx.iadm.billing.exception.InstituteAdminException;
import com.campx.iadm.billing.exception.ResourceConflictException;
import com.campx.iadm.billing.exception.ResourceNotFoundException;
import com.campx.iadm.billing.exception.UserProfileAccessDeniedException;
import com.campx.iadm.billing.payments.DevPaymentSimulator;
import com.campx.iadm.billing.payments.PaymentProviderAdapter;
import com.campx.iadm.billing.security.UserSecurityContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-04 S3 — online payment initiate, signed callback, reconcile, R02 retry.
 */
public class BillingOnlinePaymentService {

    /** Provider callback time window (seconds). */
    public static final long CALLBACK_WINDOW_SECONDS = 300L;

    private final DatabaseConnectionManager db;
    private final PaymentProviderAdapter adapter;
    private final DevPaymentSimulator simulator;

    public BillingOnlinePaymentService() {
        this(DatabaseConnectionManager.getInstance(), new DevPaymentSimulator());
    }

    public BillingOnlinePaymentService(DatabaseConnectionManager db, PaymentProviderAdapter adapter) {
        this.db = db == null ? DatabaseConnectionManager.getInstance() : db;
        this.adapter = adapter == null ? new DevPaymentSimulator() : adapter;
        this.simulator = this.adapter instanceof DevPaymentSimulator
                ? (DevPaymentSimulator) this.adapter
                : new DevPaymentSimulator();
    }

    // -------------------- US-027 initiate --------------------

    public Map<String, Object> initiate(final UserSecurityContext ctx, final UUID invoiceId,
                                        final String idempotencyKey, final String simulatorOutcome) {
        // Phase 1: persist intent + durable audit INSIDE TX (US-026). Adapter never called if audit fails.
        Map<String, Object> persisted = ctx.executeInTransaction(db,
                new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
                    public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                        requirePermission(conn, ctx, "iadm04.payments.online.initiate");
                        if (invoiceId == null) {
                            throw new InstituteAdminException(400, "IADM04_INVALID", "invoiceId is required.");
                        }
                        String key = blankToNull(idempotencyKey);
                        if (key != null) {
                            Map<String, Object> replay = findIntentByIdempotency(conn, key);
                            if (replay != null) {
                                replay.put("idempotentReplay", Boolean.TRUE);
                                replay.put("adapterCalled", Boolean.FALSE);
                                return replay;
                            }
                        }
                        Map<String, Object> inv = loadInvoice(conn, invoiceId);
                        String invStatus = String.valueOf(inv.get("status"));
                        if ("PAID".equals(invStatus) || "VOID".equals(invStatus)) {
                            throw new ResourceConflictException("invoice",
                                    "Cannot initiate payment on " + invStatus + " invoice.");
                        }
                        // One open intent per invoice (duplicate initiation)
                        Map<String, Object> open = findOpenIntentForInvoice(conn, invoiceId);
                        if (open != null) {
                            open.put("idempotentReplay", Boolean.TRUE);
                            open.put("adapterCalled", Boolean.FALSE);
                            open.put("note", "Duplicate initiation returns existing intent; no second payment.");
                            return open;
                        }
                        UUID tenantId = UUID.fromString(String.valueOf(inv.get("tenantId")));
                        long minor = ((Number) inv.get("amountMinorUnits")).longValue();
                        String currency = String.valueOf(inv.get("currencyCode")).trim();
                        UUID intentId;
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO plat.online_payment_intents ("
                                        + "tenant_id, invoice_id, amount_minor_units, currency_code, status, "
                                        + "initiated_by, idempotency_key, simulator_outcome, demo_label) "
                                        + "VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?, 'DEMO') RETURNING id")) {
                            ps.setObject(1, tenantId);
                            ps.setObject(2, invoiceId);
                            ps.setLong(3, minor);
                            ps.setString(4, currency);
                            ps.setObject(5, ctx.getUserId());
                            ps.setString(6, key);
                            ps.setString(7, blankToNull(simulatorOutcome));
                            try (ResultSet rs = ps.executeQuery()) {
                                rs.next();
                                intentId = (UUID) rs.getObject(1);
                            }
                        }
                        DurableAuditWriter.append(conn, ctx, tenantId, "IADM04_ONLINE_INITIATE",
                                "online_payment_intent", intentId.toString(),
                                "Online payment intent saved before adapter call");
                        Map<String, Object> out = loadIntent(conn, intentId);
                        out.put("adapterCalled", Boolean.FALSE);
                        out.put("readyForAdapter", Boolean.TRUE);
                        out.put("provider", adapter.providerName());
                        return out;
                    }
                });

        if (Boolean.TRUE.equals(persisted.get("idempotentReplay"))
                || !Boolean.TRUE.equals(persisted.get("readyForAdapter"))) {
            return persisted;
        }

        // Phase 2: adapter AFTER commit (no real network — DEV simulator only)
        UUID intentId = UUID.fromString(String.valueOf(persisted.get("id")));
        PaymentProviderAdapter.InitiateResult result = adapter.initiate(
                new PaymentProviderAdapter.InitiateRequest(
                        intentId.toString(),
                        invoiceId.toString(),
                        ((Number) persisted.get("amountMinorUnits")).longValue(),
                        String.valueOf(persisted.get("currencyCode")),
                        simulatorOutcome));

        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iadm04.payments.online.initiate");
                if ("FAILURE".equals(result.outcome)) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE plat.online_payment_intents SET status = 'FAILED', provider_ref = ?, "
                                    + "failure_reason = ?, updated_at = now() WHERE id = ? AND deleted_at IS NULL")) {
                        ps.setString(1, result.providerRef);
                        ps.setString(2, result.detail);
                        ps.setObject(3, intentId);
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE plat.online_payment_intents SET provider_ref = ?, updated_at = now() "
                                    + "WHERE id = ? AND deleted_at IS NULL")) {
                        ps.setString(1, result.providerRef);
                        ps.setObject(2, intentId);
                        ps.executeUpdate();
                    }
                }
                DurableAuditWriter.append(conn, ctx,
                        UUID.fromString(String.valueOf(persisted.get("tenantId"))),
                        "IADM04_ONLINE_ADAPTER", "online_payment_intent", intentId.toString(),
                        "DEV simulator outcome=" + result.outcome);
                Map<String, Object> out = loadIntent(conn, intentId);
                out.put("adapterCalled", Boolean.TRUE);
                out.put("adapterOutcome", result.outcome);
                out.put("adapterDetail", result.detail);
                out.put("provider", adapter.providerName());
                out.put("labelledDevSimulator", Boolean.TRUE);
                if (result.callbackHint != null) {
                    out.put("callbackHint", result.callbackHint);
                }
                out.put("note", "Initiation is not settlement; status remains pending until signed callback.");
                return out;
            }
        });
    }

    // -------------------- US-028 signed callback --------------------

    public Map<String, Object> handleCallback(final UserSecurityContext ctx,
                                              final String timestampHeader,
                                              final String signatureHeader,
                                              final String rawBody,
                                              final Map<String, Object> body) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                // Machine ingress: authorize by provider signature, not human commercial role.
                String eventId = str(body.get("eventId"));
                String intentIdStr = str(body.get("intentId"));
                String providerRef = str(body.get("providerRef"));
                String settlement = str(body.get("settlementStatus")).toUpperCase(Locale.ROOT);
                Long amount = asLong(body.get("amountMinorUnits"));
                String currency = str(body.get("currencyCode"));

                if (eventId == null || eventId.isEmpty()) {
                    throw new InstituteAdminException(400, "IADM04_CALLBACK_INVALID", "eventId is required.");
                }

                // Replay: same event_id already stored (accepted/settled → ack; rejected → allow retry)
                Map<String, Object> existingEvent = findCallbackByEventId(conn, eventId);
                UUID reusableCallbackId = null;
                if (existingEvent != null) {
                    String prior = String.valueOf(existingEvent.get("processingStatus"));
                    if ("REJECTED_SIGNATURE".equals(prior) || "REJECTED_WINDOW".equals(prior)
                            || "FAILED_PROCESS".equals(prior) || "RETRY_QUEUED".equals(prior)) {
                        reusableCallbackId = UUID.fromString(String.valueOf(existingEvent.get("id")));
                    } else {
                        Map<String, Object> ack = new LinkedHashMap<String, Object>();
                        ack.put("processingStatus", "DUPLICATE_ACK");
                        ack.put("eventId", eventId);
                        ack.put("priorStatus", prior);
                        ack.put("settled", Boolean.FALSE);
                        ack.put("note", "Duplicate callback safely acknowledged; no double settlement.");
                        return ack;
                    }
                }

                boolean sigOk = adapter.verifySignature(timestampHeader, rawBody, signatureHeader);
                long nowSec = System.currentTimeMillis() / 1000L;
                boolean windowOk = false;
                try {
                    long ts = Long.parseLong(timestampHeader == null ? "" : timestampHeader.trim());
                    windowOk = Math.abs(nowSec - ts) <= CALLBACK_WINDOW_SECONDS;
                } catch (NumberFormatException ex) {
                    windowOk = false;
                }

                UUID callbackRowId;
                if (reusableCallbackId != null) {
                    callbackRowId = reusableCallbackId;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE plat.payment_callback_events SET provider_ref = ?, signature_valid = ?, "
                                    + "processing_status = 'RECEIVED', settlement_status = NULL, "
                                    + "amount_minor_units = ?, currency_code = ?, payload = ?::jsonb, "
                                    + "error_detail = NULL, received_at = now() WHERE id = ?")) {
                        ps.setString(1, providerRef);
                        ps.setBoolean(2, sigOk);
                        if (amount == null) {
                            ps.setObject(3, null);
                        } else {
                            ps.setLong(3, amount.longValue());
                        }
                        ps.setString(4, currency);
                        ps.setString(5, rawBody == null ? "{}" : rawBody);
                        ps.setObject(6, callbackRowId);
                        ps.executeUpdate();
                    }
                } else {
                    callbackRowId = insertCallbackRow(conn, eventId, null, providerRef, sigOk,
                            amount, currency, rawBody, "RECEIVED", null);
                }

                if (!sigOk) {
                    updateCallbackStatus(conn, callbackRowId, "REJECTED_SIGNATURE", null,
                            "Invalid provider signature");
                    // Row retained as security evidence; billing unchanged.
                    Map<String, Object> out = new LinkedHashMap<String, Object>();
                    out.put("processingStatus", "REJECTED_SIGNATURE");
                    out.put("settled", Boolean.FALSE);
                    out.put("callbackId", callbackRowId.toString());
                    return out;
                }
                if (!windowOk) {
                    updateCallbackStatus(conn, callbackRowId, "REJECTED_WINDOW", null,
                            "Timestamp outside " + CALLBACK_WINDOW_SECONDS + "s window");
                    Map<String, Object> out = new LinkedHashMap<String, Object>();
                    out.put("processingStatus", "REJECTED_WINDOW");
                    out.put("settled", Boolean.FALSE);
                    out.put("callbackId", callbackRowId.toString());
                    return out;
                }

                Map<String, Object> intent = null;
                if (intentIdStr != null && !intentIdStr.isEmpty()) {
                    try {
                        intent = loadIntent(conn, UUID.fromString(intentIdStr));
                    } catch (Exception ignored) {
                        intent = null;
                    }
                }
                if (intent == null && providerRef != null) {
                    intent = findIntentByProviderRef(conn, providerRef);
                }
                if (intent == null) {
                    updateCallbackStatus(conn, callbackRowId, "ACCEPTED", "PENDING",
                            "Unmatched provider callback");
                    Map<String, Object> out = new LinkedHashMap<String, Object>();
                    out.put("processingStatus", "ACCEPTED");
                    out.put("match", "UNMATCHED");
                    out.put("settled", Boolean.FALSE);
                    out.put("callbackId", callbackRowId.toString());
                    return out;
                }

                UUID intentId = UUID.fromString(String.valueOf(intent.get("id")));
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE plat.payment_callback_events SET intent_id = ? WHERE id = ?")) {
                    ps.setObject(1, intentId);
                    ps.setObject(2, callbackRowId);
                    ps.executeUpdate();
                }

                // Already confirmed → duplicate settlement attempt
                if ("CONFIRMED".equals(String.valueOf(intent.get("status")))) {
                    updateCallbackStatus(conn, callbackRowId, "DUPLICATE_ACK", "SUCCESS",
                            "Intent already confirmed");
                    Map<String, Object> out = new LinkedHashMap<String, Object>();
                    out.put("processingStatus", "DUPLICATE_ACK");
                    out.put("settled", Boolean.FALSE);
                    out.put("intentId", intentId.toString());
                    out.put("note", "Already settled; no double payment.");
                    return out;
                }

                long intentMinor = ((Number) intent.get("amountMinorUnits")).longValue();
                String intentCur = String.valueOf(intent.get("currencyCode")).trim();
                boolean amountOk = amount != null && amount.longValue() == intentMinor
                        && currency != null && currency.trim().equalsIgnoreCase(intentCur);

                if (!"SUCCESS".equals(settlement)) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE plat.online_payment_intents SET status = 'FAILED', failure_reason = ?, "
                                    + "updated_at = now() WHERE id = ? AND deleted_at IS NULL")) {
                        ps.setString(1, "Provider settlementStatus=" + settlement);
                        ps.setObject(2, intentId);
                        ps.executeUpdate();
                    }
                    updateCallbackStatus(conn, callbackRowId, "ACCEPTED", "FAILED",
                            "Non-success settlement");
                    DurableAuditWriter.append(conn, ctx,
                            UUID.fromString(String.valueOf(intent.get("tenantId"))),
                            "IADM04_CALLBACK_FAILED_SETTLEMENT", "online_payment_intent",
                            intentId.toString(), "Callback reported non-success");
                    Map<String, Object> out = new LinkedHashMap<String, Object>();
                    out.put("processingStatus", "ACCEPTED");
                    out.put("settled", Boolean.FALSE);
                    out.put("intentStatus", "FAILED");
                    return out;
                }

                if (!amountOk) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE plat.online_payment_intents SET status = 'MISMATCHED', mismatch_reason = ?, "
                                    + "updated_at = now() WHERE id = ? AND deleted_at IS NULL")) {
                        ps.setString(1, "AMOUNT_OR_CURRENCY_MISMATCH");
                        ps.setObject(2, intentId);
                        ps.executeUpdate();
                    }
                    updateCallbackStatus(conn, callbackRowId, "ACCEPTED", "PENDING",
                            "Amount/currency mismatch — held");
                    DurableAuditWriter.append(conn, ctx,
                            UUID.fromString(String.valueOf(intent.get("tenantId"))),
                            "IADM04_CALLBACK_MISMATCH", "online_payment_intent",
                            intentId.toString(), "Mismatch held; invoice unchanged");
                    Map<String, Object> out = new LinkedHashMap<String, Object>();
                    out.put("processingStatus", "ACCEPTED");
                    out.put("match", "MISMATCHED");
                    out.put("settled", Boolean.FALSE);
                    out.put("intentStatus", "MISMATCHED");
                    return out;
                }

                // Settle
                settleIntent(conn, ctx, intent);
                updateCallbackStatus(conn, callbackRowId, "ACCEPTED", "SUCCESS", null);
                DurableAuditWriter.append(conn, ctx,
                        UUID.fromString(String.valueOf(intent.get("tenantId"))),
                        "IADM04_CALLBACK_SETTLED", "online_payment_intent",
                        intentId.toString(), "Online payment confirmed via signed callback");
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("processingStatus", "ACCEPTED");
                out.put("settled", Boolean.TRUE);
                out.put("intentStatus", "CONFIRMED");
                out.put("invoiceStatus", "PAID");
                out.put("intentId", intentId.toString());
                out.put("callbackId", callbackRowId.toString());
                return out;
            }
        });
    }

    // -------------------- US-029 reconcile --------------------

    public Map<String, Object> listOnlinePayments(final UserSecurityContext ctx, final String statusFilter) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, ctx, "iadm04.payments.online.initiate", "iadm04.invoices.manage",
                        "iadm04.payments.callback.retry", "iadm04.audit.view");
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                String sql = "SELECT id, tenant_id, invoice_id, amount_minor_units, currency_code, status, "
                        + "provider_ref, initiated_by, initiated_at, confirmed_at, failure_reason, "
                        + "mismatch_reason, simulator_outcome, demo_label "
                        + "FROM plat.online_payment_intents WHERE deleted_at IS NULL ";
                String filter = blankToNull(statusFilter);
                if (filter != null) {
                    sql += "AND status = ? ";
                }
                sql += "ORDER BY initiated_at DESC LIMIT 100";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (filter != null) {
                        ps.setString(1, filter.toUpperCase(Locale.ROOT));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            items.add(mapIntent(rs));
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                out.put("count", Integer.valueOf(items.size()));
                out.put("provider", adapter.providerName());
                return out;
            }
        });
    }

    public Map<String, Object> reconcileOnline(final UserSecurityContext ctx, final UUID intentId,
                                               final String decision, final String reason) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iadm04.payments.online.initiate");
                // Auditor / System Admin cannot resolve — matrix + requirePermission; extra deny if only audit/retry
                if (!isPlatformOperator(conn) && !hasPermission(conn, "iadm04.payments.online.initiate")) {
                    throw forbidden("iadm04.payments.online.initiate");
                }
                Map<String, Object> intent = loadIntent(conn, intentId);
                String status = String.valueOf(intent.get("status"));
                if (!"UNMATCHED".equals(status) && !"MISMATCHED".equals(status) && !"FAILED".equals(status)
                        && !"PENDING".equals(status)) {
                    throw new ResourceConflictException("online_payment_intent",
                            "Only UNMATCHED/MISMATCHED/FAILED/PENDING intents can be reconciled.");
                }
                String dec = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
                if (!"FORCE_MATCH".equals(dec) && !"MARK_FAILED".equals(dec) && !"DISMISS".equals(dec)) {
                    throw new InstituteAdminException(400, "IADM04_INVALID",
                            "decision must be FORCE_MATCH, MARK_FAILED, or DISMISS.");
                }
                UUID tenantId = UUID.fromString(String.valueOf(intent.get("tenantId")));
                UUID reconId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO plat.online_payment_reconciliations ("
                                + "tenant_id, intent_id, decision, reason, reconciler_user_id, demo_label) "
                                + "VALUES (?, ?, ?, ?, ?, 'DEMO') RETURNING id")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, intentId);
                    ps.setString(3, dec);
                    ps.setString(4, reason);
                    ps.setObject(5, ctx.getUserId());
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        reconId = (UUID) rs.getObject(1);
                    }
                } catch (SQLException ex) {
                    if (ex.getMessage() != null && ex.getMessage().contains("uq_online_recon_intent")) {
                        throw new ResourceConflictException("online_reconciliation",
                                "This intent was already reconciled.");
                    }
                    throw ex;
                }

                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("reconciliationId", reconId.toString());
                out.put("decision", dec);

                if ("FORCE_MATCH".equals(dec)) {
                    settleIntent(conn, ctx, intent);
                    out.put("intentStatus", "CONFIRMED");
                    out.put("invoiceStatus", "PAID");
                } else if ("MARK_FAILED".equals(dec)) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE plat.online_payment_intents SET status = 'FAILED', failure_reason = ?, "
                                    + "updated_at = now() WHERE id = ?")) {
                        ps.setString(1, reason == null ? "Reconcile MARK_FAILED" : reason);
                        ps.setObject(2, intentId);
                        ps.executeUpdate();
                    }
                    out.put("intentStatus", "FAILED");
                } else {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE plat.online_payment_intents SET status = 'CANCELLED', updated_at = now() "
                                    + "WHERE id = ?")) {
                        ps.setObject(1, intentId);
                        ps.executeUpdate();
                    }
                    out.put("intentStatus", "CANCELLED");
                }
                DurableAuditWriter.append(conn, ctx, tenantId, "IADM04_ONLINE_RECONCILE",
                        "online_payment_reconciliation", reconId.toString(), "Online reconcile " + dec);
                return out;
            }
        });
    }

    // -------------------- US-030 R02 retry --------------------

    public Map<String, Object> listFailedCallbacks(final UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, ctx, "iadm04.payments.callback.retry", "iadm04.audit.view",
                        "iadm04.payments.online.initiate");
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, event_id, intent_id, provider_ref, received_at, signature_valid, "
                                + "processing_status, settlement_status, amount_minor_units, currency_code, "
                                + "error_detail, retry_count, last_retry_at, demo_label "
                                + "FROM plat.payment_callback_events WHERE deleted_at IS NULL "
                                + "AND processing_status IN ('FAILED_PROCESS','RETRY_QUEUED',"
                                + "'REJECTED_SIGNATURE','REJECTED_WINDOW') "
                                + "ORDER BY received_at DESC LIMIT 100");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(mapCallback(rs));
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                out.put("count", Integer.valueOf(items.size()));
                return out;
            }
        });
    }

    public Map<String, Object> retryFailedCallback(final UserSecurityContext ctx, final UUID callbackId) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iadm04.payments.callback.retry");
                Map<String, Object> row = loadCallback(conn, callbackId);
                String status = String.valueOf(row.get("processingStatus"));
                if ("ACCEPTED".equals(status) && "SUCCESS".equals(String.valueOf(row.get("settlementStatus")))) {
                    Map<String, Object> out = new LinkedHashMap<String, Object>();
                    out.put("idempotentRetry", Boolean.TRUE);
                    out.put("processingStatus", status);
                    out.put("note", "Already accepted; retry is a no-op.");
                    return out;
                }
                // Mark retry queued — commercial mutations stay denied for R02
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE plat.payment_callback_events SET processing_status = 'RETRY_QUEUED', "
                                + "retry_count = retry_count + 1, last_retry_at = now(), last_retry_by = ? "
                                + "WHERE id = ?")) {
                    ps.setObject(1, ctx.getUserId());
                    ps.setObject(2, callbackId);
                    ps.executeUpdate();
                }
                Map<String, Object> out = loadCallback(conn, callbackId);
                out.put("note", "Retry queued. Creating plans/prices/approvals/refunds remains denied for R02.");
                out.put("commercialActionsDenied", Boolean.TRUE);
                return out;
            }
        });
    }

    /** DEV helper: build a signed success callback envelope for an intent (headed demos). */
    public Map<String, Object> buildDevSuccessCallback(final UserSecurityContext ctx, final UUID intentId,
                                                       final Long overrideAmount) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iadm04.payments.online.initiate");
                Map<String, Object> intent = loadIntent(conn, intentId);
                long amount = overrideAmount != null ? overrideAmount.longValue()
                        : ((Number) intent.get("amountMinorUnits")).longValue();
                String eventId = "evt-" + UUID.randomUUID().toString();
                PaymentProviderAdapter.SignedCallbackEnvelope env = simulator.buildSignedCallback(
                        new PaymentProviderAdapter.CallbackDraft(
                                eventId,
                                intentId.toString(),
                                str(intent.get("providerRef")),
                                "SUCCESS",
                                amount,
                                String.valueOf(intent.get("currencyCode")).trim()));
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("timestamp", env.timestampEpochSeconds);
                out.put("signature", env.signatureHex);
                out.put("body", env.bodyMap);
                out.put("bodyJson", env.bodyJson);
                out.put("provider", adapter.providerName());
                out.put("labelledDevSimulator", Boolean.TRUE);
                return out;
            }
        });
    }

    // -------------------- settle + helpers --------------------

    private void settleIntent(Connection conn, UserSecurityContext ctx, Map<String, Object> intent)
            throws SQLException {
        UUID intentId = UUID.fromString(String.valueOf(intent.get("id")));
        UUID invoiceId = UUID.fromString(String.valueOf(intent.get("invoiceId")));
        UUID tenantId = UUID.fromString(String.valueOf(intent.get("tenantId")));
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE plat.online_payment_intents SET status = 'CONFIRMED', confirmed_at = now(), "
                        + "updated_at = now() WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, intentId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE plat.invoices SET status = 'PAID', paid_at = now(), updated_at = now() "
                        + "WHERE id = ? AND deleted_at IS NULL AND status <> 'PAID'")) {
            ps.setObject(1, invoiceId);
            ps.executeUpdate();
        }
        UUID subId = null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT subscription_id FROM plat.invoices WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getObject(1) != null) {
                    subId = (UUID) rs.getObject(1);
                }
            }
        }
        if (subId != null) {
            String prior = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT status FROM plat.subscriptions WHERE id = ? AND deleted_at IS NULL")) {
                ps.setObject(1, subId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        prior = rs.getString(1);
                    }
                }
            }
            if ("PENDING".equals(prior)) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE plat.subscriptions SET status = 'ACTIVE', status_reason = ?, updated_at = now() "
                                + "WHERE id = ? AND deleted_at IS NULL AND status = 'PENDING'")) {
                    ps.setString(1, "Online payment confirmed");
                    ps.setObject(2, subId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO plat.subscription_history "
                                + "(tenant_id, subscription_id, from_status, to_status, reason, "
                                + "actor_user_id, correlation_id) "
                                + "VALUES (?, ?, ?, 'ACTIVE', 'online_payment', ?, ?)")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, subId);
                    ps.setString(3, prior);
                    ps.setObject(4, ctx.getUserId());
                    ps.setString(5, intentId.toString());
                    ps.executeUpdate();
                } catch (SQLException ignored) {
                    // history insert must not undo settlement
                }
            }
        }
    }

    private UUID insertCallbackRow(Connection conn, String eventId, UUID intentId, String providerRef,
                                   boolean sigOk, Long amount, String currency, String rawBody,
                                   String processingStatus, String error) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO plat.payment_callback_events ("
                        + "event_id, intent_id, provider_ref, signature_valid, processing_status, "
                        + "amount_minor_units, currency_code, payload, error_detail, demo_label) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, 'DEMO') RETURNING id")) {
            ps.setString(1, eventId);
            ps.setObject(2, intentId);
            ps.setString(3, providerRef);
            ps.setBoolean(4, sigOk);
            ps.setString(5, processingStatus);
            if (amount == null) {
                ps.setObject(6, null);
            } else {
                ps.setLong(6, amount.longValue());
            }
            ps.setString(7, currency);
            ps.setString(8, rawBody == null ? "{}" : rawBody);
            ps.setString(9, error);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return (UUID) rs.getObject(1);
            }
        }
    }

    private void updateCallbackStatus(Connection conn, UUID id, String processing, String settlement,
                                      String error) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE plat.payment_callback_events SET processing_status = ?, settlement_status = ?, "
                        + "error_detail = COALESCE(?, error_detail) WHERE id = ?")) {
            ps.setString(1, processing);
            ps.setString(2, settlement);
            ps.setString(3, error);
            ps.setObject(4, id);
            ps.executeUpdate();
        }
    }

    private Map<String, Object> loadInvoice(Connection conn, UUID invoiceId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, tenant_id, subscription_id, status, amount_minor_units, currency_code, internal_ref "
                        + "FROM plat.invoices WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("invoice", invoiceId.toString());
                }
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("id", rs.getObject("id").toString());
                m.put("tenantId", rs.getObject("tenant_id").toString());
                Object sub = rs.getObject("subscription_id");
                m.put("subscriptionId", sub == null ? null : sub.toString());
                m.put("status", rs.getString("status"));
                m.put("amountMinorUnits", Long.valueOf(rs.getLong("amount_minor_units")));
                m.put("currencyCode", rs.getString("currency_code"));
                m.put("internalRef", rs.getString("internal_ref"));
                return m;
            }
        }
    }

    private Map<String, Object> loadIntent(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, tenant_id, invoice_id, amount_minor_units, currency_code, status, "
                        + "provider_ref, initiated_by, initiated_at, confirmed_at, failure_reason, "
                        + "mismatch_reason, simulator_outcome, demo_label, idempotency_key "
                        + "FROM plat.online_payment_intents WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("online_payment_intent", id.toString());
                }
                return mapIntent(rs);
            }
        }
    }

    private Map<String, Object> findIntentByIdempotency(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM plat.online_payment_intents WHERE idempotency_key = ? AND deleted_at IS NULL")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return loadIntent(conn, (UUID) rs.getObject(1));
            }
        }
    }

    private Map<String, Object> findOpenIntentForInvoice(Connection conn, UUID invoiceId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM plat.online_payment_intents WHERE invoice_id = ? AND deleted_at IS NULL "
                        + "AND status IN ('PENDING','MISMATCHED','UNMATCHED') ORDER BY initiated_at DESC LIMIT 1")) {
            ps.setObject(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return loadIntent(conn, (UUID) rs.getObject(1));
            }
        }
    }

    private Map<String, Object> findIntentByProviderRef(Connection conn, String providerRef) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM plat.online_payment_intents WHERE provider_ref = ? AND deleted_at IS NULL")) {
            ps.setString(1, providerRef);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return loadIntent(conn, (UUID) rs.getObject(1));
            }
        }
    }

    private Map<String, Object> findCallbackByEventId(Connection conn, String eventId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, event_id, intent_id, processing_status, settlement_status "
                        + "FROM plat.payment_callback_events WHERE event_id = ? AND deleted_at IS NULL")) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("id", rs.getObject("id").toString());
                m.put("eventId", rs.getString("event_id"));
                Object iid = rs.getObject("intent_id");
                m.put("intentId", iid == null ? null : iid.toString());
                m.put("processingStatus", rs.getString("processing_status"));
                m.put("settlementStatus", rs.getString("settlement_status"));
                return m;
            }
        }
    }

    private Map<String, Object> loadCallback(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, event_id, intent_id, provider_ref, received_at, signature_valid, "
                        + "processing_status, settlement_status, amount_minor_units, currency_code, "
                        + "error_detail, retry_count, last_retry_at, demo_label "
                        + "FROM plat.payment_callback_events WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("payment_callback_event", id.toString());
                }
                return mapCallback(rs);
            }
        }
    }

    private Map<String, Object> mapIntent(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", rs.getObject("id").toString());
        m.put("tenantId", rs.getObject("tenant_id").toString());
        m.put("invoiceId", rs.getObject("invoice_id").toString());
        m.put("amountMinorUnits", Long.valueOf(rs.getLong("amount_minor_units")));
        m.put("currencyCode", rs.getString("currency_code"));
        m.put("status", rs.getString("status"));
        m.put("providerRef", rs.getString("provider_ref"));
        m.put("initiatedBy", rs.getObject("initiated_by").toString());
        m.put("initiatedAt", rs.getTimestamp("initiated_at") == null ? null
                : rs.getTimestamp("initiated_at").toInstant().toString());
        m.put("confirmedAt", rs.getTimestamp("confirmed_at") == null ? null
                : rs.getTimestamp("confirmed_at").toInstant().toString());
        m.put("failureReason", rs.getString("failure_reason"));
        m.put("mismatchReason", rs.getString("mismatch_reason"));
        m.put("simulatorOutcome", rs.getString("simulator_outcome"));
        m.put("demoLabel", rs.getString("demo_label"));
        try {
            m.put("idempotencyKey", rs.getString("idempotency_key"));
        } catch (SQLException ignored) {
            /* optional column in some selects */
        }
        return m;
    }

    private Map<String, Object> mapCallback(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", rs.getObject("id").toString());
        m.put("eventId", rs.getString("event_id"));
        Object iid = rs.getObject("intent_id");
        m.put("intentId", iid == null ? null : iid.toString());
        m.put("providerRef", rs.getString("provider_ref"));
        m.put("receivedAt", rs.getTimestamp("received_at") == null ? null
                : rs.getTimestamp("received_at").toInstant().toString());
        m.put("signatureValid", Boolean.valueOf(rs.getBoolean("signature_valid")));
        m.put("processingStatus", rs.getString("processing_status"));
        m.put("settlementStatus", rs.getString("settlement_status"));
        long amt = rs.getLong("amount_minor_units");
        m.put("amountMinorUnits", rs.wasNull() ? null : Long.valueOf(amt));
        m.put("currencyCode", rs.getString("currency_code"));
        m.put("errorDetail", rs.getString("error_detail"));
        m.put("retryCount", Integer.valueOf(rs.getInt("retry_count")));
        m.put("lastRetryAt", rs.getTimestamp("last_retry_at") == null ? null
                : rs.getTimestamp("last_retry_at").toInstant().toString());
        m.put("demoLabel", rs.getString("demo_label"));
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

    private static String blankToNull(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        return s.trim();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static Long asLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return Long.valueOf(((Number) o).longValue());
        }
        try {
            return Long.valueOf(Long.parseLong(String.valueOf(o).trim()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
