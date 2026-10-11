package com.campx.academic.attendance.controller;

import com.campx.academic.attendance.exception.*;
import com.campx.academic.attendance.model.AttendanceModels.*;
import com.campx.academic.attendance.model.ErrorResponse;
import com.campx.academic.attendance.service.AttendanceDomainService;
import com.campx.academic.attendance.service.MetricsCollector;
import com.campx.academic.attendance.service.RateLimiter;
import com.campx.academic.attendance.service.StructuredLogEntry;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
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
 * HTTP REST Controller for ACD-06: Attendance Management Service.
 * Serves endpoints mounted under {@code /api/v1/academics/attendance/**}, {@code /api/v1/attendance/**}, and canonical aliases.
 */
public class AttendanceController implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AttendanceController.class);

    private final AttendanceDomainService domainService;
    private final RateLimiter rateLimiter;
    private final MetricsCollector metricsCollector = MetricsCollector.getInstance();

    // Regex routing patterns
    private final Pattern pSessionSubmit = Pattern.compile("^/sessions/([^/]+)/submit/?$");
    private final Pattern pSessionLock = Pattern.compile("^/sessions/([^/]+)/lock/?$");
    private final Pattern pSessionArchive = Pattern.compile("^/sessions/([^/]+)/archive/?$");
    private final Pattern pSessionRecords = Pattern.compile("^/sessions/([^/]+)/records/?$");
    private final Pattern pRecordCorrect = Pattern.compile("^/sessions/([^/]+)/records/([^/]+)/correct/?$");
    private final Pattern pSessionCorrections = Pattern.compile("^/sessions/([^/]+)/corrections/?$");
    private final Pattern pSessionItem = Pattern.compile("^/sessions/([^/]+)/?$");
    private final Pattern pStudentSummary = Pattern.compile("^/summaries/student/([^/]+)/?$");
    private final Pattern pBatchSummary = Pattern.compile("^/summaries/batch/([^/]+)/?$");
    private final Pattern pStudentHistory = Pattern.compile("^/student/([^/]+)/?$");

    public AttendanceController(AttendanceDomainService domainService) {
        this.domainService = domainService;
        this.rateLimiter = new RateLimiter(100, 50.0); // 100 capacity, 50 tokens/sec
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

            // External API Key check (Story 68, 70)
            String apiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                ApiKeyRecord keyRecord = domainService.validateApiKey(apiKey.trim(), tenantId);
                if (keyRecord == null) {
                    finalOutcome = "UNAUTHORIZED";
                    finalResponseCode = 401;
                    sendError(exchange, 401, "ACD_ATTENDANCE_UNAUTHORIZED", "Invalid, revoked, or expired API Key", null, fullPath);
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
                    sendError(exchange, 403, "ACD_ATTENDANCE_FORBIDDEN", "External API consumers have read-only access", null, fullPath);
                    return;
                }
            }

            LogContext.setService("ACD-06-AttendanceService");
            exchange.getResponseHeaders().set("X-Trace-Id", traceId);
            exchange.getResponseHeaders().set("X-Correlation-Id", traceId);

            // Operational Endpoints
            if ("/actuator/health".equals(fullPath) || fullPath.endsWith("/health")) {
                sendJson(exchange, 200, "{\"status\":\"UP\",\"service\":\"ACD-06-AttendanceService\"}");
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

            // Rate Limiter Check (Story 61)
            RateLimiter.RateLimitResult rateCheck = rateLimiter.tryAcquire(tenantId + ":" + userId);
            exchange.getResponseHeaders().set("X-RateLimit-Limit", String.valueOf(rateCheck.getLimit()));
            exchange.getResponseHeaders().set("X-RateLimit-Remaining", String.valueOf(rateCheck.getRemaining()));
            if (!rateCheck.isAllowed()) {
                finalOutcome = "RATE_LIMITED";
                finalResponseCode = 429;
                exchange.getResponseHeaders().set("Retry-After", String.valueOf(rateCheck.getRetryAfterSeconds()));
                metricsCollector.recordError("ACD_ATTENDANCE_RATE_LIMIT_EXCEEDED");
                metricsCollector.recordRequest(method, fullPath, 429, System.currentTimeMillis() - startTime);
                sendError(exchange, 429, "ACD_ATTENDANCE_RATE_LIMIT_EXCEEDED", "Too many requests. Limit exceeded.", null, fullPath);
                return;
            }

            // Normalize path
            String path = fullPath;
            if (path.startsWith("/api/v1/academics/attendance")) {
                path = path.substring("/api/v1/academics/attendance".length());
            } else if (path.startsWith("/api/v1/attendance")) {
                path = path.substring("/api/v1/attendance".length());
            } else if (path.startsWith("/v1/attendance")) {
                path = path.substring("/v1/attendance".length());
            }
            if (path.isEmpty()) {
                path = "/";
            }

            // Idempotency check for mutating requests (Story 60)
            if (idempotencyKey != null && !idempotencyKey.trim().isEmpty() &&
                    ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))) {
                IdempotencyRecord cached = domainService.getIdempotency(idempotencyKey.trim(), tenantId);
                if (cached != null) {
                    metricsCollector.recordIdempotencyHit();
                    finalResponseCode = cached.getStatusCode();
                    sendJson(exchange, cached.getStatusCode(), cached.getResponseBody());
                    return;
                }
            }

            String requestBody = readBody(exchange);
            String responseJson;
            int responseCode = 200;

            // RBAC Authorization Matrix (Story 64, 65, 66)
            if (!isAuthorized(method, path, userRole, userId, requestBody)) {
                finalOutcome = "FORBIDDEN";
                finalResponseCode = 403;
                sendError(exchange, 403, "ACD_ATTENDANCE_FORBIDDEN",
                        "Role '" + userRole + "' is not authorized for " + method + " " + fullPath, null, fullPath);
                return;
            }

            // Status Catalog: GET /status-catalog
            if ("/status-catalog".equals(path) && "GET".equalsIgnoreCase(method)) {
                List<StatusCatalogEntry> list = domainService.getStatusCatalog();
                responseJson = buildSuccessEnvelope(list, "Status catalog retrieved", traceId);
                responseCode = 200;
            }
            // Route Dispatching
            else if ("/events".equals(path) || path.startsWith("/events/")) {
                Map<String, Object> eventMap = parseSimpleJsonMap(requestBody);
                boolean consumed = domainService.consumeEvent(eventMap);
                responseJson = "{\"success\":" + consumed + ",\"status\":\"" + (consumed ? "EVENT_PROCESSED" : "FAILED") + "\"}";
                responseCode = consumed ? 200 : 400;
            }
            // Bulk Import: POST /import
            else if ("/import".equals(path) && "POST".equalsIgnoreCase(method)) {
                List<BulkImportRow> rows = parseBulkImportRows(requestBody);
                Map<String, Object> importRes = domainService.bulkImport(rows, tenantId, userId, userRole);
                responseJson = buildSuccessEnvelope(importRes, "Bulk attendance imported", traceId);
                responseCode = 200;
            }
            // Device Capture: POST /device/capture
            else if ("/device/capture".equals(path) && "POST".equalsIgnoreCase(method)) {
                DeviceCaptureRequest capReq = parseDeviceCaptureRequest(requestBody);
                Map<String, Object> capRes = domainService.deviceCapture(capReq, tenantId);
                responseJson = buildSuccessEnvelope(capRes, "Device attendance captured", traceId);
                responseCode = 201;
            }
            // Direct Corrections endpoint: POST /corrections
            else if ("/corrections".equals(path) && "POST".equalsIgnoreCase(method)) {
                Map<String, String> m = parseSimpleJson(requestBody);
                String sId = m.get("sessionId");
                String stuId = m.get("studentId");
                CorrectionRequest cReq = new CorrectionRequest();
                cReq.studentId = stuId;
                cReq.newStatus = m.get("newStatus");
                cReq.reason = m.get("reason");
                if (m.containsKey("expectedVersion")) {
                    cReq.expectedVersion = Integer.parseInt(m.get("expectedVersion"));
                }
                AttendanceCorrection corr = domainService.correctAttendance(sId, stuId, cReq, tenantId, userId, userRole);
                responseJson = buildSuccessEnvelope(corr, "Attendance corrected successfully", traceId);
                responseCode = 200;
            }
            // Reconcile Summaries: POST /summaries/reconcile
            else if ("/summaries/reconcile".equals(path) && "POST".equalsIgnoreCase(method)) {
                int reconciledCount = domainService.reconcileAllSummaries(tenantId);
                responseJson = buildSuccessEnvelope(Collections.singletonMap("reconciledSummaries", reconciledCount), "Summaries reconciled", traceId);
                responseCode = 200;
            }
            // Shortage query: GET /summaries/shortage
            else if ("/summaries/shortage".equals(path) && "GET".equalsIgnoreCase(method)) {
                Map<String, String> q = parseQuery(exchange.getRequestURI().getQuery());
                String batchId = q.get("batchId");
                List<AttendanceSummary> shortages = domainService.getShortageList(batchId, tenantId);
                responseJson = buildSuccessEnvelope(shortages, "Shortage list retrieved", traceId);
            }
            // Report: GET /report
            else if ("/report".equals(path) && "GET".equalsIgnoreCase(method)) {
                Map<String, String> q = parseQuery(exchange.getRequestURI().getQuery());
                Map<String, Object> report = domainService.generateReport(tenantId, q.get("batchId"), q.get("subjectId"), q.get("fromDate"), q.get("toDate"));
                responseJson = buildSuccessEnvelope(report, "Attendance report generated", traceId);
            }
            // Export: GET /export
            else if ("/export".equals(path) && "GET".equalsIgnoreCase(method)) {
                Map<String, String> q = parseQuery(exchange.getRequestURI().getQuery());
                Map<String, Object> export = domainService.exportAttendance(tenantId, q.get("batchId"), q.get("format"), userId, userRole);
                responseJson = buildSuccessEnvelope(export, "Attendance exported", traceId);
            }
            // Close EOD: POST /sessions/close-eod
            else if ("/sessions/close-eod".equals(path) && "POST".equalsIgnoreCase(method)) {
                Map<String, String> q = parseQuery(exchange.getRequestURI().getQuery());
                int closedCount = domainService.closeEndOfDaySessions(tenantId, q.get("date"), userId);
                responseJson = buildSuccessEnvelope(Collections.singletonMap("closedSessions", closedCount), "End-of-day sessions closed", traceId);
            }
            // Pattern Matches on /sessions, /summaries, /student
            else {
                responseJson = dispatchPatternRoutes(method, path, requestBody, tenantId, userId, userRole, traceId, exchange);
                if ("POST".equalsIgnoreCase(method) && ("/sessions".equals(path) || path.endsWith("/records"))) {
                    responseCode = 201;
                }
            }

            // Save idempotency record on success
            if (idempotencyKey != null && !idempotencyKey.trim().isEmpty() &&
                    ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))) {
                domainService.saveIdempotency(idempotencyKey.trim(), tenantId, String.valueOf(requestBody.hashCode()),
                        responseCode, responseJson);
            }

            long duration = System.currentTimeMillis() - startTime;
            finalResponseCode = responseCode;
            metricsCollector.recordRequest(method, fullPath, responseCode, duration);
            sendJson(exchange, responseCode, responseJson);

        } catch (AttendanceException ae) {
            long duration = System.currentTimeMillis() - startTime;
            finalResponseCode = ae.getHttpStatus();
            finalOutcome = "FAILURE";
            metricsCollector.recordError(ae.getErrorCode());
            metricsCollector.recordRequest(method, fullPath, ae.getHttpStatus(), duration);
            sendError(exchange, ae.getHttpStatus(), ae.getErrorCode(), ae.getMessage(), ae.getDetails(), fullPath);
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            finalResponseCode = 500;
            finalOutcome = "ERROR";
            logger.error("Unhandled exception processing attendance request: {}", ex.getMessage(), ex);
            metricsCollector.recordError("ACD_ATTENDANCE_INTERNAL_ERROR");
            metricsCollector.recordRequest(method, fullPath, 500, duration);
            sendError(exchange, 500, "ACD_ATTENDANCE_INTERNAL_ERROR", "Internal Server Error: " + ex.getMessage(), null, fullPath);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            StructuredLogEntry logEntry = StructuredLogEntry.builder()
                    .service("ACD-06-AttendanceService")
                    .tenantId(currentTenantId)
                    .requestId(currentTraceId)
                    .correlationId(currentTraceId)
                    .actorId(currentUserId)
                    .operation(method + " " + fullPath)
                    .outcome(finalOutcome)
                    .durationMs(duration)
                    .build();
            logger.info("{}", logEntry.toJson());
            LogContext.clear();
        }
    }

    private String dispatchPatternRoutes(String method, String path, String body,
                                         String tenantId, String userId, String userRole, String traceId,
                                         HttpExchange exchange) {
        Matcher m;

        // /sessions (POST: create, GET: list)
        if ("/sessions".equals(path) || "/sessions/".equals(path) || "/".equals(path)) {
            if ("POST".equalsIgnoreCase(method)) {
                CreateSessionRequest req = parseCreateSessionRequest(body);
                AttendanceSession sess = domainService.createSession(req, tenantId, userId, userRole);
                return buildSuccessEnvelope(sess, "Attendance session created", traceId);
            } else if ("GET".equalsIgnoreCase(method)) {
                Map<String, String> q = parseQuery(exchange.getRequestURI().getQuery());
                SessionStatus st = q.containsKey("status") ? SessionStatus.valueOf(q.get("status").toUpperCase()) : null;
                List<AttendanceSession> list = domainService.listSessions(tenantId, q.get("batchId"), q.get("subjectId"), q.get("date"), st);
                return buildSuccessEnvelope(list, "Attendance sessions retrieved", traceId);
            }
        }

        // /sessions/{id}/submit
        if ((m = pSessionSubmit.matcher(path)).matches()) {
            String sId = m.group(1);
            AttendanceSession sess = domainService.submitSession(sId, tenantId, userId, userRole);
            return buildSuccessEnvelope(sess, "Session submitted successfully", traceId);
        }

        // /sessions/{id}/lock
        if ((m = pSessionLock.matcher(path)).matches()) {
            String sId = m.group(1);
            AttendanceSession sess = domainService.lockSession(sId, tenantId, userId, userRole);
            return buildSuccessEnvelope(sess, "Session locked successfully", traceId);
        }

        // /sessions/{id}/archive
        if ((m = pSessionArchive.matcher(path)).matches()) {
            String sId = m.group(1);
            AttendanceSession sess = domainService.archiveSession(sId, tenantId, userId, userRole);
            return buildSuccessEnvelope(sess, "Session archived successfully", traceId);
        }

        // /sessions/{id}/records (POST: mark attendance)
        if ((m = pSessionRecords.matcher(path)).matches()) {
            String sId = m.group(1);
            MarkAttendanceRequest req = parseMarkAttendanceRequest(body);
            MarkSummaryResponse res = domainService.markAttendance(sId, req, tenantId, userId, userRole);
            return buildSuccessEnvelope(res, "Attendance marked successfully", traceId);
        }

        // /sessions/{id}/records/{studentId}/correct (PUT: correct record)
        if ((m = pRecordCorrect.matcher(path)).matches()) {
            String sId = m.group(1);
            String stuId = m.group(2);
            CorrectionRequest req = parseCorrectionRequest(body);
            AttendanceCorrection corr = domainService.correctAttendance(sId, stuId, req, tenantId, userId, userRole);
            return buildSuccessEnvelope(corr, "Attendance record corrected", traceId);
        }

        // /sessions/{id}/corrections (GET: list corrections for session)
        if ((m = pSessionCorrections.matcher(path)).matches()) {
            String sId = m.group(1);
            List<AttendanceCorrection> list = domainService.getSessionCorrections(sId, tenantId);
            return buildSuccessEnvelope(list, "Session corrections retrieved", traceId);
        }

        // /sessions/{id} (GET: detail, PUT: update, DELETE: cancel)
        if ((m = pSessionItem.matcher(path)).matches()) {
            String sId = m.group(1);
            if ("GET".equalsIgnoreCase(method)) {
                AttendanceSession sess = domainService.getSession(sId, tenantId);
                return buildSuccessEnvelope(sess, "Attendance session retrieved", traceId);
            } else if ("PUT".equalsIgnoreCase(method)) {
                UpdateSessionRequest req = parseUpdateSessionRequest(body);
                AttendanceSession updated = domainService.updateSession(sId, req, tenantId, userId, userRole);
                return buildSuccessEnvelope(updated, "Session updated successfully", traceId);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                Map<String, String> q = parseQuery(exchange.getRequestURI().getQuery());
                String reason = q.getOrDefault("reason", "Cancelled by authorized administrator");
                AttendanceSession cancelled = domainService.cancelSession(sId, tenantId, userId, userRole, reason);
                return buildSuccessEnvelope(cancelled, "Session cancelled", traceId);
            }
        }

        // /summaries/student/{studentId}
        if ((m = pStudentSummary.matcher(path)).matches()) {
            String stuId = m.group(1);
            List<AttendanceSummary> sums = domainService.getStudentSummaries(stuId, tenantId);
            return buildSuccessEnvelope(sums, "Student summaries retrieved", traceId);
        }

        // /summaries/batch/{batchId}
        if ((m = pBatchSummary.matcher(path)).matches()) {
            String bId = m.group(1);
            List<AttendanceSummary> sums = domainService.getBatchSummaries(bId, tenantId);
            return buildSuccessEnvelope(sums, "Batch summaries retrieved", traceId);
        }

        // /student/{studentId} (History view)
        if ((m = pStudentHistory.matcher(path)).matches()) {
            String stuId = m.group(1);
            List<AttendanceRecord> hist = domainService.getStudentHistory(stuId, tenantId);
            return buildSuccessEnvelope(hist, "Student attendance history retrieved", traceId);
        }

        throw new AttendanceBadRequestException("Unrecognized endpoint: " + method + " " + path);
    }

    // =========================================================================
    // RBAC Authorization Matrix (Stories 64, 65, 66, 67, 68)
    // =========================================================================

    private boolean isAuthorized(String method, String path, String userRole, String userId, String body) {
        if (userRole == null || userRole.trim().isEmpty()) {
            return false;
        }
        String role = userRole.toUpperCase();

        // Super Admin & Academic Admin have full access across the institution
        if ("SUPER_ADMIN".equals(role) || "ACADEMIC_ADMIN".equals(role)) {
            return true;
        }

        // Faculty: Create, Mark, Submit, Correct assigned classes; view reports/summaries
        if ("FACULTY".equals(role)) {
            return true; // fine-grained assignment verified in domain service
        }

        // Student: Can only view own summary and own history (Story 66)
        if ("STUDENT".equals(role)) {
            if ("GET".equalsIgnoreCase(method)) {
                if (path.startsWith("/student/")) {
                    String targetStudent = path.substring("/student/".length());
                    return userId.equalsIgnoreCase(targetStudent);
                }
                if (path.startsWith("/summaries/student/")) {
                    String targetStudent = path.substring("/summaries/student/".length());
                    return userId.equalsIgnoreCase(targetStudent);
                }
            }
            return false;
        }

        // Parent: Can view linked student history/summary (Story 67)
        if ("PARENT".equals(role)) {
            return "GET".equalsIgnoreCase(method) &&
                    (path.startsWith("/student/") || path.startsWith("/summaries/student/"));
        }

        // Department Head: Read-only access to department batches/subjects (Story 68)
        if ("DEPARTMENT_HEAD".equals(role)) {
            return "GET".equalsIgnoreCase(method);
        }

        // Exam Cell: Read-only access to reports and summaries (Story 69)
        if ("EXAM_CELL".equals(role)) {
            return "GET".equalsIgnoreCase(method) &&
                    (path.startsWith("/report") || path.startsWith("/summaries") || path.startsWith("/export"));
        }

        // External API: Read-only via API key (Story 70)
        if ("EXTERNAL_API".equals(role)) {
            return "GET".equalsIgnoreCase(method);
        }

        // Management: Read-only
        if ("MANAGEMENT".equals(role)) {
            return "GET".equalsIgnoreCase(method);
        }

        return false;
    }

    // =========================================================================
    // JSON & Envelope Helpers
    // =========================================================================

    private void sendJson(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String errorCode, String detail,
                           List<String> details, String path) throws IOException {
        ErrorResponse err = new ErrorResponse(errorCode, detail, statusCode, path, details);
        sendJson(exchange, statusCode, err.toJson());
    }

    private String buildSuccessEnvelope(Object data, String message, String traceId) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"success\":true,");
        sb.append("\"message\":\"").append(escape(message)).append("\",");
        sb.append("\"requestId\":\"").append(escape(traceId)).append("\",");
        sb.append("\"correlationId\":\"").append(escape(traceId)).append("\",");
        sb.append("\"timestamp\":").append(System.currentTimeMillis()).append(",");
        sb.append("\"data\":").append(serializeAny(data));
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String serializeAny(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escape((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return String.valueOf(obj);
        if (obj instanceof AttendanceSession) {
            AttendanceSession s = (AttendanceSession) obj;
            return "{\"sessionId\":\"" + s.getId() + "\",\"batchId\":\"" + s.getBatchId() +
                    "\",\"subjectId\":\"" + s.getSubjectId() + "\",\"timetableEntryId\":\"" + s.getTimetableEntryId() +
                    "\",\"attendanceDate\":\"" + s.getAttendanceDate() + "\",\"periodNo\":" + s.getPeriodNo() +
                    ",\"startTime\":\"" + s.getStartTime() + "\",\"endTime\":\"" + s.getEndTime() +
                    "\",\"status\":\"" + s.getStatus() + "\",\"rosterCount\":" + s.getRosterCount() +
                    ",\"presentCount\":" + s.getPresentCount() + ",\"absentCount\":" + s.getAbsentCount() +
                    ",\"leaveCount\":" + s.getLeaveCount() + ",\"lateCount\":" + s.getLateCount() +
                    ",\"version\":" + s.getVersion() + "}";
        }
        if (obj instanceof AttendanceRecord) {
            AttendanceRecord r = (AttendanceRecord) obj;
            return "{\"recordId\":\"" + r.getId() + "\",\"sessionId\":\"" + r.getSessionId() +
                    "\",\"studentId\":\"" + r.getStudentId() + "\",\"status\":\"" + r.getStatus() +
                    "\",\"source\":\"" + r.getSource() + "\",\"recordVersion\":" + r.getRecordVersion() + "}";
        }
        if (obj instanceof AttendanceCorrection) {
            AttendanceCorrection c = (AttendanceCorrection) obj;
            return "{\"correctionId\":\"" + c.getId() + "\",\"sessionId\":\"" + c.getSessionId() +
                    "\",\"studentId\":\"" + c.getStudentId() + "\",\"oldStatus\":\"" + c.getOldStatus() +
                    "\",\"newStatus\":\"" + c.getNewStatus() + "\",\"reason\":\"" + escape(c.getReason()) +
                    "\",\"correctedBy\":\"" + c.getCorrectedBy() + "\",\"workflowStatus\":\"" + c.getWorkflowStatus() + "\"}";
        }
        if (obj instanceof AttendanceSummary) {
            AttendanceSummary s = (AttendanceSummary) obj;
            return "{\"summaryId\":\"" + s.getId() + "\",\"studentId\":\"" + s.getStudentId() +
                    "\",\"subjectId\":\"" + s.getSubjectId() + "\",\"batchId\":\"" + s.getBatchId() +
                    "\",\"totalSessions\":" + s.getTotalSessions() + ",\"presentCount\":" + s.getPresentCount() +
                    ",\"absentCount\":" + s.getAbsentCount() + ",\"attendancePercentage\":" + s.getAttendancePercentage() +
                    ",\"shortageFlag\":" + s.isShortageFlag() + ",\"shortageThreshold\":" + s.getShortageThreshold() + "}";
        }
        if (obj instanceof StatusCatalogEntry) {
            StatusCatalogEntry sc = (StatusCatalogEntry) obj;
            return "{\"statusCode\":\"" + sc.getStatusCode() + "\",\"name\":\"" + escape(sc.getName()) +
                    "\",\"countsAsPresent\":" + sc.isCountsAsPresent() + ",\"weight\":" + sc.getWeight() +
                    ",\"requiresReason\":" + sc.isRequiresReason() + "}";
        }
        if (obj instanceof MarkSummaryResponse) {
            MarkSummaryResponse r = (MarkSummaryResponse) obj;
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"sessionId\":\"").append(r.sessionId).append("\",");
            sb.append("\"markedCount\":").append(r.markedCount).append(",");
            sb.append("\"presentCount\":").append(r.presentCount).append(",");
            sb.append("\"absentCount\":").append(r.absentCount).append(",");
            sb.append("\"leaveCount\":").append(r.leaveCount).append(",");
            sb.append("\"shortageStudents\":[");
            for (int i = 0; i < r.shortageStudents.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escape(r.shortageStudents.get(i))).append("\"");
            }
            sb.append("]}");
            return sb.toString();
        }
        if (obj instanceof List) {
            List<?> l = (List<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(serializeAny(l.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            int i = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (i++ > 0) sb.append(",");
                sb.append("\"").append(escape(String.valueOf(e.getKey()))).append("\":").append(serializeAny(e.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        return "\"" + escape(obj.toString()) + "\"";
    }

    private String readBody(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private CreateSessionRequest parseCreateSessionRequest(String body) {
        Map<String, String> m = parseSimpleJson(body);
        CreateSessionRequest r = new CreateSessionRequest();
        r.batchId = m.get("batchId");
        r.subjectId = m.get("subjectId");
        r.timetableEntryId = m.get("timetableEntryId");
        r.attendanceDate = m.get("attendanceDate");
        if (m.containsKey("periodNo")) {
            try { r.periodNo = Integer.parseInt(m.get("periodNo")); } catch (Exception ignored) {}
        }
        if (m.containsKey("startTime")) r.startTime = m.get("startTime");
        if (m.containsKey("endTime")) r.endTime = m.get("endTime");
        return r;
    }

    private UpdateSessionRequest parseUpdateSessionRequest(String body) {
        Map<String, String> m = parseSimpleJson(body);
        UpdateSessionRequest r = new UpdateSessionRequest();
        r.startTime = m.get("startTime");
        r.endTime = m.get("endTime");
        if (m.containsKey("expectedVersion")) {
            try { r.expectedVersion = Integer.parseInt(m.get("expectedVersion")); } catch (Exception ignored) {}
        }
        return r;
    }

    private MarkAttendanceRequest parseMarkAttendanceRequest(String body) {
        MarkAttendanceRequest req = new MarkAttendanceRequest();
        if (body == null || body.trim().isEmpty()) return req;

        // Parse submittedBy
        Matcher mSub = Pattern.compile("\"submittedBy\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        if (mSub.find()) req.submittedBy = mSub.group(1);

        // Parse records array: [{"studentId":"...", "status":"..."}, ...]
        Matcher mRec = Pattern.compile("\\{[^{}]*\"studentId\"\\s*:\\s*\"([^\"]+)\"[^{}]*\\}").matcher(body);
        while (mRec.find()) {
            String itemBlock = mRec.group(0);
            Matcher mId = Pattern.compile("\"studentId\"\\s*:\\s*\"([^\"]+)\"").matcher(itemBlock);
            Matcher mSt = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]+)\"").matcher(itemBlock);
            Matcher mRsn = Pattern.compile("\"statusReason\"\\s*:\\s*\"([^\"]+)\"").matcher(itemBlock);

            String stuId = mId.find() ? mId.group(1) : null;
            String st = mSt.find() ? mSt.group(1) : "PRESENT";
            String rsn = mRsn.find() ? mRsn.group(1) : null;

            if (stuId != null) {
                req.records.add(new RecordItem(stuId, st, rsn));
            }
        }
        return req;
    }

    private CorrectionRequest parseCorrectionRequest(String body) {
        Map<String, String> m = parseSimpleJson(body);
        CorrectionRequest r = new CorrectionRequest();
        r.studentId = m.get("studentId");
        r.newStatus = m.get("newStatus");
        r.reason = m.get("reason");
        if (m.containsKey("expectedVersion")) {
            try { r.expectedVersion = Integer.parseInt(m.get("expectedVersion")); } catch (Exception ignored) {}
        }
        return r;
    }

    private List<BulkImportRow> parseBulkImportRows(String body) {
        List<BulkImportRow> rows = new ArrayList<>();
        Matcher mRow = Pattern.compile("\\{[^{}]*\"studentId\"\\s*:\\s*\"([^\"]+)\"[^{}]*\\}").matcher(body);
        while (mRow.find()) {
            String block = mRow.group(0);
            Map<String, String> map = parseSimpleJson(block);
            BulkImportRow row = new BulkImportRow();
            row.batchId = map.get("batchId");
            row.subjectId = map.get("subjectId");
            row.attendanceDate = map.get("attendanceDate");
            row.studentId = map.get("studentId");
            row.status = map.get("status");
            row.statusReason = map.get("statusReason");
            if (map.containsKey("periodNo")) {
                try { row.periodNo = Integer.parseInt(map.get("periodNo")); } catch (Exception ignored) {}
            }
            rows.add(row);
        }
        return rows;
    }

    private DeviceCaptureRequest parseDeviceCaptureRequest(String body) {
        Map<String, String> m = parseSimpleJson(body);
        DeviceCaptureRequest r = new DeviceCaptureRequest();
        r.deviceId = m.get("deviceId");
        r.cardUid = m.get("cardUid");
        r.studentId = m.get("studentId");
        r.readerLocation = m.get("readerLocation");
        return r;
    }

    private Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return map;
        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:\\s*([0-9.]+|\"[^\"]*\"|true|false)").matcher(json);
        while (m.find()) {
            String key = m.group(1);
            String val = m.group(2).replace("\"", "");
            map.put(key, val);
        }
        return map;
    }

    private Map<String, Object> parseSimpleJsonMap(String json) {
        Map<String, Object> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return map;
        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:\\s*([0-9.]+|\"[^\"]*\"|true|false)").matcher(json);
        while (m.find()) {
            String key = m.group(1);
            String val = m.group(2);
            if (val.startsWith("\"") && val.endsWith("\"")) {
                map.put(key, val.substring(1, val.length() - 1));
            } else if ("true".equalsIgnoreCase(val) || "false".equalsIgnoreCase(val)) {
                map.put(key, Boolean.parseBoolean(val));
            } else {
                map.put(key, val);
            }
        }
        return map;
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.trim().isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                map.put(kv[0].trim(), kv[1].trim());
            } else if (kv.length == 1) {
                map.put(kv[0].trim(), "");
            }
        }
        return map;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
