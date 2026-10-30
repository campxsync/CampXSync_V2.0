package com.campx.student.controller;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.student.exception.*;
import com.campx.student.model.*;
import com.campx.student.security.GatewayHmacVerifier;
import com.campx.student.security.UserSecurityContext;
import com.campx.student.service.RateLimiter;
import com.campx.student.service.StudentDomainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * REST Controller for the Student Domain Service (STM-01).
 * Handles client/gateway requests, extracts security context, delegates to
 * {@link StudentDomainService}, and maps errors to RFC 7807 problem details.
 * Implements {@link HttpHandler} to run directly on embedded {@link com.sun.net.httpserver.HttpServer}.
 */
public class StudentController implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(StudentController.class);

    private final StudentDomainService studentDomainService;
    private final GatewayHmacVerifier hmacVerifier;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public StudentController() {
        this(new StudentDomainService());
    }

    public StudentController(StudentDomainService studentDomainService) {
        this(studentDomainService, new GatewayHmacVerifier(null), new RateLimiter());
    }

    public StudentController(StudentDomainService studentDomainService, GatewayHmacVerifier hmacVerifier) {
        this(studentDomainService, hmacVerifier, new RateLimiter());
    }

    public StudentController(StudentDomainService studentDomainService, RateLimiter rateLimiter) {
        this(studentDomainService, new GatewayHmacVerifier(null), rateLimiter);
    }

    public StudentController(StudentDomainService studentDomainService, GatewayHmacVerifier hmacVerifier, RateLimiter rateLimiter) {
        if (studentDomainService == null) throw new IllegalArgumentException("studentDomainService cannot be null");
        this.studentDomainService = studentDomainService;
        this.hmacVerifier = hmacVerifier != null ? hmacVerifier : new GatewayHmacVerifier(null);
        this.rateLimiter = rateLimiter != null ? rateLimiter : new RateLimiter();
        this.objectMapper = new ObjectMapper();
    }

    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String fullPath = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase();

        // 1. Correlation context extraction & propagation
        String traceId = exchange.getRequestHeaders().getFirst("X-Correlation-Id");
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = exchange.getRequestHeaders().getFirst("X-Trace-Id");
        }
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = UUID.randomUUID().toString();
        }
        exchange.getResponseHeaders().set("X-Correlation-Id", traceId);
        exchange.getResponseHeaders().set("X-Trace-Id", traceId);

        // 2. Operational endpoints
        if ("/actuator/health".equals(fullPath) || fullPath.endsWith("/health")) {
            sendResponse(exchange, HttpResponse.ok("{\"status\":\"UP\",\"service\":\"student-service\"}"), traceId);
            return;
        }
        if ("/metrics".equals(fullPath) || fullPath.endsWith("/metrics")) {
            sendResponse(exchange, HttpResponse.ok("# HELP student_service_up Status\nstudent_service_up 1\n"), traceId);
            return;
        }

        // 3. Extract headers map
        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : exchange.getRequestHeaders().entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                headers.put(entry.getKey(), entry.getValue().get(0));
            }
        }

        // 4. Read body
        byte[] bodyBytes = readRequestBody(exchange);
        String body = new String(bodyBytes, StandardCharsets.UTF_8);

        // 5. Query parameters
        Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getRawQuery());

        // 6. Dispatch routes
        HttpResponse response;
        try {
            response = dispatchRoute(method, fullPath, headers, queryParams, body);
        } catch (Exception e) {
            logger.error("Unhandled top-level controller error: {}", e.getMessage(), e);
            response = HttpResponse.internalServerError("An internal server error occurred.");
        }

        sendResponse(exchange, response, traceId);
    }

    private HttpResponse dispatchRoute(String method, String path, Map<String, String> headers, Map<String, String> queryParams, String body) {
        // Strip trailing slash if any
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        // GET /api/v1/students/me
        if ("GET".equals(method) && (path.endsWith("/me") || path.endsWith("/my-profile"))) {
            return getMyProfile(headers);
        }

        // POST /api/v1/students
        if ("POST".equals(method) && (path.equals("/api/v1/students") || path.equals("/v1/students"))) {
            return enrollStudent(headers, body);
        }

        // GET /api/v1/students or /v1/student-roster
        if ("GET".equals(method) && (path.equals("/api/v1/students") || path.equals("/v1/students") || path.equals("/v1/student-roster"))) {
            return getRoster(headers, queryParams);
        }

        // Match /api/v1/students/{id} sub-routes
        String[] parts = path.split("/");
        // e.g. ["", "api", "v1", "students", "{id}", ...]
        int studentIdIdx = -1;
        for (int i = 0; i < parts.length; i++) {
            if ("students".equals(parts[i]) && i + 1 < parts.length) {
                studentIdIdx = i + 1;
                break;
            }
        }

        if (studentIdIdx != -1 && studentIdIdx < parts.length) {
            String studentId = parts[studentIdIdx];
            int subIdx = studentIdIdx + 1;

            if (subIdx >= parts.length) {
                // /api/v1/students/{id}
                if ("GET".equals(method)) return getStudentById(headers, studentId);
                if ("PATCH".equals(method) || "PUT".equals(method)) return updatePermittedFields(headers, studentId, body);
                if ("DELETE".equals(method)) return deleteStudent(headers, studentId);
            } else {
                String sub = parts[subIdx];
                if ("status".equals(sub) && "PATCH".equals(method)) {
                    return updateStatus(headers, studentId, body);
                } else if ("identifiers".equals(sub)) {
                    if ("POST".equals(method)) return addIdentifier(headers, studentId, body);
                    if ("GET".equals(method)) return getIdentifiers(headers, studentId);
                } else if ("corrections".equals(sub)) {
                    if (subIdx + 1 < parts.length) {
                        String reqId = parts[subIdx + 1];
                        if ("PATCH".equals(method)) return decideCorrectionRequest(headers, studentId, reqId, body);
                    } else {
                        if ("POST".equals(method)) return submitCorrectionRequest(headers, studentId, body);
                        if ("GET".equals(method)) return getCorrectionRequests(headers, studentId);
                    }
                } else if ("verifications".equals(sub)) {
                    if (subIdx + 1 < parts.length) {
                        String vId = parts[subIdx + 1];
                        if ("PATCH".equals(method)) return decideVerification(headers, studentId, vId, body);
                    } else {
                        if ("POST".equals(method)) return initiateVerification(headers, studentId, body);
                    }
                } else if ("history".equals(sub) && "GET".equals(method)) {
                    return getProfileChangeHistory(headers, studentId);
                } else if (("projection".equals(sub) || "export".equals(sub)) && "GET".equals(method)) {
                    return getDownstreamProjection(headers, studentId);
                } else if ("preferences".equals(sub)) {
                    if ("GET".equals(method)) return getPreferences(headers, studentId);
                    if ("PUT".equals(method) || "PATCH".equals(method)) return updatePreferences(headers, studentId, body);
                } else if ("address".equals(sub) && ("PUT".equals(method) || "PATCH".equals(method))) {
                    return updateAddress(headers, studentId, body);
                } else if ("guardians".equals(sub) && "POST".equals(method)) {
                    return addGuardian(headers, studentId, body);
                } else if ("documents".equals(sub) && "POST".equals(method)) {
                    return attachDocument(headers, studentId, body);
                } else if ("iam-link".equals(sub) && "POST".equals(method)) {
                    return linkIamAccount(headers, studentId, body);
                } else if ("provision-iam".equals(sub) && "POST".equals(method)) {
                    return requestAccountProvisioning(headers, studentId);
                }
            }
        }

        return HttpResponse.notFound("Endpoint not found: " + method + " " + path);
    }

    private void sendResponse(HttpExchange exchange, HttpResponse response, String traceId) throws IOException {
        byte[] bytes = response.getBody() != null ? response.getBody().getBytes(StandardCharsets.UTF_8) : new byte[0];
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("X-Correlation-Id", traceId);
        exchange.getResponseHeaders().set("X-Trace-Id", traceId);
        if (response.getHeaders() != null) {
            for (Map.Entry<String, String> entry : response.getHeaders().entrySet()) {
                exchange.getResponseHeaders().set(entry.getKey(), entry.getValue());
            }
        }
        exchange.sendResponseHeaders(response.getStatusCode(), bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private byte[] readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] temp = new byte[4096];
        int read;
        while ((read = is.read(temp)) != -1) {
            buffer.write(temp, 0, read);
        }
        return buffer.toByteArray();
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
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

    /**
     * Handles student enrollment (STM01-US-001, US-006, US-007, US-034, US-035).
     */
    public HttpResponse enrollStudent(Map<String, String> headers, String body) {
        try {
            UserSecurityContext context = resolveContext(headers);
            String idempotencyKey = headers != null ? headers.get("Idempotency-Key") : null;
            EnrollStudentRequest request = objectMapper.readValue(body, EnrollStudentRequest.class);
            StudentProfileResponse response = studentDomainService.enrollStudent(context, request, idempotencyKey);
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
     * Handles student self-profile retrieval (STM01-US-003, US-031).
     */
    public HttpResponse getMyProfile(Map<String, String> headers) {
        try {
            UserSecurityContext context = resolveContext(headers);
            StudentProfileResponse response = studentDomainService.getMyProfile(context);
            return HttpResponse.ok(objectMapper.writeValueAsString(response));
        } catch (StudentNotFoundException e) {
            return HttpResponse.notFound(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in getMyProfile: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    /**
     * Handles cursor-paginated roster search (STM01-US-002, US-038).
     */
    public HttpResponse getRoster(Map<String, String> headers, Map<String, String> queryParams) {
        try {
            UserSecurityContext context = resolveContext(headers);

            // Rate limit expensive roster searches (STM01-US-038)
            RateLimiter.RateLimitResult rateCheck = rateLimiter.tryAcquire(RateLimiter.RateLimitScope.TENANT_AND_CALLER, "ROSTER_SEARCH", context);
            if (!rateCheck.isAllowed()) {
                return HttpResponse.tooManyRequests("Search rate limit exceeded. Retry after " + rateCheck.getRetryAfterSeconds() + " seconds", rateCheck.getRetryAfterSeconds());
            }

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
     * Handles single student retrieval by ID (STM01-US-003, US-030).
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
     * Handles partial profile update on allowlisted fields (STM01-US-004, US-005, US-033, US-036).
     */
    public HttpResponse updatePermittedFields(Map<String, String> headers, String idParam, String body) {
        try {
            UserSecurityContext context = resolveContext(headers);
            UUID studentId = UUID.fromString(idParam);
            UpdateStudentProfileRequest request = objectMapper.readValue(body, UpdateStudentProfileRequest.class);

            // If-Match optimistic concurrency check
            if (headers != null && headers.containsKey("If-Match")) {
                try {
                    String matchVal = headers.get("If-Match").replace("\"", "").trim();
                    request.setRowVersion(Integer.parseInt(matchVal));
                } catch (NumberFormatException ignored) {}
            }

            StudentProfileResponse response = studentDomainService.updatePermittedFields(context, studentId, request);
            return HttpResponse.ok(objectMapper.writeValueAsString(response));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid student UUID format");
        } catch (StudentNotFoundException e) {
            return HttpResponse.notFound(e.getMessage());
        } catch (StudentConflictException e) {
            return HttpResponse.conflict(e.getMessage());
        } catch (StudentValidationException e) {
            return HttpResponse.badRequest(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in updatePermittedFields: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    /**
     * Handles alternate identifier creation/management (STM01-US-008).
     */
    public HttpResponse addIdentifier(Map<String, String> headers, String idParam, String body) {
        try {
            UserSecurityContext context = resolveContext(headers);
            UUID studentId = UUID.fromString(idParam);
            PersonIdentifierDto dto = objectMapper.readValue(body, PersonIdentifierDto.class);
            PersonIdentifierDto response = studentDomainService.addIdentifier(context, studentId, dto);
            return HttpResponse.created(objectMapper.writeValueAsString(response));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid student UUID format");
        } catch (StudentNotFoundException e) {
            return HttpResponse.notFound(e.getMessage());
        } catch (StudentConflictException e) {
            return HttpResponse.conflict(e.getMessage());
        } catch (StudentValidationException e) {
            return HttpResponse.badRequest(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in addIdentifier: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    /**
     * Handles retrieving identifiers with masking support (STM01-US-009).
     */
    public HttpResponse getIdentifiers(Map<String, String> headers, String idParam) {
        try {
            UserSecurityContext context = resolveContext(headers);
            UUID studentId = UUID.fromString(idParam);
            List<PersonIdentifierDto> response = studentDomainService.getIdentifiers(context, studentId);
            return HttpResponse.ok(objectMapper.writeValueAsString(response));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid student UUID format");
        } catch (StudentNotFoundException e) {
            return HttpResponse.notFound(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in getIdentifiers: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    /**
     * Handles submitting a profile correction request (STM01-US-010).
     */
    public HttpResponse submitCorrectionRequest(Map<String, String> headers, String idParam, String body) {
        try {
            UserSecurityContext context = resolveContext(headers);
            UUID studentId = UUID.fromString(idParam);
            SubmitCorrectionRequest request = objectMapper.readValue(body, SubmitCorrectionRequest.class);
            StudentCorrectionRequestEntity response = studentDomainService.submitCorrectionRequest(context, studentId, request);
            return HttpResponse.created(objectMapper.writeValueAsString(response));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid student UUID format");
        } catch (StudentNotFoundException e) {
            return HttpResponse.notFound(e.getMessage());
        } catch (StudentValidationException e) {
            return HttpResponse.badRequest(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in submitCorrectionRequest: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    /**
     * Handles retrieving correction requests for student (STM01-US-010).
     */
    public HttpResponse getCorrectionRequests(Map<String, String> headers, String idParam) {
        try {
            UserSecurityContext context = resolveContext(headers);
            UUID studentId = UUID.fromString(idParam);
            List<StudentCorrectionRequestEntity> response = studentDomainService.getCorrectionRequests(context, studentId);
            return HttpResponse.ok(objectMapper.writeValueAsString(response));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid student UUID format");
        } catch (StudentNotFoundException e) {
            return HttpResponse.notFound(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in getCorrectionRequests: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    /**
     * Handles approving or rejecting a profile correction request (STM01-US-011).
     */
    public HttpResponse decideCorrectionRequest(Map<String, String> headers, String idParam, String reqIdParam, String body) {
        try {
            UserSecurityContext context = resolveContext(headers);
            UUID studentId = UUID.fromString(idParam);
            UUID reqId = UUID.fromString(reqIdParam);
            DecideCorrectionRequest request = objectMapper.readValue(body, DecideCorrectionRequest.class);
            StudentCorrectionRequestEntity response = studentDomainService.decideCorrectionRequest(context, studentId, reqId, request);
            return HttpResponse.ok(objectMapper.writeValueAsString(response));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid UUID format");
        } catch (StudentNotFoundException e) {
            return HttpResponse.notFound(e.getMessage());
        } catch (StudentConflictException e) {
            return HttpResponse.conflict(e.getMessage());
        } catch (StudentValidationException e) {
            return HttpResponse.badRequest(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in decideCorrectionRequest: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    /**
     * Handles initiating verification (STM01-US-013).
     */
    public HttpResponse initiateVerification(Map<String, String> headers, String idParam, String body) {
        try {
            UserSecurityContext context = resolveContext(headers);
            UUID studentId = UUID.fromString(idParam);
            InitiateVerificationRequest request = body != null && !body.trim().isEmpty() ?
                    objectMapper.readValue(body, InitiateVerificationRequest.class) : new InitiateVerificationRequest();
            StudentVerificationEntity response = studentDomainService.initiateVerification(context, studentId, request);
            return HttpResponse.created(objectMapper.writeValueAsString(response));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid student UUID format");
        } catch (StudentNotFoundException e) {
            return HttpResponse.notFound(e.getMessage());
        } catch (StudentConflictException e) {
            return HttpResponse.conflict(e.getMessage());
        } catch (StudentValidationException e) {
            return HttpResponse.badRequest(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in initiateVerification: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    /**
     * Handles deciding verification (STM01-US-014, US-047).
     */
    public HttpResponse decideVerification(Map<String, String> headers, String idParam, String vIdParam, String body) {
        try {
            UserSecurityContext context = resolveContext(headers);
            UUID studentId = UUID.fromString(idParam);
            UUID verificationId = UUID.fromString(vIdParam);
            DecideVerificationRequest request = objectMapper.readValue(body, DecideVerificationRequest.class);
            StudentVerificationEntity response = studentDomainService.decideVerification(context, studentId, verificationId, request);
            return HttpResponse.ok(objectMapper.writeValueAsString(response));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid UUID format");
        } catch (StudentNotFoundException e) {
            return HttpResponse.notFound(e.getMessage());
        } catch (StudentConflictException e) {
            return HttpResponse.conflict(e.getMessage());
        } catch (StudentValidationException e) {
            return HttpResponse.badRequest(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in decideVerification: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    /**
     * Handles lifecycle state transitions (STM01-US-015).
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
     * Handles updating student addresses (STM01-US-017).
     */
    public HttpResponse updateAddress(Map<String, String> headers, String idParam, String body) {
        try {
            UserSecurityContext context = resolveContext(headers);
            UUID studentId = UUID.fromString(idParam);
            StudentAddressRequest request = objectMapper.readValue(body, StudentAddressRequest.class);
            StudentAddressEntity response = studentDomainService.updateAddress(context, studentId, request);
            return HttpResponse.ok(objectMapper.writeValueAsString(response));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid student UUID format");
        } catch (StudentNotFoundException e) {
            return HttpResponse.notFound(e.getMessage());
        } catch (StudentConflictException e) {
            return HttpResponse.conflict(e.getMessage());
        } catch (StudentValidationException e) {
            return HttpResponse.badRequest(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in updateAddress: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    /**
     * Handles adding a guardian relationship (STM01-US-018).
     */
    public HttpResponse addGuardian(Map<String, String> headers, String idParam, String body) {
        try {
            UserSecurityContext context = resolveContext(headers);
            UUID studentId = UUID.fromString(idParam);
            EnrollStudentRequest.GuardianRequest request = objectMapper.readValue(body, EnrollStudentRequest.GuardianRequest.class);
            StudentProfileResponse response = studentDomainService.addGuardian(context, studentId, request);
            return HttpResponse.ok(objectMapper.writeValueAsString(response));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid student UUID format");
        } catch (StudentNotFoundException e) {
            return HttpResponse.notFound(e.getMessage());
        } catch (StudentConflictException e) {
            return HttpResponse.conflict(e.getMessage());
        } catch (StudentValidationException e) {
            return HttpResponse.badRequest(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in addGuardian: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    /**
     * Handles attaching document metadata (STM01-US-020, US-021, US-042).
     */
    public HttpResponse attachDocument(Map<String, String> headers, String idParam, String body) {
        try {
            UserSecurityContext context = resolveContext(headers);
            UUID studentId = UUID.fromString(idParam);
            AttachDocumentRequest request = objectMapper.readValue(body, AttachDocumentRequest.class);
            StudentDocumentEntity response = studentDomainService.attachDocument(context, studentId, request);
            return HttpResponse.created(objectMapper.writeValueAsString(response));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid student UUID format");
        } catch (StudentNotFoundException e) {
            return HttpResponse.notFound(e.getMessage());
        } catch (StudentConflictException e) {
            return HttpResponse.conflict(e.getMessage());
        } catch (StudentValidationException e) {
            return HttpResponse.badRequest(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in attachDocument: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    /**
     * Handles linking student profile to IAM identity (STM01-US-023).
     */
    public HttpResponse linkIamAccount(Map<String, String> headers, String idParam, String body) {
        try {
            UserSecurityContext context = resolveContext(headers);
            UUID studentId = UUID.fromString(idParam);
            IamLinkageRequest request = objectMapper.readValue(body, IamLinkageRequest.class);
            StudentProfileResponse response = studentDomainService.linkIamAccount(context, studentId, request);
            return HttpResponse.ok(objectMapper.writeValueAsString(response));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid student UUID format");
        } catch (StudentNotFoundException e) {
            return HttpResponse.notFound(e.getMessage());
        } catch (StudentConflictException e) {
            return HttpResponse.conflict(e.getMessage());
        } catch (StudentValidationException e) {
            return HttpResponse.badRequest(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in linkIamAccount: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    /**
     * Handles requesting IAM account provisioning (STM01-US-024).
     */
    public HttpResponse requestAccountProvisioning(Map<String, String> headers, String idParam) {
        try {
            UserSecurityContext context = resolveContext(headers);
            UUID studentId = UUID.fromString(idParam);
            studentDomainService.requestAccountProvisioning(context, studentId);
            return HttpResponse.ok("{\"success\":true,\"message\":\"Account provisioning requested successfully.\"}");
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid student UUID format");
        } catch (StudentNotFoundException e) {
            return HttpResponse.notFound(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in requestAccountProvisioning: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    /**
     * Handles student deactivation / soft deletion (STM01-US-039).
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
     * Handles profile change history retrieval (STM01-US-025).
     */
    public HttpResponse getProfileChangeHistory(Map<String, String> headers, String studentId) {
        try {
            UserSecurityContext context = resolveContext(headers);
            UUID sId = UUID.fromString(studentId.trim());
            List<ProfileChangeHistoryDto> history = studentDomainService.getProfileChangeHistory(context, sId);
            return HttpResponse.ok(objectMapper.writeValueAsString(history));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid student UUID format");
        } catch (StudentNotFoundException e) {
            return HttpResponse.notFound(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (StudentValidationException e) {
            return HttpResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in getProfileChangeHistory: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    /**
     * Handles downstream student projection / export (STM01-US-044, STM01-US-049).
     */
    public HttpResponse getDownstreamProjection(Map<String, String> headers, String studentId) {
        try {
            UserSecurityContext context = resolveContext(headers);

            // Rate limit projection/export calls (STM01-US-038, US-049)
            RateLimiter.RateLimitResult rateCheck = rateLimiter.tryAcquire(RateLimiter.RateLimitScope.TENANT_AND_CALLER, "STUDENT_EXPORT", context);
            if (!rateCheck.isAllowed()) {
                return HttpResponse.tooManyRequests("Export rate limit exceeded. Retry after " + rateCheck.getRetryAfterSeconds() + " seconds", rateCheck.getRetryAfterSeconds());
            }

            UUID sId = UUID.fromString(studentId.trim());
            StudentProjectionResponse projection = studentDomainService.getDownstreamProjection(context, sId);
            return HttpResponse.ok(objectMapper.writeValueAsString(projection));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid student UUID format");
        } catch (StudentNotFoundException e) {
            return HttpResponse.notFound(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (StudentValidationException e) {
            return HttpResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in getDownstreamProjection: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    /**
     * Handles student preferences retrieval (STM01-US-022, STM01-US-045).
     */
    public HttpResponse getPreferences(Map<String, String> headers, String studentId) {
        try {
            UserSecurityContext context = resolveContext(headers);
            UUID sId = UUID.fromString(studentId.trim());
            StudentPreferencesDto prefs = studentDomainService.getPreferences(context, sId);
            return HttpResponse.ok(objectMapper.writeValueAsString(prefs));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid student UUID format");
        } catch (StudentNotFoundException e) {
            return HttpResponse.notFound(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (StudentValidationException e) {
            return HttpResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in getPreferences: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
    }

    /**
     * Handles student preferences update (STM01-US-022, STM01-US-045).
     */
    public HttpResponse updatePreferences(Map<String, String> headers, String studentId, String body) {
        try {
            UserSecurityContext context = resolveContext(headers);
            UUID sId = UUID.fromString(studentId.trim());
            StudentPreferencesDto prefs = objectMapper.readValue(body, StudentPreferencesDto.class);
            StudentProfileResponse updated = studentDomainService.updatePreferences(context, sId, prefs);
            return HttpResponse.ok(objectMapper.writeValueAsString(updated));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid student UUID format");
        } catch (StudentNotFoundException e) {
            return HttpResponse.notFound(e.getMessage());
        } catch (StudentSecurityViolationException e) {
            return HttpResponse.forbidden(e.getMessage());
        } catch (StudentValidationException e) {
            return HttpResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled error in updatePreferences: {}", e.getMessage(), e);
            return HttpResponse.internalServerError(e.getMessage());
        }
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
        public static HttpResponse preconditionFailed(String detail) {
            return new HttpResponse(412, String.format("{\"type\":\"https://campxsync.edu/errors/precondition-failed\",\"title\":\"Precondition Failed\",\"status\":412,\"detail\":\"%s\"}", escape(detail)));
        }
        public static HttpResponse unprocessableEntity(String detail) {
            return new HttpResponse(422, String.format("{\"type\":\"https://campxsync.edu/errors/unprocessable-entity\",\"title\":\"Unprocessable Entity\",\"status\":422,\"detail\":\"%s\"}", escape(detail)));
        }
        public static HttpResponse tooManyRequests(String detail, long retryAfterSeconds) {
            HttpResponse resp = new HttpResponse(429, String.format("{\"type\":\"https://campxsync.edu/errors/too-many-requests\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"%s\",\"errorCode\":\"RATE_LIMIT_EXCEEDED\"}", escape(detail)));
            resp.getHeaders().put("Retry-After", String.valueOf(retryAfterSeconds));
            resp.getHeaders().put("X-RateLimit-Limit", "60");
            resp.getHeaders().put("X-RateLimit-Remaining", "0");
            return resp;
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
