package com.campx.academic.curriculum.exception;

/**
 * Thrown when attempting to map the same subject to the same semester more than once without an explicit exemption (HTTP 409).
 */
public class DuplicateMappingException extends CurriculumException {

    public static final String ERROR_CODE = "ACD2_DUPLICATE_MAPPING";

    public DuplicateMappingException(String message) {
        super(409, ERROR_CODE, message);
    }
}
