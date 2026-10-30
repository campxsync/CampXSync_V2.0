package com.campx.academic.timetable.exception;

public class TimetableNotFoundException extends TimetableException {
    public TimetableNotFoundException(String message) {
        super("ACD_TIMETABLE_NOT_FOUND", message, 404);
    }
}
