package com.campx.academic.timetable.exception;

public class TimetableValidationException extends TimetableException {
    public TimetableValidationException(String message) {
        super("ACD_TIMETABLE_VALIDATION_FAILED", message, 422);
    }

    public TimetableValidationException(String message, Object details) {
        super("ACD_TIMETABLE_VALIDATION_FAILED", message, 422, details);
    }
}
