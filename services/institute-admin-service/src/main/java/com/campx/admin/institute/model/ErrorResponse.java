package com.campx.admin.institute.model;

import java.nio.charset.StandardCharsets;

/**
 * Standardized RFC 7807-compliant Error Response model for the Institute Admin Service.
 * Formats structured JSON error payloads with trace correlation and domain error codes.
 */
public class ErrorResponse {

    private final long timestamp;
    private final int status;
    private final String error;
    private final String errorCode;
    private final String message;
    private final String path;
    private final String traceId;

    /**
     * Constructs a fully-specified error response payload.
     *
     * @param status    HTTP status code
     * @param error     standard HTTP error reason phrase
     * @param errorCode application-specific error code (e.g. "RESOURCE_NOT_FOUND")
     * @param message   human-readable description of the error condition
     * @param path      request URI that triggered the failure
     * @param traceId   distributed correlation identifier for distributed tracing
     */
    public ErrorResponse(int status, String error, String errorCode, String message, String path, String traceId) {
        this.timestamp = System.currentTimeMillis();
        this.status = status;
        this.error = error != null ? error : "Error";
        this.errorCode = errorCode != null ? errorCode : "GENERAL_ERROR";
        this.message = message != null ? message : "";
        this.path = path != null ? path : "";
        this.traceId = traceId != null ? traceId : "";
    }

    public long getTimestamp() { return timestamp; }
    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getErrorCode() { return errorCode; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
    public String getTraceId() { return traceId; }

    /**
     * Serializes this error response object into a valid RFC 8259 JSON string representation.
     *
     * @return JSON formatted error string
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

    /**
     * Serializes this error response into UTF-8 encoded byte array for direct HTTP streaming.
     *
     * @return byte array representation of the JSON string
     */
    public byte[] toBytes() {
        return toJson().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Escapes JSON special characters to guarantee valid serialized JSON.
     *
     * @param s raw string input
     * @return escaped string
     */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
