package com.campx.iadm.security.exception;

/**
 * Thrown when a request violates security governance, triggering HTTP 400 Bad Request.
 */
public class SecurityViolationException extends InstituteAdminException {

    public SecurityViolationException(String message) {
        super(400, "IADM07_SECURITY_VIOLATION", message);
    }

    public SecurityViolationException(String errorCode, String message) {
        super(400, errorCode, message);
    }
}
