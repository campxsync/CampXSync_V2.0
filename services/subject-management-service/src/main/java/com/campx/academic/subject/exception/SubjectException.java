package com.campx.academic.subject.exception;

/**
 * Base unchecked exception for all ACD-03 Subject Management Service domain errors.
 */
public class SubjectException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public SubjectException(int statusCode, String errorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public SubjectException(int statusCode, String errorCode, String message, Throwable cause) {
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
