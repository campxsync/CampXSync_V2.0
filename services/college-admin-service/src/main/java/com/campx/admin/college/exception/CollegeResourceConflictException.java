package com.campx.admin.college.exception;

/**
 * Thrown when a department or program violates code uniqueness constraints (HTTP 409).
 */
public class CollegeResourceConflictException extends CollegeAdminException {

    public CollegeResourceConflictException(String entityType, String field, String value) {
        super(409, "ADM02_DUPLICATE_RESOURCE", "Uniqueness violation: " + entityType + " with " + field + " '" + value + "' already exists");
    }

    public CollegeResourceConflictException(String message) {
        super(409, "ADM02_DUPLICATE_RESOURCE", message);
    }
}
