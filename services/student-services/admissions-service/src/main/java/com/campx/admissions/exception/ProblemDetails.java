package com.campx.admissions.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;

/**
 * Standard RFC 7807 Problem Details for HTTP APIs.
 * <p>
 * Provides structured, machine-readable, and sanitized representations of error conditions
 * returned by the CampXSync Admissions Domain Service (STM-02). Ensures internal stack traces
 * and database error details are never leaked to external callers.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetails {

    private String type;
    private String title;
    private int status;
    private String detail;
    private String instance;
    private String errorCode;
    private String correlationId;
    private String timestamp;
    private Map<String, Object> invalidParams;

    public ProblemDetails() {
        this.timestamp = OffsetDateTime.now().toString();
    }

    public ProblemDetails(String type, String title, int status, String detail, String instance, String errorCode, String correlationId) {
        this.type = type != null ? type : "about:blank";
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
        this.errorCode = errorCode;
        this.correlationId = correlationId;
        this.timestamp = OffsetDateTime.now().toString();
    }

    public static Builder builder() {
        return new Builder();
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
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public Map<String, Object> getInvalidParams() { return invalidParams; }
    public void setInvalidParams(Map<String, Object> invalidParams) { this.invalidParams = invalidParams; }

    public static class Builder {
        private String type = "about:blank";
        private String title;
        private int status = 500;
        private String detail;
        private String instance;
        private String errorCode = "INTERNAL_SERVER_ERROR";
        private String correlationId;
        private Map<String, Object> invalidParams;

        public Builder type(String type) { this.type = type; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder status(int status) { this.status = status; return this; }
        public Builder detail(String detail) { this.detail = detail; return this; }
        public Builder instance(String instance) { this.instance = instance; return this; }
        public Builder errorCode(String errorCode) { this.errorCode = errorCode; return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder invalidParams(Map<String, Object> invalidParams) { this.invalidParams = invalidParams; return this; }

        public ProblemDetails build() {
            ProblemDetails pd = new ProblemDetails(type, title, status, detail, instance, errorCode, correlationId);
            pd.setInvalidParams(invalidParams);
            return pd;
        }
    }
}
