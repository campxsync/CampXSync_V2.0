package com.campx.academic.assessment.exception;

/**
 * Thrown when an assessment structure identity or component code violates uniqueness (HTTP 409).
 */
public class AssessmentDuplicateException extends AssessmentException {

    public AssessmentDuplicateException(String message) {
        super(409, "ACD_ASSESSMENT_DUPLICATE_IDENTITY", message);
    }
}
