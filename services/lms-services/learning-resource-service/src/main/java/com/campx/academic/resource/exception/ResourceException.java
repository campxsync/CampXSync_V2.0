package com.campx.academic.resource.exception;

/**
 * Base abstract exception for ACD-08 Learning Resource Service.
 */
public abstract class ResourceException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public ResourceException(int statusCode, String errorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public ResourceException(int statusCode, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
