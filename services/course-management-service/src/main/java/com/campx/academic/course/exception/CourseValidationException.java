package com.campx.academic.course.exception;

/**
 * Thrown when course payload validation fails, e.g., non-positive totalCredits,
 * missing mandatory fields, or invalid department references (HTTP 400 Bad Request / 422).
 */
public class CourseValidationException extends CourseException {

    /**
     * Constructs a new {@code CourseValidationException} with the default validation error code.
     *
     * @param message the validation failure description
     */
    public CourseValidationException(String message) {
        super(400, "ACD_VALIDATION_ERROR", message);
    }

    /**
     * Constructs a new {@code CourseValidationException} with a specific domain error code and message.
     *
     * @param errorCode the machine-readable validation error code
     * @param message   the validation failure description
     */
    public CourseValidationException(String errorCode, String message) {
        super(400, errorCode, message);
    }
}
