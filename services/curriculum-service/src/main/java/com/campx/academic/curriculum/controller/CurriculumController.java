package com.campx.academic.curriculum.controller;

import com.campx.academic.curriculum.exception.*;
import com.campx.academic.curriculum.model.CurriculumModels.*;
import com.campx.academic.curriculum.model.ErrorResponse;
import com.campx.academic.curriculum.service.CurriculumDomainService;
import com.campx.academic.curriculum.service.MetricsCollector;
import com.campx.academic.curriculum.service.StructuredLogEntry;
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
 * HTTP REST Controller for ACD-02: Curriculum Management Service.
 * Serves endpoints mounted at {@code /api/v1/curricula/**} and {@code /api/v1/academics/curricula/**}.
 */
public class CurriculumController implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(CurriculumController.class);

    private final CurriculumDomainService domainService;

    public CurriculumController(CurriculumDomainService domainService) {
        this.domainService = domainService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String fullPath = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        long startTime = System.currentTimeMillis();
        exchange.setAttribute("startTime", startTime);
        exchange.setAttribute("method", method);
        exchange.setAttribute("path", fullPath);

        // 1. Correlation & LogContext extraction
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

        exchange.setAttribute("traceId", traceId);
        exchange.setAttribute("tenantId", tenantId);
        exchange.setAttribute("userId", userId != null ? userId : "anonymous");

        // External API Key Authentication (Story 63)
        String apiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            ApiKeyRecord keyRecord = domainService.validateApiKey(apiKey.trim(), tenantId);
            if (keyRecord == null) {
                sendError(exchange, 401, "Unauthorized", "ACD2_UNAUTHORIZED", "Invalid, revoked, or expired API Key", fullPath);
                return;
            }
            userRole = "EXTERNAL_API";
            userId = "api-consumer-" + keyRecord.getKeyId();
            LogContext.setUserRole(userRole);
            LogContext.setUserId(userId);
            exchange.setAttribute("userId", userId);
            if (keyRecord.getTenantId() != null) {
                tenantId = keyRecord.getTenantId();
                LogContext.setTenantId(tenantId);
                exchange.setAttribute("tenantId", tenantId);
            }

            // External API consumers have strict read-only access (Story 63)
            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
                sendError(exchange, 403, "Forbidden", "ACD2_FORBIDDEN", "External API consumers have read-only access", fullPath);
                return;
            }
        }

        String departmentId = exchange.getRequestHeaders().getFirst("X-Department-Id");

        LogContext.setService("ACD-02-CurriculumService");
        exchange.getResponseHeaders().set("X-Trace-Id", traceId);

        // Normalize path: support both /api/v1/academics/curricula and /api/v1/curricula
        String path = fullPath;
        if (path.startsWith("/api/v1/academics/curricula")) {
            path = "/api/v1/curricula" + path.substring("/api/v1/academics/curricula".length());
        }

        String maskedKey = apiKey != null ? (apiKey.length() > 6 ? apiKey.substring(0, 6) + "..." : "***") : "none";
        logger.info("[CurriculumService] Incoming [{}] {} (role={}, apiKey={})", method, path, userRole, maskedKey);

        try (FlowTracker flow = logger.flow("CurriculumControllerRequest", method + " " + path)) {
            // Health endpoint
            if ("/actuator/health".equals(path)) {
                sendJson(exchange, 200, "{\"status\":\"UP\",\"service\":\"ACD-02-Curriculum-Management-Service\"}");
                return;
            }

            // Prometheus Metrics Endpoint (Story 69)
            if (("/metrics".equals(path) || "/api/v1/curricula/metrics".equals(path) || "/metrics".equals(fullPath))
                    && "GET".equalsIgnoreCase(method)) {
                String prometheusData = MetricsCollector.getInstance().toPrometheusFormat(domainService);
                byte[] bytes = prometheusData.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                recordAuditAndMetrics(exchange, 200, null);
                return;
            }

            // Catalog & Active endpoints
            if (("/api/v1/curricula/active".equals(path) || "/api/v1/active".equals(fullPath) || "/v1/curriculum-catalog".equals(fullPath))
                    && "GET".equalsIgnoreCase(method)) {
                flow.step("handleListActiveCurricula");
                handleListActiveCurricula(exchange);
                return;
            }

            // Outbox Events Endpoint
            if ("/api/v1/curricula/events/outbox".equals(path) && "GET".equalsIgnoreCase(method)) {
                flow.step("handleGetOutboxEvents");
                handleGetOutboxEvents(exchange);
                return;
            }

            // Dead-Letter Events Endpoint
            if ("/api/v1/curricula/events/dead-letter".equals(path) && "GET".equalsIgnoreCase(method)) {
                flow.step("handleGetDeadLetterEvents");
                handleGetDeadLetterEvents(exchange);
                return;
            }

            // Inbound Event Consumption Endpoint
            if ("/api/v1/curricula/events/inbox".equals(path) && "POST".equalsIgnoreCase(method)) {
                flow.step("handleProcessInboxEvent");
                handleProcessInboxEvent(exchange);
                return;
            }

            // Primary Collection: /api/v1/curricula
            if ("/api/v1/curricula".equals(path)) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateCurriculum");
                    handleCreateCurriculum(exchange, tenantId, userId, userRole);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleSearchCurricula");
                    handleSearchCurricula(exchange, userRole);
                    return;
                }
            }

            // Sub-Resource Dispatches: /api/v1/curricula/{id}/...
            if (path.startsWith("/api/v1/curricula/")) {
                String subPath = path.substring("/api/v1/curricula/".length());

                // Detail and Delete on /api/v1/curricula/{id}
                if (!subPath.contains("/")) {
                    String id = subPath;
                    if ("GET".equalsIgnoreCase(method)) {
                        flow.step("handleGetCurriculum");
                        handleGetCurriculum(exchange, id, userRole, departmentId);
                        return;
                    } else if ("DELETE".equalsIgnoreCase(method)) {
                        flow.step("handleDeleteCurriculum");
                        handleDeleteCurriculum(exchange, id, userRole);
                        return;
                    }
                }

                String[] parts = subPath.split("/");
                String curriculumId = parts[0];
                String actionOrResource = parts[1];

                // Compliance View: /api/v1/curricula/{id}/compliance-view
                if ("compliance-view".equalsIgnoreCase(actionOrResource) && "GET".equalsIgnoreCase(method)) {
                    flow.step("handleComplianceView");
                    handleComplianceView(exchange, curriculumId, userId, userRole);
                    return;
                }

                // 1. Submit for approval
                if ("submit".equalsIgnoreCase(actionOrResource) && "POST".equalsIgnoreCase(method)) {
                    flow.step("handleSubmitCurriculum");
                    handleSubmitCurriculum(exchange, curriculumId, userId, userRole);
                    return;
                }

                // 2. Review curriculum
                if ("review".equalsIgnoreCase(actionOrResource) && "POST".equalsIgnoreCase(method)) {
                    flow.step("handleReviewCurriculum");
                    handleReviewCurriculum(exchange, curriculumId, userId, userRole, departmentId);
                    return;
                }

                // 3. Approve curriculum
                if ("approve".equalsIgnoreCase(actionOrResource) && "POST".equalsIgnoreCase(method)) {
                    flow.step("handleApproveCurriculum");
                    handleApproveCurriculum(exchange, curriculumId, userId, userRole);
                    return;
                }

                // 4. Publish curriculum
                if ("publish".equalsIgnoreCase(actionOrResource) && "POST".equalsIgnoreCase(method)) {
                    flow.step("handlePublishCurriculum");
                    handlePublishCurriculum(exchange, curriculumId, userId, userRole);
                    return;
                }

                // 5. Retire curriculum
                if ("retire".equalsIgnoreCase(actionOrResource) && "POST".equalsIgnoreCase(method)) {
                    flow.step("handleRetireCurriculum");
                    handleRetireCurriculum(exchange, curriculumId, userId, userRole);
                    return;
                }

                // 6. Annual revision
                if ("annual-revision".equalsIgnoreCase(actionOrResource) && "POST".equalsIgnoreCase(method)) {
                    flow.step("handleAnnualRevision");
                    handleAnnualRevision(exchange, curriculumId, userId, userRole);
                    return;
                }

                // 7. Audit History
                if ("history".equalsIgnoreCase(actionOrResource) && "GET".equalsIgnoreCase(method)) {
                    flow.step("handleGetHistory");
                    handleGetHistory(exchange, curriculumId, userRole);
                    return;
                }

                // 8. Prerequisites
                if ("prerequisites".equalsIgnoreCase(actionOrResource)) {
                    if ("POST".equalsIgnoreCase(method)) {
                        flow.step("handleAddPrerequisite");
                        handleAddPrerequisite(exchange, curriculumId, userId, userRole);
                        return;
                    } else if ("GET".equalsIgnoreCase(method)) {
                        flow.step("handleListPrerequisites");
                        handleListPrerequisites(exchange, curriculumId);
                        return;
                    }
                }

                // 9. Syllabus shortcut: /api/v1/curricula/{id}/syllabus
                if ("syllabus".equalsIgnoreCase(actionOrResource) && "GET".equalsIgnoreCase(method)) {
                    flow.step("handleGetSyllabusShortcut");
                    Curriculum c = domainService.getCurriculum(curriculumId, userRole, departmentId);
                    handleGetSyllabus(exchange, curriculumId, c.getCurrentVersion());
                    return;
                }

                // 10. Versions: /api/v1/curricula/{id}/versions/...
                if ("versions".equalsIgnoreCase(actionOrResource)) {
                    if (parts.length == 2) {
                        if ("POST".equalsIgnoreCase(method)) {
                            flow.step("handleCreateVersion");
                            handleCreateVersion(exchange, curriculumId, userId, userRole, tenantId);
                            return;
                        } else if ("GET".equalsIgnoreCase(method)) {
                            flow.step("handleListVersions");
                            handleListVersions(exchange, curriculumId);
                            return;
                        }
                    }

                    int versionNo = Integer.parseInt(parts[2]);

                    // /api/v1/curricula/{id}/versions/{v}
                    if (parts.length == 3) {
                        if ("GET".equalsIgnoreCase(method)) {
                            flow.step("handleGetVersion");
                            handleGetVersion(exchange, curriculumId, versionNo);
                            return;
                        } else if ("PUT".equalsIgnoreCase(method)) {
                            flow.step("handleUpdateDraftVersion");
                            handleUpdateDraftVersion(exchange, curriculumId, versionNo, userId, userRole);
                            return;
                        }
                    }

                    // /api/v1/curricula/{id}/versions/{v}/semesters
                    if (parts.length >= 4 && "semesters".equalsIgnoreCase(parts[3])) {
                        if ("POST".equalsIgnoreCase(method)) {
                            flow.step("handleAddSemester");
                            handleAddSemester(exchange, curriculumId, versionNo, userRole);
                            return;
                        }
                    }

                    // /api/v1/curricula/{id}/versions/{v}/subjects
                    if (parts.length >= 4 && "subjects".equalsIgnoreCase(parts[3])) {
                        if (parts.length == 4) {
                            if ("POST".equalsIgnoreCase(method)) {
                                flow.step("handleMapSubject");
                                handleMapSubject(exchange, curriculumId, versionNo, userId, userRole, tenantId);
                                return;
                            } else if ("GET".equalsIgnoreCase(method)) {
                                flow.step("handleGetSubjectMappings");
                                handleGetSubjectMappings(exchange, curriculumId, versionNo);
                                return;
                            }
                        } else if (parts.length == 5 && "DELETE".equalsIgnoreCase(method)) {
                            flow.step("handleRemoveSubjectMapping");
                            String mappingId = parts[4];
                            handleRemoveSubjectMapping(exchange, curriculumId, versionNo, mappingId, userId, userRole);
                            return;
                        }
                    }

                    // /api/v1/curricula/{id}/versions/{v}/syllabus
                    if (parts.length >= 4 && "syllabus".equalsIgnoreCase(parts[3])) {
                        if ("PUT".equalsIgnoreCase(method)) {
                            flow.step("handleUpdateSyllabus");
                            handleUpdateSyllabus(exchange, curriculumId, versionNo, userId, userRole);
                            return;
                        } else if ("GET".equalsIgnoreCase(method)) {
                            flow.step("handleGetSyllabus");
                            handleGetSyllabus(exchange, curriculumId, versionNo);
                            return;
                        }
                    }

                    // /api/v1/curricula/{id}/versions/{v}/outcomes
                    if (parts.length >= 4 && "outcomes".equalsIgnoreCase(parts[3])) {
                        if ("POST".equalsIgnoreCase(method)) {
                            flow.step("handleAddOutcome");
                            handleAddOutcome(exchange, curriculumId, versionNo, userId, userRole);
                            return;
                        } else if ("GET".equalsIgnoreCase(method)) {
                            flow.step("handleListOutcomes");
                            handleListOutcomes(exchange, curriculumId, versionNo);
                            return;
                        }
                    }
                }
            }

            // Route Not Found
            logger.warn("[CurriculumService] Route not found: [{}] {}", method, fullPath);
            sendError(exchange, 404, "Not Found", "ACD2_ROUTE_NOT_FOUND",
                    "Resource not found in Curriculum Management Service: " + fullPath, fullPath);

        } catch (CurriculumException e) {
            logger.warn("[CurriculumService] Domain exception [{} {}]: {}", method, fullPath, e.getMessage());
            sendError(exchange, e.getStatus(), getStatusReason(e.getStatus()), e.getErrorCode(), e.getMessage(), fullPath);
        } catch (Exception e) {
            logger.error("[CurriculumService] Internal server error [{} {}]: {}", method, fullPath, e.getMessage(), e);
            sendError(exchange, 500, "Internal Server Error", "ACD2_INTERNAL_SERVER_ERROR",
                    "An unexpected error occurred: " + escape(e.getMessage()), fullPath);
        } finally {
            LogContext.clear();
        }
    }

    // =========================================================================
    // Endpoint Handlers
    // =========================================================================

    private void handleCreateCurriculum(HttpExchange exchange, String tenantId, String userId, String userRole) throws IOException {
        String body = readBody(exchange);
        String courseId = extract(body, "courseId", null);
        String academicPattern = extract(body, "academicPattern", "CBCS");
        String academicYear = extract(body, "academicYear", "2026-2027");
        String departmentId = extract(body, "departmentId", "DEPT-CA");
        String campusId = extract(body, "campusId", "MAIN");
        String name = extract(body, "name", null);
        String institutionId = extract(body, "institutionId", "INST-001");

        Curriculum curr = domainService.createCurriculum(courseId, academicPattern, academicYear,
                departmentId, campusId, name, tenantId, institutionId, userId, userRole);

        String dataJson = String.format("{\"curriculumId\":\"%s\",\"id\":\"%s\",\"courseId\":\"%s\",\"version\":%d,\"status\":\"%s\",\"academicPattern\":\"%s\",\"name\":\"%s\"}",
                curr.getId(), curr.getId(), curr.getCourseId(), curr.getCurrentVersion(), curr.getStatus(), curr.getAcademicPattern(), escape(curr.getName()));

        sendStandardResponse(exchange, 201, dataJson);
    }

    private void handleGetCurriculum(HttpExchange exchange, String curriculumId, String userRole, String departmentId) throws IOException {
        Curriculum curr = domainService.getCurriculum(curriculumId, userRole, departmentId);
        String dataJson = String.format("{\"id\":\"%s\",\"curriculumId\":\"%s\",\"courseId\":\"%s\",\"departmentId\":\"%s\",\"status\":\"%s\",\"currentVersion\":%d,\"name\":\"%s\",\"academicPattern\":\"%s\"}",
                curr.getId(), curr.getId(), curr.getCourseId(), curr.getDepartmentId(), curr.getStatus(), curr.getCurrentVersion(), escape(curr.getName()), curr.getAcademicPattern());
        sendStandardResponse(exchange, 200, dataJson);
    }

    private void handleSearchCurricula(HttpExchange exchange, String userRole) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String courseId = getQueryParam(query, "courseId");
        String departmentId = getQueryParam(query, "departmentId");
        String academicYear = getQueryParam(query, "academicYear");
        String statusStr = getQueryParam(query, "status");
        int page = parseInt(getQueryParam(query, "page"), 1);
        int limit = parseInt(getQueryParam(query, "limit"), 50);

        List<Curriculum> list = domainService.searchCurricula(courseId, departmentId, academicYear, statusStr, userRole, page, limit);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            Curriculum c = list.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"id\":\"%s\",\"courseId\":\"%s\",\"departmentId\":\"%s\",\"status\":\"%s\",\"currentVersion\":%d,\"name\":\"%s\"}",
                    c.getId(), c.getCourseId(), c.getDepartmentId(), c.getStatus(), c.getCurrentVersion(), escape(c.getName())));
        }
        sb.append("]");

        sendStandardResponse(exchange, 200, "{\"curricula\":" + sb.toString() + ",\"total\":" + list.size() + "}");
    }

    private void handleListActiveCurricula(HttpExchange exchange) throws IOException {
        List<Curriculum> list = domainService.listActiveCurricula();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            Curriculum c = list.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"id\":\"%s\",\"courseId\":\"%s\",\"status\":\"%s\",\"currentVersion\":%d,\"name\":\"%s\"}",
                    c.getId(), c.getCourseId(), c.getStatus(), c.getCurrentVersion(), escape(c.getName())));
        }
        sb.append("]");
        sendStandardResponse(exchange, 200, "{\"activeCurricula\":" + sb.toString() + ",\"count\":" + list.size() + "}");
    }

    private void handleCreateVersion(HttpExchange exchange, String curriculumId, String userId, String userRole, String tenantId) throws IOException {
        String idempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        String body = readBody(exchange);

        IdempotencyRecord rec = domainService.checkIdempotency(tenantId, idempotencyKey, body);
        if (rec != null) {
            sendJson(exchange, rec.getStatusCode(), rec.getResponseBody());
            return;
        }

        String effectiveFrom = extract(body, "effectiveFrom", null);
        String effectiveTo = extract(body, "effectiveTo", null);
        String academicYear = extract(body, "academicYear", "2026-2027");
        String regulation = extract(body, "regulation", null);
        String changeSummary = extract(body, "changeSummary", null);

        CurriculumVersion ver = domainService.createVersion(curriculumId, effectiveFrom, effectiveTo, academicYear,
                regulation, changeSummary, userId, userRole);

        String dataJson = String.format("{\"id\":\"%s\",\"curriculumId\":\"%s\",\"versionNo\":%d,\"status\":\"%s\",\"academicYear\":\"%s\"}",
                ver.getId(), ver.getCurriculumId(), ver.getVersionNo(), ver.getStatus(), ver.getAcademicYear());

        String resp = buildStandardEnvelope(dataJson);
        domainService.saveIdempotency(tenantId, idempotencyKey, body, 201, resp);
        sendJson(exchange, 201, resp);
    }

    private void handleUpdateDraftVersion(HttpExchange exchange, String curriculumId, int versionNo, String userId, String userRole) throws IOException {
        String body = readBody(exchange);
        String academicYear = extract(body, "academicYear", null);
        String effectiveFrom = extract(body, "effectiveFrom", null);
        String effectiveTo = extract(body, "effectiveTo", null);
        String regulation = extract(body, "regulation", null);
        String changeSummary = extract(body, "changeSummary", null);
        String expVerStr = extract(body, "version", null);
        Long expectedVersion = expVerStr != null ? Long.parseLong(expVerStr) : null;

        CurriculumVersion ver = domainService.updateDraftVersion(curriculumId, versionNo, academicYear,
                effectiveFrom, effectiveTo, regulation, changeSummary, expectedVersion, userId, userRole);

        String dataJson = String.format("{\"id\":\"%s\",\"versionNo\":%d,\"status\":\"%s\",\"version\":%d}",
                ver.getId(), ver.getVersionNo(), ver.getStatus(), ver.getVersion());
        sendStandardResponse(exchange, 200, dataJson);
    }

    private void handleListVersions(HttpExchange exchange, String curriculumId) throws IOException {
        List<CurriculumVersion> list = domainService.listVersions(curriculumId);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            CurriculumVersion v = list.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"id\":\"%s\",\"versionNo\":%d,\"status\":\"%s\",\"academicYear\":\"%s\",\"totalCredits\":%.1f}",
                    v.getId(), v.getVersionNo(), v.getStatus(), v.getAcademicYear(), v.getTotalCredits()));
        }
        sb.append("]");
        sendStandardResponse(exchange, 200, "{\"versions\":" + sb.toString() + "}");
    }

    private void handleGetVersion(HttpExchange exchange, String curriculumId, int versionNo) throws IOException {
        CurriculumVersion v = domainService.getVersion(curriculumId, versionNo);
        String dataJson = String.format("{\"id\":\"%s\",\"curriculumId\":\"%s\",\"versionNo\":%d,\"status\":\"%s\",\"academicYear\":\"%s\",\"totalCredits\":%.1f,\"semesterCount\":%d}",
                v.getId(), v.getCurriculumId(), v.getVersionNo(), v.getStatus(), v.getAcademicYear(), v.getTotalCredits(), v.getSemesterCount());
        sendStandardResponse(exchange, 200, dataJson);
    }

    private void handleAnnualRevision(HttpExchange exchange, String curriculumId, String userId, String userRole) throws IOException {
        String body = readBody(exchange);
        String academicYear = extract(body, "academicYear", "2027-2028");
        CurriculumVersion v = domainService.cloneForAnnualRevision(curriculumId, academicYear, userId, userRole);
        String dataJson = String.format("{\"id\":\"%s\",\"curriculumId\":\"%s\",\"versionNo\":%d,\"status\":\"%s\",\"academicYear\":\"%s\"}",
                v.getId(), v.getCurriculumId(), v.getVersionNo(), v.getStatus(), v.getAcademicYear());
        sendStandardResponse(exchange, 201, dataJson);
    }

    private void handleAddSemester(HttpExchange exchange, String curriculumId, int versionNo, String userRole) throws IOException {
        String body = readBody(exchange);
        int semesterNo = parseInt(extract(body, "semesterNo", "1"), 1);
        String name = extract(body, "name", "Semester " + semesterNo);
        String academicYear = extract(body, "academicYear", null);

        Semester sem = domainService.addSemester(curriculumId, versionNo, semesterNo, name, academicYear, userRole);
        String dataJson = String.format("{\"semesterNo\":%d,\"name\":\"%s\",\"academicYear\":\"%s\"}",
                sem.getSemesterNo(), escape(sem.getName()), sem.getAcademicYear());
        sendStandardResponse(exchange, 201, dataJson);
    }

    private void handleMapSubject(HttpExchange exchange, String curriculumId, int versionNo, String userId,
                                  String userRole, String tenantId) throws IOException {
        String idempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        String body = readBody(exchange);

        IdempotencyRecord rec = domainService.checkIdempotency(tenantId, idempotencyKey, body);
        if (rec != null) {
            sendJson(exchange, rec.getStatusCode(), rec.getResponseBody());
            return;
        }

        String subjectId = extract(body, "subjectId", null);
        int semesterNo = parseInt(extract(body, "semesterNo", "1"), 1);
        int subjectOrder = parseInt(extract(body, "subjectOrder", "1"), 1);
        String subjectType = extract(body, "subjectType", "CORE");
        double credits = parseDouble(extract(body, "credits", "4.0"), 4.0);
        double contactHours = parseDouble(extract(body, "contactHours", "60.0"), 60.0);
        boolean mandatory = Boolean.parseBoolean(extract(body, "mandatory", "true"));

        CurriculumSubject cs = domainService.mapSubject(curriculumId, versionNo, subjectId, semesterNo,
                subjectOrder, subjectType, credits, contactHours, mandatory, userId, userRole);

        String dataJson = String.format("{\"mappingId\":\"%s\",\"id\":\"%s\",\"subjectId\":\"%s\",\"semesterNo\":%d,\"credits\":%.1f,\"status\":\"%s\"}",
                cs.getId(), cs.getId(), cs.getSubjectId(), cs.getSemesterNo(), cs.getCredits(), cs.getStatus());

        String resp = buildStandardEnvelope(dataJson);
        domainService.saveIdempotency(tenantId, idempotencyKey, body, 201, resp);
        sendJson(exchange, 201, resp);
    }

    private void handleGetSubjectMappings(HttpExchange exchange, String curriculumId, int versionNo) throws IOException {
        List<CurriculumSubject> list = domainService.getSubjectMappings(curriculumId, versionNo);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            CurriculumSubject s = list.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"id\":\"%s\",\"subjectId\":\"%s\",\"semesterNo\":%d,\"credits\":%.1f,\"status\":\"%s\",\"mandatory\":%b}",
                    s.getId(), s.getSubjectId(), s.getSemesterNo(), s.getCredits(), s.getStatus(), s.isMandatory()));
        }
        sb.append("]");
        sendStandardResponse(exchange, 200, "{\"subjects\":" + sb.toString() + "}");
    }

    private void handleRemoveSubjectMapping(HttpExchange exchange, String curriculumId, int versionNo, String mappingId,
                                            String userId, String userRole) throws IOException {
        domainService.removeSubjectMapping(curriculumId, versionNo, mappingId, userId, userRole);
        sendStandardResponse(exchange, 200, "{\"status\":\"REMOVED\",\"mappingId\":\"" + mappingId + "\"}");
    }

    private void handleUpdateSyllabus(HttpExchange exchange, String curriculumId, int versionNo, String userId, String userRole) throws IOException {
        String body = readBody(exchange);
        List<SyllabusModule> modules = parseSyllabusModules(body);
        if (modules.isEmpty()) {
            throw new CurriculumValidationException("Syllabus must contain at least one module with moduleId, title, order, and topics");
        }

        domainService.updateSyllabus(curriculumId, versionNo, modules, userId, userRole);
        sendStandardResponse(exchange, 200, "{\"status\":\"UPDATED\",\"moduleCount\":" + modules.size() + "}");
    }

    private void handleGetSyllabus(HttpExchange exchange, String curriculumId, int versionNo) throws IOException {
        List<SyllabusModule> modules = domainService.getSyllabus(curriculumId, versionNo);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < modules.size(); i++) {
            SyllabusModule m = modules.get(i);
            if (i > 0) sb.append(",");
            StringBuilder topSb = new StringBuilder("[");
            for (int j = 0; j < m.getTopics().size(); j++) {
                if (j > 0) topSb.append(",");
                topSb.append("\"").append(escape(m.getTopics().get(j))).append("\"");
            }
            topSb.append("]");
            sb.append(String.format("{\"moduleId\":\"%s\",\"title\":\"%s\",\"order\":%d,\"hours\":%.1f,\"topics\":%s}",
                    m.getModuleId(), escape(m.getTitle()), m.getOrder(), m.getHours(), topSb.toString()));
        }
        sb.append("]");
        sendStandardResponse(exchange, 200, "{\"syllabus\":" + sb.toString() + "}");
    }

    private void handleComplianceView(HttpExchange exchange, String curriculumId, String userId, String userRole) throws IOException {
        ComplianceView cv = domainService.getComplianceView(curriculumId, userId, userRole);
        Curriculum c = cv.getCurriculum();

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append(String.format("\"curriculum\":{\"id\":\"%s\",\"courseId\":\"%s\",\"departmentId\":\"%s\",\"name\":\"%s\",\"academicPattern\":\"%s\",\"status\":\"%s\",\"currentVersion\":%d},",
                c.getId(), c.getCourseId(), c.getDepartmentId(), escape(c.getName()), c.getAcademicPattern(), c.getStatus(), c.getCurrentVersion()));

        sb.append("\"versions\":[");
        for (int i = 0; i < cv.getVersions().size(); i++) {
            CurriculumVersion v = cv.getVersions().get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"id\":\"%s\",\"versionNo\":%d,\"status\":\"%s\",\"academicYear\":\"%s\",\"totalCredits\":%.1f,\"semesterCount\":%d}",
                    v.getId(), v.getVersionNo(), v.getStatus(), v.getAcademicYear(), v.getTotalCredits(), v.getSemesterCount()));
        }
        sb.append("],");

        sb.append("\"outcomes\":[");
        for (int i = 0; i < cv.getOutcomes().size(); i++) {
            CurriculumOutcome o = cv.getOutcomes().get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"id\":\"%s\",\"versionNo\":%d,\"outcomeCode\":\"%s\",\"outcomeType\":\"%s\",\"bloomLevel\":\"%s\",\"description\":\"%s\"}",
                    o.getId(), o.getVersionNo(), o.getOutcomeCode(), o.getOutcomeType(), o.getBloomLevel(), escape(o.getDescription())));
        }
        sb.append("],");

        sb.append("\"subjectMappings\":[");
        for (int i = 0; i < cv.getSubjectMappings().size(); i++) {
            CurriculumSubject s = cv.getSubjectMappings().get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"id\":\"%s\",\"versionNo\":%d,\"subjectId\":\"%s\",\"semesterNo\":%d,\"subjectType\":\"%s\",\"credits\":%.1f,\"mandatory\":%b}",
                    s.getId(), s.getVersionNo(), s.getSubjectId(), s.getSemesterNo(), s.getSubjectType(), s.getCredits(), s.isMandatory()));
        }
        sb.append("],");

        sb.append("\"auditHistory\":[");
        for (int i = 0; i < cv.getAuditHistory().size(); i++) {
            CurriculumHistory h = cv.getAuditHistory().get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"id\":\"%s\",\"sequenceNo\":%d,\"action\":\"%s\",\"actorId\":\"%s\",\"actorRole\":\"%s\",\"occurredAt\":%d}",
                    h.getId(), h.getSequenceNo(), h.getAction(), h.getActorId(), h.getActorRole(), h.getOccurredAt()));
        }
        sb.append("]}");

        sendStandardResponse(exchange, 200, sb.toString());
    }

    /**
     * Parses syllabus module and topic structure from JSON body.
     */
    List<SyllabusModule> parseSyllabusModules(String body) {
        List<SyllabusModule> modules = new ArrayList<>();
        if (body == null || body.trim().isEmpty()) {
            return modules;
        }

        Pattern arrayPattern = Pattern.compile("\"modules\"\\s*:\\s*\\[([\\s\\S]*?)\\]\\s*(?:\\}|$)");
        Matcher arrayMatcher = arrayPattern.matcher(body);
        String modulesContent;
        if (arrayMatcher.find()) {
            modulesContent = arrayMatcher.group(1);
        } else if (body.trim().startsWith("[")) {
            modulesContent = body.trim();
        } else {
            return modules;
        }

        Pattern objPattern = Pattern.compile("\\{([^{}]+)\\}");
        Matcher objMatcher = objPattern.matcher(modulesContent);

        int fallbackOrder = 1;
        while (objMatcher.find()) {
            String objStr = objMatcher.group(1);
            String moduleId = extract(objStr, "moduleId", null);
            String title = extract(objStr, "title", null);
            int order = parseInt(extract(objStr, "order", String.valueOf(fallbackOrder)), fallbackOrder);
            double hours = parseDouble(extract(objStr, "hours", "0.0"), 0.0);

            List<String> topics = new ArrayList<>();
            Pattern topicsPattern = Pattern.compile("\"topics\"\\s*:\\s*\\[([^\\]]*)\\]");
            Matcher topicsMatcher = topicsPattern.matcher(objStr);
            if (topicsMatcher.find()) {
                String topicsContent = topicsMatcher.group(1);
                Pattern itemPattern = Pattern.compile("\"([^\"]*)\"");
                Matcher itemMatcher = itemPattern.matcher(topicsContent);
                while (itemMatcher.find()) {
                    String t = itemMatcher.group(1).trim();
                    if (!t.isEmpty()) {
                        topics.add(t);
                    }
                }
            }

            if (moduleId != null && !moduleId.trim().isEmpty() && title != null && !title.trim().isEmpty()) {
                modules.add(new SyllabusModule(moduleId.trim(), title.trim(), order, topics, hours));
                fallbackOrder++;
            }
        }
        return modules;
    }

    private void handleAddOutcome(HttpExchange exchange, String curriculumId, int versionNo, String userId, String userRole) throws IOException {
        String body = readBody(exchange);
        String code = extract(body, "code", "PO-01");
        String desc = extract(body, "description", "Outcome description");
        String bloom = extract(body, "bloomLevel", "APPLY");
        String type = extract(body, "type", "PO");
        String mappedElementType = extract(body, "mappedElementType", "SEMESTER");
        String mappedElementId = extract(body, "mappedElementId", "1");

        CurriculumOutcome out = domainService.addOutcome(curriculumId, versionNo, code, desc, bloom, type,
                mappedElementType, mappedElementId, userId, userRole);

        String dataJson = String.format("{\"outcomeId\":\"%s\",\"id\":\"%s\",\"outcomeCode\":\"%s\",\"status\":\"%s\"}",
                out.getId(), out.getId(), out.getOutcomeCode(), out.getStatus());
        sendStandardResponse(exchange, 201, dataJson);
    }

    private void handleListOutcomes(HttpExchange exchange, String curriculumId, int versionNo) throws IOException {
        List<CurriculumOutcome> list = domainService.listOutcomes(curriculumId, versionNo);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            CurriculumOutcome o = list.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"id\":\"%s\",\"outcomeCode\":\"%s\",\"outcomeType\":\"%s\",\"description\":\"%s\"}",
                    o.getId(), o.getOutcomeCode(), o.getOutcomeType(), escape(o.getDescription())));
        }
        sb.append("]");
        sendStandardResponse(exchange, 200, "{\"outcomes\":" + sb.toString() + "}");
    }

    private void handleAddPrerequisite(HttpExchange exchange, String curriculumId, String userId, String userRole) throws IOException {
        String body = readBody(exchange);
        String prereqCourseId = extract(body, "prerequisiteCourseId", null);
        String prereqCurrId = extract(body, "prerequisiteCurriculumId", null);
        String relType = extract(body, "relationshipType", "MANDATORY");
        String minGrade = extract(body, "minimumGrade", "PASS");

        CurriculumPrerequisite pre = domainService.addPrerequisite(curriculumId, prereqCourseId, prereqCurrId,
                relType, minGrade, userId, userRole);

        String dataJson = String.format("{\"prerequisiteId\":\"%s\",\"id\":\"%s\",\"status\":\"%s\"}",
                pre.getId(), pre.getId(), pre.getStatus());
        sendStandardResponse(exchange, 201, dataJson);
    }

    private void handleListPrerequisites(HttpExchange exchange, String curriculumId) throws IOException {
        List<CurriculumPrerequisite> list = domainService.listPrerequisites(curriculumId);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            CurriculumPrerequisite p = list.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"id\":\"%s\",\"prerequisiteCourseId\":\"%s\",\"relationshipType\":\"%s\",\"status\":\"%s\"}",
                    p.getId(), p.getPrerequisiteCourseId(), p.getRelationshipType(), p.getStatus()));
        }
        sb.append("]");
        sendStandardResponse(exchange, 200, "{\"prerequisites\":" + sb.toString() + "}");
    }

    private void handleSubmitCurriculum(HttpExchange exchange, String curriculumId, String userId, String userRole) throws IOException {
        Curriculum c = domainService.getCurriculum(curriculumId, userRole, null);
        CurriculumVersion v = domainService.submitForApproval(curriculumId, c.getCurrentVersion(), userId, userRole);
        sendStandardResponse(exchange, 200, String.format("{\"curriculumId\":\"%s\",\"versionNo\":%d,\"status\":\"%s\"}",
                curriculumId, v.getVersionNo(), v.getStatus()));
    }

    private void handleReviewCurriculum(HttpExchange exchange, String curriculumId, String userId, String userRole,
                                        String departmentId) throws IOException {
        String body = readBody(exchange);
        String decision = extract(body, "decision", "APPROVE");
        String feedback = extract(body, "feedback", "Reviewed");
        Curriculum c = domainService.getCurriculum(curriculumId, userRole, departmentId);
        CurriculumVersion v = domainService.reviewCurriculum(curriculumId, c.getCurrentVersion(), decision, feedback,
                userId, userRole, departmentId);
        sendStandardResponse(exchange, 200, String.format("{\"curriculumId\":\"%s\",\"versionNo\":%d,\"status\":\"%s\"}",
                curriculumId, v.getVersionNo(), v.getStatus()));
    }

    private void handleApproveCurriculum(HttpExchange exchange, String curriculumId, String userId, String userRole) throws IOException {
        Curriculum c = domainService.getCurriculum(curriculumId, userRole, null);
        CurriculumVersion v = domainService.approveCurriculum(curriculumId, c.getCurrentVersion(), userId, userRole);
        sendStandardResponse(exchange, 200, String.format("{\"curriculumId\":\"%s\",\"versionNo\":%d,\"status\":\"%s\"}",
                curriculumId, v.getVersionNo(), v.getStatus()));
    }

    private void handlePublishCurriculum(HttpExchange exchange, String curriculumId, String userId, String userRole) throws IOException {
        Curriculum c = domainService.getCurriculum(curriculumId, userRole, null);
        CurriculumVersion v = domainService.publishCurriculum(curriculumId, c.getCurrentVersion(), userId, userRole);
        sendStandardResponse(exchange, 200, String.format("{\"curriculumId\":\"%s\",\"versionNo\":%d,\"status\":\"%s\",\"effectiveFrom\":\"%s\"}",
                curriculumId, v.getVersionNo(), v.getStatus(), v.getPublishedAt()));
    }

    private void handleRetireCurriculum(HttpExchange exchange, String curriculumId, String userId, String userRole) throws IOException {
        String body = readBody(exchange);
        String reason = extract(body, "reason", "Retired by administrator");
        Curriculum c = domainService.retireCurriculum(curriculumId, reason, userId, userRole);
        sendStandardResponse(exchange, 200, String.format("{\"curriculumId\":\"%s\",\"status\":\"%s\"}",
                curriculumId, c.getStatus()));
    }

    private void handleDeleteCurriculum(HttpExchange exchange, String curriculumId, String userRole) throws IOException {
        domainService.deleteCurriculum(curriculumId, userRole);
        sendStandardResponse(exchange, 200, "{\"status\":\"DELETED\",\"curriculumId\":\"" + curriculumId + "\"}");
    }

    private void handleGetHistory(HttpExchange exchange, String curriculumId, String userRole) throws IOException {
        List<CurriculumHistory> history = domainService.getHistory(curriculumId, userRole);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < history.size(); i++) {
            CurriculumHistory h = history.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"id\":\"%s\",\"action\":\"%s\",\"fromStatus\":\"%s\",\"toStatus\":\"%s\",\"actorId\":\"%s\",\"timestamp\":%d}",
                    h.getId(), h.getAction(), h.getFromStatus(), h.getToStatus(), h.getActorId(), h.getOccurredAt()));
        }
        sb.append("]");
        sendStandardResponse(exchange, 200, "{\"history\":" + sb.toString() + "}");
    }

    private void handleGetOutboxEvents(HttpExchange exchange) throws IOException {
        List<OutboxEvent> events = domainService.getOutboxEvents();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < events.size(); i++) {
            OutboxEvent e = events.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"eventId\":\"%s\",\"eventType\":\"%s\",\"status\":\"%s\",\"aggregateId\":\"%s\"}",
                    e.getEventId(), e.getEventType(), e.getStatus(), e.getAggregateId()));
        }
        sb.append("]");
        sendStandardResponse(exchange, 200, "{\"outbox\":" + sb.toString() + "}");
    }

    private void handleGetDeadLetterEvents(HttpExchange exchange) throws IOException {
        List<DeadLetterEvent> list = domainService.getDeadLetterEvents();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            DeadLetterEvent d = list.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"eventId\":\"%s\",\"errorReason\":\"%s\",\"attempts\":%d}",
                    d.getEventId(), escape(d.getErrorReason()), d.getAttempts()));
        }
        sb.append("]");
        sendStandardResponse(exchange, 200, "{\"deadLetters\":" + sb.toString() + "}");
    }

    private void handleProcessInboxEvent(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        String eventType = extract(body, "eventType", null);
        String eventId = extract(body, "eventId", UUID.randomUUID().toString());

        if ("SubjectDeactivated".equalsIgnoreCase(eventType)) {
            String subjectId = extract(body, "subjectId", null);
            domainService.handleSubjectDeactivatedEvent(eventId, subjectId);
            sendStandardResponse(exchange, 200, "{\"status\":\"PROCESSED\",\"eventType\":\"SubjectDeactivated\"}");
        } else if ("CourseDeactivated".equalsIgnoreCase(eventType) || "CourseArchived".equalsIgnoreCase(eventType)) {
            String courseId = extract(body, "courseId", null);
            domainService.handleCourseDeactivatedEvent(eventId, courseId, "Course inactive");
            sendStandardResponse(exchange, 200, "{\"status\":\"PROCESSED\",\"eventType\":\"" + eventType + "\"}");
        } else {
            domainService.routeToDeadLetterQueue(eventId, "INBOX", "Unrecognized event type: " + eventType, body);
            sendStandardResponse(exchange, 200, "{\"status\":\"DEAD_LETTERED\"}");
        }
    }

    // =========================================================================
    // Response Serialization Helpers
    // =========================================================================

    private void sendStandardResponse(HttpExchange exchange, int status, String dataJson) throws IOException {
        String role = LogContext.getUserRole();
        DataClassification classification = domainService.getClassificationLevel(role);
        String filteredJson = filterFieldsByClassification(dataJson, classification);
        String resp = buildStandardEnvelope(filteredJson);
        sendJson(exchange, status, resp);
    }

    /**
     * Filters response JSON properties according to data classification clearance (Story 65).
     */
    public static String filterFieldsByClassification(String json, DataClassification level) {
        if (json == null || json.isEmpty() || level == DataClassification.L4_RESTRICTED) {
            return json;
        }

        // L4 Restricted fields to strip for L1, L2, L3: tenantId, institutionId
        json = json.replaceAll(",?\\s*\"tenantId\"\\s*:\\s*(?:\"[^\"]*\"|null)", "")
                   .replaceAll(",?\\s*\"institutionId\"\\s*:\\s*(?:\"[^\"]*\"|null)", "");

        // L3 Confidential fields to strip for L1, L2: createdBy, updatedBy, approvedBy, publishedBy, approvedAt, publishedAt, auditHistory
        if (level == DataClassification.L1_PUBLIC || level == DataClassification.L2_INTERNAL) {
            json = json.replaceAll(",?\\s*\"createdBy\"\\s*:\\s*(?:\"[^\"]*\"|null)", "")
                       .replaceAll(",?\\s*\"updatedBy\"\\s*:\\s*(?:\"[^\"]*\"|null)", "")
                       .replaceAll(",?\\s*\"approvedBy\"\\s*:\\s*(?:\"[^\"]*\"|null)", "")
                       .replaceAll(",?\\s*\"publishedBy\"\\s*:\\s*(?:\"[^\"]*\"|null)", "")
                       .replaceAll(",?\\s*\"approvedAt\"\\s*:\\s*(?:\"[^\"]*\"|null)", "")
                       .replaceAll(",?\\s*\"publishedAt\"\\s*:\\s*(?:\"[^\"]*\"|null)", "")
                       .replaceAll(",?\\s*\"auditHistory\"\\s*:\\s*\\[[^\\]]*\\]", "");
        }

        // L2 Internal fields to strip for L1 Public: syllabus, changeSummary, checksum
        if (level == DataClassification.L1_PUBLIC) {
            json = json.replaceAll(",?\\s*\"syllabus\"\\s*:\\s*\\[[^\\]]*\\]", "")
                       .replaceAll(",?\\s*\"changeSummary\"\\s*:\\s*(?:\"[^\"]*\"|null)", "")
                       .replaceAll(",?\\s*\"checksum\"\\s*:\\s*(?:\"[^\"]*\"|null)", "");
        }

        // Clean up trailing/leading commas resulting from stripped properties
        json = json.replaceAll("\\{,", "{")
                   .replaceAll(",\\s*,", ",")
                   .replaceAll(",\\s*\\}", "}")
                   .replaceAll(",\\s*\\]", "]");
        return json;
    }

    private String buildStandardEnvelope(String dataJson) {
        String traceId = LogContext.getTraceId();
        String correlationId = traceId != null ? traceId : "CORR-" + System.currentTimeMillis();
        String requestId = "REQ-" + (traceId != null ? traceId : UUID.randomUUID().toString().substring(0, 8));
        String timestamp = new Date().toString();

        return "{"
                + "\"success\":true,"
                + "\"data\":" + dataJson + ","
                + "\"meta\":{"
                + "\"requestId\":\"" + escape(requestId) + "\","
                + "\"correlationId\":\"" + escape(correlationId) + "\","
                + "\"timestamp\":\"" + escape(timestamp) + "\""
                + "}"
                + "}";
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
        recordAuditAndMetrics(exchange, status, null);
    }

    private void sendError(HttpExchange exchange, int status, String error, String errorCode, String message, String path) {
        try {
            String traceId = LogContext.getTraceId();
            if (traceId == null || traceId.isEmpty()) {
                traceId = exchange.getResponseHeaders().getFirst("X-Trace-Id");
            }
            ErrorResponse errorResponse = new ErrorResponse(status, error, errorCode, message, path, traceId);
            byte[] bytes = errorResponse.toBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            if (traceId != null && !traceId.isEmpty()) {
                exchange.getResponseHeaders().set("X-Trace-Id", traceId);
            }
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            recordAuditAndMetrics(exchange, status, errorCode);
        } catch (IOException ioException) {
            logger.warn("Failed to send error response to client: {}", ioException.getMessage());
        }
    }

    private void recordAuditAndMetrics(HttpExchange exchange, int status, String errorCode) {
        try {
            Object startObj = exchange.getAttribute("startTime");
            long startTime = (startObj instanceof Long) ? (Long) startObj : System.currentTimeMillis();
            long durationMs = System.currentTimeMillis() - startTime;
            String method = (String) exchange.getAttribute("method");
            if (method == null) method = exchange.getRequestMethod();
            String path = (String) exchange.getAttribute("path");
            if (path == null) path = exchange.getRequestURI().getPath();
            String traceId = (String) exchange.getAttribute("traceId");
            if (traceId == null) traceId = LogContext.getTraceId();
            String tenantId = (String) exchange.getAttribute("tenantId");
            if (tenantId == null) tenantId = LogContext.getTenantId();
            String userId = (String) exchange.getAttribute("userId");
            if (userId == null) userId = LogContext.getUserId();

            StructuredLogEntry entry = StructuredLogEntry.builder()
                    .service("ACD-02-CurriculumService")
                    .tenantId(tenantId)
                    .requestId("REQ-" + (traceId != null ? traceId : UUID.randomUUID().toString().substring(0, 8)))
                    .correlationId(traceId != null ? traceId : "CORR-UNKNOWN")
                    .actorId(userId != null ? userId : "anonymous")
                    .operation(method + " " + path)
                    .outcome(status < 400 ? "SUCCESS" : "FAILURE")
                    .durationMs(durationMs)
                    .build();

            logger.info("[AUDIT] {}", entry.toJson());

            MetricsCollector.getInstance().recordRequest(method, path, status, durationMs);
            if (errorCode != null && !errorCode.isEmpty()) {
                MetricsCollector.getInstance().recordError(errorCode);
            }
        } catch (Exception ex) {
            logger.warn("Failed to record audit log and metrics: {}", ex.getMessage());
        }
    }

    private String getStatusReason(int status) {
        switch (status) {
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 409: return "Conflict";
            case 422: return "Unprocessable Entity";
            case 503: return "Service Unavailable";
            default: return "Error";
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private String extract(String json, String key, String defaultValue) {
        if (json == null) return defaultValue;
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(?:\"([^\"]*)\"|([^,}\\]\\s]+))");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            String strVal = matcher.group(1);
            if (strVal != null) return strVal;
            String rawVal = matcher.group(2);
            if (rawVal != null) return rawVal;
        }
        return defaultValue;
    }

    private String getQueryParam(String query, String key) {
        if (query == null) return null;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=");
            if (kv.length == 2 && kv[0].equalsIgnoreCase(key)) {
                return kv[1];
            }
        }
        return null;
    }

    private int parseInt(String val, int defaultVal) {
        if (val == null) return defaultVal;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private double parseDouble(String val, double defaultVal) {
        if (val == null) return defaultVal;
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
