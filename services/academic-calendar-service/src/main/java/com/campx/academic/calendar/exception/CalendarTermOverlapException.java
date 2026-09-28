package com.campx.academic.calendar.exception;

public class CalendarTermOverlapException extends CalendarException {
    public CalendarTermOverlapException(String message) {
        super(message, "ACD_CALENDAR_TERM_OVERLAP", 422);
    }
}
