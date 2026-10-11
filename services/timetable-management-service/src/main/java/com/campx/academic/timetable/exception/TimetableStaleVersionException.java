package com.campx.academic.timetable.exception;

public class TimetableStaleVersionException extends TimetableException {
    public TimetableStaleVersionException(String message) {
        super("ACD_TIMETABLE_STALE_VERSION", message, 409);
    }
}
