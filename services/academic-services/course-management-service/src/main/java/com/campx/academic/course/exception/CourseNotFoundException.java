package com.campx.academic.course.exception;

/**
 * Thrown when a requested course, version, or prerequisite cannot be found.
 */
public class CourseNotFoundException extends CourseException {

    /**
     * Constructs a new {@code CourseNotFoundException} with an explicit message.
     *
     * @param message the missing resource description
     */
    public CourseNotFoundException(String message) {
        super(404, "ACD_NOT_FOUND", message);
    }

    /**
     * Constructs a new {@code CourseNotFoundException} indicating the missing resource type and identifier.
     *
     * @param resourceType the category of resource
     * @param id           the unresolved identifier
     */
    public CourseNotFoundException(String resourceType, String id) {
        super(404, "ACD_NOT_FOUND", resourceType + " not found with identifier: " + id);
    }
}
