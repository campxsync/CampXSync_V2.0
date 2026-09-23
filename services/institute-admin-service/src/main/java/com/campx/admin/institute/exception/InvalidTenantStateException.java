package com.campx.admin.institute.exception;

/**
 * Thrown when an administrative action violates entity lifecycle constraints, triggering HTTP 422 Unprocessable Entity.
 * <p>
 * Example: Attempting to register a college under an {@code INACTIVE} institute or provisioning an archived plan.
 */
public class InvalidTenantStateException extends InstituteAdminException {

    /**
     * Constructs an invalid state exception with an explicit message.
     *
     * @param message descriptive lifecycle error message
     */
    public InvalidTenantStateException(String message) {
        super(422, "ADM01_INVALID_LIFECYCLE_STATE", message);
    }

    /**
     * Constructs an invalid state exception identifying entity type, current lifecycle state, and required state.
     *
     * @param entityType     entity model name
     * @param currentStatus  actual current state
     * @param requiredStatus expected state for this operation
     */
    public InvalidTenantStateException(String entityType, String currentStatus, String requiredStatus) {
        super(422, "ADM01_INVALID_LIFECYCLE_STATE",
                "Action rejected: " + entityType + " is in state '" + currentStatus + "', but requires state '" + requiredStatus + "'");
    }
}
