package com.campx.academic.attendance.exception;

import java.util.Collections;
import java.util.List;

/**
 * Base domain exception for ACD-06: Attendance Management Service.
 */
public class AttendanceException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;
    private final List<String> details;

    public AttendanceException(String errorCode, String message, int httpStatus) {
        this(errorCode, message, httpStatus, Collections.emptyList());
    }

    public AttendanceException(String errorCode, String message, int httpStatus, List<String> details) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.details = details != null ? details : Collections.emptyList();
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public List<String> getDetails() {
        return details;
    }
}
