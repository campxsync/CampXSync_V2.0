package com.campx.admin.institute.exception;

/**
 * Thrown when an institute or college violates unique code constraints (HTTP 409).
 */
public class InstituteAlreadyExistsException extends InstituteAdminException {

    public InstituteAlreadyExistsException(String entityType, String field, String value) {
        super(409, "ADM01_DUPLICATE_RESOURCE", "Uniqueness violation: " + entityType + " with " + field + " '" + value + "' already exists");
    }

    public InstituteAlreadyExistsException(String message) {
        super(409, "ADM01_DUPLICATE_RESOURCE", message);
    }
}
