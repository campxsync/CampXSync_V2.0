package com.campx.academic.timetable.controller;

import com.campx.academic.timetable.exception.*;
import com.campx.academic.timetable.model.ErrorResponse;
import com.campx.academic.timetable.model.TimetableModels.*;
import com.campx.academic.timetable.service.MetricsCollector;
import com.campx.academic.timetable.service.RateLimiter;
import com.campx.academic.timetable.service.StructuredLogEntry;
import com.campx.academic.timetable.service.TimetableDomainService;
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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP REST Controller for ACD-05: Timetable Management Service.
 * Serves endpoints mounted under {@code /api/v1/academics/timetables/**}, {@code /api/v1/timetables/**}, and canonical aliases.
 */
public class TimetableController implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(TimetableController.class);

    private final TimetableDomainService domainService;
    private final RateLimiter rateLimiter;
    private final MetricsCollector metricsCollector = MetricsCollector.getInstance();

    public TimetableController(TimetableDomainService domainService) {
        this(domainService, new RateLimiter());
    }

    public TimetableController(TimetableDomainService domainService, RateLimiter rateLimiter) {
        this.domainService = domainService;
        this.rateLimiter = rateLimiter != null ? rateLimiter : new RateLimiter();
    }

    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String fullPath = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        long startTime = System.currentTimeMillis();

        String currentTraceId = "TRACE-DEFAULT";
        String currentTenantId = "TENANT-001";
        String currentUserId = "admin-1";
        String finalOutcome = "SUCCESS";
        int finalResponseCode = 200;

        try {
            // 1. Correlation Context Extraction (§51)
            String traceId = exchange.getRequestHeaders().getFirst("X-Trace-Id");
            if (traceId == null || traceId.trim().isEmpty()) {
                traceId = exchange.getRequestHeaders().getFirst("X-Correlation-Id");
            }
            if (traceId == null || traceId.trim().isEmpty()) {
                traceId = LogContext.initTraceId();
            } else {
                LogContext.setTraceId(traceId);
            }
            currentTraceId = traceId;

            String tenantId = exchange.getRequestHeaders().getFirst("X-Tenant-Id");
            if (tenantId != null && !tenantId.trim().isEmpty()) {
                LogContext.setTenantId(tenantId);
            } else {
                tenantId = "TENANT-001";
            }
            currentTenantId = tenantId;

            String userId = exchange.getRequestHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.trim().isEmpty()) {
                LogContext.setUserId(userId);
            } else {
                userId = "admin-1";
            }
            currentUserId = userId;

            String userRole = exchange.getRequestHeaders().getFirst("X-User-Role");
            if (userRole != null && !userRole.trim().isEmpty()) {
                LogContext.setUserRole(userRole);
            } else {
                userRole = "ACADEMIC_ADMIN";
            }

            String idempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");

            // External API Key check (Story 50, 61)
            String apiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                ApiKeyRecord keyRecord = domainService.validateApiKey(apiKey.trim(), tenantId);
                if (keyRecord == null) {
                    finalOutcome = "UNAUTHORIZED";
                    finalResponseCode = 401;
                    sendError(exchange, 401, "ACD_TIMETABLE_UNAUTHORIZED", "Invalid, revoked, or expired API Key", null, fullPath);
                    return;
                }
                userRole = "EXTERNAL_API";
                userId = "api-consumer-" + keyRecord.getKeyId();
                currentUserId = userId;
                LogContext.setUserRole(userRole);
                LogContext.setUserId(userId);

                if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
                    finalOutcome = "FORBIDDEN";
                    finalResponseCode = 403;
                    sendError(exchange, 403, "ACD_TIMETABLE_FORBIDDEN", "External API consumers have read-only access", null, fullPath);
                    return;
                }
            }

            LogContext.setService("ACD-05-TimetableManagementService");
            exchange.getResponseHeaders().set("X-Trace-Id", traceId);
            exchange.getResponseHeaders().set("X-Correlation-Id", traceId);

            // Operational Endpoints
            if ("/actuator/health".equals(fullPath) || fullPath.endsWith("/health")) {
                sendJson(exchange, 200, "{\"status\":\"UP\",\"service\":\"ACD-05-TimetableManagementService\"}");
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

            // Rate Limiter Check (Story 57)
            RateLimiter.RateLimitResult rateCheck = rateLimiter.tryAcquire(tenantId + ":" + userId);
            exchange.getResponseHeaders().set("X-RateLimit-Limit", String.valueOf(rateCheck.getLimit()));
            exchange.getResponseHeaders().set("X-RateLimit-Remaining", String.valueOf(rateCheck.getRemaining()));
            if (!rateCheck.isAllowed()) {
                exchange.getResponseHeaders().set("Retry-After", String.valueOf(rateCheck.getRetryAfterSeconds()));
                metricsCollector.recordError("ACD_TIMETABLE_RATE_LIMIT_EXCEEDED");
                metricsCollector.recordRequest(method, fullPath, 429, System.currentTimeMillis() - startTime);
                sendError(exchange, 429, "ACD_TIMETABLE_RATE_LIMIT_EXCEEDED", "Too many requests. Limit exceeded.", null, fullPath);
                return;
            }

            // Normalize path
            String path = fullPath;
            if (path.startsWith("/api/v1/academics/timetables")) {
                path = path.substring("/api/v1/academics/timetables".length());
            } else if (path.startsWith("/api/v1/timetables")) {
                path = path.substring("/api/v1/timetables".length());
            } else if (path.startsWith("/v1/timetables")) {
                path = path.substring("/v1/timetables".length());
            }
            if (path.isEmpty()) {
                path = "/";
            }

            // Idempotency check for mutating requests (Story 45)
            if (idempotencyKey != null && !idempotencyKey.trim().isEmpty() &&
                    ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))) {
                IdempotencyRecord cached = domainService.getIdempotency(idempotencyKey.trim(), tenantId);
                if (cached != null) {
                    metricsCollector.recordIdempotencyHit();
                    sendJson(exchange, cached.getStatusCode(), cached.getResponseBody());
                    return;
                }
            }

            String requestBody = readBody(exchange);
            String responseJson;
            int responseCode = 200;

            // RBAC Enforcement Matrix (Story 47-50)
            if (!isAuthorized(method, path, userRole, requestBody)) {
                sendError(exchange, 403, "ACD_TIMETABLE_FORBIDDEN",
                        "Role '" + userRole + "' is not authorized for " + method + " " + fullPath, null, fullPath);
                return;
            }

            // Route Dispatching
            if ("/events".equals(path) || path.startsWith("/events/")) {
                // Event ingestion
                Map<String, Object> eventMap = parseSimpleJsonMap(requestBody);
                boolean consumed = domainService.consumeEvent(eventMap);
                responseJson = "{\"success\":" + consumed + ",\"status\":\"" + (consumed ? "EVENT_PROCESSED" : "FAILED") + "\"}";
                responseCode = consumed ? 200 : 400;
            }
            // 1. Root Collection: GET / or POST /
            else if ("/".equals(path) || path.isEmpty()) {
                if ("POST".equalsIgnoreCase(method)) {
                    CreateTimetableRequest req = parseCreateTimetableRequest(requestBody);
                    Timetable created = domainService.createDraft(req, tenantId, userId);
                    responseJson = buildSuccessEnvelope(created, "Timetable draft created successfully", traceId);
                    responseCode = 201;
                } else if ("GET".equalsIgnoreCase(method)) {
                    Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
                    String batchId = query.get("batchId");
                    String facultyId = query.get("facultyId");
                    String roomId = query.get("roomId");
                    String subjectId = query.get("subjectId");
                    String departmentId = query.get("departmentId");
                    String status = query.get("status");
                    String academicYear = query.get("academicYear");
                    int page = query.containsKey("page") ? Integer.parseInt(query.get("page")) : 0;
                    int size = query.containsKey("size") ? Integer.parseInt(query.get("size")) : 20;

                    List<Timetable> results = domainService.searchTimetables(tenantId, batchId, facultyId, roomId,
                            subjectId, departmentId, status, academicYear, page, size);
                    responseJson = buildSuccessEnvelope(results, "Timetables retrieved successfully", traceId);
                    responseCode = 200;
                } else {
                    sendError(exchange, 405, "ACD_TIMETABLE_METHOD_NOT_ALLOWED", "Method not allowed", null, fullPath);
                    return;
                }
            }
            // 2. Batch Timetable View: /batch/{batchId}
            else if (path.startsWith("/batch/")) {
                String batchId = path.substring("/batch/".length());
                List<TimetableEntry> entries = domainService.getPublishedBatchTimetable(batchId, tenantId);
                responseJson = buildSuccessEnvelope(entries, "Batch timetable retrieved", traceId);
            }
            // 3. Faculty Timetable View: /faculty/{facultyId}
            else if (path.startsWith("/faculty/")) {
                String facultyId = path.substring("/faculty/".length());
                List<TimetableEntry> entries = domainService.getPublishedFacultyTimetable(facultyId, tenantId);
                responseJson = buildSuccessEnvelope(entries, "Faculty timetable retrieved", traceId);
            }
            // 4. Room Utilization View: /room/{roomId}
            else if (path.startsWith("/room/")) {
                String roomId = path.substring("/room/".length());
                List<TimetableEntry> entries = domainService.getPublishedRoomUtilization(roomId, tenantId);
                responseJson = buildSuccessEnvelope(entries, "Room schedule retrieved", traceId);
            }
            // 5. Department Timetable View: /department/{departmentId}
            else if (path.startsWith("/department/")) {
                String departmentId = path.substring("/department/".length());
                List<Timetable> tts = domainService.getDepartmentTimetables(departmentId, tenantId);
                responseJson = buildSuccessEnvelope(tts, "Department timetables retrieved", traceId);
            }
            // 6. Direct Export: /export
            else if ("/export".equals(path)) {
                Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
                String timetableId = query.get("timetableId");
                String format = query.get("format");
                if (timetableId == null) {
                    throw new TimetableBadRequestException("timetableId query parameter required for export");
                }
                Map<String, Object> exp = domainService.exportTimetable(timetableId, format, tenantId, userId);
                responseJson = buildSuccessEnvelope(exp, "Timetable exported successfully", traceId);
            }
            // 7. Operations on / {id} / ...
            else {
                responseJson = handleTimetableItemRoutes(method, path, requestBody, tenantId, userId, traceId);
                if ("POST".equalsIgnoreCase(method)) {
                    responseCode = 201;
                }
            }

            // Save idempotency record on success if key present
            if (idempotencyKey != null && !idempotencyKey.trim().isEmpty() &&
                    ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))) {
                domainService.saveIdempotency(idempotencyKey.trim(), tenantId, String.valueOf(requestBody.hashCode()),
                        responseCode, responseJson);
            }

            long duration = System.currentTimeMillis() - startTime;
            metricsCollector.recordRequest(method, fullPath, responseCode, duration);
            sendJson(exchange, responseCode, responseJson);

        } catch (TimetableException te) {
            long duration = System.currentTimeMillis() - startTime;
            finalResponseCode = te.getHttpStatus();
            finalOutcome = "FAILURE";
            metricsCollector.recordError(te.getErrorCode());
            metricsCollector.recordRequest(method, fullPath, te.getHttpStatus(), duration);
            sendError(exchange, te.getHttpStatus(), te.getErrorCode(), te.getMessage(), te.getDetails(), fullPath);
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            finalResponseCode = 500;
            finalOutcome = "ERROR";
            logger.error("Unhandled exception processing request: {}", ex.getMessage(), ex);
            metricsCollector.recordError("ACD_TIMETABLE_INTERNAL_ERROR");
            metricsCollector.recordRequest(method, fullPath, 500, duration);
            sendError(exchange, 500, "ACD_TIMETABLE_INTERNAL_ERROR", "Internal Server Error: " + ex.getMessage(), null, fullPath);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            StructuredLogEntry logEntry = StructuredLogEntry.builder()
                    .service("ACD-05-TimetableManagementService")
                    .tenantId(currentTenantId)
                    .requestId(currentTraceId)
                    .correlationId(currentTraceId)
                    .actorId(currentUserId)
                    .operation(method + " " + fullPath)
                    .outcome(finalOutcome)
                    .durationMs(duration)
                    .build();
            logger.info("{}", logEntry.toJson());

            // Mandatory ThreadLocal cleanup to prevent thread-pool context leaks
            LogContext.clear();
        }
    }

    private String handleTimetableItemRoutes(String method, String path, String body,
                                            String tenantId, String userId, String traceId) {
        // Pattern matches:
        // /{id}
        // /{id}/entries
        // /{id}/entries/{entryId}
        // /{id}/entries/bulk
        // /{id}/validate
        // /{id}/conflicts
        // /{id}/publish
        // /{id}/versions
        // /{id}/clone
        // /{id}/ad-hoc-sessions
        // /{id}/export

        Pattern pValidate = Pattern.compile("^/([^/]+)/validate/?$");
        Pattern pConflicts = Pattern.compile("^/([^/]+)/conflicts/?$");
        Pattern pPublish = Pattern.compile("^/([^/]+)/publish/?$");
        Pattern pVersions = Pattern.compile("^/([^/]+)/versions/?$");
        Pattern pClone = Pattern.compile("^/([^/]+)/clone/?$");
        Pattern pAdHoc = Pattern.compile("^/([^/]+)/ad-hoc-sessions/?$");
        Pattern pExport = Pattern.compile("^/([^/]+)/export/?$");
        Pattern pBulkEntries = Pattern.compile("^/([^/]+)/entries/bulk/?$");
        Pattern pEntries = Pattern.compile("^/([^/]+)/entries/?$");
        Pattern pEntryItem = Pattern.compile("^/([^/]+)/entries/([^/]+)/?$");
        Pattern pTimetable = Pattern.compile("^/([^/]+)/?$");

        Matcher m;

        // / {id} / validate
        if ((m = pValidate.matcher(path)).matches()) {
            String id = m.group(1);
            List<ConflictResult> conflicts = domainService.validateTimetable(id, tenantId, userId);
            long blocking = conflicts.stream().filter(c -> c.getSeverity() == ConflictSeverity.BLOCKING).count();
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("timetableId", id);
            res.put("status", blocking == 0 ? "VALIDATED" : "INVALID");
            res.put("blockingConflicts", blocking);
            res.put("warnings", conflicts.size() - blocking);
            res.put("conflicts", conflicts);
            return buildSuccessEnvelope(res, "Conflict validation completed", traceId);
        }

        // / {id} / conflicts
        if ((m = pConflicts.matcher(path)).matches()) {
            String id = m.group(1);
            List<ConflictResult> conflicts = domainService.getConflicts(id, tenantId);
            return buildSuccessEnvelope(conflicts, "Conflict diagnostics retrieved", traceId);
        }

        // / {id} / publish
        if ((m = pPublish.matcher(path)).matches()) {
            String id = m.group(1);
            PublishRequest req = parsePublishRequest(body);
            TimetableVersion pubVer = domainService.publishTimetable(id, req, tenantId, userId);
            return buildSuccessEnvelope(pubVer, "Timetable version v" + pubVer.getVersionNo() + " published successfully", traceId);
        }

        // / {id} / versions
        if ((m = pVersions.matcher(path)).matches()) {
            String id = m.group(1);
            List<TimetableVersion> versions = domainService.getVersionHistory(id, tenantId);
            return buildSuccessEnvelope(versions, "Timetable versions retrieved", traceId);
        }

        // / {id} / clone
        if ((m = pClone.matcher(path)).matches()) {
            String id = m.group(1);
            CloneRequest req = parseCloneRequest(body);
            Timetable cloned = domainService.cloneEffectiveVersion(id, req, tenantId, userId);
            return buildSuccessEnvelope(cloned, "Timetable cloned to new draft version v" + cloned.getCurrentVersionNo(), traceId);
        }

        // / {id} / ad-hoc-sessions
        if ((m = pAdHoc.matcher(path)).matches()) {
            String id = m.group(1);
            AdHocSessionRequest req = parseAdHocRequest(body);
            TimetableEntry adHoc = domainService.createAdHocSession(id, req, tenantId, userId);
            return buildSuccessEnvelope(adHoc, "Ad-hoc session created", traceId);
        }

        // / {id} / export
        if ((m = pExport.matcher(path)).matches()) {
            String id = m.group(1);
            Map<String, Object> exp = domainService.exportTimetable(id, "PDF", tenantId, userId);
            return buildSuccessEnvelope(exp, "Timetable export generated", traceId);
        }

        // / {id} / entries / bulk
        if ((m = pBulkEntries.matcher(path)).matches()) {
            String id = m.group(1);
            BulkEntriesRequest req = parseBulkEntriesRequest(body);
            BulkEntriesResult res = domainService.bulkAddEntries(id, req, tenantId, userId);
            return buildSuccessEnvelope(res, "Bulk entries processed", traceId);
        }

        // / {id} / entries (POST: add, GET: list)
        if ((m = pEntries.matcher(path)).matches()) {
            String id = m.group(1);
            if ("POST".equalsIgnoreCase(method)) {
                CreateEntryRequest req = parseCreateEntryRequest(body);
                TimetableEntry created = domainService.addEntry(id, req, tenantId, userId);
                return buildSuccessEnvelope(created, "Slot entry created", traceId);
            } else if ("GET".equalsIgnoreCase(method)) {
                Timetable tt = domainService.getTimetable(id, tenantId);
                List<TimetableEntry> entries = domainService.getEntriesForTimetable(id, tt.getCurrentVersionNo());
                return buildSuccessEnvelope(entries, "Timetable entries retrieved", traceId);
            }
        }

        // / {id} / entries / {entryId} (PUT: update, DELETE: remove)
        if ((m = pEntryItem.matcher(path)).matches()) {
            String id = m.group(1);
            String entryId = m.group(2);
            if ("PUT".equalsIgnoreCase(method)) {
                UpdateEntryRequest req = parseUpdateEntryRequest(body);
                TimetableEntry updated = domainService.updateEntry(id, entryId, req, tenantId, userId);
                return buildSuccessEnvelope(updated, "Slot entry updated", traceId);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                domainService.removeEntry(id, entryId, tenantId, userId);
                return buildSuccessEnvelope(MapBuilder.create().put("status", "REMOVED").put("entryId", entryId).build(),
                        "Slot entry removed", traceId);
            }
        }

        // / {id} (GET: detail, PUT: update metadata, DELETE: soft-delete)
        if ((m = pTimetable.matcher(path)).matches()) {
            String id = m.group(1);
            if ("GET".equalsIgnoreCase(method)) {
                Timetable tt = domainService.getTimetable(id, tenantId);
                return buildSuccessEnvelope(tt, "Timetable retrieved", traceId);
            } else if ("PUT".equalsIgnoreCase(method)) {
                UpdateTimetableRequest req = parseUpdateTimetableRequest(body);
                Timetable updated = domainService.updateDraftMetadata(id, req, tenantId, userId);
                return buildSuccessEnvelope(updated, "Timetable metadata updated", traceId);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                domainService.deleteDraft(id, tenantId, userId);
                return buildSuccessEnvelope(MapBuilder.create().put("status", "DELETED").put("timetableId", id).build(),
                        "Draft timetable deleted", traceId);
            }
        }

        throw new TimetableNotFoundException("No endpoint route matched for path: " + path);
    }

    // =========================================================================
    // RBAC Authorization Matrix (Stories 47-50, Table 36)
    // =========================================================================

    private boolean isAuthorized(String method, String path, String userRole, String body) {
        if (userRole == null || userRole.trim().isEmpty()) {
            return false;
        }
        String role = userRole.toUpperCase();

        // Super Admin & Academic Admin have full access
        if ("SUPER_ADMIN".equals(role) || "ACADEMIC_ADMIN".equals(role)) {
            return true;
        }

        // Exam Cell: Scoped permission to manage EXAM timetable entries (Story 48)
        if ("EXAM_CELL".equals(role)) {
            if (path.contains("/entries")) {
                if (body != null && body.contains("\"entryType\"") && body.contains("\"EXM\"")) {
                    return true;
                }
                if ("GET".equalsIgnoreCase(method)) {
                    return true;
                }
            }
            return false;
        }

        // Facilities Manager: Read-only access to room utilization (Story 49)
        if ("FACILITIES_MANAGER".equals(role)) {
            return "GET".equalsIgnoreCase(method) && path.startsWith("/room/");
        }

        // External API: Read-only access (Story 50)
        if ("EXTERNAL_API".equals(role)) {
            return "GET".equalsIgnoreCase(method);
        }

        // Department Head: View, Create, Edit, Validate within department
        if ("DEPARTMENT_HEAD".equals(role)) {
            return true;
        }

        // Registrar: View, Publish / Approve
        if ("REGISTRAR".equals(role)) {
            if ("GET".equalsIgnoreCase(method) || path.endsWith("/publish")) {
                return true;
            }
            return false;
        }

        // Faculty: View assigned timetable
        if ("FACULTY".equals(role)) {
            return "GET".equalsIgnoreCase(method) &&
                    (path.startsWith("/faculty/") || path.startsWith("/batch/") || "/".equals(path));
        }

        // Student: View published timetable
        if ("STUDENT".equals(role)) {
            return "GET".equalsIgnoreCase(method) &&
                    (path.startsWith("/batch/") || "/".equals(path));
        }

        // Management: View & Export
        if ("MANAGEMENT".equals(role)) {
            return "GET".equalsIgnoreCase(method);
        }

        return false;
    }

    // =========================================================================
    // Response & Error Envelopes (RFC 7807)
    // =========================================================================

    private void sendJson(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String errorCode, String message, Object details, String path) throws IOException {
        String traceId = exchange.getResponseHeaders().getFirst("X-Trace-Id");
        if (traceId == null) traceId = "TRACE-DEFAULT";

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"success\":false,");
        sb.append("\"error\":{");
        sb.append("\"code\":\"").append(escape(errorCode)).append("\",");
        sb.append("\"errorCode\":\"").append(escape(errorCode)).append("\",");
        sb.append("\"message\":\"").append(escape(message)).append("\"");
        if (details != null) {
            sb.append(",\"details\":").append(toJson(details));
        }
        sb.append("},");
        sb.append("\"meta\":{");
        sb.append("\"requestId\":\"").append(escape(traceId)).append("\",");
        sb.append("\"correlationId\":\"").append(escape(traceId)).append("\",");
        sb.append("\"timestamp\":").append(System.currentTimeMillis());
        sb.append("}");
        sb.append("}");

        sendJson(exchange, statusCode, sb.toString());
    }

    private String buildSuccessEnvelope(Object data, String message, String traceId) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"success\":true,");
        sb.append("\"message\":\"").append(escape(message)).append("\",");
        sb.append("\"data\":").append(toJson(data)).append(",");
        sb.append("\"meta\":{");
        sb.append("\"requestId\":\"").append(escape(traceId)).append("\",");
        sb.append("\"correlationId\":\"").append(escape(traceId)).append("\",");
        sb.append("\"timestamp\":").append(System.currentTimeMillis());
        sb.append("}");
        sb.append("}");
        return sb.toString();
    }

    // =========================================================================
    // Simple JSON Parsers & Serializers (Standard Java 8, No external deps)
    // =========================================================================

    private String readBody(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                map.put(pair.substring(0, idx), pair.substring(idx + 1));
            } else {
                map.put(pair, "");
            }
        }
        return map;
    }

    private CreateTimetableRequest parseCreateTimetableRequest(String json) {
        CreateTimetableRequest req = new CreateTimetableRequest();
        req.timetableCode = extractJsonString(json, "timetableCode");
        req.name = extractJsonString(json, "name");
        req.academicYear = extractJsonString(json, "academicYear");
        req.semester = extractJsonString(json, "semester");
        req.termId = extractJsonString(json, "termId");
        req.departmentId = extractJsonString(json, "departmentId");
        req.programId = extractJsonString(json, "programId");
        req.batchId = extractJsonString(json, "batchId");
        req.effectiveFrom = extractJsonString(json, "effectiveFrom");
        req.effectiveTo = extractJsonString(json, "effectiveTo");
        return req;
    }

    private UpdateTimetableRequest parseUpdateTimetableRequest(String json) {
        UpdateTimetableRequest req = new UpdateTimetableRequest();
        req.name = extractJsonString(json, "name");
        req.academicYear = extractJsonString(json, "academicYear");
        req.semester = extractJsonString(json, "semester");
        req.termId = extractJsonString(json, "termId");
        req.departmentId = extractJsonString(json, "departmentId");
        req.programId = extractJsonString(json, "programId");
        req.effectiveFrom = extractJsonString(json, "effectiveFrom");
        req.effectiveTo = extractJsonString(json, "effectiveTo");
        String vStr = extractJsonField(json, "version");
        if (vStr != null) {
            try { req.version = Long.parseLong(vStr.trim()); } catch (NumberFormatException ignored) {}
        }
        return req;
    }

    private CreateEntryRequest parseCreateEntryRequest(String json) {
        CreateEntryRequest req = new CreateEntryRequest();
        req.batchId = extractJsonString(json, "batchId");
        req.subjectId = extractJsonString(json, "subjectId");
        req.facultyId = extractJsonString(json, "facultyId");
        req.roomId = extractJsonString(json, "roomId");
        req.dayOfWeek = extractJsonString(json, "dayOfWeek");
        String pStr = extractJsonField(json, "period");
        if (pStr != null) {
            try { req.period = Integer.parseInt(pStr.trim()); } catch (NumberFormatException ignored) {}
        }
        req.periodId = extractJsonString(json, "periodId");
        req.startTime = extractJsonString(json, "startTime");
        req.endTime = extractJsonString(json, "endTime");
        String et = extractJsonString(json, "entryType");
        if (et != null) req.entryType = et;
        String ah = extractJsonField(json, "isAdHoc");
        if (ah != null) req.isAdHoc = Boolean.parseBoolean(ah.trim());
        return req;
    }

    private UpdateEntryRequest parseUpdateEntryRequest(String json) {
        UpdateEntryRequest req = new UpdateEntryRequest();
        req.batchId = extractJsonString(json, "batchId");
        req.subjectId = extractJsonString(json, "subjectId");
        req.facultyId = extractJsonString(json, "facultyId");
        req.roomId = extractJsonString(json, "roomId");
        req.dayOfWeek = extractJsonString(json, "dayOfWeek");
        String pStr = extractJsonField(json, "period");
        if (pStr != null) {
            try { req.period = Integer.parseInt(pStr.trim()); } catch (NumberFormatException ignored) {}
        }
        req.periodId = extractJsonString(json, "periodId");
        req.startTime = extractJsonString(json, "startTime");
        req.endTime = extractJsonString(json, "endTime");
        req.entryType = extractJsonString(json, "entryType");
        String ah = extractJsonField(json, "isAdHoc");
        if (ah != null) req.isAdHoc = Boolean.parseBoolean(ah.trim());
        return req;
    }

    private BulkEntriesRequest parseBulkEntriesRequest(String json) {
        BulkEntriesRequest req = new BulkEntriesRequest();
        // Extract array under "entries"
        int start = json.indexOf("\"entries\"");
        if (start < 0) return req;
        int arrStart = json.indexOf("[", start);
        int arrEnd = json.lastIndexOf("]");
        if (arrStart < 0 || arrEnd <= arrStart) return req;

        String arrContent = json.substring(arrStart + 1, arrEnd).trim();
        // Split objects
        String[] objs = arrContent.split("\\},\\s*\\{");
        for (String o : objs) {
            String clean = o;
            if (!clean.startsWith("{")) clean = "{" + clean;
            if (!clean.endsWith("}")) clean = clean + "}";
            req.entries.add(parseCreateEntryRequest(clean));
        }
        return req;
    }

    private PublishRequest parsePublishRequest(String json) {
        PublishRequest req = new PublishRequest();
        req.effectiveFrom = extractJsonString(json, "effectiveFrom");
        req.effectiveTo = extractJsonString(json, "effectiveTo");
        req.comment = extractJsonString(json, "comment");
        return req;
    }

    private CloneRequest parseCloneRequest(String json) {
        CloneRequest req = new CloneRequest();
        req.reason = extractJsonString(json, "reason");
        return req;
    }

    private AdHocSessionRequest parseAdHocRequest(String json) {
        AdHocSessionRequest req = new AdHocSessionRequest();
        req.date = extractJsonString(json, "date");
        req.dayOfWeek = extractJsonString(json, "dayOfWeek");
        String pStr = extractJsonField(json, "period");
        if (pStr != null) {
            try { req.period = Integer.parseInt(pStr.trim()); } catch (NumberFormatException ignored) {}
        }
        req.batchId = extractJsonString(json, "batchId");
        req.subjectId = extractJsonString(json, "subjectId");
        req.facultyId = extractJsonString(json, "facultyId");
        req.roomId = extractJsonString(json, "roomId");
        String et = extractJsonString(json, "entryType");
        if (et != null) req.entryType = et;
        req.reason = extractJsonString(json, "reason");
        return req;
    }

    private Map<String, Object> parseSimpleJsonMap(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (json == null || json.isEmpty()) return map;
        map.put("eventId", extractJsonString(json, "eventId"));
        map.put("eventType", extractJsonString(json, "eventType"));
        map.put("entityId", extractJsonString(json, "entityId"));
        map.put("source", extractJsonString(json, "source"));
        map.put("tenantId", extractJsonString(json, "tenantId"));

        // If data object exists, extract it simply
        int dataIdx = json.indexOf("\"data\"");
        if (dataIdx >= 0) {
            int dStart = json.indexOf("{", dataIdx);
            int dEnd = json.indexOf("}", dStart);
            if (dStart >= 0 && dEnd > dStart) {
                String sub = json.substring(dStart, dEnd + 1);
                Map<String, Object> subMap = new LinkedHashMap<>();
                subMap.put("batchId", extractJsonString(sub, "batchId"));
                subMap.put("subjectId", extractJsonString(sub, "subjectId"));
                subMap.put("facultyId", extractJsonString(sub, "facultyId"));
                subMap.put("unavailableSlot", extractJsonString(sub, "unavailableSlot"));
                subMap.put("startDate", extractJsonString(sub, "startDate"));
                subMap.put("endDate", extractJsonString(sub, "endDate"));
                map.put("data", subMap);
            }
        }
        return map;
    }

    private String extractJsonString(String json, String key) {
        if (json == null) return null;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String extractJsonField(String json, String key) {
        if (json == null) return null;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([^,}\\]]+)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1).replace("\"", "").trim();
        }
        return null;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escape((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return String.valueOf(obj);
        if (obj instanceof Timetable) {
            Timetable t = (Timetable) obj;
            return "{" +
                    "\"id\":\"" + escape(t.getId()) + "\"," +
                    "\"timetableCode\":\"" + escape(t.getTimetableCode()) + "\"," +
                    "\"name\":\"" + escape(t.getName()) + "\"," +
                    "\"academicYear\":\"" + escape(t.getAcademicYear()) + "\"," +
                    "\"semester\":\"" + escape(t.getSemester()) + "\"," +
                    "\"departmentId\":\"" + escape(t.getDepartmentId()) + "\"," +
                    "\"programId\":\"" + escape(t.getProgramId()) + "\"," +
                    "\"batchId\":" + (t.getBatchId() != null ? "\"" + escape(t.getBatchId()) + "\"" : "null") + "," +
                    "\"status\":\"" + t.getStatus().name() + "\"," +
                    "\"currentVersionNo\":" + t.getCurrentVersionNo() + "," +
                    "\"effectiveFrom\":" + (t.getEffectiveFrom() != null ? "\"" + escape(t.getEffectiveFrom()) + "\"" : "null") + "," +
                    "\"effectiveTo\":" + (t.getEffectiveTo() != null ? "\"" + escape(t.getEffectiveTo()) + "\"" : "null") + "," +
                    "\"version\":" + t.getVersion() + "," +
                    "\"createdAt\":" + t.getCreatedAt() + "," +
                    "\"updatedAt\":" + t.getUpdatedAt() +
                    "}";
        }
        if (obj instanceof TimetableEntry) {
            TimetableEntry e = (TimetableEntry) obj;
            return "{" +
                    "\"id\":\"" + escape(e.getId()) + "\"," +
                    "\"timetableId\":\"" + escape(e.getTimetableId()) + "\"," +
                    "\"versionNo\":" + e.getVersionNo() + "," +
                    "\"batchId\":\"" + escape(e.getBatchId()) + "\"," +
                    "\"subjectId\":\"" + escape(e.getSubjectId()) + "\"," +
                    "\"facultyId\":\"" + escape(e.getFacultyId()) + "\"," +
                    "\"roomId\":" + (e.getRoomId() != null ? "\"" + escape(e.getRoomId()) + "\"" : "null") + "," +
                    "\"dayOfWeek\":\"" + e.getDayOfWeek().name() + "\"," +
                    "\"period\":" + e.getPeriod() + "," +
                    "\"periodId\":\"" + escape(e.getPeriodId()) + "\"," +
                    "\"startTime\":" + (e.getStartTime() != null ? "\"" + escape(e.getStartTime()) + "\"" : "null") + "," +
                    "\"endTime\":" + (e.getEndTime() != null ? "\"" + escape(e.getEndTime()) + "\"" : "null") + "," +
                    "\"entryType\":\"" + e.getEntryType().name() + "\"," +
                    "\"status\":\"" + e.getStatus().name() + "\"," +
                    "\"isAdHoc\":" + e.isAdHoc() +
                    "}";
        }
        if (obj instanceof TimetableVersion) {
            TimetableVersion v = (TimetableVersion) obj;
            return "{" +
                    "\"id\":\"" + escape(v.getId()) + "\"," +
                    "\"timetableId\":\"" + escape(v.getTimetableId()) + "\"," +
                    "\"versionNo\":" + v.getVersionNo() + "," +
                    "\"status\":\"" + v.getStatus().name() + "\"," +
                    "\"effectiveFrom\":" + (v.getEffectiveFrom() != null ? "\"" + escape(v.getEffectiveFrom()) + "\"" : "null") + "," +
                    "\"effectiveTo\":" + (v.getEffectiveTo() != null ? "\"" + escape(v.getEffectiveTo()) + "\"" : "null") + "," +
                    "\"validationStatus\":\"" + v.getValidationStatus().name() + "\"," +
                    "\"blockingConflictCount\":" + v.getBlockingConflictCount() + "," +
                    "\"warningCount\":" + v.getWarningCount() + "," +
                    "\"publishedAt\":" + v.getPublishedAt() + "," +
                    "\"publishedBy\":" + (v.getPublishedBy() != null ? "\"" + escape(v.getPublishedBy()) + "\"" : "null") + "," +
                    "\"entryCount\":" + v.getEntriesSnapshot().size() +
                    "}";
        }
        if (obj instanceof ConflictResult) {
            ConflictResult c = (ConflictResult) obj;
            return "{" +
                    "\"id\":\"" + escape(c.getId()) + "\"," +
                    "\"conflictType\":\"" + c.getConflictType().name() + "\"," +
                    "\"severity\":\"" + c.getSeverity().name() + "\"," +
                    "\"message\":\"" + escape(c.getMessage()) + "\"," +
                    "\"entryIds\":" + toJson(c.getEntryIds()) +
                    "}";
        }
        if (obj instanceof BulkEntriesResult) {
            BulkEntriesResult b = (BulkEntriesResult) obj;
            return "{" +
                    "\"successfulEntries\":" + toJson(b.successfulEntries) + "," +
                    "\"errors\":" + toJson(b.errors) +
                    "}";
        }
        if (obj instanceof BulkRowError) {
            BulkRowError e = (BulkRowError) obj;
            return "{" +
                    "\"rowIndex\":" + e.rowIndex + "," +
                    "\"errorCode\":\"" + escape(e.errorCode) + "\"," +
                    "\"reason\":\"" + escape(e.reason) + "\"" +
                    "}";
        }
        if (obj instanceof List) {
            List<?> l = (List<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < l.size(); i++) {
                sb.append(toJson(l.get(i)));
                if (i < l.size() - 1) sb.append(",");
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            int i = 0;
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                sb.append("\"").append(escape(String.valueOf(entry.getKey()))).append("\":").append(toJson(entry.getValue()));
                if (i < m.size() - 1) sb.append(",");
                i++;
            }
            sb.append("}");
            return sb.toString();
        }
        return "\"" + escape(obj.toString()) + "\"";
    }

    public static class MapBuilder {
        private final Map<String, Object> map = new LinkedHashMap<>();
        public static MapBuilder create() { return new MapBuilder(); }
        public MapBuilder put(String k, Object v) { map.put(k, v); return this; }
        public Map<String, Object> build() { return map; }
    }
}
