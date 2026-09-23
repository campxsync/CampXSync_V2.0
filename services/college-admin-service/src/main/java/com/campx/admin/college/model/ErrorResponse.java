package com.campx.admin.college.model;

import java.nio.charset.StandardCharsets;

/**
 * Standardized RFC 7807-compliant Error Response model for College Admin Service.
 * Formats structured JSON error payloads with trace correlation.
 */
public class ErrorResponse {

    /**
     * Epoch timestamp in milliseconds indicating when the error occurred.
     */
    private final long timestamp;

    /**
     * HTTP response status code (e.g., 400, 404, 409, 422, 500).
     */
    private final int status;

    /**
     * Short HTTP status description or category (e.g., "Bad Request", "Not Found").
     */
    private final String error;

    /**
     * Machine-readable error code identifying the specific domain failure.
     */
    private final String errorCode;

    /**
     * Human-readable detail message explaining the cause of the failure.
     */
    private final String message;

    /**
     * URI request path on which the error occurred.
     */
    private final String path;

    /**
     * Distributed trace identifier for end-to-end request correlation.
     */
    private final String traceId;

    /**
     * Constructs a new {@code ErrorResponse} instance with the given error metadata.
     *
     * @param status    the HTTP status code
     * @param error     the short HTTP status description
     * @param errorCode the machine-readable domain error code
     * @param message   the descriptive error message
     * @param path      the request URI path
     * @param traceId   the active distributed trace ID
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

    /**
     * Returns the epoch timestamp in milliseconds when this error was captured.
     *
     * @return error generation timestamp
     */
    public long getTimestamp() { return timestamp; }

    /**
     * Returns the HTTP status code associated with this error response.
     *
     * @return HTTP status integer
     */
    public int getStatus() { return status; }

    /**
     * Returns the short HTTP status description or category.
     *
     * @return error description
     */
    public String getError() { return error; }

    /**
     * Returns the machine-readable error code.
     *
     * @return error code string
     */
    public String getErrorCode() { return errorCode; }

    /**
     * Returns the human-readable explanation of this error.
     *
     * @return error detail message
     */
    public String getMessage() { return message; }

    /**
     * Returns the request URI path where the error occurred.
     *
     * @return URI path
     */
    public String getPath() { return path; }

    /**
     * Returns the distributed trace identifier for end-to-end request correlation.
     *
     * @return trace identifier string
     */
    public String getTraceId() { return traceId; }

    /**
     * Serializes this error response into an RFC 7807 JSON representation.
     *
     * @return formatted JSON string
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
     * Converts the serialized JSON error payload into a UTF-8 encoded byte array.
     *
     * @return UTF-8 byte array
     */
    public byte[] toBytes() {
        return toJson().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Escapes control characters, backslashes, and quotes for safe JSON serialization.
     *
     * @param s the raw input string
     * @return sanitized string
     */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
