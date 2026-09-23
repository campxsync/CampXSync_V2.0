package com.campx.academic.subject.exception;

/**
 * Thrown when an authenticated caller lacks permissions or scope for an operation.
 * Returns HTTP 403 Forbidden per §48 and §53.
 */
public class SubjectForbiddenException extends SubjectException {
    public SubjectForbiddenException(String message) {
        super(403, "ACD_FORBIDDEN", message);
    }
}
