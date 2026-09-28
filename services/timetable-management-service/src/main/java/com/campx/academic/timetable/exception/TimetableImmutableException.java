package com.campx.academic.timetable.exception;

public class TimetableImmutableException extends TimetableException {
    public TimetableImmutableException(String message) {
        super("ACD_TIMETABLE_IMMUTABLE", message, 409);
    }
}
