package com.campx.student.controller;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.student.exception.*;
import com.campx.student.model.*;
import com.campx.student.security.GatewayHmacVerifier;
import com.campx.student.security.UserSecurityContext;
import com.campx.student.service.StudentDomainService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for the Student Domain Service (STU-01).
 * Handles client/gateway requests, extracts security context, delegates to
 * {@link StudentDomainService}, and maps errors to RFC 7807 problem details.
 */
public class StudentController {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(StudentController.class);

    private final StudentDomainService studentDomainService;
    private final GatewayHmacVerifier hmacVerifier;
    private final ObjectMapper objectMapper;

    public StudentController(StudentDomainService studentDomainService) {
        this(studentDomainService, new GatewayHmacVerifier(null));
    }

    public StudentController(StudentDomainService studentDomainService, GatewayHmacVerifier hmacVerifier) {
        if (studentDomainService == null) throw new IllegalArgumentException("studentDomainService cannot be null");
        this.studentDomainService = studentDomainService;
        this.hmacVerifier = hmacVerifier;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Handles student enrollment.
     */
    public HttpResponse enrollStudent(Map<String, String> headers, String body) {
        try {
            UserSecurityContext context = resolveContext(headers);
            EnrollStudentRequest request = objectMapper.readValue(body, EnrollStudentRequest.class);
            StudentProfileResponse response = studentDomainService.enrollStudent(context, request);
            return HttpResponse.created(objectMapper.writeValueAsString(response));
        } catch (StudentConflictException e) {
            return HttpResponse.conflict(e.getMessage());
        } catch (StudentValidationException e) {
            return HttpResponse.badRequest(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in enrollStudent: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    /**
     * Handles cursor-paginated roster search.
     */
    public HttpResponse getRoster(Map<String, String> headers, Map<String, String> queryParams) {
        try {
            UserSecurityContext context = resolveContext(headers);
            StudentRosterQuery query = new StudentRosterQuery();
            if (queryParams != null) {
                if (queryParams.containsKey("limit")) {
                    try { query.setLimit(Integer.parseInt(queryParams.get("limit"))); } catch (NumberFormatException ignored) {}
                }
                query.setCursor(queryParams.get("cursor"));
                query.setSearch(queryParams.get("search"));
                if (queryParams.containsKey("collegeId")) {
                    try { query.setCollegeId(UUID.fromString(queryParams.get("collegeId"))); } catch (Exception ignored) {}
                }
                if (queryParams.containsKey("programId")) {
                    try { query.setProgramId(UUID.fromString(queryParams.get("programId"))); } catch (Exception ignored) {}
                }
                if (queryParams.containsKey("batchId")) {
                    try { query.setBatchId(UUID.fromString(queryParams.get("batchId"))); } catch (Exception ignored) {}
                }
                if (queryParams.containsKey("status")) {
                    try { query.setStatus(StudentStatus.valueOf(queryParams.get("status").toUpperCase())); } catch (Exception ignored) {}
                }
            }

            CursorPagedResult<StudentRosterItem> result = studentDomainService.getRoster(context, query);
            return HttpResponse.ok(objectMapper.writeValueAsString(result));
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in getRoster: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    /**
     * Handles single student retrieval by ID.
     */
    public HttpResponse getStudentById(Map<String, String> headers, String idParam) {
        try {
            UserSecurityContext context = resolveContext(headers);
            UUID studentId = UUID.fromString(idParam);
            StudentProfileResponse response = studentDomainService.getStudentById(context, studentId);
            return HttpResponse.ok(objectMapper.writeValueAsString(response));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid student UUID format");
        } catch (StudentNotFoundException e) {
            return HttpResponse.notFound(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in getStudentById: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    /**
     * Handles lifecycle state transitions.
     */
    public HttpResponse updateStatus(Map<String, String> headers, String idParam, String body) {
        try {
            UserSecurityContext context = resolveContext(headers);
            UUID studentId = UUID.fromString(idParam);
            UpdateStudentStatusRequest request = objectMapper.readValue(body, UpdateStudentStatusRequest.class);
            StudentProfileResponse response = studentDomainService.transitionLifecycle(context, studentId, request);
            return HttpResponse.ok(objectMapper.writeValueAsString(response));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid student UUID format");
        } catch (StudentNotFoundException e) {
            return HttpResponse.notFound(e.getMessage());
        } catch (StudentLifecycleException e) {
            return HttpResponse.unprocessableEntity(e.getMessage());
        } catch (StudentValidationException e) {
            return HttpResponse.badRequest(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in updateStatus: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    /**
     * Handles student deactivation / soft deletion.
     */
    public HttpResponse deleteStudent(Map<String, String> headers, String idParam) {
        try {
            UserSecurityContext context = resolveContext(headers);
            UUID studentId = UUID.fromString(idParam);
            studentDomainService.deactivateStudent(context, studentId);
            return HttpResponse.ok("{\"success\":true,\"message\":\"Student deactivated successfully.\"}");
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid student UUID format");
        } catch (StudentNotFoundException e) {
            return HttpResponse.notFound(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in deleteStudent: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    private UserSecurityContext resolveContext(Map<String, String> headers) {
        if (headers == null) {
            throw new StudentSecurityViolationException("Missing request headers");
        }
        String userId = headers.get("X-User-Id");
        String tenantId = headers.get("X-Tenant-Id");
        String collegeId = headers.get("X-College-Id");
        String role = headers.get("X-User-Role");

        return UserSecurityContext.fromHeaders(userId, tenantId, collegeId, role);
    }

    /**
     * Lightweight immutable representation of HTTP response for controller testing and embedded server integration.
     */
    public static class HttpResponse {
        private final int statusCode;
        private final String body;
        private final Map<String, String> headers;

        public HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers = new HashMap<>();
            this.headers.put("Content-Type", "application/json");
        }

        public static HttpResponse ok(String body) { return new HttpResponse(200, body); }
        public static HttpResponse created(String body) { return new HttpResponse(201, body); }
        public static HttpResponse badRequest(String detail) {
            return new HttpResponse(400, String.format("{\"type\":\"https://campxsync.edu/errors/bad-request\",\"title\":\"Bad Request\",\"status\":400,\"detail\":\"%s\"}", escape(detail)));
        }
        public static HttpResponse forbidden(String detail) {
            return new HttpResponse(403, String.format("{\"type\":\"https://campxsync.edu/errors/forbidden\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"%s\"}", escape(detail)));
        }
        public static HttpResponse notFound(String detail) {
            return new HttpResponse(404, String.format("{\"type\":\"https://campxsync.edu/errors/not-found\",\"title\":\"Not Found\",\"status\":404,\"detail\":\"%s\"}", escape(detail)));
        }
        public static HttpResponse conflict(String detail) {
            return new HttpResponse(409, String.format("{\"type\":\"https://campxsync.edu/errors/conflict\",\"title\":\"Conflict\",\"status\":409,\"detail\":\"%s\"}", escape(detail)));
        }
        public static HttpResponse unprocessableEntity(String detail) {
            return new HttpResponse(422, String.format("{\"type\":\"https://campxsync.edu/errors/unprocessable-entity\",\"title\":\"Unprocessable Entity\",\"status\":422,\"detail\":\"%s\"}", escape(detail)));
        }
        public static HttpResponse internalServerError(String detail) {
            return new HttpResponse(500, String.format("{\"type\":\"https://campxsync.edu/errors/internal-server-error\",\"title\":\"Internal Server Error\",\"status\":500,\"detail\":\"%s\"}", escape(detail)));
        }

        private static String escape(String s) {
            return s != null ? s.replace("\"", "\\\"").replace("\n", " ") : "";
        }

        public int getStatusCode() { return statusCode; }
        public String getBody() { return body; }
        public Map<String, String> getHeaders() { return headers; }
    }
}
