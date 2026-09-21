package com.campx.admin.college.controller;

import com.campx.admin.college.model.CollegeModels.*;
import com.campx.admin.college.service.CollegeAdminDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

        logger.info("[CollegeAdminService] [{}] {}", method, path);

        try {
            // 1. Profile Endpoint
            if (path.equals("/api/v1/college-admin/profile")) {
                if ("GET".equalsIgnoreCase(method)) {
                    handleGetProfile(exchange);
                    return;
                } else if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                    handleUpdateProfile(exchange);
                    return;
                }
            }

            // 2. Departments Endpoint
            if (path.equals("/api/v1/college-admin/departments")) {
                if ("POST".equalsIgnoreCase(method)) {
                    handleCreateDepartment(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    handleListDepartments(exchange);
                    return;
                }
            }

            // 3. Programs Endpoint
            if (path.equals("/api/v1/college-admin/programs")) {
                if ("POST".equalsIgnoreCase(method)) {
                    handleCreateProgram(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    handleListPrograms(exchange);
                    return;
                }
            }

            // 4. Data Imports Endpoint
            if (path.equals("/api/v1/college-admin/imports") && "POST".equalsIgnoreCase(method)) {
                handleSubmitImport(exchange);
                return;
            } else if (path.startsWith("/api/v1/college-admin/imports/") && "GET".equalsIgnoreCase(method)) {
                String id = path.substring("/api/v1/college-admin/imports/".length());
                handleGetImport(exchange, id);
                return;
            }

            // 5. Governance Documents Endpoint
            if (path.equals("/api/v1/college-admin/documents")) {
                if ("POST".equalsIgnoreCase(method)) {
                    handleRegisterDocument(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    handleListDocuments(exchange);
                    return;
                }
            } else if (path.matches("^/api/v1/college-admin/documents/[^/]+/submit$") && "POST".equalsIgnoreCase(method)) {
                String[] parts = path.split("/");
                String docId = parts[parts.length - 2];
                handleApproveDocument(exchange, docId);
                return;
            }

            sendJson(exchange, 404, "{\"error\":\"Resource not found in College Admin Service\",\"path\":\"" + path + "\"}");
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.warn("Validation error in College Admin: {}", e.getMessage());
            sendJson(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        } catch (Exception e) {
            logger.error("Internal server error in College Admin: {}", e.getMessage(), e);
            sendJson(exchange, 500, "{\"error\":\"Internal Server Error\",\"message\":\"" + escape(e.getMessage()) + "\"}");
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
        d.setDepartmentCode(extract(body, "departmentCode", "DEP-" + System.currentTimeMillis()));
        d.setName(extract(body, "name", "Department of Studies"));
        d.setHeadUserId(extract(body, "headUserId", "FACULTY_HOD"));

        Department created = domainService.createDepartment(d);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"code\":\"" + created.getDepartmentCode() + "\",\"status\":\"" + created.getStatus() + "\"}");
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
        p.setProgramCode(extract(body, "programCode", "PRG_NEW"));
        p.setName(extract(body, "name", "New Degree Program"));
        p.setDepartmentId(extract(body, "departmentId", "DEP_CS"));
        String dur = extract(body, "durationYears", "4");
        p.setDurationYears(Integer.parseInt(dur));

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
            sendJson(exchange, 404, "{\"error\":\"Import job not found\"}");
            return;
        }
        sendJson(exchange, 200, "{\"id\":\"" + job.getImportId() + "\",\"status\":\"" + job.getStatus() + "\",\"processed\":" + job.getProcessedRows() + "}");
    }

    private void handleRegisterDocument(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        GovernanceDocument doc = new GovernanceDocument();
        doc.setDocumentType(extract(body, "documentType", "POLICY"));
        doc.setTitle(extract(body, "title", "Campus Policy"));
        doc.setOwnerId(extract(body, "ownerId", "ADMIN_01"));
        doc.setClassification(extract(body, "classification", "INTERNAL"));

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
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
