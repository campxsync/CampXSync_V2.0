package com.campx.admin.institute.exception;

/**
 * Thrown when an illegal lifecycle state transition is requested on a user profile.
 * Maps to HTTP 422 Unprocessable Entity.
 */
public class InvalidStatusTransitionException extends InstituteAdminException {

    public InvalidStatusTransitionException(String fromStatus, String toStatus) {
        super(422, "ADM01_INVALID_STATUS_TRANSITION",
                String.format("Invalid status transition from '%s' to '%s'", fromStatus, toStatus));
    }

    public InvalidStatusTransitionException(String message) {
        super(422, "ADM01_INVALID_STATUS_TRANSITION", message);
    }
}
