package com.campx.academic.attendance.exception;

public class AttendanceCorrectionForbiddenException extends AttendanceException {
    public AttendanceCorrectionForbiddenException(String message) {
        super("ACD_ATTENDANCE_CORRECTION_FORBIDDEN", message, 403);
    }
}
