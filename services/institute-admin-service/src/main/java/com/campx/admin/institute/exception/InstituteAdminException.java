package com.campx.admin.institute.exception;

/**
 * Base runtime exception for the ADM-01 Institute Admin Service.
 * <p>
 * Associates an HTTP response status code and a semantic machine-readable error code
 * formatted for RFC 7807 problem details responses.
 */
public class InstituteAdminException extends RuntimeException {

    private final int status;
    private final String errorCode;

    /**
     * Constructs an instance with specified HTTP status, application error code, and message.
     *
     * @param status    HTTP status code (e.g. 400, 404, 409)
     * @param errorCode semantic machine-readable error code
     * @param message   human-readable explanation of the error
     */
    public InstituteAdminException(int status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    /**
     * Constructs an instance with specified HTTP status, error code, message, and root cause.
     *
     * @param status    HTTP status code
     * @param errorCode semantic machine-readable error code
     * @param message   human-readable explanation of the error
     * @param cause     root cause throwable
     */
    public InstituteAdminException(int status, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    /**
     * Returns the HTTP status code mapped to this exception.
     *
     * @return HTTP status integer
     */
    public int getStatus() {
        return status;
    }

    /**
     * Returns the semantic application error code.
     *
     * @return error code string
     */
    public String getErrorCode() {
        return errorCode;
    }
}
