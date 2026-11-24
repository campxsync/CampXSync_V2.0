package com.campx.iadm.access.controller;

import com.campx.iadm.access.exception.InstituteAdminException;
import com.campx.iadm.access.exception.UserProfileAccessDeniedException;
import com.campx.iadm.access.security.GatewayHmacVerifier;
import com.campx.iadm.access.security.GrantorCeiling;
import com.campx.iadm.access.security.UserSecurityContext;
import com.campx.iadm.access.service.AccessGovernanceService;
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
import java.util.Map;
import java.util.UUID;

/**
 * HTTP ingress for IADM-03 (port 8101) under /api/v1/iadm/03.
 */
public class Iadm03Controller implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm03Controller.class);

    private final AccessGovernanceService service;
    private final GatewayHmacVerifier hmacVerifier = new GatewayHmacVerifier();

    public Iadm03Controller(AccessGovernanceService service) {
        this.service = service;
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
        LogContext.put(LogContext.KEY_SERVICE, "IADM-03");
        LogContext.put(LogContext.KEY_TRACE_ID, correlationId);

        byte[] bodyBytes = readAll(exchange.getRequestBody());
        FlowTracker tracker = logger.flow(method + " " + path, correlationId);
        try {
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/health") || path.endsWith("/healthz"))) {
                sendJson(exchange, 200, toJson(service.health()));
                return;
            }

            GatewayHmacVerifier.VerificationResult auth = hmacVerifier.verify(exchange, method, path, bodyBytes);
            if (!auth.isSuccess()) {
                logger.warn("IADM-03 auth failed path={} code={} correlationId={}",
                        path, auth.getErrorCode(), correlationId);
                sendJson(exchange, auth.getStatus(), problem(auth.getErrorCode(), auth.getMessage()));
                return;
            }
            if (auth.getUserId() != null && !auth.getUserId().trim().isEmpty()) {
                LogContext.put(LogContext.KEY_USER_ID, auth.getUserId().trim());
            }
            if (auth.getTenantId() != null && !auth.getTenantId().trim().isEmpty()) {
                LogContext.put(LogContext.KEY_TENANT_ID, auth.getTenantId().trim());
            }
            tracker.step("authenticated");

            UserSecurityContext ctx = UserSecurityContext.fromPlatformHeaders(auth.getUserId(), auth.getTenantId());

            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/roles") || path.endsWith("/roles/"))) {
                sendJson(exchange, 200, toJson(service.listRoleCatalogue(ctx)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/permissions") || path.endsWith("/permissions/"))) {
                String q = queryParam(exchange, "q");
                sendJson(exchange, 200, toJson(service.searchPermissions(ctx, q)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/idempotency/begin")) {
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                String op = extract(body, "operation");
                String key = firstNonEmpty(extract(body, "idempotencyKey"), header(exchange, "Idempotency-Key"));
                sendJson(exchange, 200, toJson(service.beginIdempotent(ctx, op == null ? "MUTATION" : op, key, correlationId)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/ceilings/evaluate")) {
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                String grantor = extract(body, "grantor");
                String target = extract(body, "targetRole");
                boolean self = "true".equalsIgnoreCase(extract(body, "selfGrant"));
                String deny = GrantorCeiling.denyReason(grantor, target, self);
                Map<String, Object> out = new java.util.LinkedHashMap<String, Object>();
                out.put("allowed", Boolean.valueOf(deny == null));
                out.put("denyReason", deny);
                sendJson(exchange, 200, toJson(out));
                return;
            }

            sendJson(exchange, 404, problem("IADM03_NOT_FOUND", "No IADM-03 route for " + path));
        } catch (UserProfileAccessDeniedException ex) {
            sendJson(exchange, 403, problem("IADM03_FORBIDDEN", ex.getMessage()));
        } catch (InstituteAdminException ex) {
            sendJson(exchange, ex.getStatus(), problem(ex.getErrorCode(), ex.getMessage()));
        } catch (Exception ex) {
            logger.error("IADM-03 unhandled path={} err={}", path, ex.getMessage(), ex);
            sendJson(exchange, 500, problem("IADM03_INTERNAL", "Unexpected server error"));
        } finally {
            tracker.close();
            LogContext.clear();
        }
    }

    private static String queryParam(HttpExchange exchange, String name) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String[] parts = raw.split("&");
        for (String part : parts) {
            int eq = part.indexOf('=');
            if (eq > 0 && name.equals(part.substring(0, eq))) {
                try {
                    return java.net.URLDecoder.decode(part.substring(eq + 1), "UTF-8");
                } catch (Exception ex) {
                    return part.substring(eq + 1);
                }
            }
        }
        return null;
    }

    private static String header(HttpExchange exchange, String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a.trim();
        }
        if (b != null && !b.trim().isEmpty()) {
            return b.trim();
        }
        return null;
    }

    private static String extract(String json, String field) {
        if (json == null || field == null) {
            return null;
        }
        String needle = "\"" + field + "\"";
        int i = json.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) {
            return null;
        }
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length()) {
            return null;
        }
        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            if (end < 0) {
                return null;
            }
            return json.substring(start + 1, end);
        }
        int end = start;
        while (end < json.length() && ",}] \n\r\t".indexOf(json.charAt(end)) < 0) {
            end++;
        }
        return json.substring(start, end).trim();
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
        exchange.getResponseHeaders().set("X-Service", "IADM-03");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String problem(String code, String message) {
        return "{\"type\":\"about:blank\",\"title\":\"" + escape(code) + "\",\"detail\":\""
                + escape(message) + "\",\"errorCode\":\"" + escape(code) + "\"}";
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
