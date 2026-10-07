package com.campx.academic.analytics.controller;

import com.campx.academic.analytics.exception.AnalyticsExceptions.*;
import com.campx.academic.analytics.model.AnalyticsModels.*;
import com.campx.academic.analytics.model.ErrorResponse;
import com.campx.academic.analytics.service.AnalyticsDomainService;
import com.campx.academic.analytics.service.MetricsCollector;
import com.campx.academic.analytics.service.RateLimiter;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.FlowTracker;
import com.campx.logger.context.LogContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * REST HTTP Controller for ACD-10: Reporting & Analytics Service.
 * Implements canonical REST APIs, authentication/authorization enforcement,
 * rate limiting, idempotency, RFC 7807 error responses, and logging context.
 */
public class AnalyticsController implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AnalyticsController.class);

    private final AnalyticsDomainService domainService;
    private final RateLimiter rateLimiter;
    private final MetricsCollector metricsCollector = MetricsCollector.getInstance();

    // Regex routing patterns
    private final Pattern pExportStatus = Pattern.compile("^/export/([^/]+)(?:/status)?/?$");
    private final Pattern pExportDownload = Pattern.compile("^/export/([^/]+)/download/?$");
    private final Pattern pDlqReplay = Pattern.compile("^/dlq/([^/]+)/replay/?$");

    public AnalyticsController(AnalyticsDomainService domainService) {
        this.domainService = domainService != null ? domainService : new AnalyticsDomainService();
        this.rateLimiter = new RateLimiter(100, 50.0); // 100 max burst, 50 req/sec
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String fullPath = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        long startTime = System.currentTimeMillis();

        String traceId = "TRACE-DEFAULT";
        String tenantId = "INST-001";
        String userId = "admin-1";
        String userRole = "ACADEMIC_ADMIN";
        String userDeptId = "DEP-CSE";

        try {
            // 1. Context Extraction (§48, §51, ACD10-US-001, ACD10-US-002, ACD10-US-003)
            traceId = exchange.getRequestHeaders().getFirst("X-Trace-Id");
            if (traceId == null || traceId.trim().isEmpty()) {
                traceId = exchange.getRequestHeaders().getFirst("X-Correlation-Id");
            }
            if (traceId == null || traceId.trim().isEmpty()) {
                traceId = LogContext.initTraceId();
            } else {
                LogContext.setTraceId(traceId);
            }

            String tenantHeader = exchange.getRequestHeaders().getFirst("X-Tenant-Id");
            if (tenantHeader != null && !tenantHeader.trim().isEmpty()) {
                tenantId = tenantHeader.trim();
                LogContext.setTenantId(tenantId);
            } else {
                LogContext.setTenantId(tenantId);
            }

            String userHeader = exchange.getRequestHeaders().getFirst("X-User-Id");
            if (userHeader != null && !userHeader.trim().isEmpty()) {
                userId = userHeader.trim();
                LogContext.setUserId(userId);
            }

            String roleHeader = exchange.getRequestHeaders().getFirst("X-User-Role");
            if (roleHeader != null && !roleHeader.trim().isEmpty()) {
                userRole = roleHeader.trim();
                LogContext.setUserRole(userRole);
            }

            String deptHeader = exchange.getRequestHeaders().getFirst("X-Department-Id");
            if (deptHeader != null && !deptHeader.trim().isEmpty()) {
                userDeptId = deptHeader.trim();
            }

            LogContext.setService("ACD-10-ReportingAnalyticsService");
            exchange.getResponseHeaders().set("X-Trace-Id", traceId);
            exchange.getResponseHeaders().set("X-Correlation-Id", traceId);

            // 2. Health & Prometheus Metrics direct handling
            if ("/actuator/health".equals(fullPath) || fullPath.endsWith("/health")) {
                sendJson(exchange, 200, "{\"status\":\"UP\",\"service\":\"ACD-10-ReportingAnalyticsService\"}");
                return;
            }
            if ("/metrics".equals(fullPath) || fullPath.endsWith("/metrics")) {
                String prom = metricsCollector.toPrometheusFormat(domainService);
                byte[] bytes = prom.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }

            // 3. Rate Limiter (ACD10-US-001)
            RateLimiter.RateLimitResult rateCheck = rateLimiter.tryAcquire(tenantId + ":" + userId);
            exchange.getResponseHeaders().set("X-RateLimit-Limit", String.valueOf(rateCheck.getLimit()));
            exchange.getResponseHeaders().set("X-RateLimit-Remaining", String.valueOf(rateCheck.getRemaining()));
            if (!rateCheck.isAllowed()) {
                exchange.getResponseHeaders().set("Retry-After", String.valueOf(rateCheck.getRetryAfterSeconds()));
                metricsCollector.recordError("ACD10_ANALYTICS_RATE_LIMIT_EXCEEDED");
                throw new AnalyticsRateLimitException("Too many requests. Rate limit exceeded.", rateCheck.getRetryAfterSeconds());
            }

            // 4. Normalize path for canonical and aliased routes
            String path = normalizePath(fullPath);

            try (FlowTracker flow = logger.flow("AnalyticsDispatch", "ANL-" + traceId)) {
                logger.info("Executing [{}] path={} role={} tenant={}", method, path, userRole, tenantId);

                // Query parameters extraction
                Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
                String requestBody = readBody(exchange);

                // 5. Check Idempotency for mutating POST requests (ACD10-US-050)
                String idempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");
                if (idempotencyKey != null && !idempotencyKey.trim().isEmpty() && "POST".equalsIgnoreCase(method)) {
                    String hash = sha256(requestBody);
                    IdempotencyRecord cached = domainService.getIdempotency(idempotencyKey.trim(), tenantId);
                    if (cached != null) {
                        if (!cached.getRequestHash().equals(hash)) {
                            throw new AnalyticsConflictException("Idempotency key '" + idempotencyKey + "' was previously used with a different request payload");
                        }
                        metricsCollector.recordIdempotencyHit();
                        sendJson(exchange, cached.getStatusCode(), cached.getResponseBody());
                        return;
                    }
                }

                // 6. Route Dispatching
                if ("/dashboard".equals(path) && "GET".equalsIgnoreCase(method)) {
                    String period = queryParams.get("period");
                    String scope = queryParams.get("scope");
                    String scopeId = queryParams.get("scopeId");
                    Map<String, Object> res = domainService.getDashboard(period, scope, scopeId, tenantId, userRole, userId, userDeptId);
                    sendJson(exchange, 200, toJson(res));
                }
                else if ("/attendance".equals(path) && "GET".equalsIgnoreCase(method)) {
                    String batchId = queryParams.get("batchId");
                    String from = queryParams.get("from");
                    String to = queryParams.get("to");
                    String subjectId = queryParams.get("subjectId");
                    Map<String, Object> res = domainService.getAttendanceAnalytics(batchId, from, to, subjectId, tenantId, userRole, userId, userDeptId);
                    sendJson(exchange, 200, toJson(res));
                }
                else if ("/timetable".equals(path) && "GET".equalsIgnoreCase(method)) {
                    String from = queryParams.get("from");
                    String to = queryParams.get("to");
                    String departmentId = queryParams.get("departmentId");
                    Map<String, Object> res = domainService.getTimetableUtilization(from, to, departmentId, tenantId, userRole, userDeptId);
                    sendJson(exchange, 200, toJson(res));
                }
                else if ("/progression".equals(path) && "GET".equalsIgnoreCase(method)) {
                    String batchId = queryParams.get("batchId");
                    String period = queryParams.get("period");
                    Map<String, Object> res = domainService.getAcademicProgression(batchId, period, tenantId, userRole);
                    sendJson(exchange, 200, toJson(res));
                }
                else if ("/export".equals(path) && "POST".equalsIgnoreCase(method)) {
                    Map<String, Object> bodyMap = parseSimpleJson(requestBody);
                    String rptTypeStr = String.valueOf(bodyMap.getOrDefault("reportType", "ATTENDANCE_SUMMARY"));
                    String formatStr = String.valueOf(bodyMap.getOrDefault("format", "CSV"));

                    ReportType reportType;
                    try {
                        reportType = ReportType.valueOf(rptTypeStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new AnalyticsValidationException("Unsupported reportType: " + rptTypeStr);
                    }

                    ReportFormat format;
                    try {
                        format = ReportFormat.valueOf(formatStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new AnalyticsValidationException("Unsupported format: " + formatStr);
                    }

                    Map<String, String> filters = new HashMap<>();
                    if (bodyMap.containsKey("filters") && bodyMap.get("filters") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> fMap = (Map<String, Object>) bodyMap.get("filters");
                        for (Map.Entry<String, Object> e : fMap.entrySet()) {
                            filters.put(e.getKey(), String.valueOf(e.getValue()));
                        }
                    }

                    ReportJob job = domainService.createExport(reportType, format, filters, tenantId, userId, userRole, traceId);
                    Map<String, Object> respMap = new LinkedHashMap<>();
                    respMap.put("jobId", job.getJobId());
                    respMap.put("status", job.getStatus().name());
                    respMap.put("pollUri", job.getPollUri());

                    String respJson = toJson(respMap);
                    if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
                        domainService.saveIdempotency(tenantId, idempotencyKey.trim(), "POST_EXPORT", sha256(requestBody), 202, respJson);
                    }
                    sendJson(exchange, 202, respJson);
                }
                else if (pExportDownload.matcher(path).matches() && "GET".equalsIgnoreCase(method)) {
                    Matcher m = pExportDownload.matcher(path);
                    m.matches();
                    String jobId = m.group(1);
                    String fileContent = domainService.downloadExport(jobId, tenantId, userId, userRole, traceId);
                    byte[] bytes = fileContent.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                }
                else if (pExportStatus.matcher(path).matches() && "GET".equalsIgnoreCase(method)) {
                    Matcher m = pExportStatus.matcher(path);
                    m.matches();
                    String jobId = m.group(1);
                    ReportJob job = domainService.getExportJob(jobId, tenantId, userId, userRole);
                    Map<String, Object> respMap = new LinkedHashMap<>();
                    respMap.put("jobId", job.getJobId());
                    respMap.put("reportType", job.getReportType().name());
                    respMap.put("format", job.getFormat().name());
                    respMap.put("status", job.getStatus().name());
                    respMap.put("outputRef", job.getOutputRef());
                    respMap.put("rowCount", job.getRowCount());
                    respMap.put("fileSize", job.getFileSize());
                    respMap.put("createdAt", job.getCreatedAt());
                    respMap.put("completedAt", job.getCompletedAt());
                    respMap.put("expiresAt", job.getExpiresAt());
                    if (job.getDiagnosticMessage() != null) {
                        respMap.put("diagnosticMessage", job.getDiagnosticMessage());
                    }
                    sendJson(exchange, 200, toJson(respMap));
                }
                else if ("/events".equals(path) && "POST".equalsIgnoreCase(method)) {
                    EventEnvelope envelope = parseEventEnvelope(requestBody, traceId, tenantId);
                    boolean consumed = domainService.consumeEvent(envelope);
                    sendJson(exchange, consumed ? 200 : 400, "{\"success\":" + consumed + ",\"status\":\"" + (consumed ? "EVENT_PROCESSED" : "FAILED") + "\"}");
                }
                else if (pDlqReplay.matcher(path).matches() && "POST".equalsIgnoreCase(method)) {
                    Matcher m = pDlqReplay.matcher(path);
                    m.matches();
                    String eventId = m.group(1);
                    boolean replayed = domainService.replayDlqEvent(tenantId, eventId, userRole, traceId);
                    sendJson(exchange, replayed ? 200 : 400, "{\"replayed\":" + replayed + ",\"eventId\":\"" + eventId + "\"}");
                }
                else if ("/rebuild".equals(path) && "POST".equalsIgnoreCase(method)) {
                    int count = domainService.rebuildProjections(tenantId, userRole, traceId);
                    sendJson(exchange, 200, "{\"rebuilt\":true,\"replayedFacts\":" + count + "}");
                }
                else if ("/reports/definitions".equals(path) && "GET".equalsIgnoreCase(method)) {
                    List<ReportDefinition> defs = domainService.getReportDefinitions();
                    sendJson(exchange, 200, toJson(defs));
                }
                else {
                    throw new AnalyticsNotFoundException("Endpoint not found: " + method + " " + fullPath);
                }
            }
        } catch (AnalyticsException ae) {
            logger.warn("Analytics handled exception: [{}] {}", ae.getErrorCode(), ae.getMessage());
            metricsCollector.recordError(ae.getErrorCode());
            sendError(exchange, ae.getHttpStatus(), ae.getErrorCode(), ae.getMessage(), traceId, fullPath, ae.getDetails());
        } catch (Exception e) {
            logger.error("Unhandled exception processing {}: {}", fullPath, e.getMessage(), e);
            metricsCollector.recordError("ACD10_ANALYTICS_INTERNAL_ERROR");
            sendError(exchange, 500, "ACD10_ANALYTICS_INTERNAL_ERROR", "Internal service error: " + e.getMessage(), traceId, fullPath, Collections.emptyList());
        } finally {
            metricsCollector.recordRequest(System.currentTimeMillis() - startTime);
            LogContext.clear();
        }
    }

    private String normalizePath(String fullPath) {
        String p = fullPath;
        if (p.startsWith("/api/v1/academics/analytics")) {
            p = p.substring("/api/v1/academics/analytics".length());
        } else if (p.startsWith("/api/v1/analytics")) {
            p = p.substring("/api/v1/analytics".length());
        } else if (p.startsWith("/v1/analytics")) {
            p = p.substring("/v1/analytics".length());
        } else if (p.startsWith("/api/v1/dashboards") || p.startsWith("/v1/dashboards") ||
                p.startsWith("/api/v1/kpis") || p.startsWith("/v1/kpis")) {
            return "/dashboard";
        } else if (p.startsWith("/api/v1/reports") || p.startsWith("/v1/reports")) {
            return "/export";
        }

        if (p.isEmpty()) return "/";
        return p;
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.trim().isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                map.put(pair.substring(0, idx), pair.substring(idx + 1));
            } else if (!pair.isEmpty()) {
                map.put(pair, "");
            }
        }
        return map;
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        }
    }

    private EventEnvelope parseEventEnvelope(String json, String traceId, String defaultTenant) {
        Map<String, Object> map = parseSimpleJson(json);
        EventEnvelope env = new EventEnvelope();
        env.setEventId(String.valueOf(map.getOrDefault("eventId", "EVT-" + UUID.randomUUID().toString().substring(0, 8))));
        env.setEventType(String.valueOf(map.getOrDefault("eventType", "AttendanceMarked")));
        env.setSource(String.valueOf(map.getOrDefault("source", "ACD-06")));
        env.setOccurredAt(String.valueOf(map.getOrDefault("occurredAt", Instant.now().toString())));
        env.setTenantId(String.valueOf(map.getOrDefault("tenantId", defaultTenant)));
        env.setCorrelationId(String.valueOf(map.getOrDefault("correlationId", traceId)));
        env.setVersion(String.valueOf(map.getOrDefault("version", "1.0")));

        if (map.containsKey("data") && map.get("data") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> d = (Map<String, Object>) map.get("data");
            env.setData(d);
        } else {
            env.setData(map);
        }
        return env;
    }

    private Map<String, Object> parseSimpleJson(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty() || !json.contains("{")) return map;

        String content = json.trim();
        if (content.startsWith("{")) content = content.substring(1);
        if (content.endsWith("}")) content = content.substring(0, content.length() - 1);

        // Simple regex-based key-value parsing for flat and simple maps
        Pattern p = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"[^\"]*\"|\\d+(?:\\.\\d+)?|true|false|null|\\{[^}]*\\})");
        Matcher m = p.matcher(content);
        while (m.find()) {
            String key = m.group(1);
            String rawVal = m.group(2).trim();
            if (rawVal.startsWith("\"") && rawVal.endsWith("\"")) {
                map.put(key, rawVal.substring(1, rawVal.length() - 1));
            } else if ("true".equalsIgnoreCase(rawVal)) {
                map.put(key, true);
            } else if ("false".equalsIgnoreCase(rawVal)) {
                map.put(key, false);
            } else if ("null".equalsIgnoreCase(rawVal)) {
                map.put(key, null);
            } else if (rawVal.startsWith("{") && rawVal.endsWith("}")) {
                map.put(key, parseSimpleJson(rawVal));
            } else {
                try {
                    if (rawVal.contains(".")) {
                        map.put(key, Double.parseDouble(rawVal));
                    } else {
                        map.put(key, Long.parseLong(rawVal));
                    }
                } catch (NumberFormatException e) {
                    map.put(key, rawVal);
                }
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escape((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            int i = 0;
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escape(String.valueOf(entry.getKey()))).append("\":");
                sb.append(toJson(entry.getValue()));
                i++;
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof Collection) {
            Collection<?> c = (Collection<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            int i = 0;
            for (Object item : c) {
                if (i > 0) sb.append(",");
                sb.append(toJson(item));
                i++;
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj instanceof ReportDefinition) {
            ReportDefinition d = (ReportDefinition) obj;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("definitionId", d.getDefinitionId());
            m.put("name", d.getName());
            m.put("code", d.getCode());
            m.put("description", d.getDescription());
            m.put("type", d.getType().name());
            m.put("parameters", d.getParameters());
            m.put("allowedFilters", d.getAllowedFilters());
            m.put("systemFlag", d.isSystemFlag());
            return toJson(m);
        }
        return "\"" + escape(obj.toString()) + "\"";
    }

    private void sendJson(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int status, String code, String message,
                           String correlationId, String path, List<String> details) {
        try {
            ErrorResponse error = new ErrorResponse(status, code, message, correlationId, path, details);
            String json = error.toJson();
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            logger.warn("Failed sending error response: {}", e.getMessage());
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public AnalyticsDomainService getDomainService() {
        return domainService;
    }
}
