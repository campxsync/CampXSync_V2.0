package com.campx.iadm.security.exception;

/**
 * Thrown when a unique constraint or resource state conflicts (409).
 */
public class ResourceConflictException extends InstituteAdminException {

    public ResourceConflictException(String resourceType, String field, String value) {
        super(409, "IADM07_RESOURCE_CONFLICT", resourceType + " with " + field + " '" + value + "' already exists");
    }

    public ResourceConflictException(String resourceType, String message) {
        super(409, "IADM07_RESOURCE_CONFLICT", resourceType + ": " + message);
    }
}
