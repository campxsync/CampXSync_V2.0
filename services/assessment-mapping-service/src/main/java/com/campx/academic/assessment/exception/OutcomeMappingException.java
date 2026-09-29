package com.campx.academic.assessment.exception;

/**
 * Thrown when an outcome mapping fails domain validation, level constraints,
 * duplicate relationships, or weight limits (HTTP 400).
 */
public class OutcomeMappingException extends AssessmentException {

    public OutcomeMappingException(String message) {
        super(400, "ACD_ASSESSMENT_OUTCOME_MAPPING_INVALID", message);
    }
}
