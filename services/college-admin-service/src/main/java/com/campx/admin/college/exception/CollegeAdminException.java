package com.campx.admin.college.exception;

/**
 * Base runtime exception for ADM-02 College Admin Service.
 * Associates an HTTP status code and a semantic machine-readable error code.
 */
public class CollegeAdminException extends RuntimeException {

    /**
     * HTTP response status code corresponding to this domain exception.
     */
    private final int status;

    /**
     * Machine-readable error code classifying the error type.
     */
    private final String errorCode;

    /**
     * Constructs a new {@code CollegeAdminException} with HTTP status, error code, and detail message.
     *
     * @param status    the HTTP status code (e.g., 400, 404, 409, 422)
     * @param errorCode the machine-readable error code
     * @param message   the descriptive failure message
     */
    public CollegeAdminException(int status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    /**
     * Constructs a new {@code CollegeAdminException} with status, error code, detail message, and causal throwable.
     *
     * @param status    the HTTP status code
     * @param errorCode the machine-readable error code
     * @param message   the descriptive failure message
     * @param cause     the underlying cause
     */
    public CollegeAdminException(int status, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    /**
     * Returns the HTTP status code associated with this exception.
     *
     * @return HTTP status code
     */
    public int getStatus() {
        return status;
    }

    /**
     * Returns the machine-readable error code associated with this exception.
     *
     * @return error code string
     */
    public String getErrorCode() {
        return errorCode;
    }
}
