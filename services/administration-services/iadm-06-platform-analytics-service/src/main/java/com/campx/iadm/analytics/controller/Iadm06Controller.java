package com.campx.iadm.analytics.controller;

import com.campx.iadm.analytics.exception.InstituteAdminException;
import com.campx.iadm.analytics.exception.UserProfileAccessDeniedException;
import com.campx.iadm.analytics.security.GatewayHmacVerifier;
import com.campx.iadm.analytics.security.UserSecurityContext;
import com.campx.iadm.analytics.service.Iadm06DomainService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * IADM-06 Platform Analytics & Usage. Port 8104 · Gateway /api/v1/iadm/06
 */
public class Iadm06Controller implements HttpHandler {
    private final Iadm06DomainService service;
    private final GatewayHmacVerifier hmac = new GatewayHmacVerifier();

    public Iadm06Controller(Iadm06DomainService service) { this.service = service; }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        byte[] bodyBytes = readAll(exchange.getRequestBody());
        try {
            if ("OPTIONS".equalsIgnoreCase(method)) { exchange.sendResponseHeaders(204, -1); return; }
            GatewayHmacVerifier.VerificationResult auth = hmac.verify(exchange, method, path, bodyBytes);
            if (!auth.isSuccess()) { send(exchange, auth.getStatus(), problem(auth.getErrorCode(), auth.getMessage())); return; }
            UserSecurityContext ctx = UserSecurityContext.fromHeaders(auth.getUserId(),
                    auth.getTenantId() == null || auth.getTenantId().isEmpty()
                            ? "a0000000-0000-0000-0000-000000000001" : auth.getTenantId());
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/health")) {
                send(exchange, 200, toJson(service.health(ctx))); return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/usage")) {
                send(exchange, 200, toJson(service.usageSummary(ctx))); return;
            }
            send(exchange, 404, problem("IADM06_NOT_FOUND", "Route not found: " + path));
        } catch (UserProfileAccessDeniedException e) {
            send(exchange, 403, problem("IADM06_FORBIDDEN", e.getMessage()));
        } catch (InstituteAdminException e) {
            send(exchange, e.getStatus(), problem(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            send(exchange, 500, problem("IADM06_INTERNAL", e.getMessage()));
        }
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
