package com.campx.academic.calendar.exception;

/**
 * Base abstract exception for all ACD-07 Academic Calendar Service domain faults.
 */
public abstract class CalendarException extends RuntimeException {

    private final String errorCode;
    private final int statusCode;

    public CalendarException(String message, String errorCode, int statusCode) {
        super(message);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }

    public CalendarException(String message, String errorCode, int statusCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
