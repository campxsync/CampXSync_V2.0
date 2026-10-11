package com.campx.academic.calendar.exception;

public class EventNotFoundException extends CalendarException {
    public EventNotFoundException(String message) {
        super(message, "ACD_CALENDAR_EVENT_NOT_FOUND", 404);
    }
}
