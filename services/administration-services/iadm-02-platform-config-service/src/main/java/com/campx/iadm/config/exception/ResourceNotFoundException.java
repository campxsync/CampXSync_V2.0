package com.campx.iadm.config.exception;

/**
 * Thrown when a requested resource is not found (404).
 */
public class ResourceNotFoundException extends InstituteAdminException {

    public ResourceNotFoundException(String resourceType, String identifier) {
        super(404, "IADM02_RESOURCE_NOT_FOUND", resourceType + " not found with identifier: " + identifier);
    }

    public ResourceNotFoundException(String message) {
        super(404, "IADM02_RESOURCE_NOT_FOUND", message);
    }
}
