package com.campx.admin.institute.exception;

/**
 * Thrown when an administrative action violates entity lifecycle constraints (HTTP 422).
 * For example: Attempting to register a college under an INACTIVE institute.
 */
public class InvalidTenantStateException extends InstituteAdminException {

    public InvalidTenantStateException(String message) {
        super(422, "ADM01_INVALID_LIFECYCLE_STATE", message);
    }

    public InvalidTenantStateException(String entityType, String currentStatus, String requiredStatus) {
        super(422, "ADM01_INVALID_LIFECYCLE_STATE",
                "Action rejected: " + entityType + " is in state '" + currentStatus + "', but requires state '" + requiredStatus + "'");
    }
}
