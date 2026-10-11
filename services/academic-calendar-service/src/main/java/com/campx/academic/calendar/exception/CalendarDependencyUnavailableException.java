package com.campx.academic.calendar.exception;

public class CalendarDependencyUnavailableException extends CalendarException {
    public CalendarDependencyUnavailableException(String message) {
        super(message, "ACD_CALENDAR_DEPENDENCY_UNAVAILABLE", 503);
    }
}
