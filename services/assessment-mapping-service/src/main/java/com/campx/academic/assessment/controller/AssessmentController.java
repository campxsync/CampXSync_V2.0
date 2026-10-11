package com.campx.academic.assessment.controller;

import com.campx.academic.assessment.exception.*;
import com.campx.academic.assessment.model.AssessmentModels.*;
import com.campx.academic.assessment.model.ErrorResponse;
import com.campx.academic.assessment.service.AssessmentDomainService;
import com.campx.academic.assessment.service.StructuredLogEntry;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.context.LogContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unified HTTP REST Controller for ACD-09 Assessment Mapping Service.
 * Implements endpoints for all 70 user stories, RBAC checks, idempotency,
 * RFC 7807 error envelopes, and structured logging.
 */
public class AssessmentController implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AssessmentController.class);

    private final AssessmentDomainService domainService;

    // Regex path patterns
    private static final Pattern pAssessmentItem = Pattern.compile("^/assessments/([^/]+)$");
    private static final Pattern pComponents = Pattern.compile("^/assessments/([^/]+)/components$");
    private static final Pattern pComponentItem = Pattern.compile("^/assessments/([^/]+)/components/([^/]+)$");
    private static final Pattern pMappings = Pattern.compile("^/assessments/([^/]+)/mappings$");
    private static final Pattern pMappingItem = Pattern.compile("^/assessments/([^/]+)/mappings/([^/]+)$");
    private static final Pattern pValidate = Pattern.compile("^/assessments/([^/]+)/validate$");
    private static final Pattern pSubmitReview = Pattern.compile("^/assessments/([^/]+)/submit-review$");
    private static final Pattern pApprove = Pattern.compile("^/assessments/([^/]+)/approve$");
    private static final Pattern pPublish = Pattern.compile("^/assessments/([^/]+)/publish$");
    private static final Pattern pRetire = Pattern.compile("^/assessments/([^/]+)/retire$");
    private static final Pattern pHistory = Pattern.compile("^/assessments/([^/]+)/history$");
    private static final Pattern pSubjectMap = Pattern.compile("^/assessments/subject/([^/]+)$");
    private static final Pattern pTemplateClone = Pattern.compile("^/assessments/templates/([^/]+)/clone$");

    public AssessmentController(AssessmentDomainService domainService) {
        this.domainService = domainService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        long startTime = System.currentTimeMillis();
        String fullPath = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase();

        // Normalize path
        String path = fullPath
                .replace("/api/v1/academics/assessments", "/assessments")
                .replace("/api/v1/assessments", "/assessments")
                .replace("/v1/assessments", "/assessments")
                .replace("/v1/assessment-catalog", "/assessments")
                .replace("/v1/assessment-mappings", "/assessments");

        String traceId = exchange.getRequestHeaders().getFirst("X-Trace-Id");
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = exchange.getRequestHeaders().getFirst("X-Correlation-Id");
        }
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        String finalOutcome = "SUCCESS";
        int finalResponseCode = 200;
        String currentTenantId = "TENANT-001";
        String currentUserId = "admin-1";
        String currentUserRole = "ACADEMIC_ADMIN";

        try {
            LogContext.setTraceId(traceId);

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
            currentUserRole = userRole;

            String idempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");

            // Rate limiter check
            if (!domainService.getRateLimiter().tryAcquire(tenantId + ":" + userId)) {
                throw new AssessmentRateLimitExceededException("Request rate limit exceeded. Please retry later.");
            }

            // Route dispatch
            if (path.equals("/assessments/health") || path.equals("/health")) {
                sendJsonResponse(exchange, 200, "{\"status\":\"UP\",\"service\":\"assessment-mapping-service\",\"version\":\"2.0.0\"}");
            } else if (path.equals("/assessments/metrics") || path.equals("/metrics")) {
                sendJsonResponse(exchange, 200, domainService.getMetricsCollector().toJson());
            } else if (path.equals("/assessments/analytics/coverage")) {
                sendJsonResponse(exchange, 200, domainService.getQueryService().getCoverageAnalytics(tenantId).toJson());
            } else if (path.equals("/assessments/analytics/balance")) {
                Map<String, String> qp = parseQueryParams(exchange.getRequestURI().getRawQuery());
                String aId = qp.get("assessmentId");
                if (aId == null || aId.trim().isEmpty()) {
                    throw new AssessmentValidationException("assessmentId query parameter is required for balance insights.");
                }
                sendJsonResponse(exchange, 200, domainService.getQueryService().getBalanceInsights(aId).toJson());
            } else if (path.equals("/assessments/export")) {
                Map<String, String> qp = parseQueryParams(exchange.getRequestURI().getRawQuery());
                String format = qp.getOrDefault("format", "json");
                String exportData = domainService.getQueryService().exportAssessments(tenantId, format);
                if ("csv".equalsIgnoreCase(format)) {
                    sendResponse(exchange, 200, "text/csv", exportData);
                } else {
                    sendJsonResponse(exchange, 200, exportData);
                }
            } else if (path.equals("/assessments/events/consume") && method.equals("POST")) {
                String body = readBody(exchange);
                ExternalEventPayload ev = parseExternalEvent(body);
                ev.correlationId = traceId;
                domainService.consumeExternalEvent(ev, traceId);
                sendJsonResponse(exchange, 200, "{\"status\":\"EVENT_CONSUMED\",\"eventId\":\"" + (ev.eventId != null ? ev.eventId : "") + "\"}");
            } else if (path.equals("/assessments/admin/dlq/replay") && method.equals("POST")) {
                String body = readBody(exchange);
                Map<String, String> json = parseSimpleJson(body);
                String dlqId = json.get("dlqEventId");
                boolean replayed = domainService.replayDlqEvent(dlqId, userId, userRole, traceId);
                if (replayed) {
                    sendJsonResponse(exchange, 200, "{\"status\":\"REPLAYED\",\"dlqEventId\":\"" + dlqId + "\"}");
                } else {
                    throw new AssessmentNotFoundException("DLQ event not found: " + dlqId);
                }
            } else if (path.equals("/assessments")) {
                if (method.equals("POST")) {
                    String body = readBody(exchange);
                    CreateAssessmentRequest req = parseCreateAssessment(body);
                    AssessmentStructure created = domainService.createAssessment(req, tenantId, userId, userRole, idempotencyKey, traceId);
                    sendJsonResponse(exchange, 201, created.toJson());
                } else if (method.equals("GET")) {
                    AssessmentQueryFilter filter = parseQueryFilter(exchange.getRequestURI().getRawQuery());
                    List<AssessmentStructure> list = domainService.getQueryService().queryAssessments(filter, tenantId);
                    StringBuilder sb = new StringBuilder("{\"assessments\":[");
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) sb.append(",");
                        sb.append(list.get(i).toJson());
                    }
                    sb.append("],\"count\":").append(list.size()).append("}");
                    sendJsonResponse(exchange, 200, sb.toString());
                } else {
                    sendMethodNotAllowed(exchange);
                }
            } else {
                Matcher mSubject = pSubjectMap.matcher(path);
                Matcher mItem = pAssessmentItem.matcher(path);
                Matcher mComps = pComponents.matcher(path);
                Matcher mCompItem = pComponentItem.matcher(path);
                Matcher mMappings = pMappings.matcher(path);
                Matcher mMappingItem = pMappingItem.matcher(path);
                Matcher mVal = pValidate.matcher(path);
                Matcher mRev = pSubmitReview.matcher(path);
                Matcher mApp = pApprove.matcher(path);
                Matcher mPub = pPublish.matcher(path);
                Matcher mRet = pRetire.matcher(path);
                Matcher mHist = pHistory.matcher(path);
                Matcher mClone = pTemplateClone.matcher(path);

                if (mSubject.matches() && method.equals("GET")) {
                    String subjectId = URLDecoder.decode(mSubject.group(1), "UTF-8");
                    Map<String, String> qp = parseQueryParams(exchange.getRequestURI().getRawQuery());
                    SubjectAssessmentMapResponse resp = domainService.getQueryService().getEffectiveAssessmentForSubject(
                            tenantId, subjectId, qp.get("academicYear"), qp.get("termId"));
                    sendJsonResponse(exchange, 200, resp.toJson());
                } else if (mComps.matches()) {
                    String assessmentId = mComps.group(1);
                    if (method.equals("GET")) {
                        List<AssessmentComponent> comps = domainService.getQueryService().getComponents(assessmentId);
                        StringBuilder sb = new StringBuilder("{\"components\":[");
                        for (int i = 0; i < comps.size(); i++) {
                            if (i > 0) sb.append(",");
                            sb.append(comps.get(i).toJson());
                        }
                        sb.append("]}");
                        sendJsonResponse(exchange, 200, sb.toString());
                    } else if (method.equals("POST")) {
                        String body = readBody(exchange);
                        CreateComponentRequest req = parseCreateComponent(body);
                        AssessmentComponent comp = domainService.addComponent(assessmentId, req, tenantId, userId, userRole, traceId);
                        sendJsonResponse(exchange, 201, comp.toJson());
                    } else {
                        sendMethodNotAllowed(exchange);
                    }
                } else if (mCompItem.matches()) {
                    String assessmentId = mCompItem.group(1);
                    String componentId = mCompItem.group(2);
                    if (method.equals("PUT")) {
                        String body = readBody(exchange);
                        UpdateComponentRequest req = parseUpdateComponent(body);
                        AssessmentComponent updated = domainService.updateComponent(assessmentId, componentId, req, tenantId, userId, userRole, traceId);
                        sendJsonResponse(exchange, 200, updated.toJson());
                    } else if (method.equals("DELETE")) {
                        domainService.deleteComponent(assessmentId, componentId, tenantId, userId, userRole, traceId);
                        sendJsonResponse(exchange, 200, "{\"status\":\"DELETED\",\"componentId\":\"" + componentId + "\"}");
                    } else {
                        sendMethodNotAllowed(exchange);
                    }
                } else if (mMappings.matches()) {
                    String assessmentId = mMappings.group(1);
                    if (method.equals("GET")) {
                        Map<String, String> qp = parseQueryParams(exchange.getRequestURI().getRawQuery());
                        OutcomeType ot = null;
                        if (qp.containsKey("outcomeType")) {
                            try { ot = OutcomeType.valueOf(qp.get("outcomeType").toUpperCase()); } catch (Exception ignored) {}
                        }
                        List<OutcomeMapping> list = domainService.getQueryService().getOutcomeMappings(assessmentId, qp.get("componentId"), ot);
                        StringBuilder sb = new StringBuilder("{\"mappings\":[");
                        for (int i = 0; i < list.size(); i++) {
                            if (i > 0) sb.append(",");
                            sb.append(list.get(i).toJson());
                        }
                        sb.append("]}");
                        sendJsonResponse(exchange, 200, sb.toString());
                    } else if (method.equals("POST")) {
                        String body = readBody(exchange);
                        CreateOutcomeMappingRequest req = parseCreateMapping(body);
                        OutcomeMapping mapping = domainService.addOutcomeMapping(assessmentId, req, tenantId, userId, userRole, traceId);
                        sendJsonResponse(exchange, 201, mapping.toJson());
                    } else {
                        sendMethodNotAllowed(exchange);
                    }
                } else if (mMappingItem.matches() && method.equals("DELETE")) {
                    String assessmentId = mMappingItem.group(1);
                    String mappingId = mMappingItem.group(2);
                    domainService.deleteOutcomeMapping(assessmentId, mappingId, tenantId, userId, userRole, traceId);
                    sendJsonResponse(exchange, 200, "{\"status\":\"DELETED\",\"mappingId\":\"" + mappingId + "\"}");
                } else if (mVal.matches() && (method.equals("GET") || method.equals("POST"))) {
                    String assessmentId = mVal.group(1);
                    AssessmentValidationResult res = domainService.validateAssessment(assessmentId, tenantId, traceId);
                    sendJsonResponse(exchange, 200, res.toJson());
                } else if (mRev.matches() && method.equals("POST")) {
                    String assessmentId = mRev.group(1);
                    AssessmentStructure s = domainService.submitForReview(assessmentId, tenantId, userId, userRole, traceId);
                    sendJsonResponse(exchange, 200, s.toJson());
                } else if (mApp.matches() && method.equals("POST")) {
                    String assessmentId = mApp.group(1);
                    String body = readBody(exchange);
                    ApproveAssessmentRequest req = parseApproveRequest(body);
                    AssessmentStructure s = domainService.approveAssessment(assessmentId, req, tenantId, userId, userRole, traceId);
                    sendJsonResponse(exchange, 200, s.toJson());
                } else if (mPub.matches() && method.equals("POST")) {
                    String assessmentId = mPub.group(1);
                    String body = readBody(exchange);
                    PublishAssessmentRequest req = parsePublishRequest(body);
                    AssessmentStructure s = domainService.publishAssessment(assessmentId, req, tenantId, userId, userRole, idempotencyKey, traceId);
                    sendJsonResponse(exchange, 200, s.toJson());
                } else if (mRet.matches() && method.equals("POST")) {
                    String assessmentId = mRet.group(1);
                    String body = readBody(exchange);
                    Map<String, String> json = parseSimpleJson(body);
                    String reason = json.getOrDefault("reason", "Assessment retired via API");
                    AssessmentStructure s = domainService.retireAssessment(assessmentId, reason, tenantId, userId, userRole, traceId);
                    sendJsonResponse(exchange, 200, s.toJson());
                } else if (mHist.matches() && method.equals("GET")) {
                    String assessmentId = mHist.group(1);
                    List<AssessmentHistory> hist = domainService.getQueryService().getVersionHistory(assessmentId);
                    StringBuilder sb = new StringBuilder("{\"history\":[");
                    for (int i = 0; i < hist.size(); i++) {
                        if (i > 0) sb.append(",");
                        sb.append(hist.get(i).toJson());
                    }
                    sb.append("]}");
                    sendJsonResponse(exchange, 200, sb.toString());
                } else if (mClone.matches() && method.equals("POST")) {
                    String templateId = mClone.group(1);
                    String body = readBody(exchange);
                    CloneTemplateRequest req = parseCloneRequest(body);
                    AssessmentStructure cloned = domainService.cloneFromTemplate(templateId, req, tenantId, userId, userRole, traceId);
                    sendJsonResponse(exchange, 201, cloned.toJson());
                } else if (mItem.matches()) {
                    String assessmentId = mItem.group(1);
                    if (method.equals("GET")) {
                        AssessmentStructure s = domainService.getStructureOrThrow(assessmentId, tenantId);
                        sendJsonResponse(exchange, 200, s.toJson());
                    } else if (method.equals("PUT")) {
                        String body = readBody(exchange);
                        UpdateAssessmentRequest req = parseUpdateAssessment(body);
                        AssessmentStructure updated = domainService.updateAssessment(assessmentId, req, tenantId, userId, userRole, traceId);
                        sendJsonResponse(exchange, 200, updated.toJson());
                    } else {
                        sendMethodNotAllowed(exchange);
                    }
                } else {
                    sendNotFound(exchange, "Resource not found at path: " + fullPath);
                }
            }

        } catch (AssessmentException e) {
            finalOutcome = "ERROR";
            finalResponseCode = e.getStatusCode();
            sendErrorResponse(exchange, e.getStatusCode(), e.getErrorCode(), e.getMessage(), fullPath);
        } catch (Exception e) {
            finalOutcome = "FATAL";
            finalResponseCode = 500;
            logger.error("Unhandled internal server error: " + e.getMessage(), e);
            sendErrorResponse(exchange, 500, "ACD_ASSESSMENT_INTERNAL_ERROR", "Internal system error occurred: " + e.getMessage(), fullPath);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            StructuredLogEntry logEntry = StructuredLogEntry.create(
                    currentTenantId, traceId, traceId, currentUserId, currentUserRole,
                    method + " " + fullPath, finalOutcome, duration);
            logger.info(logEntry.toJson());
            LogContext.clear();
        }
    }

    // ==========================================
    // HTTP UTILITY & RESPONSE METHODS
    // ==========================================

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        sendResponse(exchange, statusCode, "application/json", json);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String contentType, String content) throws IOException {
        byte[] bytes = content != null ? content.getBytes(StandardCharsets.UTF_8) : new byte[0];
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        if (bytes.length > 0) {
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        } else {
            exchange.getResponseBody().close();
        }
    }

    private void sendErrorResponse(HttpExchange exchange, int status, String errorCode, String detail, String path) throws IOException {
        String title = status >= 500 ? "Internal Server Error" : "Assessment Request Error";
        ErrorResponse err = new ErrorResponse("https://campx.com/errors/" + errorCode.toLowerCase(), title, status, detail, path, errorCode);
        sendJsonResponse(exchange, status, err.toJson());
    }

    private void sendNotFound(HttpExchange exchange, String detail) throws IOException {
        sendErrorResponse(exchange, 404, "ACD_ASSESSMENT_NOT_FOUND", detail, exchange.getRequestURI().getPath());
    }

    private void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        sendErrorResponse(exchange, 405, "ACD_ASSESSMENT_METHOD_NOT_ALLOWED", "HTTP method " + exchange.getRequestMethod() + " is not allowed for this route", exchange.getRequestURI().getPath());
    }

    private String readBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    private Map<String, String> parseQueryParams(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return params;
        }
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    String k = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                    String v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    params.put(k, v);
                } catch (UnsupportedEncodingException ignored) {}
            }
        }
        return params;
    }

    private AssessmentQueryFilter parseQueryFilter(String rawQuery) {
        Map<String, String> qp = parseQueryParams(rawQuery);
        AssessmentQueryFilter f = new AssessmentQueryFilter();
        f.subjectId = qp.get("subjectId");
        f.courseId = qp.get("courseId");
        f.termId = qp.get("termId");
        f.academicYear = qp.get("academicYear");
        f.status = qp.get("status");
        f.assessmentType = qp.get("assessmentType");
        if (qp.containsKey("page")) {
            try { f.page = Integer.parseInt(qp.get("page")); } catch (Exception ignored) {}
        }
        if (qp.containsKey("size")) {
            try { f.size = Integer.parseInt(qp.get("size")); } catch (Exception ignored) {}
        }
        return f;
    }

    // ==========================================
    // LIGHTWEIGHT JSON PARSERS
    // ==========================================

    private Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return map;
        String clean = json.trim();
        if (clean.startsWith("{") && clean.endsWith("}")) {
            clean = clean.substring(1, clean.length() - 1);
        }
        // Match key-value pairs
        Pattern p = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"[^\"]*\"|[^,}]+)");
        Matcher m = p.matcher(clean);
        while (m.find()) {
            String k = m.group(1);
            String v = m.group(2).trim();
            if (v.startsWith("\"") && v.endsWith("\"")) {
                v = v.substring(1, v.length() - 1);
            }
            map.put(k, v);
        }
        return map;
    }

    private CreateAssessmentRequest parseCreateAssessment(String json) {
        Map<String, String> m = parseSimpleJson(json);
        CreateAssessmentRequest req = new CreateAssessmentRequest();
        req.institutionId = m.get("institutionId");
        req.departmentId = m.get("departmentId");
        req.subjectId = m.get("subjectId");
        req.courseId = m.get("courseId");
        req.curriculumId = m.get("curriculumId");
        req.academicYear = m.get("academicYear");
        req.termId = m.get("termId");
        req.assessmentCode = m.get("assessmentCode");
        req.assessmentName = m.get("assessmentName");
        req.assessmentType = m.get("assessmentType");
        if (m.containsKey("totalMarks")) {
            try { req.totalMarks = Double.parseDouble(m.get("totalMarks")); } catch (Exception ignored) {}
        }
        if (m.containsKey("totalWeightage")) {
            try { req.totalWeightage = Double.parseDouble(m.get("totalWeightage")); } catch (Exception ignored) {}
        }
        return req;
    }

    private UpdateAssessmentRequest parseUpdateAssessment(String json) {
        Map<String, String> m = parseSimpleJson(json);
        UpdateAssessmentRequest req = new UpdateAssessmentRequest();
        req.assessmentName = m.get("assessmentName");
        req.assessmentType = m.get("assessmentType");
        req.reviewNotes = m.get("reviewNotes");
        if (m.containsKey("totalMarks")) {
            try { req.totalMarks = Double.parseDouble(m.get("totalMarks")); } catch (Exception ignored) {}
        }
        if (m.containsKey("totalWeightage")) {
            try { req.totalWeightage = Double.parseDouble(m.get("totalWeightage")); } catch (Exception ignored) {}
        }
        if (m.containsKey("expectedVersion")) {
            try { req.expectedVersion = Integer.parseInt(m.get("expectedVersion")); } catch (Exception ignored) {}
        }
        return req;
    }

    private CreateComponentRequest parseCreateComponent(String json) {
        Map<String, String> m = parseSimpleJson(json);
        CreateComponentRequest req = new CreateComponentRequest();
        req.componentCode = m.get("componentCode");
        req.componentName = m.get("componentName");
        req.componentType = m.get("componentType");
        req.evaluationMethod = m.get("evaluationMethod");
        req.rubricRef = m.get("rubricRef");
        req.attemptPolicy = m.get("attemptPolicy");
        if (m.containsKey("sequenceNo")) {
            try { req.sequenceNo = Integer.parseInt(m.get("sequenceNo")); } catch (Exception ignored) {}
        }
        if (m.containsKey("maxMarks")) {
            try { req.maxMarks = Double.parseDouble(m.get("maxMarks")); } catch (Exception ignored) {}
        }
        if (m.containsKey("passingMarks")) {
            try { req.passingMarks = Double.parseDouble(m.get("passingMarks")); } catch (Exception ignored) {}
        }
        if (m.containsKey("weightage")) {
            try { req.weightage = Double.parseDouble(m.get("weightage")); } catch (Exception ignored) {}
        }
        if (m.containsKey("maxAttempts")) {
            try { req.maxAttempts = Integer.parseInt(m.get("maxAttempts")); } catch (Exception ignored) {}
        }
        return req;
    }

    private UpdateComponentRequest parseUpdateComponent(String json) {
        Map<String, String> m = parseSimpleJson(json);
        UpdateComponentRequest req = new UpdateComponentRequest();
        req.componentName = m.get("componentName");
        req.componentType = m.get("componentType");
        req.evaluationMethod = m.get("evaluationMethod");
        req.rubricRef = m.get("rubricRef");
        req.attemptPolicy = m.get("attemptPolicy");
        if (m.containsKey("sequenceNo")) {
            try { req.sequenceNo = Integer.parseInt(m.get("sequenceNo")); } catch (Exception ignored) {}
        }
        if (m.containsKey("maxMarks")) {
            try { req.maxMarks = Double.parseDouble(m.get("maxMarks")); } catch (Exception ignored) {}
        }
        if (m.containsKey("passingMarks")) {
            try { req.passingMarks = Double.parseDouble(m.get("passingMarks")); } catch (Exception ignored) {}
        }
        if (m.containsKey("weightage")) {
            try { req.weightage = Double.parseDouble(m.get("weightage")); } catch (Exception ignored) {}
        }
        if (m.containsKey("maxAttempts")) {
            try { req.maxAttempts = Integer.parseInt(m.get("maxAttempts")); } catch (Exception ignored) {}
        }
        if (m.containsKey("expectedVersion")) {
            try { req.expectedVersion = Integer.parseInt(m.get("expectedVersion")); } catch (Exception ignored) {}
        }
        return req;
    }

    private CreateOutcomeMappingRequest parseCreateMapping(String json) {
        Map<String, String> m = parseSimpleJson(json);
        CreateOutcomeMappingRequest req = new CreateOutcomeMappingRequest();
        req.componentId = m.get("componentId");
        req.outcomeType = m.get("outcomeType");
        req.outcomeCode = m.get("outcomeCode");
        req.mappingLevel = m.get("mappingLevel");
        req.attainmentPolicyRef = m.get("attainmentPolicyRef");
        if (m.containsKey("weight")) {
            try { req.weight = Double.parseDouble(m.get("weight")); } catch (Exception ignored) {}
        }
        return req;
    }

    private PublishAssessmentRequest parsePublishRequest(String json) {
        Map<String, String> m = parseSimpleJson(json);
        PublishAssessmentRequest req = new PublishAssessmentRequest();
        req.approvalRef = m.get("approvalRef");
        req.reason = m.get("reason");
        if (m.containsKey("expectedVersion")) {
            try { req.expectedVersion = Integer.parseInt(m.get("expectedVersion")); } catch (Exception ignored) {}
        }
        return req;
    }

    private ApproveAssessmentRequest parseApproveRequest(String json) {
        Map<String, String> m = parseSimpleJson(json);
        ApproveAssessmentRequest req = new ApproveAssessmentRequest();
        req.approvalRef = m.get("approvalRef");
        req.comments = m.get("comments");
        return req;
    }

    private CloneTemplateRequest parseCloneRequest(String json) {
        Map<String, String> m = parseSimpleJson(json);
        CloneTemplateRequest req = new CloneTemplateRequest();
        req.newAssessmentCode = m.get("newAssessmentCode");
        req.newAssessmentName = m.get("newAssessmentName");
        req.subjectId = m.get("subjectId");
        req.courseId = m.get("courseId");
        req.curriculumId = m.get("curriculumId");
        req.academicYear = m.get("academicYear");
        req.termId = m.get("termId");
        return req;
    }

    private ExternalEventPayload parseExternalEvent(String json) {
        Map<String, String> m = parseSimpleJson(json);
        ExternalEventPayload ev = new ExternalEventPayload();
        ev.eventId = m.get("eventId");
        ev.eventType = m.get("eventType");
        ev.subjectId = m.get("subjectId");
        ev.curriculumId = m.get("curriculumId");
        ev.courseId = m.get("courseId");
        ev.tenantId = m.get("tenantId");
        return ev;
    }
}
