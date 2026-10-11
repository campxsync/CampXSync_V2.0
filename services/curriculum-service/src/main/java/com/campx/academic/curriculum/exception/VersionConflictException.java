package com.campx.academic.curriculum.exception;

/**
 * Thrown when an optimistic concurrency conflict occurs due to stale version references (HTTP 409).
 */
public class VersionConflictException extends CurriculumException {

    public static final String ERROR_CODE = "ACD2_VERSION_CONFLICT";

    public VersionConflictException(String message) {
        super(409, ERROR_CODE, message);
    }
}
