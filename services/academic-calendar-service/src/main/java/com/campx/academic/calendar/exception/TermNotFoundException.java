package com.campx.academic.calendar.exception;

public class TermNotFoundException extends CalendarException {
    public TermNotFoundException(String message) {
        super(message, "ACD_CALENDAR_TERM_NOT_FOUND", 404);
    }
}
