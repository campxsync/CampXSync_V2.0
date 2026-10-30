package com.campx.academic.subject.exception;

/**
 * Thrown when an update detects optimistic locking failure / stale version reference.
 * Returns HTTP 409 Conflict per BR-12 and Exceptions §15.
 */
public class VersionConflictException extends SubjectException {
    public VersionConflictException(String message) {
        super(409, "ACD_STALE_VERSION", message);
    }
}
