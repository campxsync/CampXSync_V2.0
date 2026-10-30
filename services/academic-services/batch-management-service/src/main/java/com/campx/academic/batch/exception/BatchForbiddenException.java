package com.campx.academic.batch.exception;

/**
 * Thrown when an authenticated caller lacks sufficient role permissions or data clearance (HTTP 403).
 */
public class BatchForbiddenException extends BatchException {

    public BatchForbiddenException(String message) {
        super("ACD_FORBIDDEN", message, 403);
    }
}
