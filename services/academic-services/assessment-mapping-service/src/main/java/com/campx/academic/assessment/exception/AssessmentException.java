package com.campx.academic.assessment.exception;

/**
 * Base abstract exception for ACD-09 Assessment Mapping Service.
 */
public abstract class AssessmentException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public AssessmentException(int statusCode, String errorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public AssessmentException(int statusCode, String errorCode, String message, Throwable cause) {
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
