package com.campx.academic.timetable.exception;

public class IdempotencyConflictException extends TimetableException {
    public IdempotencyConflictException(String message) {
        super("ACD_TIMETABLE_IDEMPOTENCY_CONFLICT", message, 409);
    }
}
