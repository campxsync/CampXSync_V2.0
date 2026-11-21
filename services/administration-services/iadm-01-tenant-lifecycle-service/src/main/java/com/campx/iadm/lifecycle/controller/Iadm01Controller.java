package com.campx.iadm.lifecycle.controller;

import com.campx.iadm.lifecycle.exception.InstituteAdminException;
import com.campx.iadm.lifecycle.exception.UserProfileAccessDeniedException;
import com.campx.iadm.lifecycle.security.GatewayHmacVerifier;
import com.campx.iadm.lifecycle.security.UserSecurityContext;
import com.campx.iadm.lifecycle.service.TenantLifecycleService;
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
 * HTTP ingress for IADM-01 (port 8099) under /api/v1/iadm/01.
 * <p>
 * Verifies gateway HMAC, maps domain exceptions to problem JSON, and records request flows through
 * {@link CampXLogger} per Agreement &#167;3.2. Unexpected failures return a generic message; tokens
 * and credentials are never logged or echoed in responses.
 */
public class Iadm01Controller implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm01Controller.class);

    private final TenantLifecycleService service;
    private final GatewayHmacVerifier hmacVerifier = new GatewayHmacVerifier();

    public Iadm01Controller(TenantLifecycleService service) {
        this.service = service;
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
        LogContext.put(LogContext.KEY_SERVICE, "IADM-01");
        LogContext.put(LogContext.KEY_TRACE_ID, correlationId);

        byte[] bodyBytes = readAll(exchange.getRequestBody());
        FlowTracker tracker = logger.flow(method + " " + path, correlationId);
        try {
            GatewayHmacVerifier.VerificationResult auth = hmacVerifier.verify(exchange, method, path, bodyBytes);
            if (!auth.isSuccess()) {
                logger.warn("IADM-01 auth failed path={} code={} correlationId={}", path, auth.getErrorCode(), correlationId);
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

            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            boolean platformOp = path.endsWith("/institutes") && "POST".equalsIgnoreCase(method)
                    || path.contains("/appoint-admin")
                    || path.contains("/suspend") || path.contains("/reactivate")
                    || path.contains("/lifecycle-requests/") && path.endsWith("/decide");
            UserSecurityContext ctx = UserSecurityContext.fromHeaders(auth.getUserId(), auth.getTenantId());
            if (platformOp && (auth.getTenantId() == null || auth.getTenantId().trim().isEmpty())) {
                ctx = UserSecurityContext.fromPlatformHeaders(auth.getUserId(), auth.getTenantId());
            }

            if ("POST".equalsIgnoreCase(method) && path.matches(".*/institutes/?$")) {
                Map<String, Object> r = service.registerInstitute(ctx, extract(body, "code"), extract(body, "name"), extract(body, "region"));
                sendJson(exchange, 201, toJson(r));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.matches(".*/institutes/[^/]+/?$")) {
                UUID id = UUID.fromString(path.substring(path.lastIndexOf('/') + 1).replaceAll("/$", ""));
                sendJson(exchange, 200, toJson(service.getInstitute(ctx, id)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.matches(".*/institutes/[^/]+/appoint-admin/?$")) {
                UUID tenantId = uuidFromPath(path, "/institutes/", "/appoint-admin");
                Map<String, Object> r = service.appointInstitutionAdmin(ctx, tenantId, UUID.fromString(extract(body, "userId")));
                sendJson(exchange, 200, toJson(r));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.contains("/colleges/onboard")) {
                String admin = extract(body, "firstCollegeAdminUserId");
                Map<String, Object> r = service.onboardCollege(ctx, extract(body, "collegeCode"), extract(body, "collegeName"),
                        admin == null || admin.isEmpty() ? null : UUID.fromString(admin));
                sendJson(exchange, 201, toJson(r));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.matches(".*/colleges/[^/]+/handover/?$")) {
                UUID collegeId = uuidFromPath(path, "/colleges/", "/handover");
                sendJson(exchange, 200, toJson(service.completeHandover(ctx, collegeId, extract(body, "notes"))));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.matches(".*/institutes/[^/]+/(suspend|reactivate)/?$")) {
                boolean suspend = path.contains("/suspend");
                UUID tenantId = uuidFromPath(path, "/institutes/", suspend ? "/suspend" : "/reactivate");
                sendJson(exchange, 200, toJson(service.suspendOrReactivate(ctx, tenantId, suspend ? "suspend" : "reactivate", extract(body, "reason"))));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.matches(".*/institutes/[^/]+/lifecycle-requests/?$")) {
                sendJson(exchange, 201, toJson(service.createLifecycleRequest(ctx, extract(body, "requestType"), extract(body, "reason"))));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.matches(".*/lifecycle-requests/[^/]+/decide/?$")) {
                UUID requestId = uuidFromPath(path, "/lifecycle-requests/", "/decide");
                sendJson(exchange, 200, toJson(service.decideLifecycleRequest(ctx, requestId, extract(body, "decision"), extract(body, "note"))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.matches(".*/institutes/[^/]+/lifecycle-history/?$")) {
                UUID tenantId = uuidFromPath(path, "/institutes/", "/lifecycle-history");
                List<Map<String, Object>> rows = service.lifecycleHistory(ctx, tenantId);
                sendJson(exchange, 200, "{\"items\":" + toJsonArray(rows) + "}");
                return;
            }
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/health") || path.endsWith("/healthz"))) {
                sendJson(exchange, 200, "{\"status\":\"UP\",\"service\":\"IADM-01\"}");
                return;
            }
            sendJson(exchange, 404, problem("IADM01_NOT_FOUND", "Route not found: " + path));
        } catch (UserProfileAccessDeniedException e) {
            tracker.markFailed(e);
            logger.warn("IADM-01 forbidden correlationId=" + correlationId + " message=" + e.getMessage());
            sendJson(exchange, 403, problem("IADM01_FORBIDDEN", e.getMessage()));
        } catch (InstituteAdminException e) {
            tracker.markFailed(e);
            logger.warn("IADM-01 domain rejection code={} correlationId={}", e.getErrorCode(), correlationId);
            sendJson(exchange, e.getStatus(), problem(e.getErrorCode(), e.getMessage()));
        } catch (IllegalArgumentException e) {
            tracker.markFailed(e);
            logger.warn("IADM-01 validation correlationId=" + correlationId + " message=" + e.getMessage());
            sendJson(exchange, 400, problem("IADM01_VALIDATION", e.getMessage()));
        } catch (Exception e) {
            tracker.markFailed(e);
            logger.error("IADM-01 request failed correlationId=" + correlationId, e);
            sendJson(exchange, 500, problem("IADM01_INTERNAL", "Request could not be completed"));
        } finally {
            tracker.close();
            LogContext.clear();
        }
    }

    private static String header(HttpExchange exchange, String name) {
        String value = exchange.getRequestHeaders().getFirst(name);
        if (value == null) {
            value = exchange.getRequestHeaders().getFirst(name.toLowerCase());
        }
        return value;
    }

    private static UUID uuidFromPath(String path, String startToken, String endToken) {
        int s = path.indexOf(startToken);
        int e = path.indexOf(endToken, s);
        String id = path.substring(s + startToken.length(), e);
        return UUID.fromString(id.replaceAll("/$", ""));
    }

    private static String extract(String json, String key) {
        if (json == null) return null;
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

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
            else sb.append('"').append(String.valueOf(v).replace("\"", "\\\"")).append('"');
        }
        return sb.append('}').toString();
    }

    private static String toJsonArray(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(toJson(rows.get(i)));
        }
        return sb.append(']').toString();
    }

    private static String problem(String code, String detail) {
        return "{\"type\":\"about:blank\",\"title\":\"Error\",\"status\":null,\"detail\":\"" +
                (detail == null ? "" : detail.replace("\"", "\\\"")) + "\",\"errorCode\":\"" + code + "\"}";
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
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