package com.campx.academic.analytics.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Standardized error response representation for ACD-10: Reporting & Analytics Service.
 * Complies with ACD10-US-010 and RFC 7807 problem details specification.
 */
public class ErrorResponse {

    private String code;
    private String errorCode;
    private String message;
    private String detail;
    private int status;
    private String path;
    private String correlationId;
    private String timestamp;
    private List<String> details;

    public ErrorResponse() {
        this.timestamp = Instant.now().toString();
        this.details = Collections.emptyList();
    }

    public ErrorResponse(int status, String code, String message, String correlationId, String path, List<String> details) {
        this.status = status;
        this.code = code;
        this.errorCode = code;
        this.message = message;
        this.detail = message;
        this.correlationId = correlationId;
        this.path = path;
        this.timestamp = Instant.now().toString();
        this.details = details != null ? details : Collections.emptyList();
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public List<String> getDetails() { return details; }
    public void setDetails(List<String> details) { this.details = details; }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"code\":\"").append(escape(code)).append("\",");
        sb.append("\"errorCode\":\"").append(escape(errorCode)).append("\",");
        sb.append("\"message\":\"").append(escape(message)).append("\",");
        sb.append("\"detail\":\"").append(escape(detail)).append("\",");
        sb.append("\"status\":").append(status).append(",");
        sb.append("\"path\":\"").append(escape(path)).append("\",");
        sb.append("\"correlationId\":\"").append(escape(correlationId)).append("\",");
        sb.append("\"timestamp\":\"").append(escape(timestamp)).append("\",");
        sb.append("\"error\":{");
        sb.append("\"code\":\"").append(escape(code)).append("\",");
        sb.append("\"message\":\"").append(escape(message)).append("\",");
        sb.append("\"correlationId\":\"").append(escape(correlationId)).append("\",");
        sb.append("\"details\":[");
        if (details != null) {
            for (int i = 0; i < details.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escape(details.get(i))).append("\"");
            }
        }
        sb.append("]}");
        sb.append("}");
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
