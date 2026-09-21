package com.campx.admin.institute.exception;

/**
 * Thrown when a request violates security governance or attempts to mutate immutable keys (HTTP 400).
 * Examples: Plaintext passwords/secrets submitted instead of vault references,
 * or altering immutable identity keys ('instituteCode').
 */
public class SecurityViolationException extends InstituteAdminException {

    public SecurityViolationException(String message) {
        super(400, "ADM01_SECURITY_VIOLATION", message);
    }

    public SecurityViolationException(String errorCode, String message) {
        super(400, errorCode, message);
    }
}
