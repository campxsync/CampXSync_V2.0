package com.campx.admin.institute.exception;

/**
 * Thrown when a foreign key reference (college_id, department_id, person_id) does not exist
 * within the caller's tenant.
 * Maps to HTTP 400 Bad Request.
 */
public class InvalidUserReferenceException extends InstituteAdminException {

    public InvalidUserReferenceException(String fieldName, String value) {
        super(400, "ADM01_INVALID_REFERENCE",
                String.format("Invalid foreign reference for '%s': '%s' does not exist in the current tenant", fieldName, value));
    }

    public InvalidUserReferenceException(String message) {
        super(400, "ADM01_INVALID_REFERENCE", message);
    }
}
