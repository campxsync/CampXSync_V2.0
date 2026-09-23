package com.campx.gateway.model;

import java.nio.charset.StandardCharsets;

/**
 * Standardized RFC 7807-compliant Error Response model for CampXSync API Gateway.
 * <p>
 * Provides a consistent JSON envelope for HTTP error representations when a client
 * request fails routing, upstream microservice connectivity, or parameter validation.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class ErrorResponse {

    /**
     * Epoch timestamp in milliseconds when the error occurred.
     */
    private final long timestamp;

    /**
     * HTTP status code (e.g., 400, 404, 502, 504).
     */
    private final int status;

    /**
     * Short HTTP error title (e.g., "Bad Gateway", "Not Found").
     */
    private final String error;

    /**
     * Machine-readable domain error code (e.g., "GW_DOWNSTREAM_UNAVAILABLE").
     */
    private final String errorCode;

    /**
     * Human-readable diagnostic description of the failure.
     */
    private final String message;

    /**
     * Requested URL path where the error triggered.
     */
    private final String path;

    /**
     * Distributed correlation trace identifier for log correlation.
     */
    private final String traceId;

    /**
     * Constructs a new ErrorResponse instance.
     *
     * @param status    The HTTP response status code.
     * @param error     The short HTTP error title.
     * @param errorCode The machine-readable error code.
     * @param message   The descriptive error message.
     * @param path      The requested request URI path.
     * @param traceId   The correlation trace ID.
     */
    public ErrorResponse(int status, String error, String errorCode, String message, String path, String traceId) {
        this.timestamp = System.currentTimeMillis();
        this.status = status;
        this.error = error != null ? error : "Error";
        this.errorCode = errorCode != null ? errorCode : "GATEWAY_ERROR";
        this.message = message != null ? message : "";
        this.path = path != null ? path : "";
        this.traceId = traceId != null ? traceId : "";
    }

    /**
     * @return The epoch timestamp in milliseconds.
     */
    public long getTimestamp() { return timestamp; }

    /**
     * @return The HTTP status code.
     */
    public int getStatus() { return status; }

    /**
     * @return The short HTTP error title.
     */
    public String getError() { return error; }

    /**
     * @return The machine-readable error code.
     */
    public String getErrorCode() { return errorCode; }

    /**
     * @return The descriptive error message.
     */
    public String getMessage() { return message; }

    /**
     * @return The request URI path.
     */
    public String getPath() { return path; }

    /**
     * @return The correlation trace ID.
     */
    public String getTraceId() { return traceId; }

    /**
     * Serializes this error response object into a formatted JSON string.
     *
     * @return JSON representation of this error response.
     */
    public String toJson() {
        return "{"
                + "\"timestamp\":" + timestamp + ","
                + "\"status\":" + status + ","
                + "\"error\":\"" + escape(error) + "\","
                + "\"errorCode\":\"" + escape(errorCode) + "\","
                + "\"message\":\"" + escape(message) + "\","
                + "\"path\":\"" + escape(path) + "\","
                + "\"traceId\":\"" + escape(traceId) + "\""
                + "}";
    }

    public byte[] toBytes() {
        return toJson().getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
