package com.campx.academic.batch.exception;

/**
 * Thrown when business validation fails, such as inactive course, invalid semester,
 * student ineligibility, missing faculty assignment, or malformed data attributes (BR-01, BR-05, Story 69).
 */
public class BatchValidationException extends BatchException {

    public BatchValidationException(String message) {
        super("ACD_BATCH_VALIDATION_ERROR", message, 422);
    }

    public BatchValidationException(String errorCode, String message) {
        super(errorCode, message, 422);
    }
}
