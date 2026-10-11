package com.campx.academic.calendar.exception;

public class InvalidCalendarStateException extends CalendarException {
    public InvalidCalendarStateException(String message) {
        super(message, "ACD_CALENDAR_INVALID_STATE", 409);
    }
}
