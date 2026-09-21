package com.campx.admin.institute.exception;

/**
 * Base runtime exception for ADM-01 Institute Admin Service.
 * Associates an HTTP status code and a semantic machine-readable error code.
 */
public class InstituteAdminException extends RuntimeException {

    private final int status;
    private final String errorCode;

    public InstituteAdminException(int status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public InstituteAdminException(int status, String errorCode, String message, Throwable cause) {
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
