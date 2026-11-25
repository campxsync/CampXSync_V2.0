package com.campx.iadm.governance.exception;

/**
 * Thrown when a unique constraint or resource state conflicts (409).
 */
public class ResourceConflictException extends InstituteAdminException {

    public ResourceConflictException(String resourceType, String field, String value) {
        super(409, "Iadm05_RESOURCE_CONFLICT", resourceType + " with " + field + " '" + value + "' already exists");
    }

    public ResourceConflictException(String resourceType, String message) {
        super(409, "Iadm05_RESOURCE_CONFLICT", resourceType + ": " + message);
    }
}
