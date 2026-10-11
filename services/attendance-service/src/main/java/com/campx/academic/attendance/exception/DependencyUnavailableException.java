package com.campx.academic.attendance.exception;

public class DependencyUnavailableException extends AttendanceException {
    public DependencyUnavailableException(String message) {
        super("ACD_ATTENDANCE_DEPENDENCY_UNAVAILABLE", message, 503);
    }
}
