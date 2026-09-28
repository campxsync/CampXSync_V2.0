package com.campx.academic.timetable.exception;

public class TimetableBadRequestException extends TimetableException {
    public TimetableBadRequestException(String message) {
        super("ACD_TIMETABLE_BAD_REQUEST", message, 400);
    }
}
