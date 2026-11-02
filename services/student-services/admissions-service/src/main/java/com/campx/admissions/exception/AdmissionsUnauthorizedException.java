package com.campx.admissions.exception;

import java.util.Map;

/**
 * Thrown when an incoming request fails cryptographic authentication, gateway signature verification,
 * or lacks valid credentials.
 * <p>
 * Maps to HTTP 401 Unauthorized with RFC 7807 problem details.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class AdmissionsUnauthorizedException extends AdmissionsException {

    public AdmissionsUnauthorizedException(String message) {
        super(message, 401, "UNAUTHORIZED");
    }

    public AdmissionsUnauthorizedException(String message, String correlationId) {
        super(message, 401, "UNAUTHORIZED", correlationId, null, null);
    }

    public AdmissionsUnauthorizedException(String message, String correlationId, Map<String, Object> errorDetails) {
        super(message, 401, "UNAUTHORIZED", correlationId, errorDetails, null);
    }
}
