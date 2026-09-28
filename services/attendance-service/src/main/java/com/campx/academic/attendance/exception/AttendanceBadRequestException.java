package com.campx.academic.attendance.exception;

public class AttendanceBadRequestException extends AttendanceException {
    public AttendanceBadRequestException(String message) {
        super("ACD_ATTENDANCE_BAD_REQUEST", message, 400);
    }
}
