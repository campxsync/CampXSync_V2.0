package com.campx.academic.batch.controller;

import com.campx.academic.batch.exception.*;
import com.campx.academic.batch.model.BatchModels.*;
import com.campx.academic.batch.model.ErrorResponse;
import com.campx.academic.batch.service.BatchDomainService;
import com.campx.academic.batch.service.MetricsCollector;
import com.campx.academic.batch.service.RateLimiter;
import com.campx.academic.batch.service.StructuredLogEntry;
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
 * HTTP REST Controller for ACD-04: Batch Management Service.
 * Serves endpoints mounted under {@code /api/v1/academics/batches/**} and {@code /api/v1/batches/**}.
 */
public class BatchController implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(BatchController.class);

    private final BatchDomainService domainService;
    private final RateLimiter rateLimiter;
    private final MetricsCollector metricsCollector = MetricsCollector.getInstance();

    public BatchController(BatchDomainService domainService) {
        this(domainService, new RateLimiter());
    }

    public BatchController(BatchDomainService domainService, RateLimiter rateLimiter) {
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

        // 1. Correlation Context Extraction (§51)
        String traceId = exchange.getRequestHeaders().getFirst("X-Trace-Id");
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = LogContext.initTraceId();
        } else {
            LogContext.setTraceId(traceId);
        }

        String tenantId = exchange.getRequestHeaders().getFirst("X-Tenant-Id");
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            LogContext.setTenantId(tenantId);
        } else {
            tenantId = "TENANT-001";
        }

        String userId = exchange.getRequestHeaders().getFirst("X-User-Id");
        if (userId != null && !userId.trim().isEmpty()) {
            LogContext.setUserId(userId);
        } else {
            userId = "admin-1";
        }

        String userRole = exchange.getRequestHeaders().getFirst("X-User-Role");
        if (userRole != null && !userRole.trim().isEmpty()) {
            LogContext.setUserRole(userRole);
        } else {
            userRole = "ACADEMIC_ADMIN";
        }

        String idempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");

        // External API Key check (Story 55)
        String apiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            ApiKeyRecord keyRecord = domainService.validateApiKey(apiKey.trim(), tenantId);
            if (keyRecord == null) {
                sendError(exchange, 401, "ACD_UNAUTHORIZED", "Invalid, revoked, or expired API Key", fullPath);
                return;
            }
            userRole = "EXTERNAL_API";
            userId = "api-consumer-" + keyRecord.getKeyId();
            LogContext.setUserRole(userRole);
            LogContext.setUserId(userId);

            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
                sendError(exchange, 403, "ACD_FORBIDDEN", "External API consumers have read-only access", fullPath);
                return;
            }
        }

        LogContext.setService("ACD-04-BatchManagementService");
        exchange.getResponseHeaders().set("X-Trace-Id", traceId);

        // Operational Endpoints
        if ("/actuator/health".equals(fullPath) || fullPath.endsWith("/health")) {
            sendJson(exchange, 200, "{\"status\":\"UP\",\"service\":\"ACD-04-BatchManagementService\"}");
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

        // Rate Limiter Check (Story 69)
        RateLimiter.RateLimitResult rateCheck = rateLimiter.tryAcquire(tenantId + ":" + userId);
        exchange.getResponseHeaders().set("X-RateLimit-Limit", String.valueOf(rateCheck.getLimit()));
        exchange.getResponseHeaders().set("X-RateLimit-Remaining", String.valueOf(rateCheck.getRemaining()));
        if (!rateCheck.isAllowed()) {
            exchange.getResponseHeaders().set("Retry-After", String.valueOf(rateCheck.getRetryAfterSeconds()));
            metricsCollector.recordError("ACD_RATE_LIMIT_EXCEEDED");
            metricsCollector.recordRequest(method, fullPath, 429, System.currentTimeMillis() - startTime);
            sendError(exchange, 429, "ACD_RATE_LIMIT_EXCEEDED", "Too many requests. Limit exceeded.", fullPath);
            return;
        }

        // Normalize path
        String path = fullPath;
        if (path.startsWith("/api/v1/academics/batches")) {
            path = path.substring("/api/v1/academics/batches".length());
        } else if (path.startsWith("/api/v1/batches")) {
            path = path.substring("/api/v1/batches".length());
        }
        if (path.isEmpty()) {
            path = "/";
        }

        String requestBody = readRequestBody(exchange);
        int responseStatus = 200;

        try (FlowTracker flow = logger.flow("BatchDispatch", method + ":" + fullPath)) {
            // Idempotency Check on mutation requests (Story 48)
            if (idempotencyKey != null && !idempotencyKey.trim().isEmpty() && !"GET".equalsIgnoreCase(method)) {
                IdempotencyRecord cached = domainService.checkIdempotency(idempotencyKey.trim(), tenantId);
                if (cached != null) {
                    metricsCollector.recordIdempotencyHit();
                    logger.info("Serving idempotent replay for key: {}", idempotencyKey);
                    exchange.getResponseHeaders().set("X-Idempotent-Replay", "true");
                    sendJson(exchange, cached.getStatusCode(), cached.getResponseBody());
                    return;
                }
            }

            dispatchRoute(exchange, method, path, requestBody, userRole, userId, tenantId, idempotencyKey, fullPath);
        } catch (BatchException be) {
            responseStatus = be.getHttpStatus();
            metricsCollector.recordError(be.getErrorCode());
            sendError(exchange, be.getHttpStatus(), be.getErrorCode(), be.getMessage(), fullPath);
        } catch (Exception ex) {
            responseStatus = 500;
            metricsCollector.recordError("ACD_INTERNAL_SERVER_ERROR");
            logger.error("Unhandled exception processing [{}] {}: {}", method, fullPath, ex.getMessage(), ex);
            sendError(exchange, 500, "ACD_INTERNAL_SERVER_ERROR", "Internal Server Error: " + ex.getMessage(), fullPath);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            metricsCollector.recordRequest(method, fullPath, responseStatus, duration);

            StructuredLogEntry logEntry = StructuredLogEntry.builder()
                    .service("ACD-04-BatchManagementService")
                    .tenantId(tenantId)
                    .requestId(UUID.randomUUID().toString().substring(0, 8))
                    .correlationId(traceId)
                    .actorId(userId)
                    .operation(method + " " + fullPath)
                    .outcome(responseStatus < 400 ? "SUCCESS" : "FAILURE")
                    .durationMs(duration)
                    .build();
            logger.info("StructuredAudit: {}", logEntry.toJson());
        }
    }

    private void dispatchRoute(HttpExchange exchange, String method, String path, String body,
                               String userRole, String userId, String tenantId, String idempotencyKey, String fullPath) throws IOException {

        // Endpoint: POST / (Create Batch)
        if ("POST".equalsIgnoreCase(method) && ("/".equals(path) || path.isEmpty())) {
            assertRole(userRole, "ACADEMIC_ADMIN", "DEPARTMENT_HEAD", "SUPER_ADMIN");
            Batch b = parseBatch(body);
            b.setTenantId(tenantId);
            Batch created = domainService.createBatch(b, userId, userRole, idempotencyKey);
            String respJson = formatSuccessResponse(serializeBatch(created), exchange);
            if (idempotencyKey != null) {
                domainService.saveIdempotencyRecord(idempotencyKey, tenantId, "CREATE_BATCH", 201, respJson);
            }
            sendJson(exchange, 201, respJson);
            return;
        }

        // Endpoint: GET / or /search (Search Batches)
        if ("GET".equalsIgnoreCase(method) && ("/".equals(path) || "/search".equals(path))) {
            Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getRawQuery());
            int page = parseQueryInt(queryParams, "page", 1);
            int pageSize = parseQueryInt(queryParams, "pageSize", 20);

            List<Batch> results = domainService.searchBatches(queryParams, page, pageSize, userRole, userId);
            int totalCount = domainService.countSearchResults(queryParams, userRole, userId);

            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < results.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(serializeBatch(results.get(i)));
            }
            sb.append("]");
            sendJson(exchange, 200, formatSuccessResponseWithPagination(sb.toString(), page, pageSize, totalCount, exchange));
            return;
        }

        // Endpoint: POST /merge (Merge Batches Request - Story 38)
        if ("POST".equalsIgnoreCase(method) && "/merge".equals(path)) {
            assertRole(userRole, "ACADEMIC_ADMIN", "DEPARTMENT_HEAD", "SUPER_ADMIN");
            BatchMergeRequest req = parseMergeRequest(body);
            BatchMergeRequest created = domainService.requestMerge(req, userId, userRole);
            sendJson(exchange, 202, formatSuccessResponse(serializeMergeRequest(created), exchange));
            return;
        }

        // Endpoint: POST /events/approval-decision (Consume ADM-02 approval decision - Story 39)
        if ("POST".equalsIgnoreCase(method) && "/events/approval-decision".equals(path)) {
            Map<String, String> payload = parseSimpleJson(body);
            String reqId = payload.get("requestId");
            String decisionStr = payload.get("decision");
            String decidedBy = payload.get("decidedBy");
            String reason = payload.get("reason");
            SplitMergeDecision decision = SplitMergeDecision.valueOf(decisionStr != null ? decisionStr.toUpperCase() : "REJECTED");
            domainService.consumeApprovalDecision(reqId, decision, decidedBy, System.currentTimeMillis(), reason);
            sendJson(exchange, 200, formatSuccessResponse("{\"status\":\"PROCESSED\",\"requestId\":\"" + reqId + "\"}", exchange));
            return;
        }

        // Endpoint: POST /events/student-status (Event consumption - Story 42)
        if ("POST".equalsIgnoreCase(method) && "/events/student-status".equals(path)) {
            Map<String, String> payload = parseSimpleJson(body);
            String eventId = payload.get("eventId");
            String studentId = payload.get("studentId");
            String status = payload.get("status");
            domainService.consumeStudentStatusChanged(eventId != null ? eventId : UUID.randomUUID().toString(), studentId, status);
            sendJson(exchange, 200, formatSuccessResponse("{\"status\":\"ACK\"}", exchange));
            return;
        }

        // Endpoint: POST /events/course-deactivated (Event consumption - Story 43)
        if ("POST".equalsIgnoreCase(method) && "/events/course-deactivated".equals(path)) {
            Map<String, String> payload = parseSimpleJson(body);
            String eventId = payload.get("eventId");
            String courseId = payload.get("courseId");
            domainService.consumeCourseDeactivated(eventId != null ? eventId : UUID.randomUUID().toString(), courseId);
            sendJson(exchange, 200, formatSuccessResponse("{\"status\":\"ACK\"}", exchange));
            return;
        }

        // Dynamic Sub-resource Regex Matching: /{id}/**
        Pattern rootPattern = Pattern.compile("^/([a-zA-Z0-9_-]+)(/.*)?$");
        Matcher m = rootPattern.matcher(path);
        if (m.matches()) {
            String batchId = m.group(1);
            String subPath = m.group(2) != null ? m.group(2) : "";

            // GET /{id} (Batch detail)
            if ("GET".equalsIgnoreCase(method) && subPath.isEmpty()) {
                Batch b = domainService.getBatch(batchId, userRole, userId);
                sendJson(exchange, 200, formatSuccessResponse(serializeBatch(b), exchange));
                return;
            }

            // PUT /{id} (Update Batch)
            if ("PUT".equalsIgnoreCase(method) && subPath.isEmpty()) {
                assertRole(userRole, "ACADEMIC_ADMIN", "DEPARTMENT_HEAD", "SUPER_ADMIN");
                Batch updateReq = parseBatch(body);
                long expectedVersion = updateReq.getVersion() > 0 ? updateReq.getVersion() : parseHeaderLong(exchange, "If-Match", 1L);
                Batch updated = domainService.updateBatch(batchId, updateReq, expectedVersion, userId, userRole);
                sendJson(exchange, 200, formatSuccessResponse(serializeBatch(updated), exchange));
                return;
            }

            // POST /{id}/open (Open / Activate)
            if ("POST".equalsIgnoreCase(method) && "/open".equals(subPath)) {
                assertRole(userRole, "ACADEMIC_ADMIN", "DEPARTMENT_HEAD", "SUPER_ADMIN");
                Batch opened = domainService.openBatch(batchId, userId, userRole);
                sendJson(exchange, 200, formatSuccessResponse(serializeBatch(opened), exchange));
                return;
            }

            // POST /{id}/close (Close Batch)
            if ("POST".equalsIgnoreCase(method) && "/close".equals(subPath)) {
                assertRole(userRole, "ACADEMIC_ADMIN", "REGISTRAR", "SUPER_ADMIN");
                Batch closed = domainService.closeBatch(batchId, userId, userRole);
                sendJson(exchange, 200, formatSuccessResponse(serializeBatch(closed), exchange));
                return;
            }

            // POST /{id}/reopen (Reopen Batch)
            if ("POST".equalsIgnoreCase(method) && "/reopen".equals(subPath)) {
                assertRole(userRole, "ACADEMIC_ADMIN", "REGISTRAR", "SUPER_ADMIN");
                Batch reopened = domainService.reopenBatch(batchId, userId, userRole);
                sendJson(exchange, 200, formatSuccessResponse(serializeBatch(reopened), exchange));
                return;
            }

            // POST /{id}/archive (Archive Batch)
            if ("POST".equalsIgnoreCase(method) && "/archive".equals(subPath)) {
                assertRole(userRole, "REGISTRAR", "SUPER_ADMIN");
                Batch archived = domainService.archiveBatch(batchId, userId, userRole);
                sendJson(exchange, 200, formatSuccessResponse(serializeBatch(archived), exchange));
                return;
            }

            // PUT /{id}/capacity (Update capacity)
            if ("PUT".equalsIgnoreCase(method) && "/capacity".equals(subPath)) {
                assertRole(userRole, "ACADEMIC_ADMIN", "DEPARTMENT_HEAD", "SUPER_ADMIN");
                Map<String, String> capPayload = parseSimpleJson(body);
                int cap = Integer.parseInt(capPayload.getOrDefault("capacity", "0"));
                Batch updated = domainService.updateCapacity(batchId, cap, userId, userRole);
                sendJson(exchange, 200, formatSuccessResponse(serializeBatch(updated), exchange));
                return;
            }

            // POST /{id}/capacity-override (Grant override)
            if ("POST".equalsIgnoreCase(method) && "/capacity-override".equals(subPath)) {
                assertRole(userRole, "ACADEMIC_ADMIN", "REGISTRAR", "SUPER_ADMIN");
                BatchCapacityOverride ovr = parseCapacityOverride(body);
                ovr.setTenantId(tenantId);
                BatchCapacityOverride granted = domainService.grantCapacityOverride(batchId, ovr, userId, userRole);
                sendJson(exchange, 201, formatSuccessResponse(serializeOverride(granted), exchange));
                return;
            }

            // DELETE /{id}/capacity-override/{overrideId} (Revoke override)
            if ("DELETE".equalsIgnoreCase(method) && subPath.startsWith("/capacity-override/")) {
                assertRole(userRole, "ACADEMIC_ADMIN", "REGISTRAR", "SUPER_ADMIN");
                String overrideId = subPath.substring("/capacity-override/".length());
                BatchCapacityOverride revoked = domainService.revokeCapacityOverride(batchId, overrideId, userId, userRole);
                sendJson(exchange, 200, formatSuccessResponse(serializeOverride(revoked), exchange));
                return;
            }

            // POST /{id}/sections (Create section)
            if ("POST".equalsIgnoreCase(method) && "/sections".equals(subPath)) {
                assertRole(userRole, "ACADEMIC_ADMIN", "DEPARTMENT_HEAD", "SUPER_ADMIN");
                BatchSection sec = parseSection(body);
                BatchSection created = domainService.createSection(batchId, sec, userId, userRole);
                sendJson(exchange, 201, formatSuccessResponse(serializeSection(created), exchange));
                return;
            }

            // GET /{id}/sections (List sections)
            if ("GET".equalsIgnoreCase(method) && "/sections".equals(subPath)) {
                List<BatchSection> secs = domainService.listSections(batchId);
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < secs.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(serializeSection(secs.get(i)));
                }
                sb.append("]");
                sendJson(exchange, 200, formatSuccessResponse(sb.toString(), exchange));
                return;
            }

            // POST /{id}/sections/{sectionId}/faculty (Assign faculty)
            Pattern secFacultyPattern = Pattern.compile("^/sections/([a-zA-Z0-9_-]+)/faculty$");
            Matcher mSec = secFacultyPattern.matcher(subPath);
            if ("POST".equalsIgnoreCase(method) && mSec.matches()) {
                assertRole(userRole, "ACADEMIC_ADMIN", "DEPARTMENT_HEAD", "SUPER_ADMIN");
                String sectionId = mSec.group(1);
                Map<String, String> fPayload = parseSimpleJson(body);
                String facultyId = fPayload.get("facultyId");
                BatchSection updated = domainService.assignFacultyToSection(batchId, sectionId, facultyId, userId, userRole);
                sendJson(exchange, 200, formatSuccessResponse(serializeSection(updated), exchange));
                return;
            }

            // POST /{id}/students (Enroll student - Story 23)
            if ("POST".equalsIgnoreCase(method) && "/students".equals(subPath)) {
                assertRole(userRole, "ACADEMIC_ADMIN", "DEPARTMENT_HEAD", "SUPER_ADMIN");
                Map<String, String> stuPayload = parseSimpleJson(body);
                String studentId = stuPayload.get("studentId");
                String sectionId = stuPayload.get("sectionId");
                String effectiveFrom = stuPayload.get("effectiveFrom");
                String memTypeStr = stuPayload.get("membershipType");
                MembershipType memType = memTypeStr != null ? MembershipType.valueOf(memTypeStr.toUpperCase()) : MembershipType.REGULAR;

                BatchRoster roster = domainService.addStudentToBatch(batchId, studentId, sectionId, memType, effectiveFrom, userId, userRole);
                String respJson = formatSuccessResponse(serializeRoster(roster), exchange);
                if (idempotencyKey != null) {
                    domainService.saveIdempotencyRecord(idempotencyKey, tenantId, "ADD_STUDENT", 201, respJson);
                }
                sendJson(exchange, 201, respJson);
                return;
            }

            // DELETE /{id}/students/{studentId} (Remove student - Story 26)
            if ("DELETE".equalsIgnoreCase(method) && subPath.startsWith("/students/")) {
                assertRole(userRole, "ACADEMIC_ADMIN", "DEPARTMENT_HEAD", "SUPER_ADMIN");
                String studentId = subPath.substring("/students/".length());
                Map<String, String> delPayload = parseSimpleJson(body);
                String reason = delPayload.get("reason");
                BatchRoster removed = domainService.removeStudentFromBatch(batchId, studentId, reason, userId, userRole);
                sendJson(exchange, 200, formatSuccessResponse(serializeRoster(removed), exchange));
                return;
            }

            // GET /{id}/roster (Get current roster - Story 28)
            if ("GET".equalsIgnoreCase(method) && "/roster".equals(subPath)) {
                List<BatchRoster> rosterList = domainService.getRoster(batchId, userRole, userId);
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < rosterList.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(serializeRoster(rosterList.get(i)));
                }
                sb.append("]");
                sendJson(exchange, 200, formatSuccessResponse(sb.toString(), exchange));
                return;
            }

            // GET /{id}/history (Get history / Point-in-time roster - Story 29, 54, 56)
            if ("GET".equalsIgnoreCase(method) && "/history".equals(subPath)) {
                Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getRawQuery());
                String pitStr = queryParams.get("pointInTime");
                Long pit = pitStr != null ? Long.parseLong(pitStr) : null;
                List<BatchRoster> historyList = domainService.getRosterHistory(batchId, pit, userRole);
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < historyList.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(serializeRoster(historyList.get(i)));
                }
                sb.append("]");
                sendJson(exchange, 200, formatSuccessResponse(sb.toString(), exchange));
                return;
            }

            // POST /{id}/split (Request Split - Story 37, 76)
            if ("POST".equalsIgnoreCase(method) && "/split".equals(subPath)) {
                assertRole(userRole, "ACADEMIC_ADMIN", "DEPARTMENT_HEAD", "SUPER_ADMIN");
                BatchSplitRequest splitReq = parseSplitRequest(body);
                BatchSplitRequest created = domainService.requestSplit(batchId, splitReq, userId, userRole);
                sendJson(exchange, 202, formatSuccessResponse(serializeSplitRequest(created), exchange));
                return;
            }
        }

        // If no match, return 404
        sendError(exchange, 404, "ACD_BATCH_NOT_FOUND", "Endpoint not found: " + fullPath, fullPath);
    }

    // =========================================================================
    // Role Verification Helper (Story 52)
    // =========================================================================

    private void assertRole(String callerRole, String... allowedRoles) {
        if (callerRole == null) {
            throw new BatchUnauthorizedException("Missing authentication role header");
        }
        for (String allowed : allowedRoles) {
            if (allowed.equalsIgnoreCase(callerRole) || "SUPER_ADMIN".equalsIgnoreCase(callerRole)) {
                return;
            }
        }
        throw new BatchForbiddenException("Caller role '" + callerRole + "' is not authorized for this operation");
    }

    // =========================================================================
    // JSON Serialization & Parsing Helpers
    // =========================================================================

    private String serializeBatch(Batch b) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"id\":\"").append(b.getId()).append("\",");
        sb.append("\"batchId\":\"").append(b.getId()).append("\",");
        sb.append("\"batchCode\":\"").append(b.getBatchCode()).append("\",");
        sb.append("\"name\":\"").append(escapeJson(b.getName())).append("\",");
        sb.append("\"courseId\":\"").append(b.getCourseId()).append("\",");
        sb.append("\"curriculumId\":\"").append(b.getCurriculumId() != null ? b.getCurriculumId() : "").append("\",");
        sb.append("\"departmentId\":\"").append(b.getDepartmentId() != null ? b.getDepartmentId() : "").append("\",");
        sb.append("\"campusId\":\"").append(b.getCampusId() != null ? b.getCampusId() : "").append("\",");
        sb.append("\"academicYear\":\"").append(b.getAcademicYear() != null ? b.getAcademicYear() : "").append("\",");
        sb.append("\"semesterNo\":").append(b.getSemesterNo()).append(",");
        sb.append("\"capacity\":").append(b.getCapacity()).append(",");
        sb.append("\"rosterCount\":").append(b.getRosterCount()).append(",");
        sb.append("\"status\":\"").append(b.getStatus().name()).append("\",");
        sb.append("\"version\":").append(b.getVersion()).append(",");
        sb.append("\"courseActive\":").append(b.isCourseActive()).append(",");
        sb.append("\"sections\":[");
        for (int i = 0; i < b.getSections().size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(serializeSection(b.getSections().get(i)));
        }
        sb.append("]}");
        return sb.toString();
    }

    private String serializeSection(BatchSection s) {
        return "{\"id\":\"" + s.getId() + "\",\"sectionId\":\"" + s.getId() + "\",\"sectionCode\":\"" + s.getSectionCode()
                + "\",\"sectionName\":\"" + escapeJson(s.getSectionName()) + "\",\"capacity\":" + s.getCapacity()
                + ",\"facultyId\":\"" + (s.getFacultyId() != null ? s.getFacultyId() : "") + "\",\"status\":\"" + s.getStatus() + "\"}";
    }

    private String serializeRoster(BatchRoster r) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"id\":\"").append(r.getId()).append("\",");
        sb.append("\"membershipId\":\"").append(r.getId()).append("\",");
        sb.append("\"batchId\":\"").append(r.getBatchId()).append("\",");
        sb.append("\"studentId\":\"").append(r.getStudentId()).append("\",");
        sb.append("\"sectionId\":\"").append(r.getSectionId() != null ? r.getSectionId() : "").append("\",");
        sb.append("\"status\":\"").append(r.getStatus().name()).append("\",");
        sb.append("\"membershipType\":\"").append(r.getMembershipType().name()).append("\",");
        sb.append("\"effectiveFrom\":\"").append(r.getEffectiveFrom() != null ? r.getEffectiveFrom() : "").append("\",");
        sb.append("\"effectiveTo\":\"").append(r.getEffectiveTo() != null ? r.getEffectiveTo() : "").append("\"");
        if (r.getMembershipSource() != null) {
            sb.append(",\"membershipSource\":\"").append(escapeJson(r.getMembershipSource())).append("\"");
        }
        if (r.getNotes() != null) {
            sb.append(",\"notes\":\"").append(escapeJson(r.getNotes())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private String serializeOverride(BatchCapacityOverride o) {
        return "{\"id\":\"" + o.getId() + "\",\"overrideId\":\"" + o.getId() + "\",\"batchId\":\"" + o.getBatchId()
                + "\",\"overrideCapacity\":" + o.getOverrideCapacity() + ",\"reason\":\"" + escapeJson(o.getReason())
                + "\",\"approvedBy\":\"" + escapeJson(o.getApprovedBy()) + "\",\"effectiveFrom\":\"" + (o.getEffectiveFrom() != null ? o.getEffectiveFrom() : "")
                + "\",\"effectiveTo\":\"" + (o.getEffectiveTo() != null ? o.getEffectiveTo() : "") + "\",\"status\":\"" + o.getStatus().name() + "\"}";
    }

    private String serializeSplitRequest(BatchSplitRequest r) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"requestId\":\"").append(r.getRequestId()).append("\",");
        sb.append("\"requestType\":\"SPLIT\",");
        sb.append("\"sourceBatchId\":\"").append(r.getSourceBatchId()).append("\",");
        sb.append("\"sourceBatchCode\":\"").append(r.getSourceBatchCode()).append("\",");
        sb.append("\"departmentId\":\"").append(r.getDepartmentId()).append("\",");
        sb.append("\"status\":\"").append(r.getStatus()).append("\",");
        sb.append("\"approverRole\":\"REGISTRAR\",");
        sb.append("\"proposedSections\":[");
        for (int i = 0; i < r.getProposedSections().size(); i++) {
            ProposedSectionSplit p = r.getProposedSections().get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"sectionCode\":\"").append(p.getSectionCode()).append("\",\"capacity\":").append(p.getCapacity()).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String serializeMergeRequest(BatchMergeRequest r) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"requestId\":\"").append(r.getRequestId()).append("\",");
        sb.append("\"requestType\":\"MERGE\",");
        sb.append("\"sourceBatchIds\":[");
        for (int i = 0; i < r.getSourceBatchIds().size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(r.getSourceBatchIds().get(i)).append("\"");
        }
        sb.append("],\"status\":\"").append(r.getStatus()).append("\",");
        sb.append("\"approverRole\":\"REGISTRAR\"");
        sb.append("}");
        return sb.toString();
    }

    private Batch parseBatch(String json) {
        Map<String, String> map = parseSimpleJson(json);
        Batch b = new Batch();
        b.setBatchCode(map.get("batchCode"));
        b.setName(map.get("name"));
        b.setCourseId(map.get("courseId"));
        b.setCurriculumId(map.get("curriculumId"));
        b.setDepartmentId(map.get("departmentId"));
        b.setCampusId(map.get("campusId"));
        b.setAcademicYear(map.get("academicYear"));
        b.setSemesterNo(Integer.parseInt(map.getOrDefault("semesterNo", "0")));
        b.setCapacity(Integer.parseInt(map.getOrDefault("capacity", "0")));
        String verStr = map.get("version");
        if (verStr != null && !verStr.isEmpty()) {
            b.setVersion(Long.parseLong(verStr));
        }
        return b;
    }

    private BatchSection parseSection(String json) {
        Map<String, String> map = parseSimpleJson(json);
        BatchSection s = new BatchSection();
        s.setSectionCode(map.get("sectionCode"));
        s.setSectionName(map.get("sectionName") != null ? map.get("sectionName") : map.get("name"));
        s.setCapacity(Integer.parseInt(map.getOrDefault("capacity", "0")));
        s.setFacultyId(map.get("facultyId"));
        return s;
    }

    private BatchCapacityOverride parseCapacityOverride(String json) {
        Map<String, String> map = parseSimpleJson(json);
        BatchCapacityOverride o = new BatchCapacityOverride();
        o.setOverrideCapacity(Integer.parseInt(map.getOrDefault("overrideCapacity", "0")));
        o.setReason(map.get("reason"));
        o.setEffectiveFrom(map.get("effectiveFrom"));
        o.setEffectiveTo(map.get("effectiveTo"));
        return o;
    }

    private BatchSplitRequest parseSplitRequest(String json) {
        BatchSplitRequest req = new BatchSplitRequest();
        Map<String, String> top = parseSimpleJson(json);
        req.setReason(top.get("reason"));

        // Extract proposedSections array
        List<ProposedSectionSplit> proposed = new ArrayList<>();
        Pattern secPat = Pattern.compile("\\{[^\\{\\}]*\"sectionCode\"\\s*:\\s*\"([^\"]+)\"[^\\{\\}]*\"capacity\"\\s*:\\s*(\\d+)[^\\{\\}]*\\}");
        Matcher m = secPat.matcher(json);
        while (m.find()) {
            String code = m.group(1);
            int cap = Integer.parseInt(m.group(2));
            proposed.add(new ProposedSectionSplit(code, "Section " + code, cap, Collections.emptyList()));
        }

        // If targetStudentIds are in the json, parse them per section
        if (proposed.isEmpty()) {
            // Fallback default proposed 2 sections
            proposed.add(new ProposedSectionSplit("A", "Section A", 30, Collections.emptyList()));
            proposed.add(new ProposedSectionSplit("B", "Section B", 30, Collections.emptyList()));
        }
        req.setProposedSections(proposed);
        return req;
    }

    private BatchMergeRequest parseMergeRequest(String json) {
        BatchMergeRequest req = new BatchMergeRequest();
        Map<String, String> top = parseSimpleJson(json);
        req.setReason(top.get("reason"));
        req.setTargetBatchCode(top.get("targetBatchCode"));
        req.setTargetBatchName(top.get("targetBatchName"));
        req.setTargetBatchId(top.get("targetBatchId"));

        // Parse sourceBatchIds array
        List<String> ids = new ArrayList<>();
        int start = json.indexOf("\"sourceBatchIds\"");
        if (start != -1) {
            int openBracket = json.indexOf("[", start);
            int closeBracket = json.indexOf("]", openBracket);
            if (openBracket != -1 && closeBracket != -1) {
                String sub = json.substring(openBracket + 1, closeBracket);
                String[] parts = sub.split(",");
                for (String p : parts) {
                    String clean = p.replace("\"", "").replace("'", "").trim();
                    if (!clean.isEmpty()) ids.add(clean);
                }
            }
        }
        req.setSourceBatchIds(ids);
        return req;
    }

    private Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty()) return map;

        Pattern p = Pattern.compile("\"([a-zA-Z0-9_]+)\"\\s*:\\s*(?:\"([^\"]*)\"|(\\d+(?:\\.\\d+)?)|(true|false)|null)");
        Matcher m = p.matcher(json);
        while (m.find()) {
            String key = m.group(1);
            String val = m.group(2) != null ? m.group(2) : (m.group(3) != null ? m.group(3) : m.group(4));
            if (val != null) {
                map.put(key, val);
            }
        }
        return map;
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        if (query == null || query.trim().isEmpty()) return params;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0 && idx < pair.length() - 1) {
                params.put(pair.substring(0, idx), pair.substring(idx + 1));
            } else if (idx > 0) {
                params.put(pair.substring(0, idx), "");
            }
        }
        return params;
    }

    private int parseQueryInt(Map<String, String> params, String key, int def) {
        String val = params.get(key);
        if (val == null) return def;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private long parseHeaderLong(HttpExchange exchange, String header, long def) {
        String val = exchange.getRequestHeaders().getFirst(header);
        if (val == null) return def;
        try {
            return Long.parseLong(val.replace("\"", "").trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private String formatSuccessResponse(String dataJson, HttpExchange exchange) {
        String traceId = exchange.getRequestHeaders().getFirst("X-Trace-Id");
        if (traceId == null) traceId = LogContext.getTraceId();
        String reqId = "REQ-" + UUID.randomUUID().toString().substring(0, 8);
        return "{\"success\":true,\"data\":" + dataJson + ",\"meta\":{\"requestId\":\"" + reqId
                + "\",\"correlationId\":\"" + traceId + "\",\"timestamp\":" + System.currentTimeMillis() + "}}";
    }

    private String formatSuccessResponseWithPagination(String dataJson, int page, int pageSize, int totalCount, HttpExchange exchange) {
        String traceId = exchange.getRequestHeaders().getFirst("X-Trace-Id");
        if (traceId == null) traceId = LogContext.getTraceId();
        String reqId = "REQ-" + UUID.randomUUID().toString().substring(0, 8);
        return "{\"success\":true,\"data\":" + dataJson + ",\"meta\":{\"requestId\":\"" + reqId
                + "\",\"correlationId\":\"" + traceId + "\",\"timestamp\":" + System.currentTimeMillis()
                + ",\"page\":" + page + ",\"pageSize\":" + pageSize + ",\"totalCount\":" + totalCount + "}}";
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String errorCode, String message, String path) throws IOException {
        String traceId = exchange.getRequestHeaders().getFirst("X-Trace-Id");
        if (traceId == null) traceId = LogContext.getTraceId();
        String reqId = "REQ-" + UUID.randomUUID().toString().substring(0, 8);

        String json = "{\"success\":false,\"error\":{\"code\":\"" + errorCode
                + "\",\"message\":\"" + escapeJson(message) + "\"},\"meta\":{\"requestId\":\"" + reqId
                + "\",\"correlationId\":\"" + traceId + "\",\"timestamp\":" + System.currentTimeMillis() + "}}";

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/problem+json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
