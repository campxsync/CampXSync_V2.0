package com.campx.academic.assessment.exception;

/**
 * Thrown when an optimistic concurrency conflict occurs due to stale version or concurrent edit (HTTP 409).
 */
public class AssessmentVersionConflictException extends AssessmentException {

    public AssessmentVersionConflictException(String message) {
        super(409, "ACD_ASSESSMENT_VERSION_CONFLICT", message);
    }
}
