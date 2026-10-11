package com.campx.academic.attendance.exception;

public class AttendanceUnauthorizedException extends AttendanceException {
    public AttendanceUnauthorizedException(String message) {
        super("ACD_ATTENDANCE_UNAUTHORIZED", message, 401);
    }
}
