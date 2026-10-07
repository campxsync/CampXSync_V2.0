package com.campx.academic.analytics.exception;

import java.util.Collections;
import java.util.List;

/**
 * Standardized exception hierarchy for ACD-10: Reporting & Analytics Service.
 * Maps domain and platform conditions to standard HTTP status codes and error codes.
 */
public class AnalyticsExceptions {

    public static class AnalyticsException extends RuntimeException {
        private final String errorCode;
        private final int httpStatus;
        private final List<String> details;

        public AnalyticsException(String message, String errorCode, int httpStatus) {
            this(message, errorCode, httpStatus, Collections.emptyList());
        }

        public AnalyticsException(String message, String errorCode, int httpStatus, List<String> details) {
            super(message);
            this.errorCode = errorCode;
            this.httpStatus = httpStatus;
            this.details = details != null ? details : Collections.emptyList();
        }

        public String getErrorCode() { return errorCode; }
        public int getHttpStatus() { return httpStatus; }
        public List<String> getDetails() { return details; }
    }

    /**
     * 400 Bad Request: Invalid filters, reversed date ranges, unsupported formats, schema violations.
     */
    public static class AnalyticsValidationException extends AnalyticsException {
        public AnalyticsValidationException(String message) {
            super(message, "ACD10_ANALYTICS_VALIDATION_FAILED", 400);
        }

        public AnalyticsValidationException(String message, String errorCode) {
            super(message, errorCode, 400);
        }

        public AnalyticsValidationException(String message, String errorCode, List<String> details) {
            super(message, errorCode, 400, details);
        }
    }

    /**
     * 401 Unauthorized: Missing or invalid authentication token / identity.
     */
    public static class AnalyticsUnauthorizedException extends AnalyticsException {
        public AnalyticsUnauthorizedException(String message) {
            super(message, "ACD10_ANALYTICS_UNAUTHORIZED", 401);
        }

        public AnalyticsUnauthorizedException(String message, String errorCode) {
            super(message, errorCode, 401);
        }
    }

    /**
     * 403 Forbidden: Scope violation, role unauthorized, cross-department/tenant access denied, restricted metric withheld.
     */
    public static class AnalyticsForbiddenException extends AnalyticsException {
        public AnalyticsForbiddenException(String message) {
            super(message, "ACD10_ANALYTICS_SCOPE_FORBIDDEN", 403);
        }

        public AnalyticsForbiddenException(String message, String errorCode) {
            super(message, errorCode, 403);
        }

        public AnalyticsForbiddenException(String message, String errorCode, List<String> details) {
            super(message, errorCode, 403, details);
        }
    }

    /**
     * 404 Not Found: Report job not found, projection missing.
     */
    public static class AnalyticsNotFoundException extends AnalyticsException {
        public AnalyticsNotFoundException(String message) {
            super(message, "ACD10_ANALYTICS_NOT_FOUND", 404);
        }

        public AnalyticsNotFoundException(String message, String errorCode) {
            super(message, errorCode, 404);
        }
    }

    /**
     * 409 Conflict: Idempotency key reuse with mismatched request hash, conflicting rebuild job.
     */
    public static class AnalyticsConflictException extends AnalyticsException {
        public AnalyticsConflictException(String message) {
            super(message, "ACD10_ANALYTICS_CONFLICT", 409);
        }

        public AnalyticsConflictException(String message, String errorCode) {
            super(message, errorCode, 409);
        }
    }

    /**
     * 429 Too Many Requests: Rate limit exceeded.
     */
    public static class AnalyticsRateLimitException extends AnalyticsException {
        private final long retryAfterSeconds;

        public AnalyticsRateLimitException(String message, long retryAfterSeconds) {
            super(message, "ACD10_ANALYTICS_RATE_LIMIT_EXCEEDED", 429);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    /**
     * 500 Internal Server Error: Unexpected service failure.
     */
    public static class AnalyticsServiceException extends AnalyticsException {
        public AnalyticsServiceException(String message) {
            super(message, "ACD10_ANALYTICS_INTERNAL_ERROR", 500);
        }

        public AnalyticsServiceException(String message, Throwable cause) {
            super(message, "ACD10_ANALYTICS_INTERNAL_ERROR", 500);
            initCause(cause);
        }

        public AnalyticsServiceException(String message, String errorCode, Throwable cause) {
            super(message, errorCode, 500);
            initCause(cause);
        }
    }

    /**
     * 503 Service Unavailable: Projection engine temporarily unavailable, circuit breaker open.
     */
    public static class AnalyticsUnavailableException extends AnalyticsException {
        public AnalyticsUnavailableException(String message) {
            super(message, "ACD10_ANALYTICS_UNAVAILABLE", 503);
        }

        public AnalyticsUnavailableException(String message, String errorCode) {
            super(message, errorCode, 503);
        }
    }
}
