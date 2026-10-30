package com.campx.academic.attendance.exception;

public class AttendanceNotFoundException extends AttendanceException {
    public AttendanceNotFoundException(String message) {
        super("ACD_ATTENDANCE_NOT_FOUND", message, 404);
    }
}
