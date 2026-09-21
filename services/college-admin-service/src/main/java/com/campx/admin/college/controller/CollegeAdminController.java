package com.campx.admin.college.controller;

import com.campx.admin.college.exception.*;
import com.campx.admin.college.model.ErrorResponse;
import com.campx.admin.college.model.CollegeModels.*;
import com.campx.admin.college.service.CollegeAdminDomainService;
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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP REST Controller for ADM-02 College Admin Service.
 * Serves endpoints under /api/v1/college-admin/**
 */
public class CollegeAdminController implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(CollegeAdminController.class);
    private final CollegeAdminDomainService domainService;

    public CollegeAdminController(CollegeAdminDomainService domainService) {
        this.domainService = domainService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        // 1. Trace & Tenant Correlation Context
        String traceId = exchange.getRequestHeaders().getFirst("X-Trace-Id");
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = LogContext.initTraceId();
        } else {
            LogContext.setTraceId(traceId);
        }
        String tenantId = exchange.getRequestHeaders().getFirst("X-Tenant-Id");
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            LogContext.setTenantId(tenantId);
        }
        String userId = exchange.getRequestHeaders().getFirst("X-User-Id");
        if (userId != null && !userId.trim().isEmpty()) {
            LogContext.setUserId(userId);
        }
        String userRole = exchange.getRequestHeaders().getFirst("X-User-Role");
        if (userRole != null && !userRole.trim().isEmpty()) {
            LogContext.setUserRole(userRole);
        }

        logger.info("[CollegeAdminService] Incoming [{}] {}", method, path);

        FlowTracker flow = logger.flow("CollegeAdminRequest", method + " " + path);
        try {
            // 1. Profile Endpoint
            if (path.equals("/api/v1/college-admin/profile")) {
                if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleGetProfile");
                    handleGetProfile(exchange);
                    return;
                } else if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                    flow.step("handleUpdateProfile");
                    handleUpdateProfile(exchange);
                    return;
                }
            }

            // 2. Departments Endpoint
            if (path.equals("/api/v1/college-admin/departments")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateDepartment");
                    handleCreateDepartment(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListDepartments");
                    handleListDepartments(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/college-admin/departments/")) {
                String depId = path.substring("/api/v1/college-admin/departments/".length());
                if ("DELETE".equalsIgnoreCase(method)) {
                    flow.step("handleRetireDepartment");
                    handleRetireDepartment(exchange, depId);
                    return;
                }
            }

            // 3. Programs Endpoint
            if (path.equals("/api/v1/college-admin/programs")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateProgram");
                    handleCreateProgram(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListPrograms");
                    handleListPrograms(exchange);
                    return;
                }
            }

            // 4. Data Imports Endpoint
            if (path.equals("/api/v1/college-admin/imports") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleSubmitImport");
                handleSubmitImport(exchange);
                return;
            } else if (path.startsWith("/api/v1/college-admin/imports/") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleGetImport");
                String id = path.substring("/api/v1/college-admin/imports/".length());
                handleGetImport(exchange, id);
                return;
            }

            // 5. Governance Documents Endpoint
            if (path.equals("/api/v1/college-admin/documents")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleRegisterDocument");
                    handleRegisterDocument(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListDocuments");
                    handleListDocuments(exchange);
                    return;
                }
            } else if (path.matches("^/api/v1/college-admin/documents/[^/]+/submit$") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleApproveDocument");
                String[] parts = path.split("/");
                String docId = parts[parts.length - 2];
                handleApproveDocument(exchange, docId);
                return;
            }

            // 6. Audit Trail Endpoint
            if (path.equals("/api/v1/college-admin/audit-logs") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleGetAuditLogs");
                handleGetAuditLogs(exchange);
                return;
            }

            // Not found
            logger.warn("[CollegeAdminService] Route not found: [{}] {}", method, path);
            sendError(exchange, 404, "Not Found", "ADM02_ROUTE_NOT_FOUND", "Resource not found in College Admin Service: " + path, path);
        } catch (CollegeResourceNotFoundException e) {
            flow.markFailed(e);
            logger.warn("[CollegeAdminService] Resource not found [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Not Found", e.getErrorCode(), e.getMessage(), path);
        } catch (CollegeResourceConflictException e) {
            flow.markFailed(e);
            logger.warn("[CollegeAdminService] Conflict [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Conflict", e.getErrorCode(), e.getMessage(), path);
        } catch (CollegeLifecycleException e) {
            flow.markFailed(e);
            logger.warn("[CollegeAdminService] Unprocessable entity [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Unprocessable Entity", e.getErrorCode(), e.getMessage(), path);
        } catch (DocumentGovernanceException e) {
            flow.markFailed(e);
            logger.warn("[CollegeAdminService] Document governance error [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Bad Request", e.getErrorCode(), e.getMessage(), path);
        } catch (CollegeMalformedPayloadException e) {
            flow.markFailed(e);
            logger.warn("[CollegeAdminService] Malformed payload [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Bad Request", e.getErrorCode(), e.getMessage(), path);
        } catch (CollegeAdminException e) {
            flow.markFailed(e);
            logger.warn("[CollegeAdminService] College admin error [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Client Error", e.getErrorCode(), e.getMessage(), path);
        } catch (IllegalArgumentException | IllegalStateException e) {
            flow.markFailed(e);
            logger.warn("[CollegeAdminService] Validation error [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, 400, "Bad Request", "ADM02_VALIDATION_ERROR", e.getMessage(), path);
        } catch (Exception e) {
            flow.markFailed(e);
            logger.error("[CollegeAdminService] Internal server error [{} {}]: {}", method, path, e.getMessage(), e);
            sendError(exchange, 500, "Internal Server Error", "ADM02_INTERNAL_SERVER_ERROR", "An unexpected server error occurred: " + escape(e.getMessage()), path);
        } finally {
            if (flow != null) {
                flow.close();
            }
            LogContext.clear();
        }
    }

    private void handleGetProfile(HttpExchange exchange) throws IOException {
        CollegeProfile p = domainService.getProfile();
        String json = "{"
                + "\"code\":\"" + p.getCollegeCode() + "\","
                + "\"name\":\"" + escape(p.getLegalName()) + "\","
                + "\"displayName\":\"" + escape(p.getDisplayName()) + "\","
                + "\"status\":\"" + p.getStatus() + "\","
                + "\"version\":" + p.getCurrentVersion()
                + "}";
        sendJson(exchange, 200, json);
    }

    private void handleUpdateProfile(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        CollegeProfile update = new CollegeProfile();
        update.setDisplayName(extract(body, "displayName", null));
        update.setAddress(extract(body, "address", null));
        update.setStatus(extract(body, "status", null));

        CollegeProfile updated = domainService.updateProfile(update);
        sendJson(exchange, 200, "{\"status\":\"UPDATED\",\"version\":" + updated.getCurrentVersion() + "}");
    }

    private void handleCreateDepartment(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        Department d = new Department();
        d.setDepartmentCode(extract(body, "departmentCode", null));
        d.setName(extract(body, "name", null));
        d.setHeadUserId(extract(body, "headUserId", "FACULTY_HOD"));

        Department created = domainService.createDepartment(d);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"code\":\"" + created.getDepartmentCode() + "\",\"status\":\"" + created.getStatus() + "\"}");
    }

    private void handleRetireDepartment(HttpExchange exchange, String depId) throws IOException {
        domainService.retireDepartment(depId);
        sendJson(exchange, 200, "{\"status\":\"RETIRED\",\"id\":\"" + depId + "\"}");
    }

    private void handleListDepartments(HttpExchange exchange) throws IOException {
        List<Department> list = domainService.listDepartments();
        StringBuilder sb = new StringBuilder("{\"departments\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            Department d = list.get(i);
            sb.append("{\"id\":\"").append(d.getId()).append("\",\"code\":\"").append(d.getDepartmentCode())
              .append("\",\"name\":\"").append(escape(d.getName())).append("\",\"status\":\"").append(d.getStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleCreateProgram(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        Program p = new Program();
        p.setProgramCode(extract(body, "programCode", null));
        p.setName(extract(body, "name", null));
        p.setDepartmentId(extract(body, "departmentId", null));
        String dur = extract(body, "durationYears", "4");
        try {
            p.setDurationYears(Integer.parseInt(dur));
        } catch (NumberFormatException e) {
            throw new CollegeMalformedPayloadException("Field 'durationYears' must be a valid integer");
        }

        Program created = domainService.createProgram(p);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"code\":\"" + created.getProgramCode() + "\",\"published\":true}");
    }

    private void handleListPrograms(HttpExchange exchange) throws IOException {
        List<Program> list = domainService.listPrograms();
        StringBuilder sb = new StringBuilder("{\"programs\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            Program p = list.get(i);
            sb.append("{\"id\":\"").append(p.getId()).append("\",\"code\":\"").append(p.getProgramCode())
              .append("\",\"name\":\"").append(escape(p.getName())).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleSubmitImport(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        DataImportJob job = new DataImportJob();
        job.setEntityType(extract(body, "entityType", "STUDENT"));
        job.setFileRef(extract(body, "fileRef", "s3://imports/students.csv"));
        job.setMode(extract(body, "mode", "UPSERT"));
        job.setIdempotencyKey(extract(body, "idempotencyKey", exchange.getRequestHeaders().getFirst("Idempotency-Key")));

        DataImportJob executed = domainService.submitImportJob(job);
        sendJson(exchange, 200, "{\"importId\":\"" + executed.getImportId() + "\",\"status\":\"" + executed.getStatus() + "\",\"processed\":" + executed.getProcessedRows() + ",\"failed\":" + executed.getFailedRows() + "}");
    }

    private void handleGetImport(HttpExchange exchange, String id) throws IOException {
        DataImportJob job = domainService.getImportJob(id);
        if (job == null) {
            throw new CollegeResourceNotFoundException("Data Import Job", id);
        }
        sendJson(exchange, 200, "{\"id\":\"" + job.getImportId() + "\",\"status\":\"" + job.getStatus() + "\",\"processed\":" + job.getProcessedRows() + "}");
    }

    private void handleRegisterDocument(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        GovernanceDocument doc = new GovernanceDocument();
        doc.setDocumentType(extract(body, "documentType", "POLICY"));
        doc.setTitle(extract(body, "title", "Campus Policy"));
        doc.setOwnerId(extract(body, "ownerId", "ADMIN_01"));
        doc.setClassification(extract(body, "classification", null));

        GovernanceDocument registered = domainService.registerDocument(doc);
        sendJson(exchange, 201, "{\"id\":\"" + registered.getId() + "\",\"checksum\":\"" + registered.getChecksum() + "\",\"status\":\"" + registered.getStatus() + "\"}");
    }

    private void handleApproveDocument(HttpExchange exchange, String docId) throws IOException {
        GovernanceDocument approved = domainService.submitDocumentApproval(docId, "DEAN_OFFICE");
        sendJson(exchange, 200, "{\"id\":\"" + approved.getId() + "\",\"status\":\"" + approved.getStatus() + "\"}");
    }

    private void handleListDocuments(HttpExchange exchange) throws IOException {
        List<GovernanceDocument> list = domainService.listDocuments();
        StringBuilder sb = new StringBuilder("{\"documents\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            GovernanceDocument d = list.get(i);
            sb.append("{\"id\":\"").append(d.getId()).append("\",\"title\":\"").append(escape(d.getTitle()))
              .append("\",\"status\":\"").append(d.getStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleGetAuditLogs(HttpExchange exchange) throws IOException {
        List<Map<String, Object>> logs = domainService.getAuditTrail();
        StringBuilder sb = new StringBuilder("{\"auditLogs\":[");
        for (int i = 0; i < logs.size(); i++) {
            if (i > 0) sb.append(",");
            Map<String, Object> log = logs.get(i);
            sb.append("{");
            sb.append("\"eventId\":\"").append(log.get("eventId")).append("\",");
            sb.append("\"action\":\"").append(log.get("action")).append("\",");
            sb.append("\"principalId\":\"").append(log.get("principalId")).append("\",");
            sb.append("\"principalRole\":\"").append(log.get("principalRole")).append("\",");
            sb.append("\"resourceType\":\"").append(log.get("resourceType")).append("\",");
            sb.append("\"resourceId\":\"").append(log.get("resourceId")).append("\",");
            sb.append("\"status\":\"").append(log.get("status")).append("\",");
            sb.append("\"description\":\"").append(escape((String) log.get("description"))).append("\",");
            sb.append("\"traceId\":\"").append(log.get("traceId") != null ? log.get("traceId") : "").append("\",");
            sb.append("\"timestamp\":").append(log.get("timestamp"));
            sb.append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private String readBody(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String extract(String json, String key, String defaultValue) {
        if (json == null || json.isEmpty()) return defaultValue;
        Pattern p = Pattern.compile("(?i)\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1);

        Pattern pNum = Pattern.compile("(?i)\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher mNum = pNum.matcher(json);
        if (mNum.find()) return mNum.group(1);

        return defaultValue;
    }

    private String escape(String s) {
        return s != null ? s.replace("\"", "\\\"") : "";
    }

    private void sendJson(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        String traceId = LogContext.getTraceId();
        if (traceId != null && !traceId.isEmpty()) {
            exchange.getResponseHeaders().set("X-Trace-Id", traceId);
        }
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int status, String error, String errorCode, String message, String path) throws IOException {
        String traceId = LogContext.getTraceId();
        ErrorResponse err = new ErrorResponse(status, error, errorCode, message, path, traceId);
        byte[] bytes = err.toBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        if (traceId != null && !traceId.isEmpty()) {
            exchange.getResponseHeaders().set("X-Trace-Id", traceId);
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
