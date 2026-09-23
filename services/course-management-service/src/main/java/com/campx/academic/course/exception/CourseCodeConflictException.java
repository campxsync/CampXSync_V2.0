package com.campx.academic.course.exception;

/**
 * Thrown when attempting to create or rename a course with a code that already exists
 * in the normalized scope of the tenant/institution (HTTP 409 Conflict).
 */
public class CourseCodeConflictException extends CourseException {

    /**
     * Constructs a new {@code CourseCodeConflictException} with the conflicting course code.
     *
     * @param courseCode the duplicate course code
     */
    public CourseCodeConflictException(String courseCode) {
        super(409, "ACD_COURSE_CODE_EXISTS", "Course already exists with normalized code: " + courseCode);
    }
}
