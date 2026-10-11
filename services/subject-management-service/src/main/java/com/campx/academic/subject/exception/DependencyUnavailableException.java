package com.campx.academic.subject.exception;

/**
 * Thrown when an external dependency (department reference service, event broker) is unavailable.
 * Returns HTTP 503 Service Unavailable per Error Handling table §53.
 */
public class DependencyUnavailableException extends SubjectException {
    public DependencyUnavailableException(String message) {
        super(503, "ACD_DEPENDENCY_UNAVAILABLE", message);
    }
}
