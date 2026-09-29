package com.campx.academic.resource.exception;

/**
 * Thrown when an idempotency key is reused with a different request payload (HTTP 409).
 */
public class ResourceIdempotencyConflictException extends ResourceException {

    public ResourceIdempotencyConflictException(String message) {
        super(409, "ACD_RESOURCE_IDEMPOTENCY_CONFLICT", message);
    }
}
