package com.campx.academic.resource.exception;

/**
 * Thrown when a duplicate resource code exists within the same tenant (HTTP 409).
 */
public class ResourceDuplicateException extends ResourceException {

    public ResourceDuplicateException(String message) {
        super(409, "ACD_RESOURCE_DUPLICATE", message);
    }
}
