package com.campx.academic.curriculum.exception;

/**
 * Thrown when an incoming request fails structural or business validation rules (HTTP 400).
 */
public class CurriculumValidationException extends CurriculumException {

    public static final String ERROR_CODE = "ACD2_VALIDATION_ERROR";

    public CurriculumValidationException(String message) {
        super(400, ERROR_CODE, message);
    }
}
