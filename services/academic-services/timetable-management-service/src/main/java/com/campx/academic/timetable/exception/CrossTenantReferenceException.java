package com.campx.academic.timetable.exception;

public class CrossTenantReferenceException extends TimetableException {
    public CrossTenantReferenceException(String message) {
        super("ACD_TIMETABLE_CROSS_TENANT_FORBIDDEN", message, 422);
    }
}
