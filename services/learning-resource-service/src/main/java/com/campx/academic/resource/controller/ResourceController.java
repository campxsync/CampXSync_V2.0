package com.campx.academic.resource.controller;

import com.campx.academic.resource.exception.*;
import com.campx.academic.resource.model.ErrorResponse;
import com.campx.academic.resource.model.ResourceModels.*;
import com.campx.academic.resource.service.MetricsCollector;
import com.campx.academic.resource.service.RateLimiter;
import com.campx.academic.resource.service.ResourceDomainService;
import com.campx.academic.resource.service.StructuredLogEntry;
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
 * Unified HTTP REST Controller for ACD-08 Learning Resource Service.
 * Implements endpoints, RBAC authorization, rate limiting, and structured audit logging.
 */
public class ResourceController implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(ResourceController.class);

    private final ResourceDomainService domainService;
    private final MetricsCollector metricsCollector = MetricsCollector.getInstance();
    private final RateLimiter rateLimiter = new RateLimiter(500, 100);

    // Regex path patterns
    private static final Pattern pResourceItem = Pattern.compile("^/resources/([^/]+)$");
    private static final Pattern pResourcePublish = Pattern.compile("^/resources/([^/]+)/publish$");
    private static final Pattern pResourceArchive = Pattern.compile("^/resources/([^/]+)/archive$");
    private static final Pattern pResourceVersions = Pattern.compile("^/resources/([^/]+)/versions$");
    private static final Pattern pResourceVersionRestore = Pattern.compile("^/resources/([^/]+)/versions/(\\d+)/restore$");
    private static final Pattern pResourceAccess = Pattern.compile("^/resources/([^/]+)/access$");
    private static final Pattern pResourceAccessItem = Pattern.compile("^/resources/([^/]+)/access/([^/]+)$");
    private static final Pattern pResourceDownload = Pattern.compile("^/resources/([^/]+)/download$");
    private static final Pattern pResourceHistory = Pattern.compile("^/resources/([^/]+)/history$");
    private static final Pattern pResourceUsage = Pattern.compile("^/resources/([^/]+)/usage$");
    private static final Pattern pVersionItem = Pattern.compile("^/versions/([^/]+)$");
    private static final Pattern pDlqReplay = Pattern.compile("^/resources/dlq/([^/]+)/replay$");

    public ResourceController(ResourceDomainService domainService) {
        this.domainService = domainService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        long startTime = System.currentTimeMillis();
        String fullPath = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase();

        // Normalize path by stripping prefixes
        String path = fullPath
                .replace("/api/v1/academics/resources", "/resources")
                .replace("/api/v1/resources", "/resources")
                .replace("/v1/resources", "/resources")
                .replace("/api/v1/academics/versions", "/versions")
                .replace("/api/v1/versions", "/versions")
                .replace("/v1/resource-versions", "/versions");

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
        String currentUserId = "faculty-1";
        String currentUserRole = "FACULTY";

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
                userId = "faculty-1";
            }
            currentUserId = userId;

            String userRole = exchange.getRequestHeaders().getFirst("X-User-Role");
            if (userRole != null && !userRole.trim().isEmpty()) {
                LogContext.setUserRole(userRole);
            } else {
                userRole = "FACULTY";
            }
            currentUserRole = userRole;

            String deptId = exchange.getRequestHeaders().getFirst("X-Department-Id");
            String batchId = exchange.getRequestHeaders().getFirst("X-Batch-Id");
            String programId = exchange.getRequestHeaders().getFirst("X-Program-Id");
            String idempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");

            // External API Key check
            String apiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                ApiKeyRecord keyRecord = domainService.validateApiKey(apiKey.trim(), tenantId);
                if (keyRecord == null) {
                    finalOutcome = "UNAUTHORIZED";
                    finalResponseCode = 401;
                    sendError(exchange, 401, "ACD_RESOURCE_UNAUTHORIZED", "Invalid or expired API Key", null, fullPath);
                    return;
                }
                userRole = "EXTERNAL_API";
                userId = "api-consumer-" + keyRecord.getKeyId();
                currentUserId = userId;
                currentUserRole = userRole;
            }

            LogContext.setService("ACD-08-LearningResourceService");
            exchange.getResponseHeaders().set("X-Trace-Id", traceId);
            exchange.getResponseHeaders().set("X-Correlation-Id", traceId);

            // Operational Endpoints
            if ("/actuator/health".equals(fullPath) || fullPath.endsWith("/health")) {
                sendJson(exchange, 200, "{\"status\":\"UP\",\"service\":\"ACD-08-LearningResourceService\"}");
                return;
            }
            if ("/metrics".equals(fullPath) || fullPath.endsWith("/metrics")) {
                String prom = metricsCollector.toPrometheusFormat();
                byte[] bytes = prom.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }

            // Rate Limiter Check
            RateLimiter.RateLimitResult rateCheck = rateLimiter.tryAcquire(tenantId + ":" + userId);
            exchange.getResponseHeaders().set("X-RateLimit-Limit", String.valueOf(rateCheck.getLimit()));
            exchange.getResponseHeaders().set("X-RateLimit-Remaining", String.valueOf(rateCheck.getRemaining()));
            if (!rateCheck.isAllowed()) {
                finalOutcome = "RATE_LIMITED";
                finalResponseCode = 429;
                exchange.getResponseHeaders().set("Retry-After", String.valueOf(rateCheck.getRetryAfterSeconds()));
                metricsCollector.recordError("ACD_RESOURCE_RATE_LIMIT_EXCEEDED");
                metricsCollector.recordRequest(method, fullPath, 429, System.currentTimeMillis() - startTime);
                sendError(exchange, 429, "ACD_RESOURCE_RATE_LIMIT_EXCEEDED", "Too many requests. Limit exceeded.", null, fullPath);
                return;
            }

            // Read Request Body
            String requestBody = readBody(exchange);

            String responseJson;
            int responseCode = 200;

            // Route 1: POST /resources (Create)
            if ("/resources".equals(path) && "POST".equalsIgnoreCase(method)) {
                CreateResourceRequest req = parseCreateResourceRequest(requestBody);
                LearningResource res = domainService.createResource(req, tenantId, userId, userRole, idempotencyKey, traceId);
                responseJson = buildSuccessEnvelope(res, "Resource registered successfully", traceId);
                responseCode = 201;
            }
            // Route 2: GET /resources or GET /resources/search (Search/List)
            else if (("/resources".equals(path) || "/resources/search".equals(path)) && "GET".equalsIgnoreCase(method)) {
                Map<String, String> q = parseQuery(exchange.getRequestURI().getQuery());
                ResourceSearchFilter filter = new ResourceSearchFilter();
                filter.query = q.get("q") != null ? q.get("q") : q.get("keyword");
                filter.resourceType = q.get("resourceType");
                filter.subjectId = q.get("subjectId");
                filter.courseId = q.get("courseId");
                filter.curriculumId = q.get("curriculumId");
                filter.departmentId = q.get("departmentId");
                filter.status = q.get("status");
                if (q.get("tags") != null) {
                    filter.tags = Arrays.asList(q.get("tags").split(","));
                }
                if (q.get("page") != null) {
                    try { filter.page = Integer.parseInt(q.get("page")); } catch (Exception ignored) {}
                }
                if (q.get("limit") != null) {
                    try { filter.limit = Integer.parseInt(q.get("limit")); } catch (Exception ignored) {}
                }
                if (q.get("sortBy") != null) filter.sortBy = q.get("sortBy");
                if (q.get("sortOrder") != null) filter.sortOrder = q.get("sortOrder");

                List<LearningResource> list = domainService.searchResources(filter, tenantId, userId, userRole, deptId, batchId, programId);
                responseJson = buildSuccessEnvelope(list, "Resources retrieved successfully", traceId);
                responseCode = 200;
            }
            // Route 3: GET /resources/tags (Catalog Tags)
            else if ("/resources/tags".equals(path) && "GET".equalsIgnoreCase(method)) {
                Set<String> tags = domainService.getAvailableTags(tenantId);
                responseJson = buildSuccessEnvelope(new ArrayList<>(tags), "Tags retrieved", traceId);
                responseCode = 200;
            }
            // Route 4: GET /resources/analytics (Advisory Insights)
            else if ("/resources/analytics".equals(path) && "GET".equalsIgnoreCase(method)) {
                AdvisoryInsightsResponse insights = domainService.getAdvisoryInsights(tenantId);
                responseJson = buildSuccessEnvelope(insights, "Analytics insights retrieved", traceId);
                responseCode = 200;
            }
            // Route 5: POST /resources/import (Bulk Import)
            else if ("/resources/import".equals(path) && "POST".equalsIgnoreCase(method)) {
                BulkImportRequest req = parseBulkImportRequest(requestBody);
                BulkImportResult result = domainService.bulkImport(req, tenantId, userId, userRole, traceId);
                responseJson = buildSuccessEnvelope(result, "Bulk import completed", traceId);
                responseCode = 200;
            }
            // Route 6: GET /resources/export (Export)
            else if ("/resources/export".equals(path) && "GET".equalsIgnoreCase(method)) {
                ResourceSearchFilter filter = new ResourceSearchFilter();
                filter.limit = 1000;
                List<LearningResource> resources = domainService.searchResources(filter, tenantId, userId, userRole, deptId, batchId, programId);
                ExportJobResponse export = new ExportJobResponse();
                export.exportJobId = "EXP-" + UUID.randomUUID().toString().substring(0, 8);
                export.status = "COMPLETED";
                export.generatedAt = java.time.Instant.now().toString();
                export.totalRecords = resources.size();
                export.resources = resources;
                responseJson = buildSuccessEnvelope(export, "Resource export generated", traceId);
                responseCode = 200;
            }
            // Route 7: POST /resources/events (External event ingestion)
            else if ("/resources/events".equals(path) && "POST".equalsIgnoreCase(method)) {
                Map<String, Object> eventMap = parseSimpleJsonMap(requestBody);
                boolean consumed = domainService.consumeEvent(eventMap);
                responseJson = "{\"success\":" + consumed + ",\"status\":\"" + (consumed ? "EVENT_PROCESSED" : "FAILED") + "\"}";
                responseCode = consumed ? 200 : 400;
            }
            // Route 8: Pattern-matched routes
            else {
                responseJson = dispatchPatternRoutes(method, path, requestBody, tenantId, userId, userRole,
                        deptId, batchId, programId, traceId, exchange);
            }

            finalResponseCode = responseCode;
            metricsCollector.recordRequest(method, fullPath, responseCode, System.currentTimeMillis() - startTime);
            sendJson(exchange, responseCode, responseJson);

        } catch (ResourceException re) {
            finalOutcome = "FAILURE";
            finalResponseCode = re.getStatusCode();
            metricsCollector.recordError(re.getErrorCode());
            metricsCollector.recordRequest(method, fullPath, re.getStatusCode(), System.currentTimeMillis() - startTime);
            sendError(exchange, re.getStatusCode(), re.getErrorCode(), re.getMessage(), null, fullPath);
        } catch (Exception ex) {
            finalOutcome = "SERVER_ERROR";
            finalResponseCode = 500;
            logger.error("Unhandled resource exception on [{}]: {}", fullPath, ex.getMessage(), ex);
            metricsCollector.recordError("ACD_RESOURCE_INTERNAL_ERROR");
            metricsCollector.recordRequest(method, fullPath, 500, System.currentTimeMillis() - startTime);
            sendError(exchange, 500, "ACD_RESOURCE_INTERNAL_ERROR", "Internal server error: " + ex.getMessage(), null, fullPath);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            StructuredLogEntry auditEntry = StructuredLogEntry.create(currentTenantId, traceId, traceId,
                    currentUserId, currentUserRole, method + " " + fullPath, finalOutcome, duration);
            logger.info(auditEntry.toJson());
            LogContext.clear();
        }
    }

    private String dispatchPatternRoutes(String method, String path, String body, String tenantId,
                                         String userId, String userRole, String deptId, String batchId,
                                         String programId, String traceId, HttpExchange exchange) throws IOException {
        Matcher m;

        // Route: POST /resources/{id}/publish
        if ((m = pResourcePublish.matcher(path)).matches()) {
            if (!"POST".equalsIgnoreCase(method)) throw new ResourceValidationException("Method not allowed");
            String resourceId = m.group(1);
            PublishResourceRequest req = parsePublishRequest(body);
            LearningResource res = domainService.publishResource(resourceId, req, tenantId, userId, userRole, traceId);
            return buildSuccessEnvelope(res, "Resource published successfully", traceId);
        }

        // Route: POST /resources/{id}/archive
        if ((m = pResourceArchive.matcher(path)).matches()) {
            if (!"POST".equalsIgnoreCase(method)) throw new ResourceValidationException("Method not allowed");
            String resourceId = m.group(1);
            Map<String, Object> map = parseSimpleJsonMap(body);
            String reason = (String) map.get("reason");
            LearningResource res = domainService.archiveResource(resourceId, tenantId, userId, userRole, reason, traceId);
            return buildSuccessEnvelope(res, "Resource archived successfully", traceId);
        }

        // Route: POST /resources/{id}/versions
        if ((m = pResourceVersions.matcher(path)).matches()) {
            String resourceId = m.group(1);
            if ("POST".equalsIgnoreCase(method)) {
                CreateVersionRequest req = parseCreateVersionRequest(body);
                ResourceVersion ver = domainService.createVersion(resourceId, req, tenantId, userId, userRole, traceId);
                return buildSuccessEnvelope(ver, "Resource version created successfully", traceId);
            } else if ("GET".equalsIgnoreCase(method)) {
                List<ResourceVersion> versions = domainService.getResourceVersions(resourceId, tenantId);
                return buildSuccessEnvelope(versions, "Resource versions retrieved", traceId);
            }
        }

        // Route: POST /resources/{id}/versions/{versionNo}/restore
        if ((m = pResourceVersionRestore.matcher(path)).matches()) {
            if (!"POST".equalsIgnoreCase(method)) throw new ResourceValidationException("Method not allowed");
            String resourceId = m.group(1);
            long versionNo = Long.parseLong(m.group(2));
            Map<String, Object> map = parseSimpleJsonMap(body);
            String reason = (String) map.get("reason");
            ResourceVersion restored = domainService.restoreVersion(resourceId, versionNo, tenantId, userId, userRole, reason, traceId);
            return buildSuccessEnvelope(restored, "Resource version restored successfully", traceId);
        }

        // Route: POST /resources/{id}/access or GET /resources/{id}/access
        if ((m = pResourceAccess.matcher(path)).matches()) {
            String resourceId = m.group(1);
            if ("POST".equalsIgnoreCase(method)) {
                CreateAccessGrantRequest req = parseCreateAccessGrantRequest(body);
                ResourceAccessGrant grant = domainService.addAccessGrant(resourceId, req, tenantId, userId, userRole, traceId);
                return buildSuccessEnvelope(grant, "Access grant created successfully", traceId);
            } else if ("GET".equalsIgnoreCase(method)) {
                List<ResourceAccessGrant> grants = domainService.getAccessGrants(resourceId, tenantId);
                return buildSuccessEnvelope(grants, "Access grants retrieved", traceId);
            }
        }

        // Route: PUT or DELETE /resources/{id}/access/{accessId}
        if ((m = pResourceAccessItem.matcher(path)).matches()) {
            String resourceId = m.group(1);
            String accessId = m.group(2);
            if ("PUT".equalsIgnoreCase(method)) {
                CreateAccessGrantRequest req = parseCreateAccessGrantRequest(body);
                ResourceAccessGrant grant = domainService.updateAccessGrant(resourceId, accessId, req, tenantId, userId, traceId);
                return buildSuccessEnvelope(grant, "Access grant updated", traceId);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                ResourceAccessGrant grant = domainService.revokeAccessGrant(resourceId, accessId, tenantId, userId, traceId);
                return buildSuccessEnvelope(grant, "Access grant revoked", traceId);
            }
        }

        // Route: GET /resources/{id}/download
        if ((m = pResourceDownload.matcher(path)).matches()) {
            if (!"GET".equalsIgnoreCase(method)) throw new ResourceValidationException("Method not allowed");
            String resourceId = m.group(1);
            Map<String, String> q = parseQuery(exchange.getRequestURI().getQuery());
            Long verNo = q.get("version") != null ? Long.parseLong(q.get("version")) : null;
            DownloadResponse resp = domainService.downloadResource(resourceId, verNo, tenantId, userId, userRole, deptId, batchId, programId, traceId);
            return buildSuccessEnvelope(resp, "Download reference authorized", traceId);
        }

        // Route: GET /resources/{id}/history
        if ((m = pResourceHistory.matcher(path)).matches()) {
            if (!"GET".equalsIgnoreCase(method)) throw new ResourceValidationException("Method not allowed");
            String resourceId = m.group(1);
            List<ResourceHistory> history = domainService.getResourceHistory(resourceId, tenantId);
            return buildSuccessEnvelope(history, "Resource history retrieved", traceId);
        }

        // Route: GET /resources/{id}/usage
        if ((m = pResourceUsage.matcher(path)).matches()) {
            if (!"GET".equalsIgnoreCase(method)) throw new ResourceValidationException("Method not allowed");
            String resourceId = m.group(1);
            ResourceUsageStats stats = domainService.getUsageStats(resourceId, tenantId);
            return buildSuccessEnvelope(stats, "Usage stats retrieved", traceId);
        }

        // Route: GET /versions/{id}
        if ((m = pVersionItem.matcher(path)).matches()) {
            if (!"GET".equalsIgnoreCase(method)) throw new ResourceValidationException("Method not allowed");
            String versionId = m.group(1);
            ResourceVersion v = domainService.getVersionById(versionId, tenantId);
            return buildSuccessEnvelope(v, "Version details retrieved", traceId);
        }

        // Route: POST /resources/dlq/{eventId}/replay
        if ((m = pDlqReplay.matcher(path)).matches()) {
            if (!"POST".equalsIgnoreCase(method)) throw new ResourceValidationException("Method not allowed");
            String eventId = m.group(1);
            boolean replayed = domainService.replayDeadLetterEvent(eventId);
            if (!replayed) {
                throw new ResourceNotFoundException("Dead letter event not found or replay failed: " + eventId);
            }
            return "{\"success\":true,\"replayedEventId\":\"" + eventId + "\"}";
        }

        // Route: GET or PUT /resources/{id}
        if ((m = pResourceItem.matcher(path)).matches()) {
            String resourceId = m.group(1);
            if ("GET".equalsIgnoreCase(method)) {
                LearningResource r = domainService.getResourceById(resourceId);
                return buildSuccessEnvelope(r, "Resource retrieved", traceId);
            } else if ("PUT".equalsIgnoreCase(method)) {
                UpdateResourceRequest req = parseUpdateResourceRequest(body);
                LearningResource r = domainService.updateResource(resourceId, req, tenantId, userId, userRole, traceId);
                return buildSuccessEnvelope(r, "Resource updated", traceId);
            }
        }

        throw new ResourceNotFoundException("No route matched for: " + method + " " + path);
    }

    // ==========================================
    // PARSING HELPERS
    // ==========================================

    private CreateResourceRequest parseCreateResourceRequest(String json) {
        Map<String, Object> map = parseSimpleJsonMap(json);
        CreateResourceRequest req = new CreateResourceRequest();
        req.resourceCode = (String) map.get("resourceCode");
        req.title = (String) map.get("title");
        req.resourceType = (String) map.get("resourceType");
        req.subjectId = (String) map.get("subjectId");
        req.courseId = (String) map.get("courseId");
        req.curriculumId = (String) map.get("curriculumId");
        req.departmentId = (String) map.get("departmentId");
        req.campusId = (String) map.get("campusId");
        req.institutionId = (String) map.get("institutionId");
        req.description = (String) map.get("description");
        req.academicPeriodId = (String) map.get("academicPeriodId");
        req.storageObjectRef = (String) map.get("storageObjectRef");
        req.storageProvider = (String) map.get("storageProvider");
        req.fileName = (String) map.get("fileName");
        req.mimeType = (String) map.get("mimeType");
        if (map.get("fileSize") != null) {
            req.fileSize = Long.parseLong(map.get("fileSize").toString());
        }
        req.checksum = (String) map.get("checksum");
        req.contentHash = (String) map.get("contentHash");
        req.initialAccessScope = (String) map.get("initialAccessScope");
        req.initialScopeId = (String) map.get("initialScopeId");

        if (map.get("tags") instanceof List) {
            List<?> l = (List<?>) map.get("tags");
            for (Object item : l) {
                if (item != null) req.tags.add(item.toString());
            }
        }
        return req;
    }

    private UpdateResourceRequest parseUpdateResourceRequest(String json) {
        Map<String, Object> map = parseSimpleJsonMap(json);
        UpdateResourceRequest req = new UpdateResourceRequest();
        req.title = (String) map.get("title");
        req.description = (String) map.get("description");
        if (map.get("expectedVersion") != null) {
            req.expectedVersion = Long.parseLong(map.get("expectedVersion").toString());
        }
        if (map.get("tags") instanceof List) {
            req.tags = new ArrayList<>();
            for (Object t : (List<?>) map.get("tags")) {
                if (t != null) req.tags.add(t.toString());
            }
        }
        return req;
    }

    private CreateVersionRequest parseCreateVersionRequest(String json) {
        Map<String, Object> map = parseSimpleJsonMap(json);
        CreateVersionRequest req = new CreateVersionRequest();
        req.storageObjectRef = (String) map.get("storageObjectRef");
        req.storageProvider = (String) map.get("storageProvider");
        req.fileName = (String) map.get("fileName");
        req.mimeType = (String) map.get("mimeType");
        if (map.get("fileSize") != null) {
            req.fileSize = Long.parseLong(map.get("fileSize").toString());
        }
        req.checksum = (String) map.get("checksum");
        req.contentHash = (String) map.get("contentHash");
        req.changeSummary = (String) map.get("changeSummary");
        if (map.get("expectedVersion") != null) {
            req.expectedVersion = Long.parseLong(map.get("expectedVersion").toString());
        }
        return req;
    }

    private PublishResourceRequest parsePublishRequest(String json) {
        PublishResourceRequest req = new PublishResourceRequest();
        if (json != null && !json.trim().isEmpty()) {
            Map<String, Object> map = parseSimpleJsonMap(json);
            if (map.get("expectedVersion") != null) {
                req.expectedVersion = Long.parseLong(map.get("expectedVersion").toString());
            }
            req.approvalRef = (String) map.get("approvalRef");
            req.comment = (String) map.get("comment");
        }
        return req;
    }

    private CreateAccessGrantRequest parseCreateAccessGrantRequest(String json) {
        Map<String, Object> map = parseSimpleJsonMap(json);
        CreateAccessGrantRequest req = new CreateAccessGrantRequest();
        req.principalType = (String) map.get("principalType");
        req.principalId = (String) map.get("principalId");
        req.scopeType = (String) map.get("scopeType");
        req.scopeId = (String) map.get("scopeId");
        req.permission = (String) map.get("permission");
        req.effect = (String) map.get("effect");
        req.validFrom = (String) map.get("validFrom");
        req.validTo = (String) map.get("validTo");
        return req;
    }

    private BulkImportRequest parseBulkImportRequest(String json) {
        BulkImportRequest req = new BulkImportRequest();
        Map<String, Object> map = parseSimpleJsonMap(json);
        req.importJobId = (String) map.get("importJobId");
        // items parsing if structured
        return req;
    }

    private String buildSuccessEnvelope(Object data, String message, String traceId) {
        return "{"
                + "\"status\":\"SUCCESS\","
                + "\"message\":\"" + escape(message) + "\","
                + "\"traceId\":\"" + escape(traceId) + "\","
                + "\"data\":" + toJson(data)
                + "}";
    }

    private String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escape((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Enum) return "\"" + ((Enum<?>) obj).name() + "\"";
        if (obj instanceof Collection) {
            Collection<?> col = (Collection<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : col) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escape(Objects.toString(e.getKey(), ""))).append("\":").append(toJson(e.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof LearningResource) {
            LearningResource r = (LearningResource) obj;
            return "{"
                    + "\"id\":\"" + escape(r.getId()) + "\","
                    + "\"tenantId\":\"" + escape(r.getTenantId()) + "\","
                    + "\"institutionId\":\"" + escape(r.getInstitutionId()) + "\","
                    + "\"campusId\":\"" + escape(r.getCampusId()) + "\","
                    + "\"resourceCode\":\"" + escape(r.getResourceCode()) + "\","
                    + "\"title\":\"" + escape(r.getTitle()) + "\","
                    + "\"resourceType\":\"" + (r.getResourceType() != null ? r.getResourceType().name() : "") + "\","
                    + "\"subjectId\":" + (r.getSubjectId() != null ? "\"" + escape(r.getSubjectId()) + "\"" : "null") + ","
                    + "\"courseId\":" + (r.getCourseId() != null ? "\"" + escape(r.getCourseId()) + "\"" : "null") + ","
                    + "\"curriculumId\":" + (r.getCurriculumId() != null ? "\"" + escape(r.getCurriculumId()) + "\"" : "null") + ","
                    + "\"departmentId\":\"" + escape(r.getDepartmentId()) + "\","
                    + "\"ownerId\":\"" + escape(r.getOwnerId()) + "\","
                    + "\"status\":\"" + (r.getStatus() != null ? r.getStatus().name() : "") + "\","
                    + "\"currentVersion\":" + r.getCurrentVersion() + ","
                    + "\"publishedVersion\":" + r.getPublishedVersion() + ","
                    + "\"accessPolicyId\":\"" + escape(r.getAccessPolicyId()) + "\","
                    + "\"tags\":" + toJson(r.getTags()) + ","
                    + "\"description\":" + (r.getDescription() != null ? "\"" + escape(r.getDescription()) + "\"" : "null") + ","
                    + "\"academicPeriodId\":" + (r.getAcademicPeriodId() != null ? "\"" + escape(r.getAcademicPeriodId()) + "\"" : "null") + ","
                    + "\"createdBy\":\"" + escape(r.getCreatedBy()) + "\","
                    + "\"createdAt\":\"" + escape(r.getCreatedAt()) + "\","
                    + "\"updatedBy\":\"" + escape(r.getUpdatedBy()) + "\","
                    + "\"updatedAt\":\"" + escape(r.getUpdatedAt()) + "\""
                    + "}";
        }
        if (obj instanceof ResourceVersion) {
            ResourceVersion v = (ResourceVersion) obj;
            return "{"
                    + "\"id\":\"" + escape(v.getId()) + "\","
                    + "\"resourceId\":\"" + escape(v.getResourceId()) + "\","
                    + "\"versionNo\":" + v.getVersionNo() + ","
                    + "\"storageProvider\":\"" + escape(v.getStorageProvider()) + "\","
                    + "\"objectKey\":\"" + escape(v.getObjectKey()) + "\","
                    + "\"fileName\":\"" + escape(v.getFileName()) + "\","
                    + "\"mimeType\":\"" + escape(v.getMimeType()) + "\","
                    + "\"fileSize\":" + v.getFileSize() + ","
                    + "\"checksum\":\"" + escape(v.getChecksum()) + "\","
                    + "\"status\":\"" + (v.getStatus() != null ? v.getStatus().name() : "") + "\","
                    + "\"createdAt\":\"" + escape(v.getCreatedAt()) + "\""
                    + "}";
        }
        if (obj instanceof ResourceAccessGrant) {
            ResourceAccessGrant g = (ResourceAccessGrant) obj;
            return "{"
                    + "\"id\":\"" + escape(g.getId()) + "\","
                    + "\"resourceId\":\"" + escape(g.getResourceId()) + "\","
                    + "\"principalType\":\"" + (g.getPrincipalType() != null ? g.getPrincipalType().name() : "") + "\","
                    + "\"principalId\":\"" + escape(g.getPrincipalId()) + "\","
                    + "\"scopeType\":\"" + (g.getScopeType() != null ? g.getScopeType().name() : "") + "\","
                    + "\"scopeId\":" + (g.getScopeId() != null ? "\"" + escape(g.getScopeId()) + "\"" : "null") + ","
                    + "\"permission\":\"" + (g.getPermission() != null ? g.getPermission().name() : "") + "\","
                    + "\"effect\":\"" + (g.getEffect() != null ? g.getEffect().name() : "") + "\","
                    + "\"validFrom\":" + (g.getValidFrom() != null ? "\"" + escape(g.getValidFrom()) + "\"" : "null") + ","
                    + "\"validTo\":" + (g.getValidTo() != null ? "\"" + escape(g.getValidTo()) + "\"" : "null") + ","
                    + "\"status\":\"" + (g.getStatus() != null ? g.getStatus().name() : "") + "\","
                    + "\"policyVersion\":" + g.getPolicyVersion()
                    + "}";
        }
        if (obj instanceof DownloadResponse) {
            DownloadResponse d = (DownloadResponse) obj;
            return "{"
                    + "\"resourceId\":\"" + escape(d.resourceId) + "\","
                    + "\"versionNo\":" + d.versionNo + ","
                    + "\"fileName\":\"" + escape(d.fileName) + "\","
                    + "\"mimeType\":\"" + escape(d.mimeType) + "\","
                    + "\"fileSize\":" + d.fileSize + ","
                    + "\"checksum\":\"" + escape(d.checksum) + "\","
                    + "\"downloadUrl\":\"" + escape(d.downloadUrl) + "\","
                    + "\"expiresInSeconds\":" + d.expiresInSeconds
                    + "}";
        }
        if (obj instanceof ResourceHistory) {
            ResourceHistory h = (ResourceHistory) obj;
            return "{"
                    + "\"id\":\"" + escape(h.getId()) + "\","
                    + "\"resourceId\":\"" + escape(h.getResourceId()) + "\","
                    + "\"versionNo\":" + h.getVersionNo() + ","
                    + "\"action\":\"" + escape(h.getAction()) + "\","
                    + "\"fromStatus\":" + (h.getFromStatus() != null ? "\"" + escape(h.getFromStatus()) + "\"" : "null") + ","
                    + "\"toStatus\":\"" + escape(h.getToStatus()) + "\","
                    + "\"changedBy\":\"" + escape(h.getChangedBy()) + "\","
                    + "\"changedAt\":\"" + escape(h.getChangedAt()) + "\","
                    + "\"reason\":" + (h.getReason() != null ? "\"" + escape(h.getReason()) + "\"" : "null")
                    + "}";
        }
        if (obj instanceof ResourceUsageStats) {
            ResourceUsageStats u = (ResourceUsageStats) obj;
            return "{"
                    + "\"resourceId\":\"" + escape(u.resourceId) + "\","
                    + "\"viewCount\":" + u.viewCount + ","
                    + "\"downloadCount\":" + u.downloadCount + ","
                    + "\"lastAccessedAt\":" + (u.lastAccessedAt != null ? "\"" + escape(u.lastAccessedAt) + "\"" : "null")
                    + "}";
        }
        if (obj instanceof AdvisoryInsightsResponse) {
            AdvisoryInsightsResponse a = (AdvisoryInsightsResponse) obj;
            return "{"
                    + "\"totalResources\":" + a.totalResources + ","
                    + "\"publishedResources\":" + a.publishedResources + ","
                    + "\"draftResources\":" + a.draftResources + ","
                    + "\"archivedResources\":" + a.archivedResources + ","
                    + "\"staleResourcesCount\":" + a.staleResourcesCount + ","
                    + "\"resourceTypeDistribution\":" + toJson(a.resourceTypeDistribution) + ","
                    + "\"subjectCoverage\":" + toJson(a.subjectCoverage)
                    + "}";
        }
        if (obj instanceof BulkImportResult) {
            BulkImportResult b = (BulkImportResult) obj;
            return "{"
                    + "\"importJobId\":\"" + escape(b.importJobId) + "\","
                    + "\"totalRecords\":" + b.totalRecords + ","
                    + "\"successfulRecords\":" + b.successfulRecords + ","
                    + "\"failedRecords\":" + b.failedRecords + ","
                    + "\"errors\":" + toJson(b.errors)
                    + "}";
        }
        if (obj instanceof ExportJobResponse) {
            ExportJobResponse e = (ExportJobResponse) obj;
            return "{"
                    + "\"exportJobId\":\"" + escape(e.exportJobId) + "\","
                    + "\"status\":\"" + escape(e.status) + "\","
                    + "\"generatedAt\":\"" + escape(e.generatedAt) + "\","
                    + "\"totalRecords\":" + e.totalRecords + ","
                    + "\"resources\":" + toJson(e.resources)
                    + "}";
        }
        return "{}";
    }

    private String readBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        if (is == null) return "";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String errorCode, String detail,
                           List<ErrorResponse.InvalidParam> invalidParams, String instance) throws IOException {
        ErrorResponse err = new ErrorResponse(
                "https://api.campx.internal/errors/" + errorCode.toLowerCase(),
                errorCode,
                statusCode,
                detail,
                instance,
                errorCode
        );
        if (invalidParams != null) {
            err.setInvalidParams(invalidParams);
        }
        String json = "{"
                + "\"type\":\"" + escape(err.getType()) + "\","
                + "\"title\":\"" + escape(err.getTitle()) + "\","
                + "\"status\":" + err.getStatus() + ","
                + "\"detail\":\"" + escape(err.getDetail()) + "\","
                + "\"instance\":\"" + escape(err.getInstance()) + "\","
                + "\"errorCode\":\"" + escape(err.getErrorCode()) + "\","
                + "\"code\":\"" + escape(err.getCode()) + "\","
                + "\"timestamp\":\"" + err.getTimestamp() + "\""
                + "}";
        sendJson(exchange, statusCode, json);
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.trim().isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            try {
                if (idx > 0) {
                    map.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                            URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
                } else if (idx < 0) {
                    map.put(URLDecoder.decode(pair, "UTF-8"), "");
                }
            } catch (Exception ignored) {}
        }
        return map;
    }

    private Map<String, Object> parseSimpleJsonMap(String json) {
        Map<String, Object> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return map;

        Pattern stringField = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = stringField.matcher(json);
        while (m.find()) {
            map.put(m.group(1), m.group(2));
        }

        Pattern numField = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\\d+(\\.\\d+)?)");
        Matcher mn = numField.matcher(json);
        while (mn.find()) {
            if (!map.containsKey(mn.group(1))) {
                map.put(mn.group(1), mn.group(2));
            }
        }

        Pattern boolField = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(true|false)");
        Matcher mb = boolField.matcher(json);
        while (mb.find()) {
            if (!map.containsKey(mb.group(1))) {
                map.put(mb.group(1), Boolean.parseBoolean(mb.group(2)));
            }
        }

        Pattern arrayField = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher ma = arrayField.matcher(json);
        while (ma.find()) {
            String field = ma.group(1);
            String arrStr = ma.group(2).trim();
            List<String> list = new ArrayList<>();
            if (!arrStr.isEmpty()) {
                String[] items = arrStr.split(",");
                for (String it : items) {
                    String clean = it.trim().replaceAll("^\"|\"$", "");
                    if (!clean.isEmpty()) list.add(clean);
                }
            }
            map.put(field, list);
        }

        return map;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
