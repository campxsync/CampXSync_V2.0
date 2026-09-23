package com.campx.admin.institute.exception;

/**
 * Thrown when an institute or college violates unique code constraints, triggering HTTP 409 Conflict.
 */
public class InstituteAlreadyExistsException extends InstituteAdminException {

    /**
     * Constructs a uniqueness violation exception identifying the conflicting entity type, field, and value.
     *
     * @param entityType the domain entity type (e.g. "Institute", "College")
     * @param field      the unique field name (e.g. "instituteCode")
     * @param value      the duplicate value encountered
     */
    public InstituteAlreadyExistsException(String entityType, String field, String value) {
        super(409, "ADM01_DUPLICATE_RESOURCE", "Uniqueness violation: " + entityType + " with " + field + " '" + value + "' already exists");
    }

    /**
     * Constructs an exception with an explicit message.
     *
     * @param message descriptive conflict explanation
     */
    public InstituteAlreadyExistsException(String message) {
        super(409, "ADM01_DUPLICATE_RESOURCE", message);
    }
}
