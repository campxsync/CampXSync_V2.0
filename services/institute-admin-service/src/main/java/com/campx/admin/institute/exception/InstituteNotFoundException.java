package com.campx.admin.institute.exception;

/**
 * Thrown when an institute, tenant, or related administrative resource cannot be found (HTTP 404).
 */
public class InstituteNotFoundException extends InstituteAdminException {

    public InstituteNotFoundException(String resourceType, String identifier) {
        super(404, "ADM01_RESOURCE_NOT_FOUND", resourceType + " not found with identifier: " + identifier);
    }

    public InstituteNotFoundException(String message) {
        super(404, "ADM01_RESOURCE_NOT_FOUND", message);
    }
}
