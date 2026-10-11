package com.campx.academic.calendar.exception;

public class CalendarUnauthorizedException extends CalendarException {
    public CalendarUnauthorizedException(String message) {
        super(message, "ACD_CALENDAR_UNAUTHORIZED", 401);
    }
}
