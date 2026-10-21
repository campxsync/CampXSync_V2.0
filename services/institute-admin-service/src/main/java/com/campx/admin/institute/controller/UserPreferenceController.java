package com.campx.admin.institute.controller;

import com.campx.admin.institute.exception.*;
import com.campx.admin.institute.model.ErrorResponse;
import com.campx.admin.institute.model.preference.PreferenceJsonParser;
import com.campx.admin.institute.model.preference.PreferenceResponse;
import com.campx.admin.institute.model.preference.UserPreferences;
import com.campx.admin.institute.repository.UserPreferenceRepository;
import com.campx.admin.institute.security.GatewayHmacVerifier;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.FlowTracker;
import com.campx.logger.context.LogContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * REST HTTP Controller for User Preferences (Wave 2).
 * <p>
 * Mounts at {@code /api/v1/user/preferences}.
 * Enforces:
 * <ul>
 *   <li>Gateway HMAC verification and trusted identity extraction.</li>
 *   <li>Strict rejection or discarding of client-supplied body user/tenant IDs (anti-spoofing).</li>
 *   <li>Authentication requirement (HTTP 401 unauthenticated).</li>
 *   <li>Role authority isolation (only platform_admin may specify an alternate role).</li>
 *   <li>Deep-merge preference updates in PostgreSQL {@code iam.user_profiles.preferences}.</li>
 *   <li>RFC 7807 problem details error responses.</li>
 * </ul>
 * </p>
 */
public class UserPreferenceController implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(UserPreferenceController.class);
    private static final int MAX_BODY_BYTES = 65536; // 64KB max payload guard

    private final UserPreferenceRepository repository;
    private final GatewayHmacVerifier gatewayHmacVerifier;

    public UserPreferenceController() {
        this(new UserPreferenceRepository(), new GatewayHmacVerifier());
    }

    public UserPreferenceController(UserPreferenceRepository repository) {
        this(repository, new GatewayHmacVerifier());
    }

    public UserPreferenceController(UserPreferenceRepository repository, GatewayHmacVerifier gatewayHmacVerifier) {
        this.repository = repository != null ? repository : new UserPreferenceRepository();
        this.gatewayHmacVerifier = gatewayHmacVerifier != null ? gatewayHmacVerifier : new GatewayHmacVerifier();
    }

    public UserPreferenceRepository getRepository() {
        return repository;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        String methodOverride = exchange.getRequestHeaders().getFirst("X-HTTP-Method-Override");
        if (methodOverride != null && !methodOverride.trim().isEmpty()) {
            method = methodOverride.trim().toUpperCase(Locale.ROOT);
        }

        // 1. Read and cache raw request body
        byte[] bodyBytes = readRequestBodyBytes(exchange);
        if (bodyBytes.length > MAX_BODY_BYTES) {
            sendError(exchange, 413, "Payload Too Large", "PAYLOAD_TOO_LARGE",
                    "Request body exceeds maximum allowed size of 64KB", path);
            return;
        }

        // 2. Platform Trust Boundary: Verify Gateway HMAC signature and extract verified identity
        GatewayHmacVerifier.VerificationResult authResult =
                gatewayHmacVerifier.verify(exchange, method, path, bodyBytes);
        if (!authResult.isSuccess()) {
            sendError(exchange, authResult.getStatus(), authResult.getError(), authResult.getErrorCode(), authResult.getMessage(), path);
            return;
        }

        // 3. Extract verified identity context
        String userIdStr = authResult.getUserId();
        String tenantIdStr = authResult.getTenantId();
        String userRole = authResult.getUserRole();

        if (userIdStr == null || userIdStr.trim().isEmpty()) {
            sendError(exchange, 401, "Unauthorized", "GATEWAY_UNAUTHENTICATED",
                    "Authentication required: missing or empty authenticated user identity", path);
            return;
        }

        UUID userId;
        try {
            userId = UUID.fromString(userIdStr.trim());
        } catch (IllegalArgumentException e) {
            sendError(exchange, 401, "Unauthorized", "INVALID_IDENTITY_FORMAT",
                    "Authenticated user ID is not a valid UUID: " + userIdStr, path);
            return;
        }

        UUID tenantId = null;
        if (tenantIdStr != null && !tenantIdStr.trim().isEmpty()) {
            try {
                tenantId = UUID.fromString(tenantIdStr.trim());
            } catch (IllegalArgumentException e) {
                // Non-UUID tenant header
                tenantId = null;
            }
        }

        boolean isPlatformRole = "platform_admin".equalsIgnoreCase(userRole) || "super_admin".equalsIgnoreCase(userRole);
        UserSecurityContext context = new UserSecurityContext(userId, tenantId, isPlatformRole);

        // Populate LogContext for correlation
        String traceId = exchange.getRequestHeaders().getFirst("X-Trace-Id");
        if (traceId != null && !traceId.trim().isEmpty()) {
            LogContext.setTraceId(traceId);
        }
        if (tenantId != null) {
            LogContext.setTenantId(tenantId.toString());
        }
        LogContext.setUserId(userId.toString());
        if (userRole != null) {
            LogContext.setUserRole(userRole);
        }

        try (FlowTracker flow = logger.flow("UserPreferences", method + " " + path)) {
            if ("GET".equalsIgnoreCase(method)) {
                handleGetPreferences(exchange, context, userRole, path);
            } else if ("PUT".equalsIgnoreCase(method)) {
                handlePutPreferences(exchange, context, userRole, bodyBytes, path);
            } else {
                exchange.getResponseHeaders().set("Allow", "GET, PUT, OPTIONS");
                sendError(exchange, 405, "Method Not Allowed", "METHOD_NOT_ALLOWED",
                        "HTTP method '" + method + "' is not supported for preferences", path);
            }
        } catch (PreferenceValidationException e) {
            logger.warn("Preference validation failure on [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Unprocessable Entity", e.getErrorCode(), e.getMessage(), path);
        } catch (MalformedPayloadException e) {
            logger.warn("Malformed payload on [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Bad Request", e.getErrorCode(), e.getMessage(), path);
        } catch (UserProfileNotFoundException e) {
            logger.warn("User profile not found for preferences [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Not Found", e.getErrorCode(), e.getMessage(), path);
        } catch (UserProfileAccessDeniedException e) {
            logger.warn("Access denied on preferences [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Forbidden", e.getErrorCode(), e.getMessage(), path);
        } catch (UserProfileConflictException e) {
            logger.warn("Conflict on preferences [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Conflict", e.getErrorCode(), e.getMessage(), path);
        } catch (SecurityViolationException e) {
            logger.warn("Security violation on preferences [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Forbidden", e.getErrorCode(), e.getMessage(), path);
        } catch (Exception e) {
            logger.error("Unhandled error processing preferences [{} {}]: {}", method, path, e.getMessage(), e);
            sendError(exchange, 500, "Internal Server Error", "INTERNAL_SERVER_ERROR",
                    "An unexpected error occurred while processing preferences", path);
        } finally {
            LogContext.clear();
        }
    }

    private void handleGetPreferences(HttpExchange exchange, UserSecurityContext context, String userRole, String path) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String requestedRole = extractQueryParam(query, "role");
        if (requestedRole == null || requestedRole.trim().isEmpty()) {
            requestedRole = (userRole != null && !userRole.trim().isEmpty()) ? userRole : "college_admin";
        }

        UserPreferences prefs = repository.getPreferences(context, requestedRole);
        PreferenceResponse response = new PreferenceResponse(true, prefs, true, "Preferences retrieved successfully.");
        sendJson(exchange, 200, response.toJson());
    }

    private void handlePutPreferences(HttpExchange exchange, UserSecurityContext context, String userRole, byte[] bodyBytes, String path) throws IOException {
        if (bodyBytes == null || bodyBytes.length == 0) {
            throw new MalformedPayloadException("Request body cannot be null or empty for PUT /api/v1/user/preferences");
        }

        String jsonStr = new String(bodyBytes, StandardCharsets.UTF_8).trim();
        if (!jsonStr.startsWith("{") || !jsonStr.endsWith("}")) {
            throw new MalformedPayloadException("Request body must be a valid JSON object");
        }

        Map<String, Object> map = PreferenceJsonParser.parseObject(jsonStr);

        // Anti-spoofing check: strictly reject any attempt in the body to modify another user's or tenant's identity
        if (map.containsKey("userId") || map.containsKey("user_id")) {
            String bodyUserId = String.valueOf(map.get(map.containsKey("userId") ? "userId" : "user_id"));
            if (!bodyUserId.equalsIgnoreCase(context.getUserId().toString())) {
                throw new UserProfileAccessDeniedException("Disallowed identity override: client body userId cannot override authenticated context");
            }
        }
        if (map.containsKey("tenantId") || map.containsKey("tenant_id")) {
            String bodyTenantId = String.valueOf(map.get(map.containsKey("tenantId") ? "tenantId" : "tenant_id"));
            if (context.getTenantId() != null && !bodyTenantId.equalsIgnoreCase(context.getTenantId().toString())) {
                throw new UserProfileAccessDeniedException("Disallowed identity override: client body tenantId cannot override authenticated context");
            }
        }

        // Determine effective role:
        // Non-platform callers may ONLY configure preferences for their own authenticated role.
        boolean isPlatformAdmin = "platform_admin".equalsIgnoreCase(userRole) || "super_admin".equalsIgnoreCase(userRole);
        String requestedRole = null;
        if (map.containsKey("role") && map.get("role") instanceof String) {
            requestedRole = (String) map.get("role");
        }

        String effectiveRole;
        if (isPlatformAdmin && requestedRole != null && !requestedRole.trim().isEmpty()) {
            effectiveRole = requestedRole.trim();
        } else {
            effectiveRole = (userRole != null && !userRole.trim().isEmpty()) ? userRole : "college_admin";
        }

        UserPreferences incoming = UserPreferences.fromMap(map, effectiveRole);
        incoming.validate();

        UserPreferences saved = repository.updatePreferences(context, incoming, effectiveRole);
        PreferenceResponse response = new PreferenceResponse(true, saved, true,
                "Preferences successfully synced to institutional profile in PostgreSQL.");
        sendJson(exchange, 200, response.toJson());
    }

    private byte[] readRequestBodyBytes(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = is.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }

    private String extractQueryParam(String query, String paramName) {
        if (query == null || query.trim().isEmpty() || paramName == null) return null;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = pair.substring(0, idx).trim();
                if (paramName.equalsIgnoreCase(key)) {
                    try {
                        return URLDecoder.decode(pair.substring(idx + 1).trim(), "UTF-8");
                    } catch (Exception ignored) {
                        return pair.substring(idx + 1).trim();
                    }
                }
            }
        }
        return null;
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String errorTitle, String errorCode, String detail, String path) throws IOException {
        String traceId = LogContext.getTraceId();
        ErrorResponse errorResponse = new ErrorResponse(statusCode, errorTitle, errorCode, detail, path, traceId);
        byte[] responseBytes = errorResponse.toBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/problem+json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
