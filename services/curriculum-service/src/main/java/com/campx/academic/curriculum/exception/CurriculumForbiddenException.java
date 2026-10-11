package com.campx.academic.curriculum.exception;

/**
 * Thrown when an actor does not possess the requisite RBAC permission or departmental ABAC scope (HTTP 403).
 */
public class CurriculumForbiddenException extends CurriculumException {

    public static final String ERROR_CODE = "ACD2_FORBIDDEN";

    public CurriculumForbiddenException(String message) {
        super(403, ERROR_CODE, message);
    }
}
