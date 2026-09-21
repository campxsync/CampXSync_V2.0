package com.campx.gateway.model;

import java.nio.charset.StandardCharsets;

/**
 * Standardized RFC 7807-compliant Error Response model for CampXSync API Gateway.
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
        this.errorCode = errorCode != null ? errorCode : "GATEWAY_ERROR";
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
