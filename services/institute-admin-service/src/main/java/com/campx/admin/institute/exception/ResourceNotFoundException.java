package com.campx.admin.institute.exception;

/**
 * Thrown when a requested resource is not found (404).
 */
public class ResourceNotFoundException extends InstituteAdminException {

    public ResourceNotFoundException(String resourceType, String identifier) {
        super(404, "RESOURCE_NOT_FOUND", resourceType + " not found with identifier: " + identifier);
    }
}
