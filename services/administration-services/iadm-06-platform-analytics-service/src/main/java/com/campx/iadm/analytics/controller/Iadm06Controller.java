package com.campx.iadm.analytics.controller;

import com.campx.iadm.analytics.exception.InstituteAdminException;
import com.campx.iadm.analytics.exception.UserProfileAccessDeniedException;
import com.campx.iadm.analytics.security.GatewayHmacVerifier;
import com.campx.iadm.analytics.security.UserSecurityContext;
import com.campx.iadm.analytics.service.AnalyticsDomainService;
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
 * HTTP ingress for IADM-06 (port 8104) under /api/v1/iadm/06.
 * Plain language: aggregate analytics API only — no raw or person-level rows.
 */
public class Iadm06Controller implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm06Controller.class);
    private static final Pattern EXPORT_ID = Pattern.compile(".*/exports/([0-9a-fA-F-]{36})$");
    private static final Pattern EXPORT_RETRY = Pattern.compile(".*/exports/([0-9a-fA-F-]{36})/retry$");
    private static final Pattern EXPORT_CSV = Pattern.compile(".*/exports/([0-9a-fA-F-]{36})/csv$");

    private final AnalyticsDomainService service;
    private final GatewayHmacVerifier hmacVerifier = new GatewayHmacVerifier();

    public Iadm06Controller(AnalyticsDomainService service) {
        this.service = service == null ? new AnalyticsDomainService() : service;
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
        LogContext.put(LogContext.KEY_SERVICE, "IADM-06");
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
                logger.warn("IADM-06 auth failed path={} code={} correlationId={}",
                        path, auth.getErrorCode(), correlationId);
                sendJson(exchange, auth.getStatus(), problem(auth.getErrorCode(), auth.getMessage()));
                return;
            }
            tracker.step("authenticated");
            UserSecurityContext ctx = UserSecurityContext.fromPlatformHeaders(auth.getUserId(), auth.getTenantId());
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            Map<String, Object> bodyMap = parseObject(body);
            Map<String, String> forward = forwardHeaders(exchange, auth);

            if ("GET".equalsIgnoreCase(method) && path.endsWith("/catalogue")) {
                sendJson(exchange, 200, toJson(service.listCatalogue(ctx)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/catalogue")) {
                sendJson(exchange, 403, problem("IADM06_FORBIDDEN", "Metric catalogue is read-only in V1"));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/overview/platform")) {
                sendJson(exchange, 200, toJson(service.platformOverview(ctx, queryParam(exchange, "window"), forward)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/overview/institute")) {
                sendJson(exchange, 200, toJson(service.instituteOverview(ctx,
                        queryParam(exchange, "tenantId"), queryParam(exchange, "window"), forward)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/comparison")) {
                sendJson(exchange, 200, toJson(service.compareInstitutes(ctx, queryParam(exchange, "window"), forward)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/entitlements")) {
                sendJson(exchange, 200, toJson(service.entitlementInsight(ctx,
                        queryParam(exchange, "tenantId"), forward)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/trends")) {
                sendJson(exchange, 200, toJson(service.trends(ctx,
                        queryParam(exchange, "tenantId"), queryParam(exchange, "window"), forward)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/exports")) {
                sendJson(exchange, 200, toJson(service.listExports(ctx)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/exports")) {
                sendJson(exchange, 200, toJson(service.requestExport(ctx, bodyMap, forward)));
                return;
            }
            Matcher csv = EXPORT_CSV.matcher(path);
            if ("GET".equalsIgnoreCase(method) && csv.matches()) {
                String text = service.buildExportCsv(ctx, UUID.fromString(csv.group(1)), forward);
                sendCsv(exchange, text);
                return;
            }
            Matcher retry = EXPORT_RETRY.matcher(path);
            if ("POST".equalsIgnoreCase(method) && retry.matches()) {
                sendJson(exchange, 200, toJson(service.retryExport(ctx, UUID.fromString(retry.group(1)), forward)));
                return;
            }
            Matcher exp = EXPORT_ID.matcher(path);
            if ("GET".equalsIgnoreCase(method) && exp.matches()) {
                sendJson(exchange, 200, toJson(service.getExport(ctx, UUID.fromString(exp.group(1)))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/settings/suppression")) {
                sendJson(exchange, 200, toJson(service.getSuppressionSettings(ctx)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/settings/suppression")) {
                int thr = 1;
                try {
                    thr = Integer.parseInt(String.valueOf(bodyMap.get("threshold")));
                } catch (Exception ignored) {
                }
                sendJson(exchange, 200, toJson(service.setSuppressionThreshold(ctx, thr)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/dev/outage-flags")) {
                sendJson(exchange, 200, toJson(service.getOutageFlags(ctx)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/dev/outage-flags")) {
                sendJson(exchange, 200, toJson(service.setOutageFlags(ctx,
                        parseBoolean(bodyMap.get("auditDisabled")),
                        parseBoolean(bodyMap.get("iadm01Disabled")),
                        parseBoolean(bodyMap.get("iadm04Disabled")),
                        parseBoolean(bodyMap.get("iadm05Disabled")),
                        parseBoolean(bodyMap.get("exportFailOnce")))));
                return;
            }

            sendJson(exchange, 404, problem("IADM06_NOT_FOUND", "No route for " + method + " " + path));
        } catch (UserProfileAccessDeniedException ex) {
            sendJson(exchange, 403, problem("IADM06_FORBIDDEN", ex.getMessage()));
        } catch (InstituteAdminException ex) {
            sendJson(exchange, ex.getStatus(), problem(ex.getErrorCode(), ex.getMessage()));
        } catch (Exception ex) {
            logger.error("IADM-06 unhandled error correlationId={}: {}", correlationId, ex.getMessage(), ex);
            sendJson(exchange, 500, problem("IADM06_INTERNAL", "Unexpected error"));
        } finally {
            tracker.close();
            LogContext.clear();
        }
    }

    private Map<String, String> forwardHeaders(HttpExchange exchange, GatewayHmacVerifier.VerificationResult auth) {
        Map<String, String> h = new LinkedHashMap<String, String>();
        h.put("X-User-Id", auth.getUserId());
        if (auth.getTenantId() != null) {
            h.put("X-Tenant-Id", auth.getTenantId());
        }
        String role = header(exchange, "X-User-Role");
        if (role != null) {
            h.put("X-User-Role", role);
        }
        // Downstream services also require gateway HMAC — forward original signature headers
        String ts = header(exchange, "X-Gateway-Timestamp");
        String sig = header(exchange, "X-Gateway-Signature");
        if (ts != null) {
            h.put("X-Gateway-Timestamp", ts);
        }
        if (sig != null) {
            h.put("X-Gateway-Signature", sig);
        }
        return h;
    }

    private static Boolean parseBoolean(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Boolean) {
            return (Boolean) o;
        }
        return Boolean.valueOf(String.valueOf(o));
    }

    private static String header(HttpExchange exchange, String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    private static String queryParam(HttpExchange exchange, String name) {
        String q = exchange.getRequestURI().getRawQuery();
        if (q == null) {
            return null;
        }
        String[] parts = q.split("&");
        for (int i = 0; i < parts.length; i++) {
            String[] kv = parts[i].split("=", 2);
            if (kv.length == 2 && name.equals(kv[0])) {
                try {
                    return java.net.URLDecoder.decode(kv[1], "UTF-8");
                } catch (Exception e) {
                    return kv[1];
                }
            }
        }
        return null;
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

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static void sendCsv(HttpExchange exchange, String csv) throws IOException {
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"iadm06-aggregate.csv\"");
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static String problem(String code, String detail) {
        return "{\"errorCode\":\"" + esc(code) + "\",\"detail\":\"" + esc(detail) + "\"}";
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String toJson(Map<String, Object> map) {
        return mapToJson(map);
    }

    @SuppressWarnings("unchecked")
    private static String mapToJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "\"" + esc((String) value) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) value).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(esc(e.getKey())).append("\":").append(mapToJson(e.getValue()));
            }
            sb.append('}');
            return sb.toString();
        }
        if (value instanceof Iterable) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object o : (Iterable<?>) value) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(mapToJson(o));
            }
            sb.append(']');
            return sb.toString();
        }
        return "\"" + esc(String.valueOf(value)) + "\"";
    }

    private static Map<String, Object> parseObject(String body) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (body == null || body.trim().isEmpty() || body.trim().charAt(0) != '{') {
            return out;
        }
        // Minimal key extractor for simple flat JSON used by our clients
        String[] keys = new String[]{
                "threshold", "tenantId", "scopeType", "window", "auditDisabled",
                "iadm01Disabled", "iadm04Disabled", "iadm05Disabled", "exportFailOnce"
        };
        for (int i = 0; i < keys.length; i++) {
            String v = extract(body, keys[i]);
            if (v != null) {
                if ("true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v)) {
                    out.put(keys[i], Boolean.valueOf(v));
                } else if (v.matches("-?\\d+")) {
                    out.put(keys[i], Integer.valueOf(v));
                } else {
                    out.put(keys[i], v);
                }
            }
        }
        return out;
    }

    private static String extract(String raw, String key) {
        String needle = "\"" + key + "\"";
        int i = raw.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int colon = raw.indexOf(':', i + needle.length());
        if (colon < 0) {
            return null;
        }
        int start = colon + 1;
        while (start < raw.length() && Character.isWhitespace(raw.charAt(start))) {
            start++;
        }
        if (start >= raw.length()) {
            return null;
        }
        if (raw.charAt(start) == '"') {
            int end = raw.indexOf('"', start + 1);
            return end < 0 ? null : raw.substring(start + 1, end);
        }
        int end = start;
        while (end < raw.length()) {
            char c = raw.charAt(end);
            if (c == ',' || c == '}' || Character.isWhitespace(c)) {
                break;
            }
            end++;
        }
        return raw.substring(start, end);
    }
}
