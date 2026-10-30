package com.campx.academic.assessment.exception;

/**
 * Thrown when an assessment structure, component, or mapping is not found (HTTP 404).
 */
public class AssessmentNotFoundException extends AssessmentException {

    public AssessmentNotFoundException(String message) {
        super(404, "ACD_ASSESSMENT_NOT_FOUND", message);
    }
}
