package com.campx.academic.batch.exception;

/**
 * Thrown when client exceeds allowed request throughput limit (HTTP 429).
 */
public class RateLimitExceededException extends BatchException {

    public RateLimitExceededException(String message) {
        super("ACD_RATE_LIMIT_EXCEEDED", message, 429);
    }
}
