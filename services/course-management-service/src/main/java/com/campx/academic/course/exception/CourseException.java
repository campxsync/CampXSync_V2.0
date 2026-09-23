package com.campx.academic.course.exception;

/**
 * Base runtime exception for ACD-01 Course Management Service.
 * Carries an HTTP status code and an enterprise machine-readable error code.
 */
public class CourseException extends RuntimeException {

    /**
     * HTTP response status code associated with this course exception.
     */
    private final int status;

    /**
     * Machine-readable error code classifying the domain failure.
     */
    private final String errorCode;

    /**
     * Constructs a new {@code CourseException} with status, error code, and message.
     *
     * @param status    the HTTP status code
     * @param errorCode the machine-readable error code
     * @param message   the descriptive error message
     */
    public CourseException(int status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    /**
     * Constructs a new {@code CourseException} with status, error code, message, and cause.
     *
     * @param status    the HTTP status code
     * @param errorCode the machine-readable error code
     * @param message   the descriptive error message
     * @param cause     the underlying causal throwable
     */
    public CourseException(int status, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    /**
     * Returns the HTTP status code.
     *
     * @return HTTP status integer
     */
    public int getStatus() {
        return status;
    }

    /**
     * Returns the machine-readable error code.
     *
     * @return error code string
     */
    public String getErrorCode() {
        return errorCode;
    }
}
