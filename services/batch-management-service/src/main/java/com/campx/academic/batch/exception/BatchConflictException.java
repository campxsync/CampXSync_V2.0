package com.campx.academic.batch.exception;

/**
 * Thrown when a state conflict occurs such as duplicate batch code, duplicate student assignment,
 * stale optimistic concurrency version, or invalid state transition.
 */
public class BatchConflictException extends BatchException {

    public BatchConflictException(String errorCode, String message) {
        super(errorCode, message, 409);
    }

    public BatchConflictException(String message) {
        super("ACD_BATCH_CONFLICT", message, 409);
    }
}
