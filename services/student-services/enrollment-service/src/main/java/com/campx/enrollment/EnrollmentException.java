package com.campx.enrollment;

/**
 * Business or authorization failure from the enrolment service.
 * The HTTP status and stable code are safe to return to the caller. The message does not include secrets.
 */
public class EnrollmentException extends RuntimeException {

    private final int status;
    private final String code;

    public EnrollmentException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
