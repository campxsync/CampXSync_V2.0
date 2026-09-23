package com.campx.admin.institute.exception;

/**
 * Thrown when a request violates security governance or attempts to mutate immutable keys, triggering HTTP 400 Bad Request.
 * <p>
 * Examples: Plaintext passwords or secrets submitted instead of vault references,
 * or altering immutable identity keys (e.g. {@code instituteCode}).
 */
public class SecurityViolationException extends InstituteAdminException {

    /**
     * Constructs a security violation exception with default error code {@code "ADM01_SECURITY_VIOLATION"}.
     *
     * @param message descriptive explanation of the security policy violation
     */
    public SecurityViolationException(String message) {
        super(400, "ADM01_SECURITY_VIOLATION", message);
    }

    /**
     * Constructs a security violation exception with a custom application error code.
     *
     * @param errorCode specific security violation code
     * @param message   descriptive explanation of the security policy violation
     */
    public SecurityViolationException(String errorCode, String message) {
        super(400, errorCode, message);
    }
}
