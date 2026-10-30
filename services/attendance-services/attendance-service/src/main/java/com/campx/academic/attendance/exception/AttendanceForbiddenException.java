package com.campx.academic.attendance.exception;

public class AttendanceForbiddenException extends AttendanceException {
    public AttendanceForbiddenException(String message) {
        super("ACD_ATTENDANCE_FORBIDDEN", message, 403);
    }
}
