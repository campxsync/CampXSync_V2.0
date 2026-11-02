package com.campx.admissions.exception;

import java.util.Map;

/**
 * Thrown when an applicant or caller exceeds the allocated request quota or burst threshold.
 * <p>
 * Maps to HTTP 429 Too Many Requests with RFC 7807 problem details and Retry-After header.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class AdmissionsRateLimitExceededException extends AdmissionsException {

    private final int retryAfterSeconds;

    public AdmissionsRateLimitExceededException(String message) {
        this(message, 60);
    }

    public AdmissionsRateLimitExceededException(String message, int retryAfterSeconds) {
        super(message, 429, "RATE_LIMIT_EXCEEDED");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public AdmissionsRateLimitExceededException(String message, int retryAfterSeconds, String correlationId) {
        super(message, 429, "RATE_LIMIT_EXCEEDED", correlationId, null, null);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
