package com.campx.academic.timetable.exception;

/**
 * Base domain exception for ACD-05 Timetable Management Service with HTTP status mapping.
 */
public class TimetableException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;
    private final Object details;

    public TimetableException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.details = null;
    }

    public TimetableException(String errorCode, String message, int httpStatus, Object details) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.details = details;
    }

    public TimetableException(String errorCode, String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.details = null;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public Object getDetails() {
        return details;
    }
}
