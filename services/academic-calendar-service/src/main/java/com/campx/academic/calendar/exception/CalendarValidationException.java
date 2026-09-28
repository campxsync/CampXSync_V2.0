package com.campx.academic.calendar.exception;

public class CalendarValidationException extends CalendarException {
    public CalendarValidationException(String message) {
        super(message, "ACD_CALENDAR_VALIDATION_ERROR", 422);
    }
}
