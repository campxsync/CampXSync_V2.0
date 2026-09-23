package com.campx.academic.curriculum.exception;

/**
 * Thrown when a requested curriculum or version aggregate cannot be found (HTTP 404).
 */
public class CurriculumNotFoundException extends CurriculumException {

    public static final String ERROR_CODE = "ACD2_CURRICULUM_NOT_FOUND";

    public CurriculumNotFoundException(String message) {
        super(404, ERROR_CODE, message);
    }
}
