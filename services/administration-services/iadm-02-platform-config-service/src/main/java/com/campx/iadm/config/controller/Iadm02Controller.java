package com.campx.iadm.config.controller;

import com.campx.iadm.config.exception.InstituteAdminException;
import com.campx.iadm.config.exception.UserProfileAccessDeniedException;
import com.campx.iadm.config.security.GatewayHmacVerifier;
import com.campx.iadm.config.security.UserSecurityContext;
import com.campx.iadm.config.service.ConfigDomainService;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP ingress for IADM-02 (port 8100) under /api/v1/iadm/02.
 */
public class Iadm02Controller implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm02Controller.class);
    private static final Pattern KEY_DRAFT = Pattern.compile(".*/configurations/([^/]+)/drafts/?$");
    private static final Pattern KEY_DETAIL = Pattern.compile(".*/configurations/([^/]+)/?$");
    private static final Pattern DRAFT_ID = Pattern.compile(".*/drafts/([^/]+)/?$");
    private static final Pattern DRAFT_VALIDATE = Pattern.compile(".*/drafts/([^/]+)/validate/?$");
    private static final Pattern DRAFT_SUBMIT = Pattern.compile(".*/drafts/([^/]+)/submit/?$");
    private static final Pattern DRAFT_DECIDE = Pattern.compile(".*/drafts/([^/]+)/decide/?$");
    private static final Pattern DRAFT_PUBLISH = Pattern.compile(".*/drafts/([^/]+)/publish/?$");
    private static final Pattern VERSION_COMPARE = Pattern.compile(".*/versions/([^/]+)/compare/([^/]+)/?$");
    private static final Pattern POLICY_DRAFT = Pattern.compile(".*/policies/([^/]+)/drafts/?$");

    private final ConfigDomainService service;
    private final GatewayHmacVerifier hmacVerifier = new GatewayHmacVerifier();

    public Iadm02Controller(ConfigDomainService service) {
        this.service = service;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,PATCH,OPTIONS");
            // S2 adds submit/decide/publish POSTs under /drafts/{id}/…
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
        LogContext.put(LogContext.KEY_SERVICE, "IADM-02");
        LogContext.put(LogContext.KEY_TRACE_ID, correlationId);

        byte[] bodyBytes = readAll(exchange.getRequestBody());
        FlowTracker tracker = logger.flow(method + " " + path, correlationId);
        try {
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/health") || path.endsWith("/healthz"))) {
                sendJson(exchange, 200, "{\"status\":\"UP\",\"service\":\"IADM-02\"}");
                return;
            }

            GatewayHmacVerifier.VerificationResult auth = hmacVerifier.verify(exchange, method, path, bodyBytes);
            if (!auth.isSuccess()) {
                logger.warn("IADM-02 auth failed path={} code={} correlationId={}", path, auth.getErrorCode(), correlationId);
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
            // Platform configuration is platform-scoped; allow missing tenant.
            UserSecurityContext ctx = UserSecurityContext.fromPlatformHeaders(auth.getUserId(), auth.getTenantId());

            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/configurations") || path.endsWith("/configurations/")
                    || path.endsWith("/catalogue") || path.endsWith("/catalogue/")
                    || path.endsWith("/definitions") || path.endsWith("/definitions/"))) {
                sendJson(exchange, 200, "{\"items\":" + toJsonArray(service.listCatalogue(ctx)) + "}");
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/dashboard")) {
                sendJson(exchange, 200, toJson(service.getDashboard(ctx)));
                return;
            }
            Matcher keyDraft = KEY_DRAFT.matcher(path);
            if ("POST".equalsIgnoreCase(method) && keyDraft.matches()) {
                String key = urlDecode(keyDraft.group(1));
                Map<String, Object> r = service.createDraft(ctx, key,
                        extract(body, "proposedValue"),
                        extract(body, "reason"),
                        firstNonEmpty(extract(body, "idempotencyKey"), header(exchange, "Idempotency-Key")),
                        firstNonEmpty(extract(body, "correlationId"), correlationId));
                sendJson(exchange, 201, toJson(r));
                return;
            }
            Matcher keyDetail = KEY_DETAIL.matcher(path);
            if ("GET".equalsIgnoreCase(method) && keyDetail.matches()
                    && !path.endsWith("/configurations") && !path.contains("/drafts")) {
                String key = urlDecode(keyDetail.group(1));
                if (!"dashboard".equals(key) && !"policies".equals(key)) {
                    sendJson(exchange, 200, toJson(service.getCatalogueEntry(ctx, key)));
                    return;
                }
            }
            Matcher validate = DRAFT_VALIDATE.matcher(path);
            if ("POST".equalsIgnoreCase(method) && validate.matches()) {
                UUID draftId = UUID.fromString(validate.group(1));
                sendJson(exchange, 200, toJson(service.validateDraft(ctx, draftId)));
                return;
            }
            Matcher submit = DRAFT_SUBMIT.matcher(path);
            if ("POST".equalsIgnoreCase(method) && submit.matches()) {
                UUID draftId = UUID.fromString(submit.group(1));
                sendJson(exchange, 200, toJson(service.submitDraft(ctx, draftId)));
                return;
            }
            Matcher decide = DRAFT_DECIDE.matcher(path);
            if ("POST".equalsIgnoreCase(method) && decide.matches()) {
                UUID draftId = UUID.fromString(decide.group(1));
                sendJson(exchange, 200, toJson(service.decideDraft(ctx, draftId,
                        extract(body, "decision"), extract(body, "reason"))));
                return;
            }
            Matcher publish = DRAFT_PUBLISH.matcher(path);
            if ("POST".equalsIgnoreCase(method) && publish.matches()) {
                UUID draftId = UUID.fromString(publish.group(1));
                sendJson(exchange, 200, toJson(service.publishDraft(ctx, draftId,
                        extract(body, "releaseNotes"),
                        firstNonEmpty(extract(body, "idempotencyKey"), header(exchange, "Idempotency-Key")))));
                return;
            }
            Matcher compare = VERSION_COMPARE.matcher(path);
            if ("GET".equalsIgnoreCase(method) && compare.matches()) {
                sendJson(exchange, 200, toJson(service.compareVersions(ctx,
                        UUID.fromString(compare.group(1)), UUID.fromString(compare.group(2)))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/versions") || path.endsWith("/versions/"))) {
                sendJson(exchange, 200, "{\"items\":" + toJsonArray(service.listVersions(ctx)) + "}");
                return;
            }
            if ("GET".equalsIgnoreCase(method)
                    && (path.endsWith("/consumer/effective-config") || path.endsWith("/consumer/effective-config/"))) {
                String domains = null;
                String query = exchange.getRequestURI().getRawQuery();
                if (query != null) {
                    for (String part : query.split("&")) {
                        if (part.startsWith("domains=")) {
                            domains = java.net.URLDecoder.decode(part.substring("domains=".length()), "UTF-8");
                        }
                    }
                }
                sendJson(exchange, 200, toJson(service.consumerEffectiveConfig(ctx, domains)));
                return;
            }
            if ("GET".equalsIgnoreCase(method)
                    && (path.endsWith("/consumer/diagnostics") || path.endsWith("/consumer/diagnostics/"))) {
                sendJson(exchange, 200, "{\"items\":" + toJsonArray(service.listConsumerDiagnostics(ctx)) + "}");
                return;
            }
            Matcher draftPatch = DRAFT_ID.matcher(path);
            if ("PATCH".equalsIgnoreCase(method) && draftPatch.matches() && !path.contains("/validate")) {
                UUID draftId = UUID.fromString(draftPatch.group(1));
                Map<String, Object> r = service.updateDraft(ctx, draftId,
                        extract(body, "proposedValue"), extract(body, "reason"));
                sendJson(exchange, 200, toJson(r));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && draftPatch.matches() && !path.contains("/validate")) {
                UUID draftId = UUID.fromString(draftPatch.group(1));
                sendJson(exchange, 200, toJson(service.getDraft(ctx, draftId)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/drafts") || path.endsWith("/drafts/"))) {
                sendJson(exchange, 200, "{\"items\":" + toJsonArray(service.listDrafts(ctx)) + "}");
                return;
            }
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/policies") || path.endsWith("/policies/"))) {
                sendJson(exchange, 200, "{\"items\":" + toJsonArray(service.listPolicies(ctx)) + "}");
                return;
            }
            Matcher polDraft = POLICY_DRAFT.matcher(path);
            if ("POST".equalsIgnoreCase(method) && polDraft.matches()) {
                String code = urlDecode(polDraft.group(1));
                Map<String, Object> r = service.createPolicyDraft(ctx, code,
                        extract(body, "proposedValue"),
                        extract(body, "reason"),
                        firstNonEmpty(extract(body, "idempotencyKey"), header(exchange, "Idempotency-Key")),
                        firstNonEmpty(extract(body, "correlationId"), correlationId));
                sendJson(exchange, 201, toJson(r));
                return;
            }

            sendJson(exchange, 404, problem("IADM02_NOT_FOUND", "No route for " + method + " " + path));
        } catch (UserProfileAccessDeniedException e) {
            tracker.markFailed(e);
            logger.warn("IADM-02 access denied: {}", e.getMessage());
            sendJson(exchange, e.getStatus(), problem(e.getErrorCode(), e.getMessage()));
        } catch (InstituteAdminException e) {
            tracker.markFailed(e);
            logger.warn("IADM-02 domain error code={} status={}", e.getErrorCode(), e.getStatus());
            sendJson(exchange, e.getStatus(), problem(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            tracker.markFailed(e);
            logger.error("IADM-02 unexpected error: {}", e.getMessage(), e);
            sendJson(exchange, 500, problem("IADM02_INTERNAL", "An unexpected error occurred"));
        } finally {
            tracker.close();
            LogContext.clear();
        }
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a;
        }
        return b;
    }

    private static String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static String extract(String json, String field) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        String pattern = "\"" + field + "\"\\s*:\\s*\"";
        int i = json.indexOf("\"" + field + "\"");
        if (i < 0) {
            // number / boolean
            Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*([^,}\\]]+)");
            Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1).trim().replaceAll("^\"|\"$", "");
            }
            return null;
        }
        int colon = json.indexOf(':', i);
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
            int end = start + 1;
            StringBuilder sb = new StringBuilder();
            while (end < json.length()) {
                char c = json.charAt(end);
                if (c == '\\' && end + 1 < json.length()) {
                    sb.append(json.charAt(end + 1));
                    end += 2;
                    continue;
                }
                if (c == '"') {
                    break;
                }
                sb.append(c);
                end++;
            }
            return sb.toString();
        }
        int end = start;
        while (end < json.length() && ",}]".indexOf(json.charAt(end)) < 0) {
            end++;
        }
        return json.substring(start, end).trim();
    }

    private static String header(HttpExchange exchange, String name) {
        String value = exchange.getRequestHeaders().getFirst(name);
        if (value == null) {
            value = exchange.getRequestHeaders().getFirst(name.toLowerCase());
        }
        return value;
    }

    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            sb.append(toJsonValue(e.getValue()));
        }
        return sb.append('}').toString();
    }

    @SuppressWarnings("unchecked")
    private static String toJsonValue(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Number || v instanceof Boolean) {
            return String.valueOf(v);
        }
        if (v instanceof Map) {
            return toJson((Map<String, Object>) v);
        }
        if (v instanceof List) {
            List<?> list = (List<?>) v;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                Object item = list.get(i);
                if (item instanceof Map) {
                    sb.append(toJson((Map<String, Object>) item));
                } else {
                    sb.append(toJsonValue(item));
                }
            }
            return sb.append(']').toString();
        }
        return "\"" + String.valueOf(v).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String toJsonArray(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(toJson(rows.get(i)));
        }
        return sb.append(']').toString();
    }

    private static String problem(String code, String detail) {
        return "{\"type\":\"about:blank\",\"title\":\"Error\",\"status\":null,\"detail\":\""
                + (detail == null ? "" : detail.replace("\"", "\\\""))
                + "\",\"errorCode\":\"" + code + "\"}";
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
        while ((n = in.read(buf)) >= 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }
}
