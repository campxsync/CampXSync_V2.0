package com.campx.academic.calendar.exception;

public class CalendarBadRequestException extends CalendarException {
    public CalendarBadRequestException(String message) {
        super(message, "ACD_CALENDAR_BAD_REQUEST", 400);
    }
}
