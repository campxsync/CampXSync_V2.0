package com.campx.academic.timetable.exception;

public class RateLimitExceededException extends TimetableException {
    public RateLimitExceededException(String message) {
        super("ACD_TIMETABLE_RATE_LIMIT_EXCEEDED", message, 429);
    }
}
