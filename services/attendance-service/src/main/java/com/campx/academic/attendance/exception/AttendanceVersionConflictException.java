package com.campx.academic.attendance.exception;

public class AttendanceVersionConflictException extends AttendanceException {
    public AttendanceVersionConflictException(String message) {
        super("ACD_ATTENDANCE_VERSION_CONFLICT", message, 409);
    }
}
