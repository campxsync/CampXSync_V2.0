package com.campx.academic.curriculum.exception;

/**
 * Thrown when a referenced courseId fails validation against ACD-01 (HTTP 422).
 */
public class InvalidCourseException extends CurriculumException {

    public static final String ERROR_CODE = "ACD2_COURSE_INVALID";

    public InvalidCourseException(String message) {
        super(422, ERROR_CODE, message);
    }
}
