package com.campx.gateway.security;

/**
 * Exception thrown when incoming Supabase JWT validation fails at the API Gateway boundary.
 */
public class JwtValidationException extends RuntimeException {

    private final int statusCode;
    private final String error;
    private final String errorCode;

    public JwtValidationException(int statusCode, String errorCode, String message) {
        this(statusCode, "Unauthorized", errorCode, message);
    }

    public JwtValidationException(int statusCode, String error, String errorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.error = error;
        this.errorCode = errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getError() {
        return error;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
