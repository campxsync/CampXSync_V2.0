package com.campx.academic.attendance.exception;

public class AttendanceWindowExpiredException extends AttendanceException {
    public AttendanceWindowExpiredException(String message) {
        super("ACD_ATTENDANCE_WINDOW_EXPIRED", message, 422);
    }
}
