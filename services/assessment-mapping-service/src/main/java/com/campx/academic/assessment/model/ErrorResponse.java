package com.campx.academic.assessment.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Standard RFC 7807 Problem Details representation for HTTP error responses in ACD-09.
 * Supports dual serialization for "code" and "errorCode".
 */
public class ErrorResponse {

    private String type;
    private String title;
    private int status;
    private String detail;
    private String instance;
    private String errorCode;
    private String code;
    private String timestamp;
    private List<InvalidParam> invalidParams = new ArrayList<>();

    public ErrorResponse() {}

    public ErrorResponse(String type, String title, int status, String detail, String instance, String errorCode) {
        this.type = type;
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
        this.errorCode = errorCode;
        this.code = errorCode;
        this.timestamp = java.time.Instant.now().toString();
    }

    public static class InvalidParam {
        private String name;
        private String reason;

        public InvalidParam() {}

        public InvalidParam(String name, String reason) {
            this.name = name;
            this.reason = reason;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getInstance() { return instance; }
    public void setInstance(String instance) { this.instance = instance; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
        this.code = errorCode;
    }

    public String getCode() { return code; }
    public void setCode(String code) {
        this.code = code;
        this.errorCode = code;
    }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public List<InvalidParam> getInvalidParams() { return invalidParams; }
    public void setInvalidParams(List<InvalidParam> invalidParams) { this.invalidParams = invalidParams; }

    public void addInvalidParam(String name, String reason) {
        this.invalidParams.add(new InvalidParam(name, reason));
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"type\":\"").append(escape(type)).append("\",");
        sb.append("\"title\":\"").append(escape(title)).append("\",");
        sb.append("\"status\":").append(status).append(",");
        sb.append("\"detail\":\"").append(escape(detail)).append("\",");
        sb.append("\"instance\":\"").append(escape(instance)).append("\",");
        sb.append("\"errorCode\":\"").append(escape(errorCode)).append("\",");
        sb.append("\"code\":\"").append(escape(code)).append("\",");
        sb.append("\"timestamp\":\"").append(escape(timestamp)).append("\",");
        sb.append("\"invalidParams\":[");
        for (int i = 0; i < invalidParams.size(); i++) {
            InvalidParam ip = invalidParams.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(escape(ip.getName())).append("\",\"reason\":\"").append(escape(ip.getReason())).append("\"}");
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
