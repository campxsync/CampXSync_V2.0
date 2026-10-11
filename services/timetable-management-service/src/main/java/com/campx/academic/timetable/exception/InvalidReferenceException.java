package com.campx.academic.timetable.exception;

public class InvalidReferenceException extends TimetableException {
    public InvalidReferenceException(String message) {
        super("ACD_TIMETABLE_INVALID_REFERENCE", message, 422);
    }
}
