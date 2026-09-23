package com.campx.academic.subject.exception;

/**
 * Thrown when an unauthenticated request reaches protected endpoints.
 * Returns HTTP 401 Unauthorized per §53.
 */
public class SubjectUnauthorizedException extends SubjectException {
    public SubjectUnauthorizedException(String message) {
        super(401, "ACD_UNAUTHORIZED", message);
    }
}
