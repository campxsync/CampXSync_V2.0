package com.campx.academic.timetable.exception;

public class TimetableConflictException extends TimetableException {
    public TimetableConflictException(String message) {
        super("ACD_TIMETABLE_CONFLICT", message, 422);
    }

    public TimetableConflictException(String message, Object details) {
        super("ACD_TIMETABLE_CONFLICT", message, 422, details);
    }

    public TimetableConflictException(String errorCode, String message, int httpStatus, Object details) {
        super(errorCode, message, httpStatus, details);
    }
}
