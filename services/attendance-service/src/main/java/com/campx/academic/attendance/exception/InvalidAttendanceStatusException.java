package com.campx.academic.attendance.exception;

public class InvalidAttendanceStatusException extends AttendanceException {
    public InvalidAttendanceStatusException(String message) {
        super("ACD_ATTENDANCE_INVALID_STATUS", message, 422);
    }
}
