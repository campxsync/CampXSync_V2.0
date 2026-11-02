package com.campx.admissions.exception;

import java.util.Map;

/**
 * Thrown when an integration call to a downstream domain service (e.g. STM-01 Student Service,
 * STM-03 Enrollment Service, Payment Provider, or IAM) fails or times out.
 * <p>
 * Maps to HTTP 502 Bad Gateway with RFC 7807 problem details.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class AdmissionsBadGatewayException extends AdmissionsException {

    public AdmissionsBadGatewayException(String message) {
        super(message, 502, "BAD_GATEWAY");
    }

    public AdmissionsBadGatewayException(String message, Throwable cause) {
        super(message, 502, "BAD_GATEWAY", cause);
    }

    public AdmissionsBadGatewayException(String message, String correlationId, Throwable cause) {
        super(message, 502, "BAD_GATEWAY", correlationId, null, cause);
    }
}
