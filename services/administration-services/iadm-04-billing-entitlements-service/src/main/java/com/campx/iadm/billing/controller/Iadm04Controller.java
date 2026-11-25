package com.campx.iadm.billing.controller;

import com.campx.iadm.billing.exception.InstituteAdminException;
import com.campx.iadm.billing.exception.UserProfileAccessDeniedException;
import com.campx.iadm.billing.security.GatewayHmacVerifier;
import com.campx.iadm.billing.security.UserSecurityContext;
import com.campx.iadm.billing.service.BillingDomainService;
import com.campx.iadm.billing.service.BillingFinanceService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.FlowTracker;
import com.campx.logger.context.LogContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP ingress for IADM-04 (port 8102) under /api/v1/iadm/04.
 */
public class Iadm04Controller implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm04Controller.class);
    private static final Pattern PLAN_ID = Pattern.compile(".*/plans/([0-9a-fA-F-]{36})$");
    private static final Pattern PLAN_ENTS = Pattern.compile(".*/plans/([0-9a-fA-F-]{36})/entitlements$");
    private static final Pattern SUB_ID = Pattern.compile(".*/subscriptions/([0-9a-fA-F-]{36})$");
    private static final Pattern PLAN_CHANGE_ID = Pattern.compile(".*/plan-changes/([0-9a-fA-F-]{36})/decide$");
    private static final Pattern INVOICE_ID = Pattern.compile(".*/invoices/([0-9a-fA-F-]{36})$");
    private static final Pattern INVOICE_VOID = Pattern.compile(".*/invoices/([0-9a-fA-F-]{36})/void$");
    private static final Pattern OFFLINE_RECON = Pattern.compile(".*/offline-payments/([0-9a-fA-F-]{36})/reconcile$");
    private static final Pattern TRIAL_CONVERT = Pattern.compile(".*/subscriptions/([0-9a-fA-F-]{36})/convert-trial$");

    private final BillingDomainService service;
    private final BillingFinanceService finance;
    private final GatewayHmacVerifier hmacVerifier = new GatewayHmacVerifier();

    public Iadm04Controller(BillingDomainService service) {
        this(service, new BillingFinanceService());
    }

    public Iadm04Controller(BillingDomainService service, BillingFinanceService finance) {
        this.service = service == null ? new BillingDomainService() : service;
        this.finance = finance == null ? new BillingFinanceService() : finance;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,PATCH,OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String correlationId = header(exchange, "X-Correlation-Id");
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = header(exchange, "X-Trace-Id");
        }
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }
        LogContext.put(LogContext.KEY_SERVICE, "IADM-04");
        LogContext.put(LogContext.KEY_TRACE_ID, correlationId);

        byte[] bodyBytes = readAll(exchange.getRequestBody());
        FlowTracker tracker = logger.flow(method + " " + path, correlationId);
        try {
            if ("GET".equalsIgnoreCase(method)
                    && (path.endsWith("/health") || path.endsWith("/healthz") || "/health".equals(path))) {
                sendJson(exchange, 200, toJson(service.health()));
                return;
            }

            GatewayHmacVerifier.VerificationResult auth = hmacVerifier.verify(exchange, method, path, bodyBytes);
            if (!auth.isSuccess()) {
                logger.warn("IADM-04 auth failed path={} code={} correlationId={}",
                        path, auth.getErrorCode(), correlationId);
                sendJson(exchange, auth.getStatus(), problem(auth.getErrorCode(), auth.getMessage()));
                return;
            }
            tracker.step("authenticated");
            UserSecurityContext ctx = UserSecurityContext.fromPlatformHeaders(auth.getUserId(), auth.getTenantId());
            if (ctx == null) {
                sendJson(exchange, 401, problem("UNAUTHORIZED", "Authenticated identity required."));
                return;
            }

            String body = new String(bodyBytes, StandardCharsets.UTF_8);

            if ("GET".equalsIgnoreCase(method) && path.endsWith("/plans")) {
                sendJson(exchange, 200, toJson(service.listPlans(ctx, queryParam(exchange, "status"))));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/plans")) {
                Long price = parseLong(extract(body, "priceMinorUnits"));
                if (price == null) {
                    sendJson(exchange, 400, problem("IADM04_INVALID", "priceMinorUnits is required."));
                    return;
                }
                sendJson(exchange, 200, toJson(service.createPlan(ctx,
                        extract(body, "planCode"),
                        extract(body, "name"),
                        extract(body, "billingCycle"),
                        price.longValue(),
                        extract(body, "currencyCode"),
                        extract(body, "idempotencyKey"))));
                return;
            }
            Matcher planEnts = PLAN_ENTS.matcher(path);
            if ("POST".equalsIgnoreCase(method) && planEnts.matches()) {
                UUID planId = UUID.fromString(planEnts.group(1));
                Boolean enabled = parseBoolean(extract(body, "enabled"));
                sendJson(exchange, 200, toJson(service.mapEntitlement(ctx, planId,
                        extract(body, "entitlementKey"),
                        parseLong(extract(body, "limitValue")),
                        enabled,
                        extract(body, "limitUnit"))));
                return;
            }
            Matcher planIdMatch = PLAN_ID.matcher(path);
            if (planIdMatch.matches()) {
                UUID planId = UUID.fromString(planIdMatch.group(1));
                if ("GET".equalsIgnoreCase(method)) {
                    sendJson(exchange, 200, toJson(service.getPlan(ctx, planId)));
                    return;
                }
                if ("PATCH".equalsIgnoreCase(method)) {
                    sendJson(exchange, 200, toJson(service.updatePlan(ctx, planId,
                            extract(body, "name"),
                            parseLong(extract(body, "priceMinorUnits")),
                            extract(body, "planStatus"))));
                    return;
                }
            }

            if ("GET".equalsIgnoreCase(method) && path.endsWith("/feature-flags")) {
                sendJson(exchange, 200, toJson(service.listFeatureFlags(ctx)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/trial-policy")) {
                sendJson(exchange, 200, toJson(service.listTrialPolicy(ctx)));
                return;
            }

            if ("GET".equalsIgnoreCase(method) && path.endsWith("/subscriptions/own")) {
                sendJson(exchange, 200, toJson(service.getOwnSubscription(ctx)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/subscriptions/attach")) {
                UUID tenantId = parseUuid(extract(body, "tenantId"));
                if (tenantId == null) {
                    tenantId = parseUuid(extract(body, "instituteId"));
                }
                String planRef = extract(body, "planCode");
                if (planRef == null || planRef.trim().isEmpty()) {
                    planRef = extract(body, "planId");
                }
                if (planRef == null || planRef.trim().isEmpty()) {
                    planRef = extract(body, "planReference");
                }
                sendJson(exchange, 200, toJson(service.attachSubscription(ctx, tenantId, planRef,
                        extract(body, "mode"), extract(body, "idempotencyKey"))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/subscriptions")) {
                sendJson(exchange, 200, toJson(service.listSubscriptions(ctx,
                        queryParam(exchange, "status"),
                        parseUuid(queryParam(exchange, "tenantId")))));
                return;
            }
            Matcher subMatch = SUB_ID.matcher(path);
            if ("GET".equalsIgnoreCase(method) && subMatch.matches()) {
                sendJson(exchange, 200, toJson(service.getSubscription(ctx, UUID.fromString(subMatch.group(1)))));
                return;
            }

            if ("POST".equalsIgnoreCase(method) && path.endsWith("/entitlements/check")) {
                sendJson(exchange, 200, toJson(service.checkEntitlement(ctx,
                        parseUuid(firstNonEmpty(extract(body, "instituteId"), extract(body, "tenantId"))),
                        extract(body, "featureKey"))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/access-gate")) {
                sendJson(exchange, 200, toJson(service.accessGateProbe(ctx,
                        parseUuid(firstNonEmpty(queryParam(exchange, "instituteId"),
                                queryParam(exchange, "tenantId"))))));
                return;
            }

            // ---- S2: plan-change / invoices / offline / trial convert ----
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/plan-changes")) {
                sendJson(exchange, 200, toJson(finance.listPlanChanges(ctx, queryParam(exchange, "status"))));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/plan-changes")) {
                sendJson(exchange, 200, toJson(finance.requestPlanChange(ctx,
                        parseUuid(extract(body, "requestedPlanId")),
                        extract(body, "effectiveOn"),
                        extract(body, "reason"),
                        extract(body, "idempotencyKey"))));
                return;
            }
            Matcher planChangeDecide = PLAN_CHANGE_ID.matcher(path);
            if ("POST".equalsIgnoreCase(method) && planChangeDecide.matches()) {
                sendJson(exchange, 200, toJson(finance.decidePlanChange(ctx,
                        UUID.fromString(planChangeDecide.group(1)),
                        extract(body, "decision"),
                        extract(body, "reason"))));
                return;
            }

            if ("GET".equalsIgnoreCase(method) && path.endsWith("/invoices")) {
                sendJson(exchange, 200, toJson(finance.listInvoices(ctx,
                        parseUuid(queryParam(exchange, "tenantId")))));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/invoices")) {
                sendJson(exchange, 200, toJson(finance.generateInvoice(ctx,
                        parseUuid(extract(body, "subscriptionId")),
                        extract(body, "idempotencyKey"))));
                return;
            }
            Matcher invVoid = INVOICE_VOID.matcher(path);
            if ("POST".equalsIgnoreCase(method) && invVoid.matches()) {
                sendJson(exchange, 200, toJson(finance.voidInvoice(ctx,
                        UUID.fromString(invVoid.group(1)), extract(body, "reason"))));
                return;
            }
            Matcher invId = INVOICE_ID.matcher(path);
            if ("GET".equalsIgnoreCase(method) && invId.matches()) {
                sendJson(exchange, 200, toJson(finance.getInvoice(ctx, UUID.fromString(invId.group(1)))));
                return;
            }

            Matcher trialConvert = TRIAL_CONVERT.matcher(path);
            if ("POST".equalsIgnoreCase(method) && trialConvert.matches()) {
                sendJson(exchange, 200, toJson(finance.convertTrial(ctx,
                        UUID.fromString(trialConvert.group(1)),
                        extract(body, "idempotencyKey"))));
                return;
            }

            if ("GET".equalsIgnoreCase(method) && path.endsWith("/offline-payments")) {
                sendJson(exchange, 200, toJson(finance.listOfflineEvidence(ctx,
                        parseUuid(queryParam(exchange, "invoiceId")))));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/offline-payments")) {
                sendJson(exchange, 200, toJson(finance.recordOfflineEvidence(ctx,
                        parseUuid(extract(body, "invoiceId")),
                        extract(body, "instrumentType"),
                        extract(body, "reference"),
                        parseLong(extract(body, "amountMinorUnits")),
                        extract(body, "currencyCode"),
                        extract(body, "notes"))));
                return;
            }
            Matcher offlineRecon = OFFLINE_RECON.matcher(path);
            if ("POST".equalsIgnoreCase(method) && offlineRecon.matches()) {
                sendJson(exchange, 200, toJson(finance.reconcileOffline(ctx,
                        UUID.fromString(offlineRecon.group(1)),
                        extract(body, "decision"),
                        extract(body, "reason"))));
                return;
            }

            sendJson(exchange, 404, problem("IADM04_NOT_FOUND", "No IADM-04 route matched: " + path));
        } catch (UserProfileAccessDeniedException ex) {
            sendJson(exchange, 403, problem("IADM04_FORBIDDEN", ex.getMessage()));
        } catch (InstituteAdminException ex) {
            sendJson(exchange, ex.getStatus(), problem(ex.getErrorCode(), ex.getMessage()));
        } catch (Exception ex) {
            logger.error("IADM-04 unhandled error: {}", ex.getMessage(), ex);
            sendJson(exchange, 500, problem("IADM04_INTERNAL", "An unexpected error occurred."));
        } finally {
            tracker.close();
            LogContext.clear();
        }
    }

    private static String header(HttpExchange exchange, String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    private static String queryParam(HttpExchange exchange, String name) {
        String q = exchange.getRequestURI().getRawQuery();
        if (q == null || q.isEmpty()) {
            return null;
        }
        for (String part : q.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(name)) {
                try {
                    return java.net.URLDecoder.decode(part.substring(eq + 1), "UTF-8");
                } catch (java.io.UnsupportedEncodingException ex) {
                    return part.substring(eq + 1);
                }
            }
        }
        return null;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) >= 0) {
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static String problem(String code, String detail) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("errorCode", code);
        m.put("detail", detail);
        return toJson(m);
    }

    private static String extract(String json, String field) {
        if (json == null || json.isEmpty() || field == null) {
            return null;
        }
        Pattern p = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        Pattern n = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
        Matcher nm = n.matcher(json);
        if (nm.find()) {
            return nm.group(1);
        }
        Pattern b = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(true|false|null)",
                Pattern.CASE_INSENSITIVE);
        Matcher bm = b.matcher(json);
        if (bm.find()) {
            String v = bm.group(1);
            return "null".equalsIgnoreCase(v) ? null : v;
        }
        return null;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Long parseLong(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Boolean parseBoolean(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        if ("true".equalsIgnoreCase(raw.trim())) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(raw.trim())) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a;
        }
        return b;
    }

    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":");
            appendValue(sb, e.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendValue(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v.toString());
        } else if (v instanceof Map) {
            sb.append(toJson((Map<String, Object>) v));
        } else if (v instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) v) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                appendValue(sb, item);
            }
            sb.append(']');
        } else {
            sb.append('"').append(escape(String.valueOf(v))).append('"');
        }
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
