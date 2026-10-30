package com.campx.academic.curriculum.exception;

/**
 * Thrown when adding a prerequisite relationship creates a cyclic dependency in the curriculum DAG (HTTP 422).
 */
public class PrerequisiteCycleException extends CurriculumException {

    public static final String ERROR_CODE = "ACD2_PREREQUISITE_CYCLE";

    public PrerequisiteCycleException(String message) {
        super(422, ERROR_CODE, message);
    }
}
