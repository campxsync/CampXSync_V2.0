package com.campx.admissions.exception;

import java.util.Collections;
import java.util.Map;

/**
 * Base abstract exception class for all CampXSync Admissions Domain Service (STM-02) exceptions.
 * <p>
 * Enforces standardized HTTP status codes, machine-readable error codes, correlation tracing,
 * and optional diagnostic parameter metadata across all application exception scenarios.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public abstract class AdmissionsException extends RuntimeException {

    private final int httpStatus;
    private final String errorCode;
    private final String correlationId;
    private final Map<String, Object> errorDetails;

    /**
     * Constructs a new admissions exception with message and status.
     *
     * @param message    Human-readable description of the error.
     * @param httpStatus Corresponding HTTP status code.
     * @param errorCode  Machine-readable stable error code identifier.
     */
    public AdmissionsException(String message, int httpStatus, String errorCode) {
        this(message, httpStatus, errorCode, null, null, null);
    }

    /**
     * Constructs a new admissions exception with cause.
     *
     * @param message    Human-readable description of the error.
     * @param httpStatus Corresponding HTTP status code.
     * @param errorCode  Machine-readable stable error code identifier.
     * @param cause      Root throwable causing this exception.
     */
    public AdmissionsException(String message, int httpStatus, String errorCode, Throwable cause) {
        this(message, httpStatus, errorCode, null, null, cause);
    }

    /**
     * Constructs a fully-specified admissions exception.
     *
     * @param message       Human-readable description of the error.
     * @param httpStatus    Corresponding HTTP status code.
     * @param errorCode     Machine-readable stable error code identifier.
     * @param correlationId Distributed correlation ID for request tracing.
     * @param errorDetails  Structured diagnostic parameters.
     * @param cause         Root throwable causing this exception.
     */
    public AdmissionsException(String message, int httpStatus, String errorCode, String correlationId, Map<String, Object> errorDetails, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode != null ? errorCode : "INTERNAL_ERROR";
        this.correlationId = correlationId;
        this.errorDetails = errorDetails != null ? Collections.unmodifiableMap(errorDetails) : Collections.emptyMap();
    }

    public int getHttpStatus() { return httpStatus; }
    public String getErrorCode() { return errorCode; }
    public String getCorrelationId() { return correlationId; }
    public Map<String, Object> getErrorDetails() { return errorDetails; }
}
