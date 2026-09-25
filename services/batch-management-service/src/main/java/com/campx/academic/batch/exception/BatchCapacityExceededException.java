package com.campx.academic.batch.exception;

/**
 * Thrown when an add-student operation would exceed configured batch capacity
 * and no valid, active capacity override exists (BR-04, Story 19).
 */
public class BatchCapacityExceededException extends BatchException {

    public BatchCapacityExceededException(String message) {
        super("ACD_BATCH_CAPACITY_EXCEEDED", message, 409);
    }
}
