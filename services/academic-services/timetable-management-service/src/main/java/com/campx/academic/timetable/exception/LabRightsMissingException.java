package com.campx.academic.timetable.exception;

public class LabRightsMissingException extends TimetableException {
    public LabRightsMissingException(String message) {
        super("ACD_TIMETABLE_LAB_RIGHTS_MISSING", message, 422);
    }
}
