package com.campx.academic.attendance.exception;

public class StudentNotInBatchException extends AttendanceException {
    public StudentNotInBatchException(String message) {
        super("ACD_ATTENDANCE_STUDENT_NOT_IN_BATCH", message, 422);
    }
}
