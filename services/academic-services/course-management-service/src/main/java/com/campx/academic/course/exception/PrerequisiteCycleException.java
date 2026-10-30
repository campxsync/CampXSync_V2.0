package com.campx.academic.course.exception;

/**
 * Thrown when adding a prerequisite edge would create a cycle in the Directed Acyclic Graph (DAG)
 * of courses (HTTP 422 Unprocessable Entity).
 */
public class PrerequisiteCycleException extends CourseException {

    /**
     * Constructs a new {@code PrerequisiteCycleException} indicating the conflicting course and prerequisite codes.
     *
     * @param courseCode       the course code receiving the prerequisite
     * @param prerequisiteCode the prerequisite code introducing a cyclic dependency
     */
    public PrerequisiteCycleException(String courseCode, String prerequisiteCode) {
        super(422, "ACD_PREREQUISITE_CYCLE",
                "Cyclic prerequisite dependency detected between course [" + courseCode
                        + "] and proposed prerequisite [" + prerequisiteCode + "]");
    }
}
