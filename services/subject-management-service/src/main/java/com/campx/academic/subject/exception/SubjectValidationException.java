package com.campx.academic.subject.exception;

/**
 * Thrown when subject business validation fails (e.g. invalid department, missing mandatory fields).
 * Returns HTTP 422 Unprocessable Entity per BR-08 and Exceptions §15.
 */
public class SubjectValidationException extends SubjectException {
    public SubjectValidationException(String message) {
        super(422, "ACD_SUBJECT_VALIDATION_ERROR", message);
    }

    public SubjectValidationException(String errorCode, String message) {
        super(422, errorCode, message);
    }
}
