package com.campx.academic.curriculum.exception;

/**
 * Thrown when a cross-tenant or campus scope boundary mismatch is detected (BR-13) (HTTP 403 / 422).
 */
public class TenantMismatchException extends CurriculumException {

    public static final String ERROR_CODE = "ACD2_TENANT_MISMATCH";

    public TenantMismatchException(String message) {
        super(403, ERROR_CODE, message);
    }
}
