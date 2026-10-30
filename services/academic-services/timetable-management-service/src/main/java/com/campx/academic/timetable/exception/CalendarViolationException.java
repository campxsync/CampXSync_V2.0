package com.campx.academic.timetable.exception;

public class CalendarViolationException extends TimetableException {
    public CalendarViolationException(String message) {
        super("ACD_TIMETABLE_CALENDAR_VIOLATION", message, 422);
    }
}
