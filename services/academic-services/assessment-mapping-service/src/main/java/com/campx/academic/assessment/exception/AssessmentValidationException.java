package com.campx.academic.assessment.exception;

/**
 * Thrown when an assessment structure, component, or parameter fails validation (HTTP 400).
 */
public class AssessmentValidationException extends AssessmentException {

    public AssessmentValidationException(String message) {
        super(400, "ACD_ASSESSMENT_VALIDATION_FAILED", message);
    }
}
