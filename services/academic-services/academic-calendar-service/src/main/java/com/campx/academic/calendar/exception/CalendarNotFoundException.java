package com.campx.academic.calendar.exception;

public class CalendarNotFoundException extends CalendarException {
    public CalendarNotFoundException(String message) {
        super(message, "ACD_CALENDAR_NOT_FOUND", 404);
    }
}
