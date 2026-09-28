package com.campx.academic.attendance.exception;

public class DuplicateAttendanceRecordException extends AttendanceException {
    public DuplicateAttendanceRecordException(String message) {
        super("ACD_ATTENDANCE_DUPLICATE_RECORD", message, 409);
    }
}
