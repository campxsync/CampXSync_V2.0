package com.campx.academic.calendar.exception;

public class CalendarForbiddenException extends CalendarException {
    public CalendarForbiddenException(String message) {
        super(message, "ACD_CALENDAR_FORBIDDEN", 403);
    }
}
