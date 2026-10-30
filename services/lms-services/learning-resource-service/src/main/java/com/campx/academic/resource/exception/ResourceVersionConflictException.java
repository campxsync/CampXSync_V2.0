package com.campx.academic.resource.exception;

/**
 * Thrown when an optimistic concurrency check fails or an immutable version overwrite is attempted (HTTP 409).
 */
public class ResourceVersionConflictException extends ResourceException {

    public ResourceVersionConflictException(String message) {
        super(409, "ACD_RESOURCE_VERSION_CONFLICT", message);
    }
}
