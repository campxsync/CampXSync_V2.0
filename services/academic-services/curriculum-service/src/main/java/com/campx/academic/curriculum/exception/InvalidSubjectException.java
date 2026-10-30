package com.campx.academic.curriculum.exception;

/**
 * Thrown when a referenced subjectId fails validation against ACD-03 or is inactive/deprecated (HTTP 422).
 */
public class InvalidSubjectException extends CurriculumException {

    public static final String ERROR_CODE = "ACD2_SUBJECT_INVALID";

    public InvalidSubjectException(String message) {
        super(422, ERROR_CODE, message);
    }
}
