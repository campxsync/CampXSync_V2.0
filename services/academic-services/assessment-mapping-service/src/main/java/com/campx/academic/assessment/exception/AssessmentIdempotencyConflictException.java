package com.campx.academic.assessment.exception;

/**
 * Thrown when an Idempotency-Key is reused with a different request payload (HTTP 409).
 */
public class AssessmentIdempotencyConflictException extends AssessmentException {

    public AssessmentIdempotencyConflictException(String message) {
        super(409, "ACD_ASSESSMENT_IDEMPOTENCY_CONFLICT", message);
    }
}
