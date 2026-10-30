package com.campx.academic.timetable.exception;

public class TimetableUnauthorizedException extends TimetableException {
    public TimetableUnauthorizedException(String message) {
        super("ACD_TIMETABLE_UNAUTHORIZED", message, 401);
    }
}
