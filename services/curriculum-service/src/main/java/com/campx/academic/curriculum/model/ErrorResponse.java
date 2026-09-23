package com.campx.academic.curriculum.model;

import java.nio.charset.StandardCharsets;

/**
 * Standardized RFC 7807 and enterprise envelope-compliant Error Response for ACD-02 Curriculum Management Service.
 * Satisfies both RFC 7807 clients (API Gateway) and §29.4 Error Response contract.
 */
public class ErrorResponse {

    private final long timestamp;
    private final int status;
    private final String error;
    private final String errorCode;
    private final String message;
    private final String path;
    private final String traceId;

    public ErrorResponse(int status, String error, String errorCode, String message, String path, String traceId) {
        this.timestamp = System.currentTimeMillis();
        this.status = status;
        this.error = error != null ? error : "Error";
        this.errorCode = errorCode != null ? errorCode : "ACD2_ERROR";
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
     * Serializes this error response into combined RFC 7807 and Standard Error Envelope JSON.
     */
    public String toJson() {
        return "{"
                + "\"success\":false,"
                + "\"timestamp\":" + timestamp + ","
                + "\"status\":" + status + ","
                + "\"error\":{"
                + "\"code\":\"" + escape(errorCode) + "\","
                + "\"message\":\"" + escape(message) + "\""
                + "},"
                + "\"errorCode\":\"" + escape(errorCode) + "\","
                + "\"errorReason\":\"" + escape(error) + "\","
                + "\"message\":\"" + escape(message) + "\","
                + "\"path\":\"" + escape(path) + "\","
                + "\"traceId\":\"" + escape(traceId) + "\","
                + "\"meta\":{"
                + "\"requestId\":\"REQ-" + escape(traceId) + "\","
                + "\"correlationId\":\"" + escape(traceId) + "\","
                + "\"timestamp\":\"" + timestamp + "\""
                + "}"
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
