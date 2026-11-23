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
        LogContext.put(LogContext.KEY_SERVICE, "IADM-01");
        LogContext.put(LogContext.KEY_TRACE_ID, correlationId);

        byte[] bodyBytes = readAll(exchange.getRequestBody());
        FlowTracker tracker = logger.flow(method + " " + path, correlationId);
        try {
            // Health is unauthenticated so stack/check scripts can probe without HMAC.
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/health") || path.endsWith("/healthz"))) {
                sendJson(exchange, 200, "{\"status\":\"UP\",\"service\":\"IADM-01\"}");
                return;
            }

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
            boolean noTenant = auth.getTenantId() == null || auth.getTenantId().trim().isEmpty();
            boolean platformOp = (path.endsWith("/institutes") || path.endsWith("/institutes/"))
                    || path.endsWith("/institutes/summary")
                    || path.endsWith("/platform-defaults") || path.endsWith("/platform-defaults/")
                    || path.contains("/first-admin/onboarding")
                    || path.matches(".*/colleges/[^/]+/first-admin/onboarding/?$")
                    || path.contains("/onboarding")
                    || path.contains("/suspend") || path.contains("/reactivate") || path.contains("/archive")
                    || path.contains("/lifecycle-requests")
                    || path.endsWith("/decide") || path.endsWith("/decision")
                    || ("GET".equalsIgnoreCase(method) && path.matches(".*/institutes/[^/]+/?$"))
                    || ("PATCH".equalsIgnoreCase(method) && path.matches(".*/institutes/[^/]+/?$"))
                    || path.matches(".*/institutes/[^/]+/colleges/?$")
                    || path.matches(".*/colleges/[^/]+/?$")
                    || path.matches(".*/colleges/[^/]+/handover/?$")
                    || path.matches(".*/colleges/[^/]+/user-admin-boundary/?$")
                    || path.contains("/lifecycle-history")
                    || path.contains("/history");
            UserSecurityContext ctx = (platformOp && noTenant)
                    ? UserSecurityContext.fromPlatformHeaders(auth.getUserId(), auth.getTenantId())
                    : UserSecurityContext.fromHeaders(auth.getUserId(), auth.getTenantId());
            if ("GET".equalsIgnoreCase(method)
                    && (path.endsWith("/platform-defaults") || path.endsWith("/platform-defaults/"))) {
                sendJson(exchange, 200, toJson(service.getPlatformDefaults(ctx)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/institutes/summary")) {
                sendJson(exchange, 200, toJson(service.getSummary(ctx)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/institutes") || path.endsWith("/institutes/"))) {
                String q = exchange.getRequestURI().getQuery();
                String status = queryParam(q, "status");
                String search = queryParam(q, "search");
                sendJson(exchange, 200, toJson(service.listInstitutes(ctx, status, search)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.matches(".*/institutes/?$")) {
                // US-001: contact is the primary outreach field; accept legacy "region" alias.
                String contact = extract(body, "contact");
                if (contact == null || contact.trim().isEmpty()) {
                    contact = extract(body, "region");
                }
                Map<String, Object> r = service.registerInstitute(ctx,
                        extract(body, "code"), extract(body, "name"), contact,
                        extract(body, "type"), extract(body, "planReference"));
                sendJson(exchange, 201, toJson(r));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.matches(".*/institutes/[^/]+/onboarding/?$")) {
                UUID tenantId = uuidFromPath(path, "/institutes/", "/onboarding");
                sendJson(exchange, 200, toJson(service.getOnboarding(ctx, tenantId)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.matches(".*/institutes/[^/]+/first-admin/onboarding/?$")) {
                UUID tenantId = uuidFromPath(path, "/institutes/", "/first-admin/onboarding");
                boolean simulateFail = extractBoolean(body, "simulateInviteFailure");
                Map<String, Object> r = service.resumeFirstAdminOnboarding(ctx, tenantId,
                        extract(body, "email"), extract(body, "displayName"),
                        extract(body, "correlationId"), simulateFail);
                sendJson(exchange, 200, toJson(r));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.matches(".*/institutes/[^/]+/?$")) {
                UUID id = UUID.fromString(path.substring(path.lastIndexOf('/') + 1).replaceAll("/$", ""));
                sendJson(exchange, 200, toJson(service.getInstitute(ctx, id)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.matches(".*/institutes/[^/]+/colleges/?$")) {
                UUID tenantId = uuidFromPath(path, "/institutes/", "/colleges");
                String status = queryParam(exchange.getRequestURI().getQuery(), "status");
                sendJson(exchange, 200, toJson(service.listColleges(ctx, tenantId, status)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.matches(".*/institutes/[^/]+/colleges/?$")) {
                UUID instituteId = uuidFromPath(path, "/institutes/", "/colleges");
                String code = extract(body, "code");
                if (code == null || code.trim().isEmpty()) {
                    code = extract(body, "collegeCode");
                }
                String name = extract(body, "name");
                if (name == null || name.trim().isEmpty()) {
                    name = extract(body, "collegeName");
                }
                Map<String, Object> r = service.createCollege(ctx, instituteId, code, name,
                        extract(body, "campusCode"), extract(body, "location"));
                sendJson(exchange, 201, toJson(r));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.matches(".*/colleges/[^/]+/?$")
                    && !path.contains("/handover") && !path.contains("/user-admin-boundary")
                    && !path.contains("/first-admin")) {
                UUID collegeId = UUID.fromString(path.substring(path.lastIndexOf("/colleges/") + 10).replaceAll("/$", ""));
                sendJson(exchange, 200, toJson(service.getCollege(ctx, collegeId)));
                return;
            }
            if ("PATCH".equalsIgnoreCase(method) && path.matches(".*/colleges/[^/]+/?$")) {
                UUID collegeId = UUID.fromString(path.substring(path.lastIndexOf("/colleges/") + 10).replaceAll("/$", ""));
                Map<String, Object> r = service.updateCollege(ctx, collegeId, extract(body, "name"),
                        extract(body, "campusCode"), extract(body, "location"));
                sendJson(exchange, 200, toJson(r));
                return;
            }
            if ("PATCH".equalsIgnoreCase(method) && path.matches(".*/institutes/[^/]+/?$")) {
                UUID instituteId = UUID.fromString(path.substring(path.lastIndexOf('/') + 1).replaceAll("/$", ""));
                Map<String, Object> r = service.updateInstitute(ctx, instituteId, extract(body, "name"),
                        extract(body, "contact"), extract(body, "location"));
                sendJson(exchange, 200, toJson(r));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.matches(".*/colleges/[^/]+/first-admin/onboarding/?$")) {
                UUID collegeId = uuidFromPath(path, "/colleges/", "/first-admin/onboarding");
                sendJson(exchange, 200, toJson(service.getCollegeOnboarding(ctx, collegeId)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.matches(".*/colleges/[^/]+/first-admin/onboarding/?$")) {
                UUID collegeId = uuidFromPath(path, "/colleges/", "/first-admin/onboarding");
                boolean simulateFail = extractBoolean(body, "simulateInviteFailure");
                if (!simulateFail) {
                    simulateFail = extractBoolean(body, "simulateFail");
                }
                Map<String, Object> r = service.resumeFirstCollegeAdminOnboarding(ctx, collegeId,
                        extract(body, "email"), extract(body, "displayName"),
                        extract(body, "correlationId"), simulateFail);
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
            if ("GET".equalsIgnoreCase(method) && path.matches(".*/colleges/[^/]+/user-admin-boundary/?$")) {
                UUID collegeId = uuidFromPath(path, "/colleges/", "/user-admin-boundary");
                sendJson(exchange, 200, toJson(service.collegeUserAdminBoundary(ctx, collegeId)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.matches(".*/institutes/[^/]+/(suspend|reactivate|archive)/?$")) {
                String action = path.contains("/archive") ? "archive"
                        : (path.contains("/suspend") ? "suspend" : "reactivate");
                UUID tenantId = uuidFromPath(path, "/institutes/", "/" + action);
                sendJson(exchange, 200, toJson(service.suspendOrReactivate(ctx, tenantId, action, extract(body, "reason"))));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.matches(".*/institutes/[^/]+/lifecycle-requests/?$")) {
                UUID instituteId = uuidFromPath(path, "/institutes/", "/lifecycle-requests");
                String college = extract(body, "collegeId");
                UUID collegeId = (college == null || college.trim().isEmpty()) ? null : UUID.fromString(college.trim());
                sendJson(exchange, 201, toJson(service.createLifecycleRequest(ctx, instituteId,
                        extract(body, "requestType"), extract(body, "reason"), collegeId)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.matches(".*/colleges/[^/]+/lifecycle-requests/?$")) {
                UUID collegeId = uuidFromPath(path, "/colleges/", "/lifecycle-requests");
                Map<String, Object> college = service.getCollege(ctx, collegeId);
                UUID instituteId = UUID.fromString(college.get("instituteId").toString());
                sendJson(exchange, 201, toJson(service.createLifecycleRequest(ctx, instituteId,
                        extract(body, "requestType"), extract(body, "reason"), collegeId)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.matches(".*/institutes/[^/]+/lifecycle-requests/?$")) {
                UUID instituteId = uuidFromPath(path, "/institutes/", "/lifecycle-requests");
                String status = queryParam(exchange.getRequestURI().getQuery(), "status");
                sendJson(exchange, 200, toJson(service.listLifecycleRequests(ctx, instituteId, status)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/lifecycle-requests")) {
                String status = queryParam(exchange.getRequestURI().getQuery(), "status");
                sendJson(exchange, 200, toJson(service.listLifecycleRequests(ctx, null, status)));
                return;
            }
            if ("POST".equalsIgnoreCase(method)
                    && (path.matches(".*/lifecycle-requests/[^/]+/decide/?$")
                    || path.matches(".*/lifecycle-requests/[^/]+/decision/?$"))) {
                String end = path.contains("/decision") ? "/decision" : "/decide";
                UUID requestId = uuidFromPath(path, "/lifecycle-requests/", end);
                String note = extract(body, "note");
                if (note == null || note.trim().isEmpty()) {
                    note = extract(body, "reason");
                }
                sendJson(exchange, 200, toJson(service.decideLifecycleRequest(ctx, requestId,
                        extract(body, "decision"), note)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && (path.matches(".*/institutes/[^/]+/lifecycle-history/?$")
                    || path.matches(".*/institutes/[^/]+/history/?$"))) {
                String end = path.contains("/lifecycle-history") ? "/lifecycle-history" : "/history";
                UUID tenantId = uuidFromPath(path, "/institutes/", end);
                Map<String, Object> hist = service.lifecycleHistoryBundle(ctx, tenantId);
                sendJson(exchange, 200, toJson(hist));
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

    private static String queryParam(String query, String key) {
        if (query == null || key == null) return null;
        String[] parts = query.split("&");
        for (String part : parts) {
            int eq = part.indexOf('=');
            if (eq > 0 && key.equals(part.substring(0, eq))) {
                return part.substring(eq + 1);
            }
        }
        return null;
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

    /** Parses JSON boolean (unquoted true/false) or quoted "true"/"false". */
    private static boolean extractBoolean(String json, String key) {
        if (json == null) return false;
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return false;
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) return false;
        String rest = json.substring(colon + 1).trim();
        if (rest.startsWith("\"")) {
            return "true".equalsIgnoreCase(extract(json, key));
        }
        int end = 0;
        while (end < rest.length() && Character.isLetter(rest.charAt(end))) {
            end++;
        }
        return "true".equalsIgnoreCase(rest.substring(0, end));
    }

    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            sb.append(toJsonValue(e.getValue()));
        }
        return sb.append('}').toString();
    }

    @SuppressWarnings("unchecked")
    private static String toJsonValue(Object v) {
        if (v == null) return "null";
        if (v instanceof Number || v instanceof Boolean) return String.valueOf(v);
        if (v instanceof Map) return toJson((Map<String, Object>) v);
        if (v instanceof List) {
            List<?> list = (List<?>) v;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
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