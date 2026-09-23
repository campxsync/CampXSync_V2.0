package com.campx.admin.college.exception;

/**
 * Thrown when a college profile, department, program, document, or import job is not found (HTTP 404).
 */
public class CollegeResourceNotFoundException extends CollegeAdminException {

    /**
     * Constructs a new {@code CollegeResourceNotFoundException} identifying the resource type and missing identifier.
     *
     * @param resourceType the category of resource (e.g., "Department", "Program", "GovernanceDocument")
     * @param identifier   the identifier that could not be resolved
     */
    public CollegeResourceNotFoundException(String resourceType, String identifier) {
        super(404, "ADM02_RESOURCE_NOT_FOUND", resourceType + " not found with identifier: " + identifier);
    }

    /**
     * Constructs a new {@code CollegeResourceNotFoundException} with a custom message.
     *
     * @param message the explanation of the missing resource
     */
    public CollegeResourceNotFoundException(String message) {
        super(404, "ADM02_RESOURCE_NOT_FOUND", message);
    }
}
