package com.campx.academic.assessment.exception;

/**
 * Thrown when component weightage sum does not match required assessment total (HTTP 400).
 */
public class WeightageMismatchException extends AssessmentException {

    public WeightageMismatchException(String message) {
        super(400, "ACD_ASSESSMENT_WEIGHTAGE_MISMATCH", message);
    }
}
