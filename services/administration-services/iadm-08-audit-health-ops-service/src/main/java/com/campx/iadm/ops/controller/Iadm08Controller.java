package com.campx.iadm.ops.controller;

import com.campx.iadm.ops.exception.InstituteAdminException;
import com.campx.iadm.ops.exception.UserProfileAccessDeniedException;
import com.campx.iadm.ops.security.GatewayHmacVerifier;
import com.campx.iadm.ops.security.UserSecurityContext;
import com.campx.iadm.ops.service.HealthTargetInventory;
import com.campx.iadm.ops.service.OpsDomainService;
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
 * HTTP ingress for IADM-08 (port 8106) under /api/v1/iadm/08.
 * Plain language: read-only audit investigation and health oversight API.
 */
public class Iadm08Controller implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm08Controller.class);
    private static final Pattern EVENT_DETAIL = Pattern.compile(".*/audit/events/([0-9a-fA-F-]{36})$");
    private static final Pattern HEALTH_DETAIL = Pattern.compile(".*/health/targets/([A-Za-z0-9._-]+)$");
    private static final Pattern TIMELINE = Pattern.compile(".*/audit/timeline/([0-9a-fA-F-]{36})$");

    private final OpsDomainService service;
    private final GatewayHmacVerifier hmacVerifier = new GatewayHmacVerifier();

    public Iadm08Controller(OpsDomainService service) {
        this.service = service == null ? new OpsDomainService() : service;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
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
        LogContext.put(LogContext.KEY_SERVICE, "IADM-08");
        LogContext.put(LogContext.KEY_TRACE_ID, correlationId);

        byte[] bodyBytes = readAll(exchange.getRequestBody());
        FlowTracker tracker = logger.flow(method + " " + path, correlationId);
        try {
            if ("GET".equalsIgnoreCase(method)
                    && (path.endsWith("/health") || path.endsWith("/healthz") || "/health".equals(path))) {
                // Own process health — no auth (matches IADM-01…07)
                if (path.contains("/health/targets") || path.endsWith("/health/overview")
                        || path.endsWith("/health/inventory")) {
                    // fall through to authenticated handlers below
                } else {
                    sendJson(exchange, 200, toJson(service.health()));
                    return;
                }
            }

            GatewayHmacVerifier.VerificationResult auth = hmacVerifier.verify(exchange, method, path, bodyBytes);
            if (!auth.isSuccess()) {
                logger.warn("IADM-08 auth failed path={} code={}", path, auth.getErrorCode());
                sendJson(exchange, auth.getStatus(), problem(auth.getErrorCode(), auth.getMessage()));
                return;
            }
            tracker.step("authenticated");
            UserSecurityContext ctx = UserSecurityContext.fromPlatformHeaders(auth.getUserId(), auth.getTenantId());
            Map<String, String> fwd = forwardHeaders(exchange, auth);
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            Map<String, Object> bodyMap = parseObject(body);

            if ("GET".equalsIgnoreCase(method) && path.endsWith("/limits")) {
                sendJson(exchange, 200, toJson(service.limits()));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/dev/limits")) {
                sendJson(exchange, 200, toJson(service.setLimits(ctx, bodyMap)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/dev/outage-flags")) {
                sendJson(exchange, 200, toJson(service.outageFlags(ctx, null)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/dev/outage-flags")) {
                sendJson(exchange, 200, toJson(service.outageFlags(ctx, bodyMap)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/audit/search")) {
                sendJson(exchange, 200, toJson(service.searchAudit(ctx, fwd,
                        queryParam(exchange, "from"), queryParam(exchange, "to"),
                        queryParam(exchange, "action"), queryParam(exchange, "tenantId"),
                        queryParam(exchange, "denialOnly"), queryParam(exchange, "page"))));
                return;
            }
            Matcher detail = EVENT_DETAIL.matcher(path);
            if ("GET".equalsIgnoreCase(method) && detail.matches()) {
                sendJson(exchange, 200, toJson(service.auditDetail(ctx, fwd, detail.group(1))));
                return;
            }
            Matcher timeline = TIMELINE.matcher(path);
            if ("GET".equalsIgnoreCase(method) && timeline.matches()) {
                sendJson(exchange, 200, toJson(service.activityTimeline(ctx, fwd, timeline.group(1),
                        queryParam(exchange, "from"), queryParam(exchange, "to"))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/health/inventory")) {
                rejectClientHealthTargetParams(exchange);
                sendJson(exchange, 200, toJson(service.healthInventory(ctx)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/health/overview")) {
                rejectClientHealthTargetParams(exchange);
                sendJson(exchange, 200, toJson(service.healthOverview(ctx, fwd)));
                return;
            }
            Matcher hDetail = HEALTH_DETAIL.matcher(path);
            if ("GET".equalsIgnoreCase(method) && hDetail.matches()) {
                rejectClientHealthTargetParams(exchange);
                sendJson(exchange, 200, toJson(service.healthDetail(ctx, fwd, hDetail.group(1))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/alerts")) {
                sendJson(exchange, 200, toJson(service.alerts(ctx, fwd, queryParam(exchange, "tenantId"))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/iadm07/evidence")) {
                sendJson(exchange, 200, toJson(service.iadm07EvidenceLink(ctx, fwd, queryParam(exchange, "kind"))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/sla")) {
                sendJson(exchange, 200, toJson(service.slaPlaceholder(ctx)));
                return;
            }
            // Deferred features — verify absence / refuse attempts (US-010, US-015)
            if (isDeferredMutationPath(path)) {
                sendJson(exchange, 404, refuseAcknowledgeOrExportJson());
                return;
            }
            sendJson(exchange, 404, problem("IADM08_NOT_FOUND", "No route for " + method + " " + path));
        } catch (UserProfileAccessDeniedException ex) {
            sendJson(exchange, 403, problem("IADM08_ACCESS_DENIED", ex.getMessage()));
        } catch (InstituteAdminException ex) {
            sendJson(exchange, ex.getStatus(), problem(ex.getErrorCode(), ex.getMessage()));
        } catch (Exception ex) {
            logger.error("IADM-08 unexpected error: {}", ex.getMessage(), ex);
            sendJson(exchange, 500, problem("IADM08_INTERNAL", "Unexpected error"));
        } finally {
            tracker.close();
            LogContext.clear();
        }
    }

    private Map<String, String> forwardHeaders(HttpExchange exchange, GatewayHmacVerifier.VerificationResult auth) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        if (auth.getUserId() != null) {
            m.put("X-User-Id", auth.getUserId());
        }
        if (auth.getTenantId() != null) {
            m.put("X-Tenant-Id", auth.getTenantId());
        }
        String role = header(exchange, "X-User-Role");
        if (role != null) {
            m.put("X-User-Role", role);
        }
        return m;
    }

    private static String header(HttpExchange exchange, String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    private static void rejectClientHealthTargetParams(HttpExchange exchange) {
        HealthTargetInventory.rejectClientSuppliedTarget(
                queryParam(exchange, "url"),
                queryParam(exchange, "host"),
                queryParam(exchange, "address"));
    }

    /**
     * Acknowledge / export are never available (US-010). Role does not change the refusal.
     * Plain language: every persona gets the same not-available response.
     */
    public static boolean isDeferredMutationPath(String path) {
        if (path == null) {
            return false;
        }
        return path.contains("/alerts/acknowledge") || path.endsWith("/acknowledge")
                || path.contains("/export");
    }

    /** JSON body returned for any acknowledge/export attempt (all roles). */
    public static String refuseAcknowledgeOrExportJson() {
        return problem("IADM08_NOT_AVAILABLE", "This feature is not available.");
    }

    /**
     * API contract: acknowledge is refused for the given role code (role is informational only).
     *
     * @return map with httpStatus 404 and errorCode IADM08_NOT_AVAILABLE
     */
    public static Map<String, Object> refuseAcknowledgeForRole(String roleCode) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("role", roleCode == null ? "" : roleCode);
        m.put("httpStatus", Integer.valueOf(404));
        m.put("errorCode", "IADM08_NOT_AVAILABLE");
        m.put("detail", "This feature is not available.");
        m.put("acknowledgeSupported", Boolean.FALSE);
        return m;
    }

    private static String queryParam(HttpExchange exchange, String name) {
        String q = exchange.getRequestURI().getRawQuery();
        if (q == null) {
            return null;
        }
        String[] parts = q.split("&");
        for (String p : parts) {
            int eq = p.indexOf('=');
            if (eq > 0) {
                String k = p.substring(0, eq);
                if (k.equals(name)) {
                    try {
                        return java.net.URLDecoder.decode(p.substring(eq + 1), "UTF-8");
                    } catch (Exception e) {
                        return p.substring(eq + 1);
                    }
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
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String problem(String code, String detail) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("errorCode", code);
        m.put("detail", detail == null ? "" : detail);
        return toJson(m);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseObject(String json) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        if (json == null || json.trim().isEmpty()) {
            return map;
        }
        // Minimal extractor for DEMO ONLY flags
        putBool(map, json, "auditDisabled");
        putBool(map, json, "healthDisabled");
        putBool(map, json, "iadm05Disabled");
        putBool(map, json, "iadm07Disabled");
        putInt(map, json, "maxWindowDays");
        putInt(map, json, "pageSize");
        return map;
    }

    private static void putBool(Map<String, Object> map, String json, String key) {
        String v = extract(json, key);
        if (v != null) {
            map.put(key, Boolean.valueOf("true".equalsIgnoreCase(v)));
        }
    }

    private static void putInt(Map<String, Object> map, String json, String key) {
        String v = extract(json, key);
        if (v != null) {
            try {
                map.put(key, Integer.valueOf(v.replaceAll("[^0-9-]", "")));
            } catch (Exception ignored) {
            }
        }
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

    static String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "\"" + escape((String) value) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;
            for (Object e : ((Map<?, ?>) value).entrySet()) {
                Map.Entry<?, ?> en = (Map.Entry<?, ?>) e;
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append("\"").append(escape(String.valueOf(en.getKey()))).append("\":");
                sb.append(toJson(en.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (value instanceof Iterable) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            boolean first = true;
            for (Object o : (Iterable<?>) value) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append(toJson(o));
            }
            sb.append("]");
            return sb.toString();
        }
        if (value.getClass().isArray()) {
            Object[] arr = (Object[]) value;
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(toJson(arr[i]));
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escape(String.valueOf(value)) + "\"";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
