package com.campx.academic.batch.exception;

/**
 * Thrown when an authentication token or API key is missing, invalid, or expired (HTTP 401).
 */
public class BatchUnauthorizedException extends BatchException {

    public BatchUnauthorizedException(String message) {
        super("ACD_UNAUTHORIZED", message, 401);
    }
}
