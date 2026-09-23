package com.campx.admin.college.exception;

/**
 * Thrown when a department or program violates code uniqueness constraints (HTTP 409).
 */
public class CollegeResourceConflictException extends CollegeAdminException {

    /**
     * Constructs a new {@code CollegeResourceConflictException} for a specific unique entity violation.
     *
     * @param entityType the domain entity type (e.g., "Department", "Program")
     * @param field      the unique field name violating uniqueness (e.g., "departmentCode")
     * @param value      the conflicting value
     */
    public CollegeResourceConflictException(String entityType, String field, String value) {
        super(409, "ADM02_DUPLICATE_RESOURCE", "Uniqueness violation: " + entityType + " with " + field + " '" + value + "' already exists");
    }

    /**
     * Constructs a new {@code CollegeResourceConflictException} with a custom message.
     *
     * @param message the explanation of the conflict
     */
    public CollegeResourceConflictException(String message) {
        super(409, "ADM02_DUPLICATE_RESOURCE", message);
    }
}
