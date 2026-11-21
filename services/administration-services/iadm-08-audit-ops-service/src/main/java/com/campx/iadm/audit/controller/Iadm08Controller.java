package com.campx.iadm.audit.controller;

import com.campx.iadm.audit.exception.InstituteAdminException;
import com.campx.iadm.audit.exception.UserProfileAccessDeniedException;
import com.campx.iadm.audit.security.GatewayHmacVerifier;
import com.campx.iadm.audit.security.UserSecurityContext;
import com.campx.iadm.audit.service.Iadm08DomainService;
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
 * HTTP ingress for IADM-08 Audit, Health &amp; Operations (gateway {@code /api/v1/iadm/08}).
 * <p>
 * Uses {@link CampXLogger} and {@link FlowTracker} per Agreement §3.2. Read-only audit and ops
 * probes; domain failures map to problem JSON and unexpected failures return a generic 500 message.
 * Never logs tokens, passwords, or JWTs.
 */
public class Iadm08Controller implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm08Controller.class);

    private final Iadm08DomainService service;
    private final GatewayHmacVerifier hmac = new GatewayHmacVerifier();

    public Iadm08Controller(Iadm08DomainService service) {
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
        LogContext.put(LogContext.KEY_SERVICE, "IADM-08");
        LogContext.put(LogContext.KEY_TRACE_ID, correlationId);
        FlowTracker tracker = logger.flow(method + " " + path, correlationId);
        try {
            if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            GatewayHmacVerifier.VerificationResult auth = hmac.verify(exchange, method, path, bodyBytes);
            if (!auth.isSuccess()) {
                logger.warn("IADM-08 HMAC/auth rejected path={} status={}", path, auth.getStatus());
                send(exchange, auth.getStatus(), problem(auth.getErrorCode(), auth.getMessage()));
                return;
            }
            LogContext.put(LogContext.KEY_USER_ID, auth.getUserId());
            LogContext.put(LogContext.KEY_TENANT_ID, auth.getTenantId());
            tracker.step("authenticated");

            // Platform admins often have no tenant claim; use platform scope for ops/search.
            boolean platformScoped = auth.getTenantId() == null || auth.getTenantId().trim().isEmpty();
            UserSecurityContext ctx = platformScoped
                    ? UserSecurityContext.fromPlatformHeaders(auth.getUserId(), auth.getTenantId())
                    : UserSecurityContext.fromHeaders(auth.getUserId(), auth.getTenantId());
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            String q = exchange.getRequestURI().getQuery();
            // More specific paths before bare /health (ops/health also endsWith /health)
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/audit/search")) {
                send(exchange, 200, toJson(service.searchAudit(ctx, qp(q, "actor"), qp(q, "action"))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/audit/export")) {
                send(exchange, 200, toJson(service.exportAudit(ctx, qp(q, "actor"), qp(q, "action"))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/audit/retention")) {
                send(exchange, 200, toJson(service.auditRetention(ctx)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/audit/retention")) {
                String days = extract(body, "retentionDays");
                if (days == null || days.isEmpty()) days = extract(body, "days");
                Integer retentionDays = null;
                if (days != null && !days.isEmpty()) {
                    try { retentionDays = Integer.parseInt(days.trim()); } catch (NumberFormatException ignored) {}
                }
                Boolean legalHold = null;
                String hold = extract(body, "legalHold");
                if (hold != null) legalHold = "true".equalsIgnoreCase(hold);
                send(exchange, 200, toJson(service.upsertAuditRetention(
                        UserSecurityContext.fromPlatformHeaders(auth.getUserId(), auth.getTenantId()),
                        extract(body, "entityType"),
                        retentionDays,
                        extract(body, "action"),
                        legalHold)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/ops/institute-status")) {
                send(exchange, 200, toJson(service.instituteStatus(ctx)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/ops/health")) {
                send(exchange, 200, toJson(service.health(UserSecurityContext.fromPlatformHeaders(auth.getUserId(), auth.getTenantId()))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/audit/self-check")) {
                send(exchange, 200, toJson(service.auditSelfCheck(UserSecurityContext.fromPlatformHeaders(auth.getUserId(), auth.getTenantId()))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/api/v1/iadm/08/health") || path.equals("/health"))) {
                send(exchange, 200, "{\"status\":\"UP\",\"service\":\"IADM-08\"}");
                return;
            }
            send(exchange, 404, problem("IADM08_NOT_FOUND", "Route not found: " + path));
        } catch (UserProfileAccessDeniedException e) {
            tracker.markFailed(e);
            logger.warn("IADM-08 access denied path={} message={}", path, e.getMessage());
            send(exchange, 403, problem("IADM08_FORBIDDEN", e.getMessage()));
        } catch (InstituteAdminException e) {
            tracker.markFailed(e);
            logger.warn("IADM-08 domain failure path={} code={} status={}", path, e.getErrorCode(), e.getStatus());
            send(exchange, e.getStatus(), problem(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            tracker.markFailed(e);
            logger.error("IADM-08 unhandled failure path=" + path + " correlationId=" + correlationId, e);
            send(exchange, 500, problem("IADM08_INTERNAL", "Request could not be completed"));
        } finally {
            tracker.close();
            LogContext.clear();
        }
    }

    private static String qp(String q, String name) {
        if (q == null) return null;
        for (String p : q.split("&")) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) return kv[1];
        }
        return null;
    }

    private static String extract(String json, String key) {
        if (json == null) return null;
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + needle.length());
        String rest = json.substring(colon + 1).trim();
        if (rest.startsWith("\"")) {
            int q2 = rest.indexOf('"', 1);
            return q2 < 0 ? null : rest.substring(1, q2);
        }
        int end = rest.length();
        for (int j = 0; j < rest.length(); j++) {
            char c = rest.charAt(j);
            if (c == ',' || c == '}' || c == ']') {
                end = j;
                break;
            }
        }
        String lit = rest.substring(0, end).trim();
        return "null".equalsIgnoreCase(lit) ? null : lit;
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