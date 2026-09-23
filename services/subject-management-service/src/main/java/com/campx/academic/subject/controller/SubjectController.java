package com.campx.academic.subject.controller;

import com.campx.academic.subject.exception.*;
import com.campx.academic.subject.model.ErrorResponse;
import com.campx.academic.subject.model.SubjectModels.*;
import com.campx.academic.subject.service.MetricsCollector;
import com.campx.academic.subject.service.StructuredLogEntry;
import com.campx.academic.subject.service.SubjectDomainService;
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
 * HTTP REST Controller for ACD-03: Subject Management Service.
 * Serves endpoints mounted under {@code /api/v1/academics/subjects/**} and {@code /api/v1/subjects/**}.
 */
public class SubjectController implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(SubjectController.class);

    private final SubjectDomainService domainService;
    private final MetricsCollector metricsCollector = MetricsCollector.getInstance();

    public SubjectController(SubjectDomainService domainService) {
        this.domainService = domainService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String fullPath = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        long startTime = System.currentTimeMillis();

        // 1. Correlation & Context Extraction (§51)
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
        }

        String userRole = exchange.getRequestHeaders().getFirst("X-User-Role");
        if (userRole != null && !userRole.trim().isEmpty()) {
            LogContext.setUserRole(userRole);
        } else {
            userRole = "ACADEMIC_ADMIN";
        }

        String departmentId = exchange.getRequestHeaders().getFirst("X-Department-Id");
        String idempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");

        // External API Key Authentication (Story 62)
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

            // External API consumers have strictly read-only access (Story 62)
            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
                sendError(exchange, 403, "ACD_FORBIDDEN", "External API consumers have read-only access", fullPath);
                return;
            }
        }

        LogContext.setService("ACD-03-SubjectManagementService");
        exchange.getResponseHeaders().set("X-Trace-Id", traceId);

        // Health and Metrics endpoints
        if ("/actuator/health".equals(fullPath)) {
            sendJson(exchange, 200, "{\"status\":\"UP\",\"service\":\"ACD-03-SubjectManagementService\"}");
            return;
        }
        if ("/metrics".equals(fullPath)) {
            String prom = metricsCollector.toPrometheusFormat(domainService);
            byte[] bytes = prom.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            return;
        }

        // Normalize path: map both /api/v1/academics/subjects and /api/v1/subjects
        String path = fullPath;
        if (path.startsWith("/api/v1/academics/subjects")) {
            path = "/api/v1/subjects" + path.substring("/api/v1/academics/subjects".length());
        }

        logger.info("[SubjectService] [{}] {} (role={}, tenant={})", method, path, userRole, tenantId);

        try (FlowTracker flow = logger.flow("SubjectControllerRequest", method + " " + path)) {
            dispatch(exchange, method, path, tenantId, userId, userRole, departmentId, idempotencyKey, fullPath, startTime);
        } catch (SubjectException e) {
            metricsCollector.recordError(e.getErrorCode());
            sendError(exchange, e.getStatusCode(), e.getErrorCode(), e.getMessage(), fullPath);
        } catch (Exception e) {
            logger.error("Unhandled error processing request: {}", e.getMessage(), e);
            metricsCollector.recordError("INTERNAL_SERVER_ERROR");
            sendError(exchange, 500, "ACD_INTERNAL_ERROR", "Internal system error: " + e.getMessage(), fullPath);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            StructuredLogEntry entry = StructuredLogEntry.builder()
                    .service("ACD-03-SubjectManagementService")
                    .tenantId(tenantId)
                    .requestId(traceId)
                    .correlationId(LogContext.getTraceId())
                    .actorId(userId != null ? userId : "anonymous")
                    .operation(method + " " + fullPath)
                    .outcome("COMPLETED")
                    .durationMs(duration)
                    .build();
            logger.debug("[AUDIT_LOG] {}", entry.toJson());
        }
    }

    private void dispatch(HttpExchange exchange, String method, String path,
                          String tenantId, String userId, String userRole, String departmentId,
                          String idempotencyKey, String fullPath, long startTime) throws Exception {

        // 1. Bulk Import: POST /api/v1/subjects/import
        if ("POST".equalsIgnoreCase(method) && "/api/v1/subjects/import".equals(path)) {
            checkRole(userRole, "ACADEMIC_ADMIN", "SUPER_ADMIN");
            String body = readBody(exchange);
            List<Subject> rows = parseSubjectList(body);
            BulkImportResult res = domainService.bulkImportSubjects(rows, "2026-2027", userId, userRole);
            sendSuccess(exchange, 201, toBulkImportJson(res));
            return;
        }

        // 2. Export Catalog: GET /api/v1/subjects/export
        if ("GET".equalsIgnoreCase(method) && "/api/v1/subjects/export".equals(path)) {
            checkRole(userRole, "ACADEMIC_ADMIN", "SUPER_ADMIN", "REGISTRAR", "MANAGEMENT", "AUDITOR");
            String csv = domainService.exportCatalogAsCsv(userRole);
            byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"subject_catalog.csv\"");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            return;
        }

        // 3. Published Catalog: GET /api/v1/subjects/catalog
        if ("GET".equalsIgnoreCase(method) && "/api/v1/subjects/catalog".equals(path)) {
            // Accessible to all roles including STUDENT, PARENT, EXTERNAL_API
            List<Subject> catalog = domainService.getPublishedCatalog(tenantId, null);
            sendSuccess(exchange, 200, toSubjectListJson(catalog));
            return;
        }

        // 4. Search Subjects: GET /api/v1/subjects/search or GET /api/v1/subjects
        if ("GET".equalsIgnoreCase(method) && ("/api/v1/subjects/search".equals(path) || "/api/v1/subjects".equals(path))) {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
            String code = query.get("subjectCode");
            if (code == null) code = query.get("code");
            String name = query.get("name");
            String type = query.get("subjectType");
            String dept = query.get("departmentId");
            String status = query.get("status");
            String tag = query.get("tag");
            int offset = query.containsKey("offset") ? Integer.parseInt(query.get("offset")) : 0;
            int limit = query.containsKey("limit") ? Integer.parseInt(query.get("limit")) : 50;

            // RBAC Filtering: Students and Parents only see ACTIVE/catalog subjects
            if ("STUDENT".equalsIgnoreCase(userRole) || "PARENT".equalsIgnoreCase(userRole)) {
                status = "ACTIVE";
            }

            List<Subject> results = domainService.searchSubjects(code, name, type, dept, status, tag, offset, limit);
            sendSuccess(exchange, 200, toSubjectListJson(results));
            return;
        }

        // 5. Filter by category: GET /api/v1/subjects/category/{cat}
        if ("GET".equalsIgnoreCase(method) && path.startsWith("/api/v1/subjects/category/")) {
            String category = path.substring("/api/v1/subjects/category/".length());
            List<Subject> results = domainService.getSubjectsByCategory(category);
            sendSuccess(exchange, 200, toSubjectListJson(results));
            return;
        }

        // 6. Filter by department: GET /api/v1/subjects/department/{deptId}
        if ("GET".equalsIgnoreCase(method) && path.startsWith("/api/v1/subjects/department/")) {
            String deptId = path.substring("/api/v1/subjects/department/".length());
            List<Subject> results = domainService.getSubjectsByDepartment(deptId);
            sendSuccess(exchange, 200, toSubjectListJson(results));
            return;
        }

        // 7. Create Subject: POST /api/v1/subjects
        if ("POST".equalsIgnoreCase(method) && "/api/v1/subjects".equals(path)) {
            checkRole(userRole, "ACADEMIC_ADMIN", "SUPER_ADMIN", "DEPARTMENT_HEAD");
            String body = readBody(exchange);

            // Idempotency check (§55)
            String hash = SubjectDomainService.computeHash(body);
            IdempotencyRecord cached = domainService.checkIdempotency(idempotencyKey, tenantId, hash);
            if (cached != null) {
                sendRawResponse(exchange, cached.getStatusCode(), cached.getResponseBody());
                return;
            }

            Subject req = parseSubjectJson(body);
            if (req.getTenantId() == null) req.setTenantId(tenantId);
            if (departmentId != null && req.getDepartmentId() == null) req.setDepartmentId(departmentId);

            Subject created = domainService.createSubject(req, req.getAcademicYear(), userId, userRole);
            String respJson = buildEnvelopeJson(true, "{"
                    + "\"subjectId\":\"" + created.getId() + "\","
                    + "\"subjectCode\":\"" + created.getSubjectCode() + "\","
                    + "\"name\":\"" + escape(created.getName()) + "\","
                    + "\"version\":" + created.getCurrentVersion() + ","
                    + "\"status\":\"" + created.getStatus().name() + "\""
                    + "}");

            if (idempotencyKey != null) {
                domainService.recordIdempotency(idempotencyKey, tenantId, hash, "CREATE_SUBJECT", 201, respJson);
            }

            sendRawResponse(exchange, 201, respJson);
            return;
        }

        // Sub-resource routing: /api/v1/subjects/{id}/...
        Pattern p = Pattern.compile("^/api/v1/subjects/([^/]+)(/.*)?$");
        Matcher m = p.matcher(path);
        if (m.matches()) {
            String id = m.group(1);
            String subPath = m.group(2) != null ? m.group(2) : "";

            // GET /api/v1/subjects/{id}
            if ("GET".equalsIgnoreCase(method) && subPath.isEmpty()) {
                Subject s = domainService.getSubject(id);
                // Parent / Student RBAC check: only ACTIVE subjects
                if (("STUDENT".equalsIgnoreCase(userRole) || "PARENT".equalsIgnoreCase(userRole))
                        && s.getStatus() != SubjectStatus.ACTIVE) {
                    throw new SubjectForbiddenException("Students and Parents can only access published/ACTIVE subjects");
                }
                sendSuccess(exchange, 200, SubjectDomainService.toJson(s));
                return;
            }

            // PUT /api/v1/subjects/{id}
            if ("PUT".equalsIgnoreCase(method) && subPath.isEmpty()) {
                checkRole(userRole, "ACADEMIC_ADMIN", "SUPER_ADMIN", "DEPARTMENT_HEAD");
                String body = readBody(exchange);
                Subject updateReq = parseSubjectJson(body);
                long clientVersion = parseVersionLock(body);
                Subject updated = domainService.updateSubject(id, updateReq, clientVersion, userId, userRole);
                sendSuccess(exchange, 200, SubjectDomainService.toJson(updated));
                return;
            }

            // DELETE /api/v1/subjects/{id} (Hard deletion check)
            if ("DELETE".equalsIgnoreCase(method) && subPath.isEmpty()) {
                checkRole(userRole, "SUPER_ADMIN", "ACADEMIC_ADMIN");
                domainService.deleteSubject(id, userId, userRole);
                sendSuccess(exchange, 200, "{\"status\":\"DELETED\",\"subjectId\":\"" + id + "\"}");
                return;
            }

            // Version endpoints: /api/v1/subjects/{id}/versions...
            if (subPath.equals("/versions")) {
                if ("POST".equalsIgnoreCase(method)) {
                    checkRole(userRole, "ACADEMIC_ADMIN", "SUPER_ADMIN", "DEPARTMENT_HEAD");
                    String body = readBody(exchange);
                    SubjectVersion verReq = parseVersionJson(body);
                    SubjectVersion created = domainService.createSubjectVersion(id, verReq, userId, userRole);
                    sendSuccess(exchange, 201, SubjectDomainService.toJson(created));
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    List<SubjectVersion> list = domainService.getSubjectVersions(id);
                    sendSuccess(exchange, 200, toVersionListJson(list));
                    return;
                }
            }

            // GET/PUT /api/v1/subjects/{id}/versions/{v}
            Matcher vMatcher = Pattern.compile("^/versions/(\\d+)$").matcher(subPath);
            if (vMatcher.matches()) {
                int vNo = Integer.parseInt(vMatcher.group(1));
                if ("GET".equalsIgnoreCase(method)) {
                    SubjectVersion ver = domainService.getSubjectVersion(id, vNo);
                    sendSuccess(exchange, 200, SubjectDomainService.toJson(ver));
                    return;
                } else if ("PUT".equalsIgnoreCase(method)) {
                    checkRole(userRole, "ACADEMIC_ADMIN", "SUPER_ADMIN", "DEPARTMENT_HEAD");
                    String body = readBody(exchange);
                    SubjectVersion updateReq = parseVersionJson(body);
                    SubjectVersion updated = domainService.updateDraftSubjectVersion(id, vNo, updateReq, userId, userRole);
                    sendSuccess(exchange, 200, SubjectDomainService.toJson(updated));
                    return;
                }
            }

            // POST /api/v1/subjects/{id}/versions/{v}/publish
            Matcher pubMatcher = Pattern.compile("^/versions/(\\d+)/publish$").matcher(subPath);
            if (pubMatcher.matches() && "POST".equalsIgnoreCase(method)) {
                checkRole(userRole, "ACADEMIC_ADMIN", "SUPER_ADMIN", "REGISTRAR");
                int vNo = Integer.parseInt(pubMatcher.group(1));
                SubjectVersion published = domainService.publishSubjectVersion(id, vNo, userId, userRole);
                sendSuccess(exchange, 200, SubjectDomainService.toJson(published));
                return;
            }

            // Metadata: /api/v1/subjects/{id}/metadata
            if ("/metadata".equals(subPath)) {
                if ("PUT".equalsIgnoreCase(method)) {
                    checkRole(userRole, "ACADEMIC_ADMIN", "SUPER_ADMIN", "DEPARTMENT_HEAD");
                    String body = readBody(exchange);
                    SubjectMetadata metaReq = parseMetadataJson(body);
                    SubjectMetadata saved = domainService.saveOrUpdateMetadata(id, metaReq, userId, userRole);
                    sendSuccess(exchange, 200, SubjectDomainService.toJson(saved));
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    SubjectMetadata meta = domainService.getMetadata(id, userRole);
                    sendSuccess(exchange, 200, SubjectDomainService.toJson(meta));
                    return;
                }
            }

            // Prerequisites: /api/v1/subjects/{id}/prerequisites
            if ("/prerequisites".equals(subPath)) {
                if ("POST".equalsIgnoreCase(method)) {
                    checkRole(userRole, "ACADEMIC_ADMIN", "SUPER_ADMIN", "DEPARTMENT_HEAD");
                    String body = readBody(exchange);
                    SubjectPrerequisite req = parsePrerequisiteJson(body);
                    SubjectPrerequisite added = domainService.addPrerequisite(id, req, userId, userRole);
                    sendSuccess(exchange, 201, SubjectDomainService.toJson(added));
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    PrerequisiteGraphView graph = domainService.getPrerequisiteGraph(id);
                    sendSuccess(exchange, 200, toPrerequisiteGraphJson(graph));
                    return;
                }
            }

            // DELETE /api/v1/subjects/{id}/prerequisites/{prereqId}
            Matcher delPrereqMatcher = Pattern.compile("^/prerequisites/([^/]+)$").matcher(subPath);
            if (delPrereqMatcher.matches() && "DELETE".equalsIgnoreCase(method)) {
                checkRole(userRole, "ACADEMIC_ADMIN", "SUPER_ADMIN", "DEPARTMENT_HEAD");
                String prereqId = delPrereqMatcher.group(1);
                domainService.removePrerequisite(id, prereqId, userId, userRole);
                sendSuccess(exchange, 200, "{\"status\":\"REMOVED\",\"prerequisiteId\":\"" + prereqId + "\"}");
                return;
            }

            // Lifecycle actions: /deactivate, /reactivate, /deprecate, /retire, /status
            if ("/deactivate".equals(subPath) && "POST".equalsIgnoreCase(method)) {
                checkRole(userRole, "ACADEMIC_ADMIN", "SUPER_ADMIN");
                Subject s = domainService.deactivateSubject(id, "Deactivated via API", userId, userRole);
                sendSuccess(exchange, 200, SubjectDomainService.toJson(s));
                return;
            }
            if ("/reactivate".equals(subPath) && "POST".equalsIgnoreCase(method)) {
                checkRole(userRole, "ACADEMIC_ADMIN", "SUPER_ADMIN");
                Subject s = domainService.reactivateSubject(id, "Reactivated via API", userId, userRole);
                sendSuccess(exchange, 200, SubjectDomainService.toJson(s));
                return;
            }
            if ("/deprecate".equals(subPath) && "POST".equalsIgnoreCase(method)) {
                checkRole(userRole, "ACADEMIC_ADMIN", "SUPER_ADMIN");
                Subject s = domainService.deprecateSubject(id, "Deprecated via API", userId, userRole);
                sendSuccess(exchange, 200, SubjectDomainService.toJson(s));
                return;
            }
            if ("/retire".equals(subPath) && "POST".equalsIgnoreCase(method)) {
                checkRole(userRole, "ACADEMIC_ADMIN", "SUPER_ADMIN", "REGISTRAR");
                Subject s = domainService.retireSubject(id, "Retired via API", userId, userRole);
                sendSuccess(exchange, 200, SubjectDomainService.toJson(s));
                return;
            }
            if ("/status".equals(subPath) && "PUT".equalsIgnoreCase(method)) {
                checkRole(userRole, "ACADEMIC_ADMIN", "SUPER_ADMIN", "REGISTRAR");
                String body = readBody(exchange);
                String targetStatusStr = extractJsonString(body, "status");
                String reason = extractJsonString(body, "reason");
                SubjectStatus targetStatus = SubjectStatus.valueOf(targetStatusStr.toUpperCase());
                Subject s = domainService.transitionLifecycle(id, targetStatus, reason, userId, userRole);
                sendSuccess(exchange, 200, SubjectDomainService.toJson(s));
                return;
            }

            // History / Audit: GET /api/v1/subjects/{id}/history
            if ("/history".equals(subPath) && "GET".equalsIgnoreCase(method)) {
                // Auditor and privileged roles have access (Story 61)
                checkRole(userRole, "AUDITOR", "SUPER_ADMIN", "ACADEMIC_ADMIN", "REGISTRAR", "DEPARTMENT_HEAD");
                List<SubjectHistory> history = domainService.getSubjectHistory(id);
                sendSuccess(exchange, 200, toHistoryListJson(history));
                return;
            }
        }

        // Unmatched endpoint
        sendError(exchange, 404, "ACD_ROUTE_NOT_FOUND", "Endpoint not found: " + fullPath, fullPath);
    }

    // =========================================================================
    // RBAC & Scope Checks (§48)
    // =========================================================================

    private void checkRole(String currentRole, String... allowedRoles) {
        if (currentRole == null) {
            throw new SubjectUnauthorizedException("No authentication role supplied");
        }
        for (String r : allowedRoles) {
            if (r.equalsIgnoreCase(currentRole)) {
                return;
            }
        }
        throw new SubjectForbiddenException("Role '" + currentRole + "' lacks permission for this operation");
    }

    // =========================================================================
    // Envelopes & Response Senders (§29.2, §29.3)
    // =========================================================================

    private void sendSuccess(HttpExchange exchange, int statusCode, String dataJson) throws IOException {
        String resp = buildEnvelopeJson(true, dataJson);
        sendRawResponse(exchange, statusCode, resp);
    }

    private void sendError(HttpExchange exchange, int statusCode, String errorCode, String message, String path) throws IOException {
        ErrorResponse err = new ErrorResponse(errorCode, message, LogContext.getTraceId(), LogContext.getTraceId());
        sendRawResponse(exchange, statusCode, err.toJson());
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        sendRawResponse(exchange, statusCode, json);
    }

    private void sendRawResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String buildEnvelopeJson(boolean success, String dataJson) {
        return "{"
                + "\"success\":" + success + ","
                + "\"data\":" + (dataJson != null ? dataJson : "null") + ","
                + "\"meta\":{"
                + "\"requestId\":\"" + LogContext.getTraceId() + "\","
                + "\"correlationId\":\"" + LogContext.getTraceId() + "\","
                + "\"timestamp\":" + System.currentTimeMillis()
                + "}"
                + "}";
    }

    // =========================================================================
    // Lightweight JSON Parsing & Serialization Helpers
    // =========================================================================

    private String readBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) map.put(kv[0], kv[1]);
            else if (kv.length == 1) map.put(kv[0], "");
        }
        return map;
    }

    private Subject parseSubjectJson(String json) {
        Subject s = new Subject();
        s.setSubjectCode(extractJsonString(json, "subjectCode"));
        s.setName(extractJsonString(json, "name"));
        s.setShortName(extractJsonString(json, "shortName"));
        s.setDescription(extractJsonString(json, "description"));
        s.setDepartmentId(extractJsonString(json, "departmentId"));
        s.setCampusId(extractJsonString(json, "campusId"));
        s.setCourseId(extractJsonString(json, "courseId"));
        s.setSubjectType(extractJsonString(json, "subjectType"));
        s.setClassification(extractJsonString(json, "classification"));
        s.setAcademicYear(extractJsonString(json, "academicYear"));

        String electiveStr = extractJsonValue(json, "elective");
        if (electiveStr != null) s.setElective(Boolean.parseBoolean(electiveStr));

        String creditsStr = extractJsonValue(json, "credits");
        if (creditsStr != null) s.setCredits(Double.parseDouble(creditsStr));

        String hoursStr = extractJsonValue(json, "contactHours");
        if (hoursStr != null) s.setContactHours(Double.parseDouble(hoursStr));

        String statusStr = extractJsonString(json, "status");
        if (statusStr != null) s.setStatus(SubjectStatus.valueOf(statusStr.toUpperCase()));

        return s;
    }

    private List<Subject> parseSubjectList(String json) {
        List<Subject> list = new ArrayList<>();
        // Simple JSON array of objects parser
        Pattern objPattern = Pattern.compile("\\{([^{}]+)\\}");
        Matcher m = objPattern.matcher(json);
        while (m.find()) {
            list.add(parseSubjectJson("{" + m.group(1) + "}"));
        }
        return list;
    }

    private SubjectVersion parseVersionJson(String json) {
        SubjectVersion v = new SubjectVersion();
        v.setAcademicYear(extractJsonString(json, "academicYear"));
        v.setSubjectType(extractJsonString(json, "subjectType"));
        v.setClassification(extractJsonString(json, "classification"));
        v.setChangeSummary(extractJsonString(json, "changeSummary"));

        String creditsStr = extractJsonValue(json, "credits");
        if (creditsStr != null) v.setCredits(Double.parseDouble(creditsStr));

        String hoursStr = extractJsonValue(json, "contactHours");
        if (hoursStr != null) v.setContactHours(Double.parseDouble(hoursStr));

        String electiveStr = extractJsonValue(json, "elective");
        if (electiveStr != null) v.setElective(Boolean.parseBoolean(electiveStr));

        return v;
    }

    private SubjectMetadata parseMetadataJson(String json) {
        SubjectMetadata m = new SubjectMetadata();
        m.setCategory(extractJsonString(json, "category"));
        m.setDeliveryMode(extractJsonString(json, "deliveryMode"));
        m.setAssessmentMode(extractJsonString(json, "assessmentMode"));
        m.setRegulatoryCode(extractJsonString(json, "regulatoryCode"));
        m.setLanguageOfInstruction(extractJsonString(json, "languageOfInstruction"));
        m.setPrerequisiteNotes(extractJsonString(json, "prerequisiteNotes"));

        // Extract tags array: "tags":["math","algebra"]
        Pattern tagPat = Pattern.compile("\"tags\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher tm = tagPat.matcher(json);
        if (tm.find()) {
            String inner = tm.group(1);
            List<String> tags = new ArrayList<>();
            for (String t : inner.split(",")) {
                String clean = t.trim().replace("\"", "");
                if (!clean.isEmpty()) tags.add(clean);
            }
            m.setTags(tags);
        }

        return m;
    }

    private SubjectPrerequisite parsePrerequisiteJson(String json) {
        SubjectPrerequisite sp = new SubjectPrerequisite();
        sp.setPrerequisiteSubjectId(extractJsonString(json, "prerequisiteSubjectId"));
        String typeStr = extractJsonString(json, "relationshipType");
        if (typeStr != null) {
            sp.setRelationshipType(RelationshipType.valueOf(typeStr.toUpperCase()));
        } else {
            sp.setRelationshipType(RelationshipType.PREREQUISITE);
        }
        sp.setMinimumGrade(extractJsonString(json, "minimumGrade"));

        String mandStr = extractJsonValue(json, "mandatory");
        if (mandStr != null) sp.setMandatory(Boolean.parseBoolean(mandStr));
        else sp.setMandatory(true);

        return sp;
    }

    private long parseVersionLock(String json) {
        String val = extractJsonValue(json, "versionLock");
        if (val == null) val = extractJsonValue(json, "version");
        if (val != null) {
            try { return Long.parseLong(val); } catch (NumberFormatException ignored) {}
        }
        return 0L;
    }

    private static String extractJsonString(String json, String field) {
        if (json == null) return null;
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static String extractJsonValue(String json, String field) {
        if (json == null) return null;
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*([^,}\\]\\s]+)");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private String toSubjectListJson(List<Subject> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(SubjectDomainService.toJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private String toVersionListJson(List<SubjectVersion> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(SubjectDomainService.toJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private String toPrerequisiteGraphJson(PrerequisiteGraphView g) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"subjectId\":\"").append(escape(g.getSubjectId())).append("\",");
        sb.append("\"prerequisites\":[");
        for (int i = 0; i < g.getPrerequisites().size(); i++) {
            if (i > 0) sb.append(",");
            PrerequisiteNode n = g.getPrerequisites().get(i);
            sb.append(toNodeJson(n));
        }
        sb.append("],");
        sb.append("\"dependents\":[");
        for (int i = 0; i < g.getDependents().size(); i++) {
            if (i > 0) sb.append(",");
            PrerequisiteNode n = g.getDependents().get(i);
            sb.append(toNodeJson(n));
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private String toNodeJson(PrerequisiteNode n) {
        return "{"
                + "\"subjectId\":\"" + escape(n.getSubjectId()) + "\","
                + "\"subjectCode\":\"" + escape(n.getSubjectCode()) + "\","
                + "\"subjectName\":\"" + escape(n.getSubjectName()) + "\","
                + "\"relationshipType\":\"" + escape(n.getRelationshipType()) + "\","
                + "\"mandatory\":" + n.isMandatory() + ","
                + "\"minimumGrade\":\"" + escape(n.getMinimumGrade()) + "\","
                + "\"direction\":\"" + escape(n.getDirection()) + "\""
                + "}";
    }

    private String toBulkImportJson(BulkImportResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"totalRows\":").append(r.getTotalRows()).append(",");
        sb.append("\"successfulRows\":").append(r.getSuccessfulRows()).append(",");
        sb.append("\"failedRows\":").append(r.getFailedRows()).append(",");
        sb.append("\"errors\":[");
        for (int i = 0; i < r.getErrors().size(); i++) {
            if (i > 0) sb.append(",");
            BulkImportResult.RowError err = r.getErrors().get(i);
            sb.append("{")
                    .append("\"rowNumber\":").append(err.getRowNumber()).append(",")
                    .append("\"subjectCode\":\"").append(escape(err.getSubjectCode())).append("\",")
                    .append("\"errorCode\":\"").append(escape(err.getErrorCode())).append("\",")
                    .append("\"message\":\"").append(escape(err.getMessage())).append("\"")
                    .append("}");
        }
        sb.append("],");
        sb.append("\"createdSubjectIds\":[");
        for (int i = 0; i < r.getCreatedSubjectIds().size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escape(r.getCreatedSubjectIds().get(i))).append("\"");
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private String toHistoryListJson(List<SubjectHistory> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            SubjectHistory h = list.get(i);
            sb.append("{")
                    .append("\"id\":\"").append(escape(h.getId())).append("\",")
                    .append("\"subjectId\":\"").append(escape(h.getSubjectId())).append("\",")
                    .append("\"action\":\"").append(escape(h.getAction())).append("\",")
                    .append("\"fromStatus\":\"").append(escape(h.getFromStatus())).append("\",")
                    .append("\"toStatus\":\"").append(escape(h.getToStatus())).append("\",")
                    .append("\"actorId\":\"").append(escape(h.getActorId())).append("\",")
                    .append("\"actorRole\":\"").append(escape(h.getActorRole())).append("\",")
                    .append("\"reason\":\"").append(escape(h.getReason())).append("\",")
                    .append("\"occurredAt\":").append(h.getOccurredAt())
                    .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
