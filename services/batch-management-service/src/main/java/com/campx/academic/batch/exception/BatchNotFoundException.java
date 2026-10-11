package com.campx.academic.batch.exception;

/**
 * Thrown when a requested batch, section, roster membership, or override cannot be found.
 */
public class BatchNotFoundException extends BatchException {

    public BatchNotFoundException(String message) {
        super("ACD_BATCH_NOT_FOUND", message, 404);
    }

    public BatchNotFoundException(String errorCode, String message) {
        super(errorCode, message, 404);
    }
}
