package com.campx.academic.subject.exception;

/**
 * Thrown when a requested subject, version, or related entity cannot be located.
 * Returns HTTP 404 Not Found.
 */
public class SubjectNotFoundException extends SubjectException {
    public SubjectNotFoundException(String message) {
        super(404, "ACD_SUBJECT_NOT_FOUND", message);
    }
}
