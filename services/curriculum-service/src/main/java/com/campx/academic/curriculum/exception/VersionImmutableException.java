package com.campx.academic.curriculum.exception;

/**
 * Thrown when attempting to modify a published immutable curriculum version (HTTP 409).
 */
public class VersionImmutableException extends CurriculumException {

    public static final String ERROR_CODE = "ACD2_VERSION_IMMUTABLE";

    public VersionImmutableException(String message) {
        super(409, ERROR_CODE, message);
    }
}
