package com.campx.admin.college.exception;

/**
 * Thrown when a college profile, department, program, document, or import job is not found (HTTP 404).
 */
public class CollegeResourceNotFoundException extends CollegeAdminException {

    public CollegeResourceNotFoundException(String resourceType, String identifier) {
        super(404, "ADM02_RESOURCE_NOT_FOUND", resourceType + " not found with identifier: " + identifier);
    }

    public CollegeResourceNotFoundException(String message) {
        super(404, "ADM02_RESOURCE_NOT_FOUND", message);
    }
}
