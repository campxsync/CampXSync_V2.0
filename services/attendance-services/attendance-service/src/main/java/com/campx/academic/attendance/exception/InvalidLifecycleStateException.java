package com.campx.academic.attendance.exception;

public class InvalidLifecycleStateException extends AttendanceException {
    public InvalidLifecycleStateException(String message) {
        super("ACD_ATTENDANCE_INVALID_LIFECYCLE_STATE", message, 409);
    }
}
