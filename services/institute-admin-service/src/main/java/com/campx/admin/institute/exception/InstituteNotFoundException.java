package com.campx.admin.institute.exception;

/**
 * Thrown when an institute, tenant, or related administrative resource cannot be found, triggering HTTP 404 Not Found.
 */
public class InstituteNotFoundException extends InstituteAdminException {

    /**
     * Constructs a resource not found exception identifying resource type and query key.
     *
     * @param resourceType the domain model type (e.g. "Institute", "College", "Plan")
     * @param identifier   the missing identifier value
     */
    public InstituteNotFoundException(String resourceType, String identifier) {
        super(404, "ADM01_RESOURCE_NOT_FOUND", resourceType + " not found with identifier: " + identifier);
    }

    /**
     * Constructs an exception with an explicit message.
     *
     * @param message descriptive missing resource explanation
     */
    public InstituteNotFoundException(String message) {
        super(404, "ADM01_RESOURCE_NOT_FOUND", message);
    }
}
