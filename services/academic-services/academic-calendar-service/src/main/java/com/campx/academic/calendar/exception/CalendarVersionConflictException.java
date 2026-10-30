package com.campx.academic.calendar.exception;

public class CalendarVersionConflictException extends CalendarException {
    public CalendarVersionConflictException(String message) {
        super(message, "ACD_CALENDAR_VERSION_CONFLICT", 409);
    }
}
