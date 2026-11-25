package com.campx.iadm.governance.controller;

import com.campx.iadm.governance.exception.InstituteAdminException;
import com.campx.iadm.governance.exception.UserProfileAccessDeniedException;
import com.campx.iadm.governance.security.GatewayHmacVerifier;
import com.campx.iadm.governance.security.UserSecurityContext;
import com.campx.iadm.governance.service.GovernanceDomainService;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP ingress for IADM-05 (port 8103) under /api/v1/iadm/05.
 * Plain language: policy governance API only — never deletes or exports domain data.
 */
public class Iadm05Controller implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm05Controller.class);
    private static final Pattern POLICY_ID = Pattern.compile(".*/policies/([0-9a-fA-F-]{36})$");
    private static final Pattern POLICY_SUBMIT = Pattern.compile(".*/policies/([0-9a-fA-F-]{36})/submit-review$");
    private static final Pattern POLICY_DECIDE = Pattern.compile(".*/policies/([0-9a-fA-F-]{36})/decide$");
    private static final Pattern POLICY_PUBLISH = Pattern.compile(".*/policies/([0-9a-fA-F-]{36})/publish$");
    private static final Pattern POLICY_HISTORY = Pattern.compile(".*/policies/([0-9a-fA-F-]{36})/history$");
    private static final Pattern REQUEST_ID = Pattern.compile(".*/requests/([0-9a-fA-F-]{36})$");
    private static final Pattern REQUEST_DECIDE = Pattern.compile(".*/requests/([0-9a-fA-F-]{36})/decide$");
    private static final Pattern REQUEST_DISPATCH = Pattern.compile(".*/requests/([0-9a-fA-F-]{36})/dispatch$");
    private static final Pattern REQUEST_CLOSE = Pattern.compile(".*/requests/([0-9a-fA-F-]{36})/close$");
    private static final Pattern REQUEST_OUTCOME = Pattern.compile(".*/requests/([0-9a-fA-F-]{36})/outcomes$");
    private static final Pattern SHARING_LIFE = Pattern.compile(".*/sharing/([0-9a-fA-F-]{36})/(suspend|revoke|expire)$");

    private final GovernanceDomainService service;
    private final GatewayHmacVerifier hmacVerifier = new GatewayHmacVerifier();

    public Iadm05Controller(GovernanceDomainService service) {
        this.service = service == null ? new GovernanceDomainService() : service;
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
        LogContext.put(LogContext.KEY_SERVICE, "IADM-05");
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
                logger.warn("IADM-05 auth failed path={} code={} correlationId={}",
                        path, auth.getErrorCode(), correlationId);
                sendJson(exchange, auth.getStatus(), problem(auth.getErrorCode(), auth.getMessage()));
                return;
            }
            tracker.step("authenticated");
            UserSecurityContext ctx = UserSecurityContext.fromPlatformHeaders(auth.getUserId(), auth.getTenantId());
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            Map<String, Object> bodyMap = parseObject(body);

            if ("GET".equalsIgnoreCase(method) && path.endsWith("/classifications")) {
                sendJson(exchange, 200, toJson(service.listClassifications(ctx)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/classifications")) {
                sendJson(exchange, 200, toJson(service.lifecycle().proposeClassification(ctx, bodyMap)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/retention-rules")) {
                sendJson(exchange, 200, toJson(service.listRetention(ctx)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/retention-rules")) {
                sendJson(exchange, 200, toJson(service.lifecycle().proposeRetention(ctx, bodyMap)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/privacy-policies")) {
                sendJson(exchange, 200, toJson(service.lifecycle().proposePrivacy(ctx, bodyMap)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/policies")) {
                sendJson(exchange, 200, toJson(service.lifecycle().listPolicies(ctx,
                        queryParam(exchange, "type"), queryParam(exchange, "status"))));
                return;
            }

            Matcher submit = POLICY_SUBMIT.matcher(path);
            if ("POST".equalsIgnoreCase(method) && submit.matches()) {
                sendJson(exchange, 200, toJson(service.lifecycle().submitForReview(ctx,
                        UUID.fromString(submit.group(1)))));
                return;
            }
            Matcher decide = POLICY_DECIDE.matcher(path);
            if ("POST".equalsIgnoreCase(method) && decide.matches()) {
                sendJson(exchange, 200, toJson(service.lifecycle().decide(ctx,
                        UUID.fromString(decide.group(1)),
                        extract(body, "decision"), extract(body, "rationaleRef"))));
                return;
            }
            Matcher publish = POLICY_PUBLISH.matcher(path);
            if ("POST".equalsIgnoreCase(method) && publish.matches()) {
                sendJson(exchange, 200, toJson(service.lifecycle().publish(ctx,
                        UUID.fromString(publish.group(1)), extract(body, "effectiveFrom"))));
                return;
            }
            Matcher history = POLICY_HISTORY.matcher(path);
            if ("GET".equalsIgnoreCase(method) && history.matches()) {
                sendJson(exchange, 200, toJson(service.lifecycle().history(ctx,
                        UUID.fromString(history.group(1)))));
                return;
            }

            if ("GET".equalsIgnoreCase(method) && path.endsWith("/requests")) {
                sendJson(exchange, 200, toJson(service.requests().listRequests(ctx,
                        queryParam(exchange, "type"), queryParam(exchange, "status"))));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/exports")) {
                sendJson(exchange, 200, toJson(service.requests().submitExport(ctx, bodyMap)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/erasures")) {
                sendJson(exchange, 200, toJson(service.requests().submitErasure(ctx, bodyMap)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/sharing")) {
                sendJson(exchange, 200, toJson(service.requests().submitSharing(ctx, bodyMap)));
                return;
            }
            Matcher reqDecide = REQUEST_DECIDE.matcher(path);
            if ("POST".equalsIgnoreCase(method) && reqDecide.matches()) {
                sendJson(exchange, 200, toJson(service.requests().decideRequest(ctx,
                        UUID.fromString(reqDecide.group(1)),
                        extract(body, "decision"), extract(body, "rationaleRef"))));
                return;
            }
            Matcher reqDispatch = REQUEST_DISPATCH.matcher(path);
            if ("POST".equalsIgnoreCase(method) && reqDispatch.matches()) {
                sendJson(exchange, 200, toJson(service.requests().dispatch(ctx,
                        UUID.fromString(reqDispatch.group(1)))));
                return;
            }
            Matcher reqClose = REQUEST_CLOSE.matcher(path);
            if ("POST".equalsIgnoreCase(method) && reqClose.matches()) {
                sendJson(exchange, 200, toJson(service.requests().closeErasure(ctx,
                        UUID.fromString(reqClose.group(1)))));
                return;
            }
            Matcher reqOutcome = REQUEST_OUTCOME.matcher(path);
            if ("POST".equalsIgnoreCase(method) && reqOutcome.matches()) {
                sendJson(exchange, 200, toJson(service.requests().reportOutcome(ctx,
                        UUID.fromString(reqOutcome.group(1)), bodyMap)));
                return;
            }
            Matcher shareLife = SHARING_LIFE.matcher(path);
            if ("POST".equalsIgnoreCase(method) && shareLife.matches()) {
                sendJson(exchange, 200, toJson(service.requests().sharingLifecycle(ctx,
                        UUID.fromString(shareLife.group(1)), shareLife.group(2))));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/sharing/apply-expiry")) {
                sendJson(exchange, 200, toJson(service.requests().applyExpiryJob(ctx)));
                return;
            }

            if ("GET".equalsIgnoreCase(method) && path.endsWith("/domain/resolve-policy")) {
                sendJson(exchange, 200, toJson(service.lifecycle().resolvePublished(ctx,
                        queryParam(exchange, "policyType"), queryParam(exchange, "domainCode"))));
                return;
            }

            if ("GET".equalsIgnoreCase(method) && path.endsWith("/adherence")) {
                sendJson(exchange, 200, toJson(service.adherence(ctx)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/college-view")) {
                sendJson(exchange, 200, toJson(service.collegeView(ctx)));
                return;
            }

            if ("GET".equalsIgnoreCase(method) && path.endsWith("/dev/outage-flags")) {
                sendJson(exchange, 200, toJson(service.getOutageFlags(ctx)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/dev/outage-flags")) {
                sendJson(exchange, 200, toJson(service.setOutageFlags(ctx,
                        parseBoolean(extract(body, "auditDisabled")),
                        parseBoolean(extract(body, "simulatorDisabled")))));
                return;
            }

            sendJson(exchange, 404, problem("IADM05_NOT_FOUND", "No route for " + method + " " + path));
        } catch (UserProfileAccessDeniedException ex) {
            sendJson(exchange, 403, problem("FORBIDDEN", ex.getMessage()));
        } catch (InstituteAdminException ex) {
            sendJson(exchange, ex.getStatus(), problem(ex.getErrorCode(), ex.getMessage()));
        } catch (Exception ex) {
            logger.error("IADM-05 unhandled error correlationId={}: {}", correlationId, ex.getMessage(), ex);
            sendJson(exchange, 500, problem("IADM05_INTERNAL", "An unexpected error occurred."));
        } finally {
            tracker.close();
            LogContext.clear();
        }
    }

    private static Map<String, Object> parseObject(String json) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        if (json == null || json.trim().isEmpty()) {
            return map;
        }
        // Minimal field extract for known keys (arrays kept as raw strings; service splits)
        String[] keys = new String[]{
                "code", "title", "className", "description", "handlingGuidance", "permittedUseNotes",
                "ownerLabel", "domainCode", "recordCategory", "scopeNote", "triggerNote", "ruleReference",
                "durationNote", "holdHandling", "decision", "rationaleRef", "effectiveFrom",
                "purpose", "scopeTenantId", "destinationNote", "applicablePolicyId", "idempotencyKey",
                "subjectRefOpaque", "provider", "consumer", "validityEnd", "legalBasisRef",
                "onwardSharingRestriction", "revocationConditions", "domainCodes", "fieldsRequested",
                "outcome", "receiptRef", "reasonCode", "correlationId", "auditDisabled", "simulatorDisabled"
        };
        for (int i = 0; i < keys.length; i++) {
            String v = extract(json, keys[i]);
            if (v != null) {
                map.put(keys[i], v);
            }
        }
        // Array fields: capture raw bracket content
        Matcher arr = Pattern.compile("\"domainCodes\"\\s*:\\s*(\\[[^\\]]*\\])").matcher(json);
        if (arr.find()) {
            map.put("domainCodes", arr.group(1));
        }
        Matcher fields = Pattern.compile("\"fieldsRequested\"\\s*:\\s*(\\[[^\\]]*\\])").matcher(json);
        if (fields.find()) {
            map.put("fieldsRequested", fields.group(1));
        }
        return map;
    }

    private static String queryParam(HttpExchange exchange, String name) {
        String q = exchange.getRequestURI().getRawQuery();
        if (q == null || name == null) {
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

    private static String header(HttpExchange exchange, String name) {
        List<String> values = exchange.getRequestHeaders().get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
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
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("errorCode", code);
        m.put("detail", detail);
        return toJson(m);
    }

    private static String extract(String json, String field) {
        if (json == null || json.isEmpty() || field == null) {
            return null;
        }
        Pattern p = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        Pattern n = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
        Matcher nm = n.matcher(json);
        if (nm.find()) {
            return nm.group(1);
        }
        Pattern b = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(true|false|null)",
                Pattern.CASE_INSENSITIVE);
        Matcher bm = b.matcher(json);
        if (bm.find()) {
            String v = bm.group(1);
            return "null".equalsIgnoreCase(v) ? null : v;
        }
        return null;
    }

    private static Boolean parseBoolean(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        if ("true".equalsIgnoreCase(raw.trim())) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(raw.trim())) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":");
            appendValue(sb, e.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendValue(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v.toString());
        } else if (v instanceof Map) {
            sb.append(toJson((Map<String, Object>) v));
        } else if (v instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) v) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                appendValue(sb, item);
            }
            sb.append(']');
        } else {
            sb.append('"').append(escape(String.valueOf(v))).append('"');
        }
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
