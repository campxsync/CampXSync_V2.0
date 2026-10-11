package com.campx.academic.calendar.exception;

public class DuplicateCalendarException extends CalendarException {
    public DuplicateCalendarException(String message) {
        super(message, "ACD_CALENDAR_DUPLICATE_CALENDAR", 409);
    }
}
