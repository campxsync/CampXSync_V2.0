package com.campx.academic.resource.exception;

/**
 * Thrown when shared object/document storage is unreachable or reference cannot be verified (HTTP 503).
 */
public class ResourceObjectUnavailableException extends ResourceException {

    public ResourceObjectUnavailableException(String message) {
        super(503, "ACD_RESOURCE_OBJECT_UNAVAILABLE", message);
    }
}
