package com.campx.academic.timetable.exception;

public class TimetableForbiddenException extends TimetableException {
    public TimetableForbiddenException(String message) {
        super("ACD_TIMETABLE_FORBIDDEN", message, 403);
    }
}
