package com.campx.iadm.identity.controller;

import com.campx.iadm.identity.exception.InstituteAdminException;
import com.campx.iadm.identity.exception.UserProfileAccessDeniedException;
import com.campx.iadm.identity.security.GatewayHmacVerifier;
import com.campx.iadm.identity.security.UserSecurityContext;
import com.campx.iadm.identity.service.Iadm03DomainService;
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
 * HTTP ingress for IADM-03 identity and access routes.
 * <p>
 * Uses {@link CampXLogger} for flow tracing and safe error reporting (Agreement &#167;3.2).
 * Authentication tokens are never logged.
 */
public class Iadm03Controller implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm03Controller.class);

    private final Iadm03DomainService service;
    private final GatewayHmacVerifier hmac = new GatewayHmacVerifier();

    public Iadm03Controller(Iadm03DomainService service) {
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
        LogContext.put(LogContext.KEY_SERVICE, "IADM-03");
        LogContext.put(LogContext.KEY_TRACE_ID, correlationId);

        byte[] bodyBytes = readAll(exchange.getRequestBody());
        FlowTracker tracker = logger.flow(method + " " + path, correlationId);
        try {
            GatewayHmacVerifier.VerificationResult auth = hmac.verify(exchange, method, path, bodyBytes);
            if (!auth.isSuccess()) {
                logger.warn("IADM-03 auth failed path={} code={} correlationId={}", path, auth.getErrorCode(), correlationId);
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
            boolean quorumOp = path.contains("/institution-admin-quorum");
            UserSecurityContext ctx;
            if (quorumOp) {
                ctx = UserSecurityContext.fromPlatformHeaders(auth.getUserId(), auth.getTenantId());
            } else {
                ctx = UserSecurityContext.fromHeaders(auth.getUserId(), auth.getTenantId());
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/health")) {
                send(exchange, 200, "{\"status\":\"UP\",\"service\":\"IADM-03\"}");
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/institution-admin-quorum-requests")) {
                String tenant = extract(body, "tenantId");
                if (tenant == null || tenant.isEmpty()) {
                    String q = exchange.getRequestURI().getQuery();
                    if (q != null && q.contains("tenantId=")) {
                        int i = q.indexOf("tenantId=") + 9;
                        int end = q.indexOf('&', i);
                        tenant = end < 0 ? q.substring(i) : q.substring(i, end);
                    }
                }
                UUID tenantId = (tenant == null || tenant.isEmpty()) ? null : UUID.fromString(tenant);
                send(exchange, 200, toJson(service.listInstitutionAdminQuorum(ctx, tenantId)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/institution-admin-quorum-requests")) {
                send(exchange, 201, toJson(service.requestInstitutionAdminQuorum(ctx,
                        UUID.fromString(extract(body, "tenantId")),
                        UUID.fromString(extract(body, "targetUserId")),
                        extract(body, "action"),
                        extract(body, "reason"))));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.contains("/institution-admin-quorum-requests/") && path.endsWith("/decide")) {
                String id = path.substring(path.indexOf("/institution-admin-quorum-requests/") + 35, path.indexOf("/decide"));
                send(exchange, 200, toJson(service.decideInstitutionAdminQuorum(ctx,
                        UUID.fromString(id.replace("/", "")),
                        extract(body, "decision"),
                        extract(body, "note"))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/roles")) {
                send(exchange, 200, toJson(service.listRoles(ctx)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/ceilings")) {
                send(exchange, 201, toJson(service.setCeiling(ctx, extract(body, "roleCode"), extract(body, "allowedPermissionKeys"))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.contains("/ceilings/")) {
                String code = path.substring(path.lastIndexOf('/') + 1);
                send(exchange, 200, toJson(service.getCeiling(ctx, code)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/access-requests")) {
                send(exchange, 201, toJson(service.createAccessRequest(ctx, extract(body, "requestType"),
                        extract(body, "reason"), extractRaw(body, "payload"))));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.contains("/access-requests/") && path.endsWith("/decide")) {
                String id = path.substring(path.indexOf("/access-requests/") + 17, path.indexOf("/decide"));
                send(exchange, 200, toJson(service.decideAccessRequest(ctx, UUID.fromString(id.replace("/", "")), extract(body, "decision"), extract(body, "note"))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/role-assignments")) {
                send(exchange, 200, toJson(service.listRoleAssignments(ctx)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/role-assignments")) {
                String college = extract(body, "collegeId");
                UUID collegeId = (college == null || college.isEmpty()) ? null : UUID.fromString(college);
                send(exchange, 201, toJson(service.assignRole(ctx,
                        UUID.fromString(extract(body, "userId")), extract(body, "roleCode"), collegeId)));
                return;
            }
            if (("DELETE".equalsIgnoreCase(method) && path.matches(".*/role-assignments/[^/]+/?$"))
                    || ("POST".equalsIgnoreCase(method) && path.matches(".*/role-assignments/[^/]+/revoke/?$"))) {
                String idPart = path.contains("/revoke")
                        ? path.substring(path.indexOf("/role-assignments/") + 18, path.indexOf("/revoke"))
                        : path.substring(path.lastIndexOf('/') + 1);
                send(exchange, 200, toJson(service.revokeRoleAssignment(ctx, UUID.fromString(idPart.replace("/", "")))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/delegations")) {
                send(exchange, 200, toJson(service.listDelegations(ctx)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/delegations")) {
                send(exchange, 201, toJson(service.createDelegation(ctx,
                        UUID.fromString(extract(body, "toUserId")),
                        extract(body, "roleCode"),
                        extract(body, "validFrom"),
                        extract(body, "validTo"),
                        extract(body, "reason"))));
                return;
            }
            if (("DELETE".equalsIgnoreCase(method) && path.matches(".*/delegations/[^/]+/?$"))
                    || ("POST".equalsIgnoreCase(method) && path.matches(".*/delegations/[^/]+/revoke/?$"))) {
                String idPart = path.contains("/revoke")
                        ? path.substring(path.indexOf("/delegations/") + 13, path.indexOf("/revoke"))
                        : path.substring(path.lastIndexOf('/') + 1);
                send(exchange, 200, toJson(service.revokeDelegation(ctx, UUID.fromString(idPart.replace("/", "")))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/access-reviews")) {
                send(exchange, 200, toJson(service.listAccessReviews(ctx)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.matches(".*/access-reviews/[^/]+/certify/?$")) {
                String id = path.substring(path.indexOf("/access-reviews/") + 16, path.indexOf("/certify"));
                send(exchange, 200, toJson(service.certifyAccessReview(ctx, UUID.fromString(id.replace("/", "")), body)));
                return;
            }
            send(exchange, 404, problem("IADM03_NOT_FOUND", "Route not found: " + path));
        } catch (UserProfileAccessDeniedException e) {
            tracker.markFailed(e);
            logger.warn("IADM-03 forbidden correlationId=" + correlationId);
            send(exchange, 403, problem("IADM03_FORBIDDEN", e.getMessage()));
        } catch (InstituteAdminException e) {
            tracker.markFailed(e);
            logger.warn("IADM-03 domain rejection code={} correlationId={}", e.getErrorCode(), correlationId);
            send(exchange, e.getStatus(), problem(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            tracker.markFailed(e);
            logger.error("IADM-03 request failed correlationId=" + correlationId, e);
            send(exchange, 500, problem("IADM03_INTERNAL", "Request could not be completed"));
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
            char open = rest.charAt(0);
            char close = open == '{' ? '}' : ']';
            int depth = 0;
            for (int j = 0; j < rest.length(); j++) {
                char c = rest.charAt(j);
                if (c == open) depth++;
                else if (c == close) {
                    depth--;
                    if (depth == 0) return rest.substring(0, j + 1);
                }
            }
        }
        int end = rest.indexOf(',');
        if (end < 0) end = rest.indexOf('}');
        return rest.substring(0, end).trim();
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