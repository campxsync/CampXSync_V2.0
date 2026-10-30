package com.campx.academic.course.exception;

/**
 * Thrown when an illegal lifecycle transition is attempted on a course (HTTP 422 Unprocessable Entity).
 */
public class InvalidCourseStateException extends CourseException {

    /**
     * Constructs a new {@code InvalidCourseStateException} with an explicit error message.
     *
     * @param message the explanation of the illegal state transition
     */
    public InvalidCourseStateException(String message) {
        super(422, "ACD_INVALID_STATE", message);
    }

    /**
     * Constructs a new {@code InvalidCourseStateException} describing the illegal state transition.
     *
     * @param currentStatus the current course lifecycle status
     * @param targetStatus  the invalid target status attempted
     */
    public InvalidCourseStateException(String currentStatus, String targetStatus) {
        super(422, "ACD_INVALID_STATE", "Illegal course lifecycle transition from [" + currentStatus + "] to [" + targetStatus + "]");
    }
}
