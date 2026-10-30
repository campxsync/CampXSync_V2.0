package com.campx.academic.resource.exception;

/**
 * Thrown when an actor exceeds API rate limits (HTTP 429).
 */
public class ResourceRateLimitExceededException extends ResourceException {

    public ResourceRateLimitExceededException(String message) {
        super(429, "ACD_RESOURCE_RATE_LIMIT_EXCEEDED", message);
    }
}
