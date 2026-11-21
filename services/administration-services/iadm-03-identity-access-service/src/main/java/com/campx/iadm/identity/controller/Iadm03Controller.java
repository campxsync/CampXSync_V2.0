package com.campx.iadm.identity.controller;

import com.campx.iadm.identity.exception.InstituteAdminException;
import com.campx.iadm.identity.exception.UserProfileAccessDeniedException;
import com.campx.iadm.identity.security.GatewayHmacVerifier;
import com.campx.iadm.identity.security.UserSecurityContext;
import com.campx.iadm.identity.service.Iadm03DomainService;
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

public class Iadm03Controller implements HttpHandler {
    private final Iadm03DomainService service;
    private final GatewayHmacVerifier hmac = new GatewayHmacVerifier();

    public Iadm03Controller(Iadm03DomainService service) { this.service = service; }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        byte[] bodyBytes = readAll(exchange.getRequestBody());
        try {
            if ("OPTIONS".equalsIgnoreCase(method)) { exchange.sendResponseHeaders(204, -1); return; }
            GatewayHmacVerifier.VerificationResult auth = hmac.verify(exchange, method, path, bodyBytes);
            if (!auth.isSuccess()) { send(exchange, auth.getStatus(), problem(auth.getErrorCode(), auth.getMessage())); return; }
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            UserSecurityContext ctx = UserSecurityContext.fromHeaders(auth.getUserId(), auth.getTenantId());
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/health")) {
                send(exchange, 200, "{\"status\":\"UP\",\"service\":\"IADM-03\"}"); return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/roles")) {
                send(exchange, 200, toJson(service.listRoles(ctx))); return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/ceilings")) {
                send(exchange, 201, toJson(service.setCeiling(ctx, extract(body, "roleCode"), extract(body, "allowedPermissionKeys")))); return;
            }
            if ("GET".equalsIgnoreCase(method) && path.contains("/ceilings/")) {
                String code = path.substring(path.lastIndexOf('/') + 1);
                send(exchange, 200, toJson(service.getCeiling(ctx, code))); return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/access-requests")) {
                send(exchange, 201, toJson(service.createAccessRequest(ctx, extract(body, "requestType"), extract(body, "reason")))); return;
            }
            if ("POST".equalsIgnoreCase(method) && path.contains("/access-requests/") && path.endsWith("/decide")) {
                String id = path.substring(path.indexOf("/access-requests/") + 17, path.indexOf("/decide"));
                send(exchange, 200, toJson(service.decideAccessRequest(ctx, UUID.fromString(id.replace("/", "")), extract(body, "decision"), extract(body, "note")))); return;
            }
            send(exchange, 404, problem("IADM03_NOT_FOUND", "Route not found: " + path));
        } catch (UserProfileAccessDeniedException e) {
            send(exchange, 403, problem("IADM03_FORBIDDEN", e.getMessage()));
        } catch (InstituteAdminException e) {
            send(exchange, e.getStatus(), problem(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            send(exchange, 500, problem("IADM03_INTERNAL", e.getMessage()));
        }
    }

    private static String extract(String json, String key) {
        if (json == null) return null;
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle); if (i < 0) return null;
        int colon = json.indexOf(':', i + needle.length());
        int q1 = json.indexOf('"', colon + 1); if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        return json.substring(q1 + 1, q2);
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
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096]; int n;
        while ((n = in.read(buf)) >= 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
