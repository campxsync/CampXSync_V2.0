package com.campx.academic.curriculum.exception;

/**
 * Thrown when an external dependency (such as ACD-01, ACD-03, or messaging broker) is unavailable (HTTP 503).
 */
public class DependencyUnavailableException extends CurriculumException {

    public static final String ERROR_CODE = "ACD2_DEPENDENCY_UNAVAILABLE";

    public DependencyUnavailableException(String message) {
        super(503, ERROR_CODE, message);
    }
}
