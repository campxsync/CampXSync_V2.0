package com.campx.academic.batch.exception;

/**
 * Thrown when an external upstream dependency (ACD-01 Course, STM Student, Event Broker) is unreachable (HTTP 503).
 */
public class DependencyUnavailableException extends BatchException {

    public DependencyUnavailableException(String message) {
        super("ACD_DEPENDENCY_UNAVAILABLE", message, 503);
    }
}
