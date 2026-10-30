package com.campx.academic.calendar.exception;

public class IdempotencyConflictException extends CalendarException {
    public IdempotencyConflictException(String message) {
        super(message, "ACD_CALENDAR_IDEMPOTENCY_CONFLICT", 409);
    }
}
