package com.campx.academic.assessment.exception;

/**
 * Thrown when credentials or authentication headers are missing (HTTP 401).
 */
public class AssessmentUnauthorizedException extends AssessmentException {

    public AssessmentUnauthorizedException(String message) {
        super(401, "ACD_ASSESSMENT_UNAUTHORIZED", message);
    }
}
