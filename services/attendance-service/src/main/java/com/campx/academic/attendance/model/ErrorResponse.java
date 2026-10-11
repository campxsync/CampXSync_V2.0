package com.campx.academic.attendance.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * RFC 7807 problem details error response representation.
 * Serializes both "code" and "errorCode" for strict backward and forward compatibility.
 */
public class ErrorResponse {

    private String code;
    private String errorCode;
    private String title;
    private String detail;
    private int status;
    private String instance;
    private String timestamp;
    private List<String> details;

    public ErrorResponse() {
        this.timestamp = Instant.now().toString();
        this.details = Collections.emptyList();
    }

    public ErrorResponse(String errorCode, String detail, int status, String instance, List<String> details) {
        this.code = errorCode;
        this.errorCode = errorCode;
        this.title = errorCode;
        this.detail = detail;
        this.status = status;
        this.instance = instance;
        this.timestamp = Instant.now().toString();
        this.details = details != null ? details : Collections.emptyList();
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
        this.code = errorCode;
        this.title = errorCode;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getInstance() { return instance; }
    public void setInstance(String instance) { this.instance = instance; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public List<String> getDetails() { return details; }
    public void setDetails(List<String> details) { this.details = details; }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"code\":\"").append(escape(code)).append("\",");
        sb.append("\"errorCode\":\"").append(escape(errorCode)).append("\",");
        sb.append("\"title\":\"").append(escape(title)).append("\",");
        sb.append("\"detail\":\"").append(escape(detail)).append("\",");
        sb.append("\"status\":").append(status).append(",");
        sb.append("\"instance\":\"").append(escape(instance)).append("\",");
        sb.append("\"timestamp\":\"").append(escape(timestamp)).append("\",");
        sb.append("\"details\":[");
        if (details != null) {
            for (int i = 0; i < details.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escape(details.get(i))).append("\"");
            }
        }
        sb.append("]}");
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
