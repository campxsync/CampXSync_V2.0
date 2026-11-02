package com.campx.admissions.exception;

import com.campx.admissions.controller.ApplicationController;
import com.campx.admissions.logging.AdmissionsLoggingService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception handler and RFC 7807 Problem Details mapper for STM-02.
 * <p>
 * Translates domain, security, lifecycle, concurrency, and validation exceptions into
 * standardized RFC 7807 Problem Details HTTP responses with sanitized messages and correlation tracking.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class AdmissionsExceptionHandler {

    private final ObjectMapper objectMapper;
    private final AdmissionsLoggingService loggingService;

    public AdmissionsExceptionHandler(ObjectMapper objectMapper, AdmissionsLoggingService loggingService) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.loggingService = loggingService != null ? loggingService : new AdmissionsLoggingService();
    }

    /**
     * Maps any thrown exception to a standardized RFC 7807 HttpResponse.
     *
     * @param throwable     The exception encountered.
     * @param method        HTTP request verb.
     * @param path          Request URI path.
     * @param correlationId Distributed correlation ID.
     * @return Formatted HTTP response with JSON RFC 7807 Problem Details.
     */
    public ApplicationController.HttpResponse handleException(Throwable throwable, String method, String path, String correlationId) {
        ProblemDetails.Builder builder = ProblemDetails.builder()
                .instance(path)
                .correlationId(correlationId);

        int statusCode;
        Map<String, String> extraHeaders = new HashMap<>();

        if (throwable instanceof AdmissionsRateLimitExceededException) {
            AdmissionsRateLimitExceededException ex = (AdmissionsRateLimitExceededException) throwable;
            statusCode = 429;
            builder.type("https://campxsync.edu/errors/rate-limit-exceeded")
                    .title("Too Many Requests")
                    .status(429)
                    .errorCode("RATE_LIMIT_EXCEEDED")
                    .detail(ex.getMessage());
            extraHeaders.put("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));

        } else if (throwable instanceof AdmissionsUnauthorizedException) {
            statusCode = 401;
            builder.type("https://campxsync.edu/errors/unauthorized")
                    .title("Unauthorized")
                    .status(401)
                    .errorCode("UNAUTHORIZED")
                    .detail(throwable.getMessage());

        } else if (throwable instanceof AdmissionsSecurityViolationException) {
            statusCode = 403;
            builder.type("https://campxsync.edu/errors/forbidden")
                    .title("Forbidden")
                    .status(403)
                    .errorCode("SECURITY_VIOLATION")
                    .detail(throwable.getMessage());

        } else if (throwable instanceof AdmissionsNotFoundException) {
            statusCode = 404;
            builder.type("https://campxsync.edu/errors/not-found")
                    .title("Not Found")
                    .status(404)
                    .errorCode("RESOURCE_NOT_FOUND")
                    .detail(throwable.getMessage());

        } else if (throwable instanceof AdmissionsConflictException) {
            statusCode = 409;
            builder.type("https://campxsync.edu/errors/conflict")
                    .title("Conflict")
                    .status(409)
                    .errorCode("CONFLICT")
                    .detail(throwable.getMessage());

        } else if (throwable instanceof AdmissionsLifecycleException) {
            statusCode = 422;
            builder.type("https://campxsync.edu/errors/unprocessable-entity")
                    .title("Unprocessable Entity")
                    .status(422)
                    .errorCode("LIFECYCLE_TRANSITION_VIOLATION")
                    .detail(throwable.getMessage());

        } else if (throwable instanceof AdmissionsValidationException || throwable instanceof IllegalArgumentException) {
            statusCode = 400;
            builder.type("https://campxsync.edu/errors/bad-request")
                    .title("Bad Request")
                    .status(400)
                    .errorCode("VALIDATION_FAILED")
                    .detail(throwable.getMessage());

        } else if (throwable instanceof IllegalStateException) {
            statusCode = 422;
            builder.type("https://campxsync.edu/errors/unprocessable-entity")
                    .title("Illegal State")
                    .status(422)
                    .errorCode("ILLEGAL_STATE")
                    .detail(throwable.getMessage());

        } else if (throwable instanceof AdmissionsBadGatewayException) {
            statusCode = 502;
            builder.type("https://campxsync.edu/errors/bad-gateway")
                    .title("Bad Gateway")
                    .status(502)
                    .errorCode("DOWNSTREAM_SERVICE_ERROR")
                    .detail("An error occurred while communicating with a downstream domain service: " + throwable.getMessage());

        } else {
            statusCode = 500;
            builder.type("https://campxsync.edu/errors/internal-server-error")
                    .title("Internal Server Error")
                    .status(500)
                    .errorCode("INTERNAL_SERVER_ERROR")
                    .detail("An internal error occurred while processing the request. Reference correlationId: " + correlationId);

            loggingService.logError(method + " " + path, throwable, correlationId);
        }

        ProblemDetails problemDetails = builder.build();
        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(problemDetails);
        } catch (Exception e) {
            jsonBody = "{\"type\":\"about:blank\",\"title\":\"Error\",\"status\":" + statusCode + ",\"detail\":\"" + escapeJson(throwable.getMessage()) + "\"}";
        }

        ApplicationController.HttpResponse response = new ApplicationController.HttpResponse(statusCode, jsonBody);
        response.headers.put("Content-Type", "application/problem+json");
        response.headers.putAll(extraHeaders);

        return response;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}
