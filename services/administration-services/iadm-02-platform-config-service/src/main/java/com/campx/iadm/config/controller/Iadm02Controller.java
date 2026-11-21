package com.campx.iadm.config.controller;

import com.campx.iadm.config.exception.InstituteAdminException;
import com.campx.iadm.config.exception.UserProfileAccessDeniedException;
import com.campx.iadm.config.security.GatewayHmacVerifier;
import com.campx.iadm.config.security.UserSecurityContext;
import com.campx.iadm.config.service.PlatformConfigService;
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
 * HTTP ingress for IADM-02 platform configuration routes.
 * <p>
 * Gateway-authenticated adapter with {@link CampXLogger} flow tracing (Agreement &#167;3.2).
 * Domain failures surface safe messages; internal errors return a generic completion failure.
 */
public class Iadm02Controller implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm02Controller.class);

    private final PlatformConfigService service;
    private final GatewayHmacVerifier hmac = new GatewayHmacVerifier();

    public Iadm02Controller(PlatformConfigService service) {
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
        LogContext.put(LogContext.KEY_SERVICE, "IADM-02");
        LogContext.put(LogContext.KEY_TRACE_ID, correlationId);

        byte[] bodyBytes = readAll(exchange.getRequestBody());
        FlowTracker tracker = logger.flow(method + " " + path, correlationId);
        try {
            GatewayHmacVerifier.VerificationResult auth = hmac.verify(exchange, method, path, bodyBytes);
            if (!auth.isSuccess()) {
                logger.warn("IADM-02 auth failed path={} code={} correlationId={}", path, auth.getErrorCode(), correlationId);
                send(exchange, auth.getStatus(), problem(auth.getErrorCode(), auth.getMessage()));
                return;
            }
            if (auth.getUserId() != null && !auth.getUserId().trim().isEmpty()) {
                LogContext.put(LogContext.KEY_USER_ID, auth.getUserId().trim());
            }
            if (auth.getTenantId() != null && !auth.getTenantId().trim().isEmpty()) {
                LogContext.put(LogContext.KEY_TENANT_ID, auth.getTenantId().trim());
            }
            tracker.step("authenticated");

            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            UserSecurityContext ctx = path.contains("/definitions")
                    ? UserSecurityContext.fromPlatformHeaders(auth.getUserId(), auth.getTenantId())
                    : UserSecurityContext.fromHeaders(auth.getUserId(), auth.getTenantId());

            if ("GET".equalsIgnoreCase(method) && path.endsWith("/definitions")) {
                send(exchange, 200, toJson(service.listDefinitions(ctx)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.contains("/definitions")) {
                boolean locked = "true".equalsIgnoreCase(extract(body, "platformLocked"));
                boolean delegable = "true".equalsIgnoreCase(extract(body, "delegableToCollege"));
                Map<String, Object> r = service.upsertDefinition(ctx, extract(body, "settingKey"),
                        extract(body, "dataType") == null ? "STRING" : extract(body, "dataType"),
                        extractRaw(body, "defaultValue"), locked, delegable, extractStringArray(body, "allowedScopes"));
                send(exchange, 201, toJson(r));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/setting-delegations")) {
                send(exchange, 200, toJson(service.listDelegations(ctx)));
                return;
            }
            if (("DELETE".equalsIgnoreCase(method) && path.matches(".*/setting-delegations/[^/]+/?$"))
                    || ("POST".equalsIgnoreCase(method) && path.matches(".*/setting-delegations/[^/]+/revoke/?$"))) {
                String idPart = path.contains("/revoke")
                        ? path.substring(path.indexOf("/setting-delegations/") + 21, path.indexOf("/revoke"))
                        : path.substring(path.lastIndexOf('/') + 1);
                send(exchange, 200, toJson(service.revokeDelegation(ctx, UUID.fromString(idPart.replace("/", "")))));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.matches(".*/settings/[^/]+/institute-value/?$")) {
                String key = segment(path, "/settings/", "/institute-value");
                send(exchange, 200, toJson(service.setInstituteValue(ctx, key, extractRaw(body, "value"))));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.matches(".*/settings/[^/]+/college-value/?$")) {
                String key = segment(path, "/settings/", "/college-value");
                send(exchange, 200, toJson(service.setCollegeValue(ctx, key,
                        UUID.fromString(extract(body, "collegeId")), extractRaw(body, "value"))));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.matches(".*/settings/[^/]+/delegate/?$")) {
                String key = segment(path, "/settings/", "/delegate");
                send(exchange, 201, toJson(service.delegateSetting(ctx, key, UUID.fromString(extract(body, "collegeId")))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.matches(".*/settings/[^/]+/effective/?$")) {
                String key = segment(path, "/settings/", "/effective");
                String q = exchange.getRequestURI().getQuery();
                UUID tenantId = qParamUuid(q, "tenantId");
                UUID collegeId = qParamUuid(q, "collegeId");
                send(exchange, 200, toJson(service.resolveEffective(ctx, tenantId, collegeId, key)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.matches(".*/settings/[^/]+/history/?$")) {
                String key = segment(path, "/settings/", "/history");
                send(exchange, 200, toJson(service.settingHistory(ctx, key)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.matches(".*/settings/[^/]+/rollback/?$")) {
                String key = segment(path, "/settings/", "/rollback");
                send(exchange, 200, toJson(service.rollbackSetting(ctx, key, extract(body, "versionId"))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/health")) {
                send(exchange, 200, "{\"status\":\"UP\",\"service\":\"IADM-02\"}");
                return;
            }
            send(exchange, 404, problem("IADM02_NOT_FOUND", "Route not found: " + path));
        } catch (UserProfileAccessDeniedException e) {
            tracker.markFailed(e);
            logger.warn("IADM-02 forbidden correlationId=" + correlationId);
            send(exchange, 403, problem("IADM02_FORBIDDEN", e.getMessage()));
        } catch (InstituteAdminException e) {
            tracker.markFailed(e);
            logger.warn("IADM-02 domain rejection code={} correlationId={}", e.getErrorCode(), correlationId);
            send(exchange, e.getStatus(), problem(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            tracker.markFailed(e);
            logger.error("IADM-02 request failed correlationId=" + correlationId, e);
            send(exchange, 500, problem("IADM02_INTERNAL", "Request could not be completed"));
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

    private static String segment(String path, String a, String b) {
        int s = path.indexOf(a) + a.length();
        int e = path.indexOf(b, s);
        return path.substring(s, e);
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

    private static String extractRaw(String json, String key) {
        if (json == null) return "null";
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return "null";
        int colon = json.indexOf(':', i + needle.length());
        String rest = json.substring(colon + 1).trim();
        if (rest.startsWith("\"")) {
            int q2 = rest.indexOf('"', 1);
            return "\"" + rest.substring(1, q2) + "\"";
        }
        if (rest.startsWith("{") || rest.startsWith("[")) {
            return extractBalanced(rest);
        }
        int end = rest.indexOf(',');
        if (end < 0) end = rest.indexOf('}');
        return rest.substring(0, end).trim();
    }

    /** Parses {@code "allowedScopes":["INSTITUTE","COLLEGE"]} — missing/empty → empty array (fail-closed). */
    private static String[] extractStringArray(String json, String key) {
        if (json == null) return new String[0];
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return new String[0];
        int colon = json.indexOf(':', i + needle.length());
        String rest = json.substring(colon + 1).trim();
        if (!rest.startsWith("[")) return new String[0];
        int end = rest.indexOf(']');
        if (end < 0) return new String[0];
        String inner = rest.substring(1, end).trim();
        if (inner.isEmpty()) return new String[0];
        java.util.List<String> out = new java.util.ArrayList<>();
        int pos = 0;
        while (pos < inner.length()) {
            int q1 = inner.indexOf('"', pos);
            if (q1 < 0) break;
            int q2 = inner.indexOf('"', q1 + 1);
            if (q2 < 0) break;
            out.add(inner.substring(q1 + 1, q2));
            pos = q2 + 1;
        }
        return out.toArray(new String[0]);
    }

    private static String extractBalanced(String rest) {
        char open = rest.charAt(0);
        char close = open == '{' ? '}' : ']';
        int depth = 0;
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return rest.substring(0, i + 1);
            }
        }
        return rest;
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