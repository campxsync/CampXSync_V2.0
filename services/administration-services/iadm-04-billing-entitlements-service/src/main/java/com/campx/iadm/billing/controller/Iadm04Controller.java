package com.campx.iadm.billing.controller;

import com.campx.iadm.billing.exception.InstituteAdminException;
import com.campx.iadm.billing.exception.UserProfileAccessDeniedException;
import com.campx.iadm.billing.security.GatewayHmacVerifier;
import com.campx.iadm.billing.security.UserSecurityContext;
import com.campx.iadm.billing.service.Iadm04DomainService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP ingress for IADM-04 billing entitlements routes.
 * <p>
 * Flow tracing and error handling via {@link CampXLogger} (Agreement &#167;3.2). Unexpected
 * failures return a generic message without internal exception text.
 */
public class Iadm04Controller implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm04Controller.class);

    private final Iadm04DomainService service;
    private final GatewayHmacVerifier hmac = new GatewayHmacVerifier();

    public Iadm04Controller(Iadm04DomainService service) {
        this.service = service;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String correlationId = resolveCorrelationId(exchange);
        LogContext.put(LogContext.KEY_SERVICE, "IADM-04");
        LogContext.put(LogContext.KEY_TRACE_ID, correlationId);

        byte[] body = readAll(exchange.getRequestBody());
        FlowTracker tracker = logger.flow(method + " " + path, correlationId);
        try {
            GatewayHmacVerifier.VerificationResult auth = hmac.verify(exchange, method, path, body);
            if (!auth.isSuccess()) {
                logger.warn("IADM-04 auth failed path={} code={} correlationId={}", path, auth.getErrorCode(), correlationId);
                send(exchange, auth.getStatus(), "{\"error\":\"" + auth.getErrorCode() + "\"}");
                return;
            }
            if (auth.getUserId() != null && !auth.getUserId().trim().isEmpty()) {
                LogContext.put(LogContext.KEY_USER_ID, auth.getUserId().trim());
            }
            if (auth.getTenantId() != null && !auth.getTenantId().trim().isEmpty()) {
                LogContext.put(LogContext.KEY_TENANT_ID, auth.getTenantId().trim());
            }
            tracker.step("authenticated");

            UserSecurityContext ctx = UserSecurityContext.fromHeaders(auth.getUserId(),
                    auth.getTenantId() == null || auth.getTenantId().isEmpty()
                            ? "a0000000-0000-0000-0000-000000000001" : auth.getTenantId());
            String bodyStr = new String(body, StandardCharsets.UTF_8);
            String q = exchange.getRequestURI().getQuery();
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/health")) {
                send(exchange, 200, "{\"status\":\"UP\",\"service\":\"IADM-04\"}");
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/plans")) {
                send(exchange, 200, toJson(service.listPlans(ctx)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/subscriptions")) {
                UUID tid = qParamUuid(q, "tenantId");
                send(exchange, 200, toJson(service.listSubscriptions(ctx, tid)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/invoices")) {
                UUID tid = qParamUuid(q, "tenantId");
                send(exchange, 200, toJson(service.listInvoices(ctx, tid)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/plan-change-requests")) {
                send(exchange, 201, toJson(service.createPlanChangeRequest(ctx,
                        extract(bodyStr, "reason"), extract(bodyStr, "requestedPlanCode"))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.contains("/entitlements/check")) {
                String key = null;
                String tenant = null;
                if (q != null) {
                    for (String p : q.split("&")) {
                        String[] kv = p.split("=", 2);
                        if (kv.length == 2) {
                            if ("key".equals(kv[0])) key = kv[1];
                            if ("tenantId".equals(kv[0])) tenant = kv[1];
                        }
                    }
                }
                UUID tid = tenant == null || tenant.isEmpty() ? null : UUID.fromString(tenant);
                Map<String, Object> r = service.checkEntitlement(ctx, tid, key);
                send(exchange, 200, toJson(r));
                return;
            }
            send(exchange, 404, problem("IADM04_NOT_FOUND", "Route not found: " + path));
        } catch (UserProfileAccessDeniedException e) {
            tracker.markFailed(e);
            logger.warn("IADM-04 forbidden correlationId=" + correlationId);
            send(exchange, 403, problem("IADM04_FORBIDDEN", e.getMessage()));
        } catch (InstituteAdminException e) {
            tracker.markFailed(e);
            logger.warn("IADM-04 domain rejection code={} correlationId={}", e.getErrorCode(), correlationId);
            send(exchange, e.getStatus(), problem(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            tracker.markFailed(e);
            logger.error("IADM-04 request failed correlationId=" + correlationId, e);
            send(exchange, 500, problem("IADM04_INTERNAL", "Request could not be completed"));
        } finally {
            tracker.close();
            LogContext.clear();
        }
    }

    private static String resolveCorrelationId(HttpExchange exchange) {
        String correlationId = exchange.getRequestHeaders().getFirst("X-Correlation-Id");
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = exchange.getRequestHeaders().getFirst("X-Trace-Id");
        }
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }
        return correlationId;
    }

    private static String problem(String code, String detail) {
        return "{\"errorCode\":\"" + code + "\",\"detail\":\"" +
                (detail == null ? "" : detail.replace("\"", "\\\"")) + "\"}";
    }

    private static UUID qParamUuid(String q, String name) {
        if (q == null) return null;
        for (String part : q.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name) && !kv[1].isEmpty()) return UUID.fromString(kv[1]);
        }
        return null;
    }

    private static String extract(String json, String key) {
        if (json == null) return null;
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + needle.length());
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        return json.substring(q1 + 1, q2);
    }

    @SuppressWarnings("unchecked")
    private static String toJson(Map<String, Object> r) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : r.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v == null) sb.append("null");
            else if (v instanceof Number || v instanceof Boolean) sb.append(v);
            else if (v instanceof List) {
                List<?> list = (List<?>) v;
                sb.append('[');
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(',');
                    Object item = list.get(i);
                    if (item instanceof Map) sb.append(toJson((Map<String, Object>) item));
                    else sb.append('"').append(String.valueOf(item).replace("\"", "\\\"")).append('"');
                }
                sb.append(']');
            } else sb.append('"').append(String.valueOf(v).replace("\"", "\\\"")).append('"');
        }
        return sb.append('}').toString();
    }

    private static void send(HttpExchange ex, int st, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(st, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }
}