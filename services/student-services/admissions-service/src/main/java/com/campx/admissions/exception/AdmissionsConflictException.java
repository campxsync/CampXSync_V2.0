package com.campx.admissions.exception;

import java.util.Map;

/**
 * Thrown when an operation conflicts with the current state of a resource, such as
 * duplicate application submissions, optimistic concurrency lock conflicts (If-Match version mismatch),
 * exhausted intake cohort capacity, or illegal concurrent modifications.
 * <p>
 * Maps to HTTP 409 Conflict with RFC 7807 problem details.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class AdmissionsConflictException extends AdmissionsException {

    public AdmissionsConflictException(String message) {
        super(message, 409, "CONFLICT");
    }

    public AdmissionsConflictException(String message, String correlationId) {
        super(message, 409, "CONFLICT", correlationId, null, null);
    }

    public AdmissionsConflictException(String message, String correlationId, Map<String, Object> errorDetails) {
        super(message, 409, "CONFLICT", correlationId, errorDetails, null);
    }
}
