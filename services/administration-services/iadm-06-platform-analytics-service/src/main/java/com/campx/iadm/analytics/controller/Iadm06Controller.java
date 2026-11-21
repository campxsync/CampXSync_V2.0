package com.campx.iadm.analytics.controller;

import com.campx.iadm.analytics.exception.InstituteAdminException;
import com.campx.iadm.analytics.exception.UserProfileAccessDeniedException;
import com.campx.iadm.analytics.security.GatewayHmacVerifier;
import com.campx.iadm.analytics.security.UserSecurityContext;
import com.campx.iadm.analytics.service.Iadm06DomainService;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP ingress for IADM-06 Platform Analytics &amp; Usage (port 8104, gateway {@code /api/v1/iadm/06}).
 * <p>
 * Uses {@link CampXLogger} and {@link FlowTracker} per Agreement §3.2. Domain failures map to
 * problem JSON; unexpected failures are logged and return a generic message without leaking
 * internals. Never logs tokens, passwords, or JWTs.
 */
public class Iadm06Controller implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm06Controller.class);

    private final Iadm06DomainService service;
    private final GatewayHmacVerifier hmac = new GatewayHmacVerifier();

    public Iadm06Controller(Iadm06DomainService service) {
        this.service = service;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        byte[] bodyBytes = readAll(exchange.getRequestBody());

        String correlationId = exchange.getRequestHeaders().getFirst("X-Correlation-Id");
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = exchange.getRequestHeaders().getFirst("X-Trace-Id");
        }
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }
        LogContext.put(LogContext.KEY_SERVICE, "IADM-06");
        LogContext.put(LogContext.KEY_TRACE_ID, correlationId);
        FlowTracker tracker = logger.flow(method + " " + path, correlationId);
        try {
            if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            GatewayHmacVerifier.VerificationResult auth = hmac.verify(exchange, method, path, bodyBytes);
            if (!auth.isSuccess()) {
                logger.warn("IADM-06 HMAC/auth rejected path={} status={}", path, auth.getStatus());
                send(exchange, auth.getStatus(), problem(auth.getErrorCode(), auth.getMessage()));
                return;
            }
            LogContext.put(LogContext.KEY_USER_ID, auth.getUserId());
            LogContext.put(LogContext.KEY_TENANT_ID, auth.getTenantId());
            tracker.step("authenticated");

            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            UserSecurityContext ctx = UserSecurityContext.fromHeaders(auth.getUserId(),
                    auth.getTenantId() == null || auth.getTenantId().isEmpty()
                            ? "a0000000-0000-0000-0000-000000000001" : auth.getTenantId());
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/health")) {
                send(exchange, 200, toJson(service.health(ctx)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/usage/by-college")) {
                send(exchange, 200, toJson(service.usageByCollege(ctx)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/usage/export")) {
                send(exchange, 200, toJson(service.usageExport(ctx)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/usage/platform")) {
                send(exchange, 200, toJson(service.usagePlatform(
                        UserSecurityContext.fromPlatformHeaders(auth.getUserId(), auth.getTenantId()))));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/usage/ingest")) {
                BigDecimal value = extractDecimal(body, "value");
                UUID collegeId = null;
                String collegeRaw = extract(body, "collegeId");
                if (collegeRaw != null && !collegeRaw.isEmpty()) collegeId = UUID.fromString(collegeRaw);
                send(exchange, 201, toJson(service.ingestUsage(ctx, extract(body, "metricName"), value,
                        collegeId, extract(body, "recordedOn"))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/usage")) {
                send(exchange, 200, toJson(service.usageSummary(ctx)));
                return;
            }
            send(exchange, 404, problem("IADM06_NOT_FOUND", "Route not found: " + path));
        } catch (UserProfileAccessDeniedException e) {
            tracker.markFailed(e);
            logger.warn("IADM-06 access denied path={} message={}", path, e.getMessage());
            send(exchange, 403, problem("IADM06_FORBIDDEN", e.getMessage()));
        } catch (InstituteAdminException e) {
            tracker.markFailed(e);
            logger.warn("IADM-06 domain failure path={} code={} status={}", path, e.getErrorCode(), e.getStatus());
            send(exchange, e.getStatus(), problem(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            tracker.markFailed(e);
            logger.error("IADM-06 unhandled failure path=" + path + " correlationId=" + correlationId, e);
            send(exchange, 500, problem("IADM06_INTERNAL", "Request could not be completed"));
        } finally {
            tracker.close();
            LogContext.clear();
        }
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

    private static BigDecimal extractDecimal(String json, String key) {
        if (json == null) return null;
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + needle.length());
        String rest = json.substring(colon + 1).trim();
        if (rest.startsWith("\"")) {
            int q2 = rest.indexOf('"', 1);
            rest = rest.substring(1, q2);
        } else {
            int end = rest.length();
            for (int j = 0; j < rest.length(); j++) {
                char c = rest.charAt(j);
                if (c == ',' || c == '}' || Character.isWhitespace(c)) { end = j; break; }
            }
            rest = rest.substring(0, end);
        }
        try { return new BigDecimal(rest.trim()); } catch (NumberFormatException e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v == null) sb.append("null");
            else if (v instanceof Number || v instanceof Boolean) sb.append(v);
            else if (v instanceof Map) sb.append(toJson((Map<String, Object>) v));
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

    private static String problem(String code, String detail) {
        return "{\"errorCode\":\"" + code + "\",\"detail\":\"" + (detail == null ? "" : detail.replace("\"", "\\\"")) + "\"}";
    }

    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}