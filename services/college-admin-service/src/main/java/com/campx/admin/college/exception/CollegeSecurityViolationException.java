package com.campx.admin.college.exception;

/**
 * Exception raised when an operation violates college security policy,
 * such as overriding globally locked safety flags or tampering with protected governance.
 */
public class CollegeSecurityViolationException extends CollegeAdminException {

    /**
     * Constructs a new {@code CollegeSecurityViolationException} with an explicit error code and message.
     *
     * @param errorCode the machine-readable security error code
     * @param message   the security violation description
     */
    public CollegeSecurityViolationException(String errorCode, String message) {
        super(400, errorCode, message);
    }

    /**
     * Constructs a new {@code CollegeSecurityViolationException} with the default security violation code.
     *
     * @param message the security violation description
     */
    public CollegeSecurityViolationException(String message) {
        super(400, "ADM02_SECURITY_VIOLATION", message);
    }
}
