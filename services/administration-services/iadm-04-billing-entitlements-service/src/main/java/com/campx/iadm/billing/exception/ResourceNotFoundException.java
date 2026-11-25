package com.campx.iadm.billing.exception;

/**
 * Thrown when a requested resource is not found (404).
 */
public class ResourceNotFoundException extends InstituteAdminException {

    public ResourceNotFoundException(String resourceType, String identifier) {
        super(404, "Iadm04_RESOURCE_NOT_FOUND", resourceType + " not found with identifier: " + identifier);
    }

    public ResourceNotFoundException(String message) {
        super(404, "Iadm04_RESOURCE_NOT_FOUND", message);
    }
}
