package com.campx.admin.institute.exception;

/**
 * Thrown when a unique constraint or resource state conflicts (409).
 */
public class ResourceConflictException extends InstituteAdminException {

    public ResourceConflictException(String resourceType, String field, String value) {
        super(409, "RESOURCE_CONFLICT", resourceType + " with " + field + " '" + value + "' already exists");
    }
}
