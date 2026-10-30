package com.campx.academic.resource.exception;

/**
 * Thrown when a requested resource or version cannot be found (HTTP 404).
 */
public class ResourceNotFoundException extends ResourceException {

    public ResourceNotFoundException(String message) {
        super(404, "ACD_RESOURCE_NOT_FOUND", message);
    }
}
