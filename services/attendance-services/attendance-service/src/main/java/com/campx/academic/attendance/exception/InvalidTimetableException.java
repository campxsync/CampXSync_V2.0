package com.campx.academic.attendance.exception;

public class InvalidTimetableException extends AttendanceException {
    public InvalidTimetableException(String message) {
        super("ACD_ATTENDANCE_INVALID_TIMETABLE", message, 422);
    }
}
