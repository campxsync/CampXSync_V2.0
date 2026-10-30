package com.campx.academic.course.exception;

/**
 * Thrown when an unauthenticated caller or unauthorized role attempts an action (HTTP 401/403).
 */
public class CourseSecurityException extends CourseException {

    /**
     * Constructs a new {@code CourseSecurityException} with explicit status code, error code, and message.
     *
     * @param status    HTTP status code (401 or 403)
     * @param errorCode machine-readable security error code
     * @param message   descriptive failure message
     */
    public CourseSecurityException(int status, String errorCode, String message) {
        super(status, errorCode, message);
    }

    /**
     * Creates an HTTP 401 Unauthorized course security exception.
     *
     * @param message explanation of unauthenticated attempt
     * @return new CourseSecurityException
     */
    public static CourseSecurityException unauthorized(String message) {
        return new CourseSecurityException(401, "ACD_UNAUTHORIZED", message);
    }

    /**
     * Creates an HTTP 403 Forbidden course security exception for a role and action.
     *
     * @param role   the principal role attempting the operation
     * @param action the disallowed operational action
     * @return new CourseSecurityException
     */
    public static CourseSecurityException forbidden(String role, String action) {
        return new CourseSecurityException(403, "ACD_FORBIDDEN",
                "Role [" + role + "] is forbidden from performing action: " + action);
    }
}
