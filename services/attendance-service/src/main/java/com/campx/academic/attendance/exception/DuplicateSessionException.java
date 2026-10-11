package com.campx.academic.attendance.exception;

public class DuplicateSessionException extends AttendanceException {
    public DuplicateSessionException(String message) {
        super("ACD_ATTENDANCE_DUPLICATE_SESSION", message, 409);
    }
}
