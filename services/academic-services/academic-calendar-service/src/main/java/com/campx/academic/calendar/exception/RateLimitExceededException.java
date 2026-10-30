package com.campx.academic.calendar.exception;

public class RateLimitExceededException extends CalendarException {
    public RateLimitExceededException(String message) {
        super(message, "ACD_CALENDAR_RATE_LIMIT_EXCEEDED", 429);
    }
}
