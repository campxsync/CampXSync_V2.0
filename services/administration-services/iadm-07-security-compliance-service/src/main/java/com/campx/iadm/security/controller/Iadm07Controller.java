package com.campx.iadm.security.controller;

import com.campx.iadm.security.exception.InstituteAdminException;
import com.campx.iadm.security.exception.UserProfileAccessDeniedException;
import com.campx.iadm.security.security.GatewayHmacVerifier;
import com.campx.iadm.security.security.UserSecurityContext;
import com.campx.iadm.security.service.SecurityDomainService;
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
 * HTTP ingress for IADM-07 (port 8105) under /api/v1/iadm/07.
 * Plain language: security governance registers API — never enforces policy.
 */
public class Iadm07Controller implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm07Controller.class);
    private static final Pattern UUID_TRANSITION = Pattern.compile(
            ".*/(standards|findings|risks|alerts|signals|exceptions|controls)/([0-9a-fA-F-]{36})/transition$");

    private final SecurityDomainService service;
    private final GatewayHmacVerifier hmacVerifier = new GatewayHmacVerifier();

    public Iadm07Controller(SecurityDomainService service) {
        this.service = service == null ? new SecurityDomainService() : service;
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
        LogContext.put(LogContext.KEY_SERVICE, "IADM-07");
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
                logger.warn("IADM-07 auth failed path={} code={}", path, auth.getErrorCode());
                sendJson(exchange, auth.getStatus(), problem(auth.getErrorCode(), auth.getMessage()));
                return;
            }
            tracker.step("authenticated");
            UserSecurityContext ctx = UserSecurityContext.fromPlatformHeaders(auth.getUserId(), auth.getTenantId());
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            Map<String, Object> bodyMap = parseObject(body);
            String tenantScope = queryParam(exchange, "tenantId");

            if ("GET".equalsIgnoreCase(method) && path.endsWith("/posture/overview")) {
                sendJson(exchange, 200, toJson(service.postureOverview(ctx, tenantScope)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/posture/summary")) {
                sendJson(exchange, 200, toJson(service.summaryPosture(ctx)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/standards")) {
                sendJson(exchange, 200, toJson(service.listStandards(ctx, queryParam(exchange, "status"))));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/standards")) {
                sendJson(exchange, 200, toJson(service.proposeStandard(ctx, bodyMap)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/findings")) {
                sendJson(exchange, 200, toJson(service.listFindings(ctx, tenantScope)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/findings")) {
                sendJson(exchange, 200, toJson(service.createFinding(ctx, bodyMap)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/risks")) {
                sendJson(exchange, 200, toJson(service.listRisks(ctx, tenantScope)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/risks")) {
                sendJson(exchange, 200, toJson(service.createRisk(ctx, bodyMap)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/alerts")) {
                sendJson(exchange, 200, toJson(service.listAlerts(ctx, tenantScope)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/alerts")) {
                sendJson(exchange, 200, toJson(service.createAlert(ctx, bodyMap)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/signals")) {
                sendJson(exchange, 200, toJson(service.listSignals(ctx, tenantScope)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/signals/denials")) {
                sendJson(exchange, 200, toJson(service.listDenialEvents(ctx, parseLimit(exchange))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/exceptions")) {
                sendJson(exchange, 200, toJson(service.listExceptions(ctx, tenantScope)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/exceptions")) {
                sendJson(exchange, 200, toJson(service.requestException(ctx, bodyMap)));
                return;
            }
            Matcher decideExc = Pattern.compile(".*/exceptions/([0-9a-fA-F-]{36})/decide$").matcher(path);
            if ("POST".equalsIgnoreCase(method) && decideExc.matches()) {
                sendJson(exchange, 200, toJson(service.decideException(ctx,
                        UUID.fromString(decideExc.group(1)), bodyMap)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/controls")) {
                sendJson(exchange, 200, toJson(service.listControlAssessments(ctx, tenantScope)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/evidence")) {
                sendJson(exchange, 200, toJson(service.attachEvidenceRef(ctx, bodyMap)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/enforcement")) {
                sendJson(exchange, 200, toJson(service.listEnforcement(ctx, tenantScope)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/reports/counts")) {
                sendJson(exchange, 200, toJson(service.reportCounts(ctx, tenantScope)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/audit")) {
                Integer limit = parseLimit(exchange);
                String type = queryParam(exchange, "type");
                if ("denials".equalsIgnoreCase(type)) {
                    sendJson(exchange, 200, toJson(service.listDenialEvents(ctx, limit)));
                } else {
                    sendJson(exchange, 200, toJson(service.listAuditEvidence(ctx, limit)));
                }
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/intake/signals")) {
                sendJson(exchange, 200, toJson(service.signalIntake(ctx, bodyMap)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/intake/enforcement")) {
                sendJson(exchange, 200, toJson(service.enforcementIntake(ctx, bodyMap)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/intake/simulator")) {
                sendJson(exchange, 200, toJson(service.simulatorIntake(ctx, bodyMap)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/settings/stale-threshold")) {
                sendJson(exchange, 200, toJson(service.getStaleThreshold(ctx)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/settings/stale-threshold")) {
                int minutes = 10080;
                try {
                    minutes = Integer.parseInt(String.valueOf(bodyMap.get("staleThresholdMinutes")));
                } catch (Exception ignored) {
                }
                sendJson(exchange, 200, toJson(service.setStaleThreshold(ctx, minutes)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/dev/outage-flags")) {
                sendJson(exchange, 200, toJson(service.getOutageFlags(ctx)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/dev/outage-flags")) {
                sendJson(exchange, 200, toJson(service.setOutageFlags(ctx,
                        parseBoolean(bodyMap.get("auditDisabled")),
                        parseBoolean(bodyMap.get("simulatorDisabled")),
                        parseBoolean(bodyMap.get("notificationsDisabled")))));
                return;
            }

            Matcher tr = UUID_TRANSITION.matcher(path);
            if ("POST".equalsIgnoreCase(method) && tr.matches()) {
                String kind = tr.group(1);
                UUID id = UUID.fromString(tr.group(2));
                String toStatus = extract(body, "toStatus");
                if (toStatus == null) {
                    toStatus = str(bodyMap, "toStatus");
                }
                if ("standards".equals(kind)) {
                    sendJson(exchange, 200, toJson(service.transitionStandard(ctx, id, toStatus, bodyMap)));
                } else if ("findings".equals(kind)) {
                    sendJson(exchange, 200, toJson(service.transitionFinding(ctx, id, toStatus)));
                } else if ("risks".equals(kind)) {
                    sendJson(exchange, 200, toJson(service.transitionRisk(ctx, id, toStatus)));
                } else if ("alerts".equals(kind)) {
                    sendJson(exchange, 200, toJson(service.transitionAlert(ctx, id, toStatus)));
                } else if ("signals".equals(kind)) {
                    sendJson(exchange, 200, toJson(service.transitionSignal(ctx, id, toStatus)));
                } else if ("controls".equals(kind)) {
                    sendJson(exchange, 200, toJson(service.transitionControlAssessment(ctx, id, toStatus)));
                } else {
                    sendJson(exchange, 404, problem("IADM07_NOT_FOUND", "Unsupported register transition"));
                }
                return;
            }

            sendJson(exchange, 404, problem("IADM07_NOT_FOUND", "No route for " + method + " " + path));
        } catch (UserProfileAccessDeniedException ex) {
            sendJson(exchange, 403, problem("IADM07_ACCESS_DENIED", ex.getMessage()));
        } catch (InstituteAdminException ex) {
            sendJson(exchange, ex.getStatus(), problem(ex.getErrorCode(), ex.getMessage()));
        } catch (Exception ex) {
            logger.error("IADM-07 unhandled error correlationId={}: {}", correlationId, ex.getMessage(), ex);
            sendJson(exchange, 500, problem("IADM07_INTERNAL", "Unexpected error"));
        } finally {
            tracker.close();
            LogContext.clear();
        }
    }

    private static Integer parseLimit(HttpExchange exchange) {
        try {
            String l = queryParam(exchange, "limit");
            if (l != null) {
                return Integer.valueOf(l);
            }
        } catch (Exception ignored) {
        }
        return null;
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
        String[] keys = new String[]{
                "code", "title", "severity", "bodyRef", "toStatus", "decision", "rationale",
                "instituteId", "linkedType", "linkedId", "referenceAddress", "source",
                "correlationId", "summary", "sourceDemoOnly", "serviceName", "reportedStatus",
                "reportSource", "controlId", "validFrom", "validTo", "effectiveAt", "reviewDueAt",
                "staleThresholdMinutes", "auditDisabled", "simulatorDisabled", "notificationsDisabled"
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

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }
}