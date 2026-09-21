package com.campx.admin.college.exception;

/**
 * Base runtime exception for ADM-02 College Admin Service.
 * Associates an HTTP status code and a semantic machine-readable error code.
 */
public class CollegeAdminException extends RuntimeException {

    private final int status;
    private final String errorCode;

    public CollegeAdminException(int status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public CollegeAdminException(int status, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public int getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
