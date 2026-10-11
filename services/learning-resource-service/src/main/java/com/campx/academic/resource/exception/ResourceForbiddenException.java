package com.campx.academic.resource.exception;

/**
 * Thrown when an authenticated caller lacks sufficient role/permission to execute an operation (HTTP 403).
 */
public class ResourceForbiddenException extends ResourceException {

    public ResourceForbiddenException(String message) {
        super(403, "ACD_RESOURCE_FORBIDDEN", message);
    }
}
