package com.campx.academic.assessment.exception;

/**
 * Thrown when client or tenant request rate exceeds configured thresholds (HTTP 429).
 */
public class AssessmentRateLimitExceededException extends AssessmentException {

    public AssessmentRateLimitExceededException(String message) {
        super(429, "ACD_ASSESSMENT_RATE_LIMIT_EXCEEDED", message);
    }
}
