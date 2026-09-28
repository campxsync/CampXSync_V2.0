package com.campx.academic.attendance.exception;

public class RateLimitExceededException extends AttendanceException {
    public RateLimitExceededException(String message) {
        super("ACD_ATTENDANCE_RATE_LIMIT_EXCEEDED", message, 429);
    }
}
