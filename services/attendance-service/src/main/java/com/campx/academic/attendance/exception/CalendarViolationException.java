package com.campx.academic.attendance.exception;

public class CalendarViolationException extends AttendanceException {
    public CalendarViolationException(String message) {
        super("ACD_ATTENDANCE_CALENDAR_VIOLATION", message, 422);
    }
}
