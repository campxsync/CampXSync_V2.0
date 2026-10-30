package com.campx.academic.batch.exception;

/**
 * Base domain exception for ACD-04 Batch Management Service with HTTP status mapping.
 */
public class BatchException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    public BatchException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public BatchException(String errorCode, String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
