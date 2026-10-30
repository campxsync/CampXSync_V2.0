package com.campx.academic.attendance.exception;

public class IdempotencyConflictException extends AttendanceException {
    public IdempotencyConflictException(String message) {
        super("ACD_ATTENDANCE_IDEMPOTENCY_CONFLICT", message, 409);
    }
}
