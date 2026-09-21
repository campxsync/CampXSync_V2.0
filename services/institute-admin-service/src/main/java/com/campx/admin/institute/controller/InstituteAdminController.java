package com.campx.admin.institute.controller;

import com.campx.admin.institute.model.InstituteModels.*;
import com.campx.admin.institute.service.InstituteAdminDomainService;
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
 * HTTP REST Controller for ADM-01 Institute Admin Service.
 * Serves endpoints under /api/v1/admin/**
 */
public class InstituteAdminController implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(InstituteAdminController.class);
    private final InstituteAdminDomainService domainService;

    public InstituteAdminController(InstituteAdminDomainService domainService) {
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

        logger.info("[InstituteAdminService] Incoming [{}] {}", method, path);

        FlowTracker flow = logger.flow("InstituteAdminRequest", method + " " + path);
        try {
            // 1. Institutes Endpoint
            if (path.equals("/api/v1/admin/institutes")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateInstitute");
                    handleCreateInstitute(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListInstitutes");
                    handleListInstitutes(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/admin/institutes/")) {
                String id = path.substring("/api/v1/admin/institutes/".length());
                if ("PUT".equalsIgnoreCase(method)) {
                    flow.step("handleUpdateInstitute");
                    handleUpdateInstitute(exchange, id);
                    return;
                }
            }

            // 2. Colleges Registration Endpoint
            if (path.equals("/api/v1/admin/colleges") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleRegisterCollege");
                handleRegisterCollege(exchange);
                return;
            }

            // 3. Tenant Provisioning Endpoint
            if (path.matches("^/api/v1/admin/tenants/[^/]+/provision$") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleProvisionTenant");
                handleProvisionTenant(exchange, path);
                return;
            }
            if (path.equals("/api/v1/admin/tenants/provisioning") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleListProvisioning");
                handleListProvisioning(exchange);
                return;
            }

            // 4. Configuration Endpoint
            if (path.equals("/api/v1/admin/configuration")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleSetConfiguration");
                    handleSetConfiguration(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleGetConfiguration");
                    handleGetConfiguration(exchange);
                    return;
                }
            }

            // 5. Commercial Plans Endpoint
            if (path.equals("/api/v1/admin/billing/plans") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleGetPlans");
                handleGetPlans(exchange);
                return;
            }

            // 6. Platform Audit Logs Endpoint (CampX Logger Service Integration)
            if (path.equals("/api/v1/admin/audit-logs") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleGetAuditLogs");
                handleGetAuditLogs(exchange);
                return;
            }

            // Not found
            logger.warn("[InstituteAdminService] Route not found: [{}] {}", method, path);
            sendJson(exchange, 404, "{\"error\":\"Resource not found in Institute Admin Service\",\"path\":\"" + path + "\"}");
        } catch (IllegalArgumentException | IllegalStateException e) {
            flow.markFailed(e);
            logger.warn("Validation error in Institute Admin: {}", e.getMessage());
            sendJson(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        } catch (Exception e) {
            flow.markFailed(e);
            logger.error("Internal server error in Institute Admin: {}", e.getMessage(), e);
            sendJson(exchange, 500, "{\"error\":\"Internal Server Error\",\"message\":\"" + escape(e.getMessage()) + "\"}");
        } finally {
            if (flow != null) {
                flow.close();
            }
            LogContext.clear();
        }
    }

    private void handleCreateInstitute(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        Institute inst = new Institute();
        inst.setInstituteCode(extract(body, "instituteCode", "INST-" + System.currentTimeMillis()));
        inst.setLegalName(extract(body, "legalName", "Untitled Institute"));
        inst.setDisplayName(extract(body, "displayName", inst.getLegalName()));
        inst.setTimezone(extract(body, "timezone", "Asia/Kolkata"));
        inst.setLocale(extract(body, "locale", "en_IN"));
        inst.setDefaultCurrency(extract(body, "defaultCurrency", "INR"));

        Institute created = domainService.registerInstitute(inst);
        String resp = "{"
                + "\"id\":\"" + created.getId() + "\","
                + "\"instituteCode\":\"" + created.getInstituteCode() + "\","
                + "\"legalName\":\"" + escape(created.getLegalName()) + "\","
                + "\"displayName\":\"" + escape(created.getDisplayName()) + "\","
                + "\"status\":\"" + created.getStatus() + "\","
                + "\"version\":" + created.getVersion()
                + "}";
        sendJson(exchange, 201, resp);
    }

    private void handleListInstitutes(HttpExchange exchange) throws IOException {
        List<Institute> list = domainService.listInstitutes();
        StringBuilder sb = new StringBuilder("{\"institutes\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            Institute it = list.get(i);
            sb.append("{\"id\":\"").append(it.getId()).append("\",\"code\":\"").append(it.getInstituteCode())
              .append("\",\"name\":\"").append(escape(it.getDisplayName())).append("\",\"status\":\"").append(it.getStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleUpdateInstitute(HttpExchange exchange, String id) throws IOException {
        String body = readBody(exchange);
        Institute update = new Institute();
        update.setDisplayName(extract(body, "displayName", null));
        update.setStatus(extract(body, "status", null));
        update.setTimezone(extract(body, "timezone", null));

        Institute updated = domainService.updateInstitute(id, update);
        sendJson(exchange, 200, "{\"status\":\"UPDATED\",\"version\":" + updated.getVersion() + ",\"instituteId\":\"" + id + "\"}");
    }

    private void handleRegisterCollege(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        College c = new College();
        c.setCollegeCode(extract(body, "collegeCode", "COL-" + System.currentTimeMillis()));
        c.setName(extract(body, "name", "College of Engineering"));
        c.setInstituteId(extract(body, "instituteId", null));

        College registered = domainService.registerCollege(c);
        sendJson(exchange, 201, "{\"id\":\"" + registered.getId() + "\",\"collegeCode\":\"" + registered.getCollegeCode() + "\",\"status\":\"" + registered.getStatus() + "\"}");
    }

    private void handleProvisionTenant(HttpExchange exchange, String path) throws IOException {
        // extract tenantId from path: /api/v1/admin/tenants/{id}/provision
        String[] parts = path.split("/");
        String tenantId = parts[parts.length - 2];

        String body = readBody(exchange);
        TenantProvisioning req = new TenantProvisioning();
        req.setTenantId(tenantId);
        req.setTargetScope(extract(body, "targetScope", "CAMPUS_MAIN"));
        req.setPlanId(extract(body, "planId", "ENTERPRISE_CAMPUS_2026"));
        req.setIdempotencyKey(extract(body, "idempotencyKey", exchange.getRequestHeaders().getFirst("Idempotency-Key")));

        TenantProvisioning completed = domainService.provisionTenant(req);
        sendJson(exchange, 200, "{\"provisioningId\":\"" + completed.getProvisioningId() + "\",\"tenantId\":\"" + completed.getTenantId() + "\",\"status\":\"" + completed.getProvisioningStatus() + "\"}");
    }

    private void handleListProvisioning(HttpExchange exchange) throws IOException {
        List<TenantProvisioning> list = domainService.getProvisioningJobs();
        StringBuilder sb = new StringBuilder("{\"jobs\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            TenantProvisioning p = list.get(i);
            sb.append("{\"id\":\"").append(p.getProvisioningId()).append("\",\"tenant\":\"").append(p.getTenantId())
              .append("\",\"status\":\"").append(p.getProvisioningStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleSetConfiguration(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        GlobalSetting s = new GlobalSetting();
        s.setKey(extract(body, "key", "config.key"));
        s.setValue(extract(body, "value", "config.value"));
        s.setDataType(extract(body, "dataType", "STRING"));
        s.setScope(extract(body, "scope", "GLOBAL"));
        s.setSecret("true".equalsIgnoreCase(extract(body, "isSecret", "false")));

        GlobalSetting saved = domainService.setGlobalSetting(s);
        sendJson(exchange, 200, "{\"status\":\"SAVED\",\"key\":\"" + saved.getKey() + "\"}");
    }

    private void handleGetConfiguration(HttpExchange exchange) throws IOException {
        List<GlobalSetting> list = domainService.getGlobalSettings();
        StringBuilder sb = new StringBuilder("{\"configuration\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            GlobalSetting s = list.get(i);
            sb.append("{\"key\":\"").append(s.getKey()).append("\",\"value\":\"").append(escape(s.getValue())).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleGetPlans(HttpExchange exchange) throws IOException {
        List<CommercialPlan> plans = domainService.getCommercialPlans();
        StringBuilder sb = new StringBuilder("{\"plans\":[");
        for (int i = 0; i < plans.size(); i++) {
            if (i > 0) sb.append(",");
            CommercialPlan p = plans.get(i);
            sb.append("{\"code\":\"").append(p.getPlanCode()).append("\",\"name\":\"").append(escape(p.getName()))
              .append("\",\"price\":").append(p.getPrice()).append("}");
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
}
