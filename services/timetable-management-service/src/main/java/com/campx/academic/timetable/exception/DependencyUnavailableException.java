package com.campx.academic.timetable.exception;

public class DependencyUnavailableException extends TimetableException {
    public DependencyUnavailableException(String message) {
        super("ACD_TIMETABLE_DEPENDENCY_UNAVAILABLE", message, 503);
    }
}
