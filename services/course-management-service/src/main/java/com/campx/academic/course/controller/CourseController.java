package com.campx.academic.course.controller;

import com.campx.academic.course.exception.CourseException;
import com.campx.academic.course.model.CourseModels.*;
import com.campx.academic.course.model.ErrorResponse;
import com.campx.academic.course.service.CourseDomainService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP REST Controller for ACD-01: Course Management Service.
 * <p>
 * Exposes endpoints mounted at {@code /api/v1/courses/**} and {@code /api/v1/academics/courses/**}.
 * Coordinates request tracing, security role validation, and dispatches to {@link CourseDomainService}.
 * Maps domain exceptions to standard RFC 7807 responses.
 *
 * @see CourseDomainService
 * @see ErrorResponse
 */
public class CourseController implements HttpHandler {

    /**
     * Structured logger instance for HTTP request dispatching.
     */
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(CourseController.class);

    /**
     * Backing course domain business logic service.
     */
    private final CourseDomainService domainService;

    /**
     * Constructs a {@code CourseController} backed by the specified domain service.
     *
     * @param domainService the domain business logic service
     */
    public CourseController(CourseDomainService domainService) {
        this.domainService = domainService;
    }

    /**
     * Dispatches incoming HTTP requests to their appropriate operational handler endpoints,
     * maintaining distributed trace context and mapping domain exceptions to RFC 7807 errors.
     *
     * @param exchange the encapsulated HTTP request and response
     * @throws IOException if network I/O errors occur during transmission
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String fullPath = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        // 1. Establish Trace and Correlation Tokens
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

        LogContext.setService("ACD-01-CourseService");
        exchange.getResponseHeaders().set("X-Trace-Id", traceId);

        // Normalize path: support both /api/v1/courses and /api/v1/academics/courses
        String path = fullPath;
        if (path.startsWith("/api/v1/academics/courses")) {
            path = "/api/v1/courses" + path.substring("/api/v1/academics/courses".length());
        }

        logger.info("[CourseService] Incoming [{}] {}", method, path);

        try (FlowTracker flow = logger.flow("CourseControllerRequest", method + " " + path)) {
            // 1. Course Search Endpoint
            if (path.equals("/api/v1/courses/search") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleSearchCourses");
                handleSearchCourses(exchange);
                return;
            }

            // 2. Published Catalog Endpoint
            if (path.equals("/api/v1/courses/catalog") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleGetCatalog");
                handleGetCatalog(exchange);
                return;
            }

            // 3. Outbox Events Endpoint
            if (path.equals("/api/v1/courses/events/outbox") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleGetOutboxEvents");
                handleGetOutboxEvents(exchange);
                return;
            }

            // 3b. Inbox Events Endpoint
            if (path.equals("/api/v1/courses/events/inbox")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleProcessInboxEvent");
                    handleProcessInboxEvent(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListInboxEvents");
                    handleListInboxEvents(exchange);
                    return;
                }
            }

            // 3c. Dead-Letter Events Endpoint
            if (path.equals("/api/v1/courses/events/dead-letter") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleListDeadLetterEvents");
                handleListDeadLetterEvents(exchange);
                return;
            } else if (path.matches("^/api/v1/courses/events/dead-letter/[^/]+/replay$") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleReplayDeadLetterEvent");
                String[] parts = path.split("/");
                String deadLetterId = parts[parts.length - 2];
                handleReplayDeadLetterEvent(exchange, deadLetterId);
                return;
            }

            // 3d. Idempotency Records Endpoint
            if (path.equals("/api/v1/courses/idempotency-records") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleListIdempotencyRecords");
                handleListIdempotencyRecords(exchange);
                return;
            }

            // Phase 5 Collection Endpoints: Bulk, Accreditations & Archive
            if (path.equals("/api/v1/courses/bulk/import") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleBulkImportCourses");
                handleBulkImportCourses(exchange);
                return;
            }
            if (path.equals("/api/v1/courses/bulk/export") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleBulkExportCourses");
                handleBulkExportCourses(exchange);
                return;
            }
            if (path.equals("/api/v1/courses/accreditation/expiring") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleGetExpiringAccreditations");
                handleGetExpiringAccreditations(exchange);
                return;
            }
            if (path.equals("/api/v1/courses/archive") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleGetArchivedCourses");
                handleGetArchivedCourses(exchange);
                return;
            }

            // 4. Primary Collection: Create Course & List Courses
            if (path.equals("/api/v1/courses")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateCourse");
                    handleCreateCourse(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListCourses");
                    handleListCourses(exchange);
                    return;
                }
            }

            // 5. Course Sub-Resources & Lifecycle Operations: /api/v1/courses/{id}/...
            if (path.startsWith("/api/v1/courses/")) {
                String subPath = path.substring("/api/v1/courses/".length());

                // Detail and Update on /api/v1/courses/{id}
                if (!subPath.contains("/")) {
                    String id = subPath;
                    if ("GET".equalsIgnoreCase(method)) {
                        flow.step("handleGetCourse");
                        handleGetCourse(exchange, id);
                        return;
                    } else if ("PUT".equalsIgnoreCase(method)) {
                        flow.step("handleUpdateCourse");
                        handleUpdateCourse(exchange, id);
                        return;
                    }
                }

                // Match {id}/action or {id}/sub-resource
                String[] parts = subPath.split("/");
                String courseId = parts[0];
                String actionOrResource = parts[1];

                // Credits Info
                if ("credits".equalsIgnoreCase(actionOrResource) && "GET".equalsIgnoreCase(method)) {
                    flow.step("handleGetCredits");
                    handleGetCredits(exchange, courseId);
                    return;
                }

                // Versioning
                if ("versions".equalsIgnoreCase(actionOrResource)) {
                    if ("POST".equalsIgnoreCase(method)) {
                        flow.step("handleCreateVersion");
                        handleCreateVersion(exchange, courseId);
                        return;
                    } else if ("GET".equalsIgnoreCase(method)) {
                        flow.step("handleGetVersions");
                        handleGetVersions(exchange, courseId);
                        return;
                    }
                }

                // Prerequisites
                if ("prerequisites".equalsIgnoreCase(actionOrResource)) {
                    if (parts.length == 2) {
                        if ("POST".equalsIgnoreCase(method)) {
                            flow.step("handleAddPrerequisite");
                            handleAddPrerequisite(exchange, courseId);
                            return;
                        } else if ("GET".equalsIgnoreCase(method)) {
                            flow.step("handleGetPrerequisites");
                            handleGetPrerequisites(exchange, courseId);
                            return;
                        }
                    } else if (parts.length == 3 && "DELETE".equalsIgnoreCase(method)) {
                        flow.step("handleRemovePrerequisite");
                        handleRemovePrerequisite(exchange, courseId, parts[2]);
                        return;
                    }
                }

                // Lifecycle Actions
                if ("submit".equalsIgnoreCase(actionOrResource) && "POST".equalsIgnoreCase(method)) {
                    flow.step("handleSubmitCourse");
                    Course c = domainService.submitForApproval(courseId);
                    sendJson(exchange, 200, "{\"id\":\"" + c.getId() + "\",\"status\":\"" + c.getStatus() + "\"}");
                    return;
                }
                if ("approve".equalsIgnoreCase(actionOrResource) && "POST".equalsIgnoreCase(method)) {
                    flow.step("handleApproveCourse");
                    Course c = domainService.approveCourse(courseId);
                    sendJson(exchange, 200, "{\"id\":\"" + c.getId() + "\",\"status\":\"" + c.getStatus() + "\"}");
                    return;
                }
                if ("publish".equalsIgnoreCase(actionOrResource) && "POST".equalsIgnoreCase(method)) {
                    flow.step("handlePublishCourse");
                    Course c = domainService.publishCourse(courseId);
                    sendJson(exchange, 200, "{\"id\":\"" + c.getId() + "\",\"status\":\"" + c.getStatus() + "\",\"version\":" + c.getCurrentVersion() + "}");
                    return;
                }
                if ("activate".equalsIgnoreCase(actionOrResource) && "POST".equalsIgnoreCase(method)) {
                    flow.step("handleActivateCourse");
                    Course c = domainService.activateCourse(courseId);
                    sendJson(exchange, 200, "{\"id\":\"" + c.getId() + "\",\"status\":\"" + c.getStatus() + "\"}");
                    return;
                }
                if ("suspend".equalsIgnoreCase(actionOrResource) && "POST".equalsIgnoreCase(method)) {
                    flow.step("handleSuspendCourse");
                    Course c = domainService.suspendCourse(courseId);
                    sendJson(exchange, 200, "{\"id\":\"" + c.getId() + "\",\"status\":\"" + c.getStatus() + "\"}");
                    return;
                }
                if ("deactivate".equalsIgnoreCase(actionOrResource) && "POST".equalsIgnoreCase(method)) {
                    flow.step("handleDeactivateCourse");
                    Course c = domainService.deactivateCourse(courseId);
                    sendJson(exchange, 200, "{\"id\":\"" + c.getId() + "\",\"status\":\"" + c.getStatus() + "\"}");
                    return;
                }
                if ("archive".equalsIgnoreCase(actionOrResource) && "POST".equalsIgnoreCase(method)) {
                    flow.step("handleArchiveCourse");
                    handleArchiveCourse(exchange, courseId);
                    return;
                }
                if ("status".equalsIgnoreCase(actionOrResource) && "PUT".equalsIgnoreCase(method)) {
                    flow.step("handleUpdateStatus");
                    handleUpdateStatus(exchange, courseId);
                    return;
                }

                // Batch Offerings
                if ("batch-offerings".equalsIgnoreCase(actionOrResource)) {
                    if (parts.length == 2) {
                        if ("POST".equalsIgnoreCase(method)) {
                            flow.step("handleAddBatchOffering");
                            handleAddBatchOffering(exchange, courseId);
                            return;
                        } else if ("GET".equalsIgnoreCase(method)) {
                            flow.step("handleGetBatchOfferings");
                            handleGetBatchOfferings(exchange, courseId);
                            return;
                        }
                    } else if (parts.length == 4 && "close".equalsIgnoreCase(parts[3]) && "POST".equalsIgnoreCase(method)) {
                        flow.step("handleCloseBatchOffering");
                        domainService.closeBatchOffering(parts[2]);
                        sendJson(exchange, 200, "{\"status\":\"CLOSED\",\"offeringId\":\"" + parts[2] + "\"}");
                        return;
                    }
                }

                // Accreditation
                if ("accreditation".equalsIgnoreCase(actionOrResource)) {
                    if ("POST".equalsIgnoreCase(method)) {
                        flow.step("handleAddAccreditation");
                        handleAddAccreditation(exchange, courseId);
                        return;
                    } else if ("GET".equalsIgnoreCase(method)) {
                        flow.step("handleGetAccreditation");
                        handleGetAccreditations(exchange, courseId);
                        return;
                    }
                }

                // Attendance Policy
                if ("attendance-policy".equalsIgnoreCase(actionOrResource)) {
                    if ("POST".equalsIgnoreCase(method)) {
                        flow.step("handleSetAttendancePolicy");
                        handleSetAttendancePolicy(exchange, courseId);
                        return;
                    } else if ("GET".equalsIgnoreCase(method)) {
                        flow.step("handleGetAttendancePolicy");
                        handleGetAttendancePolicy(exchange, courseId);
                        return;
                    }
                }

                // Evaluation Policy
                if ("evaluation-policy".equalsIgnoreCase(actionOrResource)) {
                    if ("POST".equalsIgnoreCase(method)) {
                        flow.step("handleSetEvaluationPolicy");
                        handleSetEvaluationPolicy(exchange, courseId);
                        return;
                    } else if ("GET".equalsIgnoreCase(method)) {
                        flow.step("handleGetEvaluationPolicy");
                        handleGetEvaluationPolicy(exchange, courseId);
                        return;
                    }
                }

                // Documents
                if ("documents".equalsIgnoreCase(actionOrResource)) {
                    if ("POST".equalsIgnoreCase(method)) {
                        flow.step("handleAttachDocument");
                        handleAttachDocument(exchange, courseId);
                        return;
                    } else if ("GET".equalsIgnoreCase(method)) {
                        flow.step("handleGetDocuments");
                        handleGetDocuments(exchange, courseId);
                        return;
                    }
                }

                // History / Audit trail
                if ("history".equalsIgnoreCase(actionOrResource) && "GET".equalsIgnoreCase(method)) {
                    flow.step("handleGetHistory");
                    handleGetHistory(exchange, courseId);
                    return;
                }

                // Department Reassignment & History (Phase 5)
                if ("department".equalsIgnoreCase(actionOrResource)) {
                    if (parts.length >= 3 && "reassign".equalsIgnoreCase(parts[2]) && "POST".equalsIgnoreCase(method)) {
                        flow.step("handleReassignDepartment");
                        handleReassignDepartment(exchange, courseId);
                        return;
                    } else if (parts.length >= 3 && "history".equalsIgnoreCase(parts[2]) && "GET".equalsIgnoreCase(method)) {
                        flow.step("handleGetDepartmentHistory");
                        handleGetDepartmentHistory(exchange, courseId);
                        return;
                    }
                }

                // Curriculum Mapping (Phase 5)
                if ("curriculum".equalsIgnoreCase(actionOrResource)) {
                    if ("POST".equalsIgnoreCase(method)) {
                        flow.step("handleLinkCurriculum");
                        handleLinkCurriculum(exchange, courseId);
                        return;
                    } else if ("GET".equalsIgnoreCase(method)) {
                        flow.step("handleGetCurriculumLinks");
                        handleGetCurriculumLinks(exchange, courseId);
                        return;
                    }
                }

                // Subject Mapping (Phase 5)
                if ("subjects".equalsIgnoreCase(actionOrResource)) {
                    if ("POST".equalsIgnoreCase(method)) {
                        flow.step("handleMapSubject");
                        handleMapSubject(exchange, courseId);
                        return;
                    } else if ("GET".equalsIgnoreCase(method)) {
                        flow.step("handleGetSubjectMappings");
                        handleGetSubjectMappings(exchange, courseId);
                        return;
                    }
                }
            }

            // Route Not Found
            logger.warn("[CourseService] Route not found: [{}] {}", method, fullPath);
            sendError(exchange, 404, "Not Found", "ACD_ROUTE_NOT_FOUND",
                    "Resource not found in Course Management Service: " + fullPath, fullPath);

        } catch (CourseException e) {
            logger.warn("[CourseService] Domain exception [{} {}]: {}", method, fullPath, e.getMessage());
            sendError(exchange, e.getStatus(), getStatusReason(e.getStatus()), e.getErrorCode(), e.getMessage(), fullPath);
        } catch (Exception e) {
            logger.error("[CourseService] Internal server error [{} {}]: {}", method, fullPath, e.getMessage(), e);
            sendError(exchange, 500, "Internal Server Error", "ACD_INTERNAL_SERVER_ERROR",
                    "An unexpected error occurred: " + escape(e.getMessage()), fullPath);
        } finally {
            LogContext.clear();
        }
    }

    // =========================================================================
    // Request Handlers
    // =========================================================================

    private void handleCreateCourse(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        Course c = new Course();
        c.setCourseCode(extract(body, "courseCode", null));
        c.setCourseName(extract(body, "courseName", null));
        c.setDescription(extract(body, "description", ""));
        c.setCourseType(extract(body, "courseType", "THEORY"));
        c.setCourseCategory(extract(body, "courseCategory", "CORE"));
        c.setDepartmentId(extract(body, "departmentId", null));

        String creditsStr = extract(body, "totalCredits", "4.0");
        try {
            c.setTotalCredits(Double.parseDouble(creditsStr));
        } catch (Exception e) {
            c.setTotalCredits(0.0);
        }

        String durationStr = extract(body, "durationYears", "1");
        try {
            c.setDurationYears(Integer.parseInt(durationStr));
        } catch (Exception e) {
            c.setDurationYears(1);
        }

        String tenantHeader = exchange.getRequestHeaders().getFirst("X-Tenant-Id");
        if (tenantHeader != null && !tenantHeader.trim().isEmpty()) {
            c.setTenantId(tenantHeader.trim());
        }

        Course created = domainService.createDraftCourse(c);
        String resp = "{"
                + "\"id\":\"" + created.getId() + "\","
                + "\"courseCode\":\"" + created.getCourseCode() + "\","
                + "\"courseName\":\"" + escape(created.getCourseName()) + "\","
                + "\"status\":\"" + created.getStatus() + "\","
                + "\"version\":\"" + created.getCurrentVersion() + ".0\","
                + "\"totalCredits\":" + created.getTotalCredits() + ","
                + "\"createdAt\":" + created.getCreatedAt()
                + "}";
        sendJson(exchange, 201, resp);
    }

    private void handleListCourses(HttpExchange exchange) throws IOException {
        List<Course> list = domainService.listCourses();
        StringBuilder sb = new StringBuilder("{\"courses\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            Course c = list.get(i);
            sb.append("{\"id\":\"").append(c.getId()).append("\",\"code\":\"").append(c.getCourseCode())
              .append("\",\"name\":\"").append(escape(c.getCourseName()))
              .append("\",\"status\":\"").append(c.getStatus())
              .append("\",\"departmentId\":\"").append(c.getDepartmentId())
              .append("\",\"credits\":").append(c.getTotalCredits()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleGetCourse(HttpExchange exchange, String id) throws IOException {
        Course c = domainService.getCourse(id);
        String resp = "{"
                + "\"id\":\"" + c.getId() + "\","
                + "\"courseCode\":\"" + c.getCourseCode() + "\","
                + "\"courseName\":\"" + escape(c.getCourseName()) + "\","
                + "\"description\":\"" + escape(c.getDescription()) + "\","
                + "\"departmentId\":\"" + c.getDepartmentId() + "\","
                + "\"durationYears\":" + c.getDurationYears() + ","
                + "\"totalCredits\":" + c.getTotalCredits() + ","
                + "\"courseType\":\"" + c.getCourseType() + "\","
                + "\"courseCategory\":\"" + c.getCourseCategory() + "\","
                + "\"status\":\"" + c.getStatus() + "\","
                + "\"version\":" + c.getCurrentVersion()
                + "}";
        sendJson(exchange, 200, resp);
    }

    private void handleUpdateCourse(HttpExchange exchange, String id) throws IOException {
        String body = readBody(exchange);
        Course update = new Course();
        update.setCourseName(extract(body, "courseName", null));
        update.setDescription(extract(body, "description", null));
        update.setDepartmentId(extract(body, "departmentId", null));

        String creds = extract(body, "totalCredits", null);
        if (creds != null) {
            try { update.setTotalCredits(Double.parseDouble(creds)); } catch (Exception ignored) {}
        }

        Course updated = domainService.updateDraftCourse(id, update);
        sendJson(exchange, 200, "{\"status\":\"UPDATED\",\"id\":\"" + updated.getId() + "\",\"courseCode\":\"" + updated.getCourseCode() + "\"}");
    }

    private void handleSearchCourses(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String dept = getQueryParam(query, "departmentId");
        String stat = getQueryParam(query, "status");
        String type = getQueryParam(query, "courseType");
        String kw = getQueryParam(query, "keyword");
        String tag = getQueryParam(query, "tag");

        List<Course> list = domainService.searchCourses(dept, stat, type, kw, tag);
        StringBuilder sb = new StringBuilder("{\"results\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            Course c = list.get(i);
            sb.append("{\"id\":\"").append(c.getId()).append("\",\"code\":\"").append(c.getCourseCode())
              .append("\",\"name\":\"").append(escape(c.getCourseName()))
              .append("\",\"status\":\"").append(c.getStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleGetCatalog(HttpExchange exchange) throws IOException {
        List<CourseCatalogItem> catalog = domainService.getPublishedCatalog();
        StringBuilder sb = new StringBuilder("{\"catalog\":[");
        for (int i = 0; i < catalog.size(); i++) {
            if (i > 0) sb.append(",");
            CourseCatalogItem item = catalog.get(i);
            sb.append("{\"courseId\":\"").append(item.getCourseId())
              .append("\",\"code\":\"").append(item.getCourseCode())
              .append("\",\"name\":\"").append(escape(item.getCourseName()))
              .append("\",\"departmentId\":\"").append(item.getDepartmentId())
              .append("\",\"credits\":").append(item.getTotalCredits())
              .append("\",\"version\":").append(item.getVersion()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleGetCredits(HttpExchange exchange, String courseId) throws IOException {
        Course c = domainService.getCourse(courseId);
        CourseEvaluationPolicy pol = domainService.getEvaluationPolicy(courseId);
        String resp = "{"
                + "\"courseId\":\"" + c.getId() + "\","
                + "\"courseCode\":\"" + c.getCourseCode() + "\","
                + "\"totalCredits\":" + c.getTotalCredits() + ","
                + "\"internalWeightage\":" + (pol != null ? pol.getInternalWeightage() : 40.0) + ","
                + "\"externalWeightage\":" + (pol != null ? pol.getExternalWeightage() : 60.0)
                + "}";
        sendJson(exchange, 200, resp);
    }

    private void handleCreateVersion(HttpExchange exchange, String courseId) throws IOException {
        String body = readBody(exchange);
        String summary = extract(body, "changeSummary", "New syllabus updates");
        CourseVersion version = domainService.createNewVersion(courseId, summary, 0L);
        sendJson(exchange, 201, "{\"versionId\":\"" + version.getId() + "\",\"versionNo\":" + version.getVersionNo() + ",\"status\":\"" + version.getStatus() + "\"}");
    }

    private void handleGetVersions(HttpExchange exchange, String courseId) throws IOException {
        List<CourseVersion> versions = domainService.getCourseVersions(courseId);
        StringBuilder sb = new StringBuilder("{\"versions\":[");
        for (int i = 0; i < versions.size(); i++) {
            if (i > 0) sb.append(",");
            CourseVersion v = versions.get(i);
            sb.append("{\"versionNo\":").append(v.getVersionNo())
              .append(",\"status\":\"").append(v.getStatus())
              .append("\",\"changeSummary\":\"").append(escape(v.getChangeSummary())).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleAddPrerequisite(HttpExchange exchange, String courseId) throws IOException {
        String body = readBody(exchange);
        String prereqId = extract(body, "prerequisiteCourseId", null);
        String type = extract(body, "relationshipType", "MANDATORY");
        String grade = extract(body, "minimumGrade", "C");

        CoursePrerequisite p = domainService.addPrerequisite(courseId, prereqId, type, grade);
        sendJson(exchange, 201, "{\"prerequisiteId\":\"" + p.getId() + "\",\"courseId\":\"" + p.getCourseId()
                + "\",\"prerequisiteCourseId\":\"" + p.getPrerequisiteCourseId() + "\",\"status\":\"" + p.getStatus() + "\"}");
    }

    private void handleRemovePrerequisite(HttpExchange exchange, String courseId, String prereqId) throws IOException {
        domainService.removePrerequisite(courseId, prereqId);
        sendJson(exchange, 200, "{\"status\":\"REMOVED\",\"prerequisiteId\":\"" + prereqId + "\"}");
    }

    private void handleGetPrerequisites(HttpExchange exchange, String courseId) throws IOException {
        List<CoursePrerequisite> list = domainService.getPrerequisites(courseId);
        StringBuilder sb = new StringBuilder("{\"prerequisites\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            CoursePrerequisite p = list.get(i);
            sb.append("{\"id\":\"").append(p.getId())
              .append("\",\"prerequisiteCourseId\":\"").append(p.getPrerequisiteCourseId())
              .append("\",\"type\":\"").append(p.getRelationshipType())
              .append("\",\"minGrade\":\"").append(p.getMinimumGrade()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleUpdateStatus(HttpExchange exchange, String courseId) throws IOException {
        String body = readBody(exchange);
        String targetStatus = extract(body, "status", "").toUpperCase();
        Course c;
        switch (targetStatus) {
            case "UNDER_REVIEW":
                c = domainService.submitForApproval(courseId);
                break;
            case "APPROVED":
                c = domainService.approveCourse(courseId);
                break;
            case "ACTIVE":
                c = domainService.publishCourse(courseId);
                break;
            case "SUSPENDED":
                c = domainService.suspendCourse(courseId);
                break;
            case "DEACTIVATED":
                c = domainService.deactivateCourse(courseId);
                break;
            case "ARCHIVED":
                c = domainService.archiveCourse(courseId);
                break;
            default:
                throw new CourseException(400, "ACD_INVALID_STATUS", "Unrecognized lifecycle status: " + targetStatus);
        }
        sendJson(exchange, 200, "{\"id\":\"" + c.getId() + "\",\"status\":\"" + c.getStatus() + "\"}");
    }

    private void handleAddBatchOffering(HttpExchange exchange, String courseId) throws IOException {
        String body = readBody(exchange);
        String batchId = extract(body, "batchId", "BATCH_2026_A");
        String term = extract(body, "term", "TERM_1");
        CourseBatchOffering bo = domainService.addBatchOffering(courseId, batchId, term);
        sendJson(exchange, 201, "{\"offeringId\":\"" + bo.getId() + "\",\"status\":\"" + bo.getStatus() + "\"}");
    }

    private void handleGetBatchOfferings(HttpExchange exchange, String courseId) throws IOException {
        List<CourseBatchOffering> list = domainService.getBatchOfferings(courseId);
        StringBuilder sb = new StringBuilder("{\"offerings\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            CourseBatchOffering o = list.get(i);
            sb.append("{\"id\":\"").append(o.getId()).append("\",\"batch\":\"").append(o.getBatchId())
              .append("\",\"status\":\"").append(o.getStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleAddAccreditation(HttpExchange exchange, String courseId) throws IOException {
        String body = readBody(exchange);
        String auth = extract(body, "authority", "UGC");
        String ref = extract(body, "referenceNumber", "REF-" + System.currentTimeMillis());
        CourseAccreditation acc = domainService.addAccreditation(courseId, auth, ref, 0L);
        sendJson(exchange, 201, "{\"id\":\"" + acc.getId() + "\",\"authority\":\"" + acc.getAuthority() + "\",\"status\":\"ACTIVE\"}");
    }

    private void handleGetAccreditations(HttpExchange exchange, String courseId) throws IOException {
        List<CourseAccreditation> list = domainService.getAccreditations(courseId);
        StringBuilder sb = new StringBuilder("{\"accreditations\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            CourseAccreditation a = list.get(i);
            sb.append("{\"id\":\"").append(a.getId()).append("\",\"authority\":\"").append(a.getAuthority())
              .append("\",\"reference\":\"").append(a.getReferenceNumber()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleSetAttendancePolicy(HttpExchange exchange, String courseId) throws IOException {
        String body = readBody(exchange);
        String minStr = extract(body, "minAttendancePercent", "75.0");
        double min = Double.parseDouble(minStr);
        CourseAttendancePolicy p = domainService.setAttendancePolicy(courseId, min);
        sendJson(exchange, 200, "{\"id\":\"" + p.getId() + "\",\"minAttendancePercent\":" + p.getMinAttendancePercent() + "}");
    }

    private void handleGetAttendancePolicy(HttpExchange exchange, String courseId) throws IOException {
        CourseAttendancePolicy p = domainService.getAttendancePolicy(courseId);
        if (p == null) {
            sendJson(exchange, 200, "{\"minAttendancePercent\":75.0,\"condonationAllowed\":true}");
        } else {
            sendJson(exchange, 200, "{\"id\":\"" + p.getId() + "\",\"minAttendancePercent\":" + p.getMinAttendancePercent() + "}");
        }
    }

    private void handleSetEvaluationPolicy(HttpExchange exchange, String courseId) throws IOException {
        String body = readBody(exchange);
        double internal = Double.parseDouble(extract(body, "internalWeightage", "40.0"));
        double external = Double.parseDouble(extract(body, "externalWeightage", "60.0"));
        CourseEvaluationPolicy p = domainService.setEvaluationPolicy(courseId, internal, external);
        sendJson(exchange, 200, "{\"id\":\"" + p.getId() + "\",\"internalWeightage\":" + p.getInternalWeightage() + ",\"externalWeightage\":" + p.getExternalWeightage() + "}");
    }

    private void handleGetEvaluationPolicy(HttpExchange exchange, String courseId) throws IOException {
        CourseEvaluationPolicy p = domainService.getEvaluationPolicy(courseId);
        if (p == null) {
            sendJson(exchange, 200, "{\"internalWeightage\":40.0,\"externalWeightage\":60.0}");
        } else {
            sendJson(exchange, 200, "{\"id\":\"" + p.getId() + "\",\"internalWeightage\":" + p.getInternalWeightage() + ",\"externalWeightage\":" + p.getExternalWeightage() + "}");
        }
    }

    private void handleAttachDocument(HttpExchange exchange, String courseId) throws IOException {
        String body = readBody(exchange);
        String docType = extract(body, "docType", "SYLLABUS");
        String title = extract(body, "title", "Course Syllabus");
        String fileUrl = extract(body, "fileUrl", "s3://campx/courses/" + courseId + ".pdf");
        CourseDocument doc = domainService.attachDocument(courseId, docType, title, fileUrl);
        sendJson(exchange, 201, "{\"id\":\"" + doc.getId() + "\",\"title\":\"" + escape(doc.getTitle()) + "\"}");
    }

    private void handleGetDocuments(HttpExchange exchange, String courseId) throws IOException {
        List<CourseDocument> list = domainService.getDocuments(courseId);
        StringBuilder sb = new StringBuilder("{\"documents\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            CourseDocument d = list.get(i);
            sb.append("{\"id\":\"").append(d.getId()).append("\",\"title\":\"").append(escape(d.getTitle()))
              .append("\",\"type\":\"").append(d.getDocType()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleGetHistory(HttpExchange exchange, String courseId) throws IOException {
        List<CourseHistory> list = domainService.getCourseHistory(courseId);
        StringBuilder sb = new StringBuilder("{\"history\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            CourseHistory h = list.get(i);
            sb.append("{\"action\":\"").append(h.getAction())
              .append("\",\"from\":\"").append(h.getFromStatus())
              .append("\",\"to\":\"").append(h.getToStatus())
              .append("\",\"actor\":\"").append(h.getActorId())
              .append("\",\"timestamp\":").append(h.getOccurredAt()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleGetOutboxEvents(HttpExchange exchange) throws IOException {
        List<OutboxEvent> list = domainService.getOutboxEvents();
        StringBuilder sb = new StringBuilder("{\"outbox\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            OutboxEvent o = list.get(i);
            sb.append("{\"id\":\"").append(o.getEventId())
              .append("\",\"type\":\"").append(o.getEventType())
              .append("\",\"aggregateId\":\"").append(o.getAggregateId())
              .append("\",\"status\":\"").append(o.getStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleProcessInboxEvent(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        String eventId = extract(body, "eventId", null);
        String sourceService = extract(body, "sourceService", "UPSTREAM");
        String consumerGroup = extract(body, "consumerGroup", "ACD-01");
        String payload = extract(body, "payload", "{}");

        InboxEvent processed = domainService.deduplicateInboundEvent(eventId, sourceService, consumerGroup, payload);
        sendJson(exchange, 200, "{\"id\":\"" + processed.getId() + "\",\"eventId\":\"" + processed.getEventId()
                + "\",\"status\":\"" + processed.getStatus() + "\"}");
    }

    private void handleListInboxEvents(HttpExchange exchange) throws IOException {
        List<InboxEvent> list = domainService.listInboxEvents();
        StringBuilder sb = new StringBuilder("{\"inbox\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            InboxEvent e = list.get(i);
            sb.append("{\"id\":\"").append(e.getId()).append("\",\"eventId\":\"").append(e.getEventId())
              .append("\",\"sourceService\":\"").append(e.getSourceService())
              .append("\",\"status\":\"").append(e.getStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleListDeadLetterEvents(HttpExchange exchange) throws IOException {
        List<DeadLetterEvent> list = domainService.listDeadLetterEvents();
        StringBuilder sb = new StringBuilder("{\"deadLetterEvents\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            DeadLetterEvent d = list.get(i);
            sb.append("{\"id\":\"").append(d.getId()).append("\",\"originalEventId\":\"").append(d.getOriginalEventId())
              .append("\",\"eventType\":\"").append(d.getEventType())
              .append("\",\"failureCode\":\"").append(d.getFailureCode())
              .append("\",\"disposition\":\"").append(d.getDisposition()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleReplayDeadLetterEvent(HttpExchange exchange, String id) throws IOException {
        DeadLetterEvent replayed = domainService.replayDeadLetterEvent(id);
        sendJson(exchange, 200, "{\"id\":\"" + replayed.getId() + "\",\"disposition\":\"" + replayed.getDisposition()
                + "\",\"status\":\"REPLAYED\"}");
    }

    private void handleListIdempotencyRecords(HttpExchange exchange) throws IOException {
        List<IdempotencyRecord> list = domainService.listIdempotencyRecords();
        StringBuilder sb = new StringBuilder("{\"records\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            IdempotencyRecord r = list.get(i);
            sb.append("{\"id\":\"").append(r.getId()).append("\",\"idempotencyKey\":\"").append(r.getIdempotencyKey())
              .append("\",\"operation\":\"").append(r.getOperation())
              .append("\",\"status\":\"").append(r.getStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    // =========================================================================
    // Phase 5 Request Handlers: Bulk, Reassignment, Mappings, Archive
    // =========================================================================

    private void handleBulkImportCourses(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        List<Course> list = new ArrayList<>();
        int arrayStart = body.indexOf('[');
        int arrayEnd = body.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            String inner = body.substring(arrayStart + 1, arrayEnd);
            int idx = 0;
            while (idx < inner.length()) {
                int openBrace = inner.indexOf('{', idx);
                if (openBrace == -1) break;
                int closeBrace = inner.indexOf('}', openBrace);
                if (closeBrace == -1) break;
                String objJson = inner.substring(openBrace, closeBrace + 1);
                Course c = new Course();
                c.setCourseCode(extract(objJson, "courseCode", null));
                c.setCourseName(extract(objJson, "courseName", null));
                c.setDescription(extract(objJson, "description", ""));
                c.setCourseType(extract(objJson, "courseType", "THEORY"));
                c.setCourseCategory(extract(objJson, "courseCategory", "CORE"));
                c.setDepartmentId(extract(objJson, "departmentId", null));
                String creditsStr = extract(objJson, "totalCredits", "4.0");
                try { c.setTotalCredits(Double.parseDouble(creditsStr)); } catch (Exception e) { c.setTotalCredits(0.0); }
                String durStr = extract(objJson, "durationYears", "1");
                try { c.setDurationYears(Integer.parseInt(durStr)); } catch (Exception e) { c.setDurationYears(1); }
                String tenantHeader = exchange.getRequestHeaders().getFirst("X-Tenant-Id");
                if (tenantHeader != null && !tenantHeader.trim().isEmpty()) {
                    c.setTenantId(tenantHeader.trim());
                }
                list.add(c);
                idx = closeBrace + 1;
            }
        }
        Map<String, Object> result = domainService.bulkImportCourses(list);
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"totalSubmitted\":").append(result.get("totalSubmitted")).append(",");
        sb.append("\"importedCount\":").append(result.get("importedCount")).append(",");
        sb.append("\"failedCount\":").append(result.get("failedCount")).append(",");
        sb.append("\"importedCourseIds\":[");
        List<?> importedIds = (List<?>) result.get("importedCourseIds");
        if (importedIds != null) {
            for (int i = 0; i < importedIds.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(importedIds.get(i)).append("\"");
            }
        }
        sb.append("],\"errors\":[");
        List<?> errors = (List<?>) result.get("errors");
        if (errors != null) {
            for (int i = 0; i < errors.size(); i++) {
                if (i > 0) sb.append(",");
                Map<?, ?> err = (Map<?, ?>) errors.get(i);
                sb.append("{\"rowIndex\":").append(err.get("rowIndex"))
                  .append(",\"courseCode\":\"").append(escape(String.valueOf(err.get("courseCode"))))
                  .append("\",\"error\":\"").append(escape(String.valueOf(err.get("error")))).append("\"}");
            }
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleBulkExportCourses(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String status = getQueryParam(query, "status");
        String departmentId = getQueryParam(query, "departmentId");
        List<Course> list = domainService.bulkExportCourses(status, departmentId);
        StringBuilder sb = new StringBuilder("{\"courses\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            Course c = list.get(i);
            sb.append("{\"id\":\"").append(c.getId())
              .append("\",\"courseCode\":\"").append(escape(c.getCourseCode()))
              .append("\",\"courseName\":\"").append(escape(c.getCourseName()))
              .append("\",\"status\":\"").append(c.getStatus())
              .append("\",\"departmentId\":\"").append(escape(c.getDepartmentId()))
              .append("\",\"totalCredits\":").append(c.getTotalCredits()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleGetExpiringAccreditations(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String leadStr = getQueryParam(query, "leadTimeDays");
        int leadTimeDays = 90;
        if (leadStr != null) {
            try { leadTimeDays = Integer.parseInt(leadStr); } catch (Exception ignored) {}
        }
        List<CourseAccreditation> list = domainService.checkAccreditationExpiry(leadTimeDays);
        StringBuilder sb = new StringBuilder("{\"expiringAccreditations\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            CourseAccreditation a = list.get(i);
            sb.append("{\"id\":\"").append(a.getId())
              .append("\",\"courseId\":\"").append(a.getCourseId())
              .append("\",\"authority\":\"").append(escape(a.getAuthority()))
              .append("\",\"referenceNumber\":\"").append(escape(a.getReferenceNumber()))
              .append("\",\"validTo\":").append(a.getValidTo()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleGetArchivedCourses(HttpExchange exchange) throws IOException {
        List<CourseArchiveRecord> list = domainService.getArchivedCourses();
        StringBuilder sb = new StringBuilder("{\"archivedCourses\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            CourseArchiveRecord r = list.get(i);
            sb.append("{\"id\":\"").append(r.getId())
              .append("\",\"courseId\":\"").append(r.getCourseId())
              .append("\",\"finalVersionNo\":").append(r.getFinalVersionNo())
              .append(",\"archivedBy\":\"").append(escape(r.getArchivedBy()))
              .append("\",\"archivedAt\":").append(r.getArchivedAt())
              .append(",\"snapshot\":").append(r.getSnapshotJson()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleReassignDepartment(HttpExchange exchange, String courseId) throws IOException {
        String body = readBody(exchange);
        String newDepartmentId = extract(body, "newDepartmentId", null);
        String reassignedBy = extract(body, "reassignedBy", "REGISTRAR");
        String reason = extract(body, "reason", "Department restructuring");
        CourseDepartmentAssociation assoc = domainService.reassignCourseDepartment(courseId, newDepartmentId, reassignedBy, reason);
        sendJson(exchange, 200, "{\"id\":\"" + assoc.getId() + "\",\"courseId\":\"" + assoc.getCourseId()
                + "\",\"departmentId\":\"" + assoc.getDepartmentId() + "\",\"effectiveFrom\":" + assoc.getEffectiveFrom()
                + ",\"reassignedBy\":\"" + escape(assoc.getReassignedBy()) + "\"}");
    }

    private void handleGetDepartmentHistory(HttpExchange exchange, String courseId) throws IOException {
        List<CourseDepartmentAssociation> list = domainService.getDepartmentAssociationHistory(courseId);
        StringBuilder sb = new StringBuilder("{\"departmentHistory\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            CourseDepartmentAssociation d = list.get(i);
            sb.append("{\"id\":\"").append(d.getId())
              .append("\",\"departmentId\":\"").append(escape(d.getDepartmentId()))
              .append("\",\"effectiveFrom\":").append(d.getEffectiveFrom())
              .append(",\"effectiveTo\":").append(d.getEffectiveTo())
              .append(",\"reassignedBy\":\"").append(escape(d.getReassignedBy()))
              .append("\",\"reason\":\"").append(escape(d.getReason())).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleLinkCurriculum(HttpExchange exchange, String courseId) throws IOException {
        String body = readBody(exchange);
        String curriculumId = extract(body, "curriculumId", null);
        CourseCurriculumMap ccm = domainService.linkCurriculum(courseId, curriculumId);
        sendJson(exchange, 201, "{\"id\":\"" + ccm.getId() + "\",\"courseId\":\"" + ccm.getCourseId()
                + "\",\"curriculumId\":\"" + escape(ccm.getCurriculumId()) + "\"}");
    }

    private void handleGetCurriculumLinks(HttpExchange exchange, String courseId) throws IOException {
        List<CourseCurriculumMap> list = domainService.getCurriculumLinks(courseId);
        StringBuilder sb = new StringBuilder("{\"curriculumLinks\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            CourseCurriculumMap c = list.get(i);
            sb.append("{\"id\":\"").append(c.getId())
              .append("\",\"curriculumId\":\"").append(escape(c.getCurriculumId())).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleMapSubject(HttpExchange exchange, String courseId) throws IOException {
        String body = readBody(exchange);
        String subjectId = extract(body, "subjectId", null);
        String semStr = extract(body, "semesterNo", "1");
        int semNo = 1;
        try { semNo = Integer.parseInt(semStr); } catch (Exception ignored) {}
        CourseSubjectMapping csm = domainService.mapSubject(courseId, subjectId, semNo);
        sendJson(exchange, 201, "{\"id\":\"" + csm.getId() + "\",\"courseId\":\"" + csm.getCourseId()
                + "\",\"subjectId\":\"" + escape(csm.getSubjectId()) + "\",\"semesterNo\":" + csm.getSemesterNo() + "}");
    }

    private void handleGetSubjectMappings(HttpExchange exchange, String courseId) throws IOException {
        List<CourseSubjectMapping> list = domainService.getSubjectMappings(courseId);
        StringBuilder sb = new StringBuilder("{\"subjectMappings\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            CourseSubjectMapping s = list.get(i);
            sb.append("{\"id\":\"").append(s.getId())
              .append("\",\"subjectId\":\"").append(escape(s.getSubjectId()))
              .append("\",\"semesterNo\":").append(s.getSemesterNo()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleArchiveCourse(HttpExchange exchange, String courseId) throws IOException {
        String body = readBody(exchange);
        String archivedBy = extract(body, "archivedBy", "ACADEMIC_ADMIN");
        CourseArchiveRecord rec = domainService.archiveCourseToCollection(courseId, archivedBy);
        sendJson(exchange, 200, "{\"id\":\"" + rec.getId() + "\",\"courseId\":\"" + rec.getCourseId()
                + "\",\"finalVersionNo\":" + rec.getFinalVersionNo()
                + ",\"archivedBy\":\"" + escape(rec.getArchivedBy())
                + "\",\"archivedAt\":" + rec.getArchivedAt()
                + ",\"snapshot\":" + rec.getSnapshotJson() + "}");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
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
        } catch (IOException ioException) {
            logger.warn("Failed to send error response to client: {}", ioException.getMessage());
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

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
