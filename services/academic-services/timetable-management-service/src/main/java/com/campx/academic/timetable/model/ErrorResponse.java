package com.campx.academic.timetable.model;

/**
 * Standardized RFC 7807 Problem Details and CampXSync Error Response Envelope (§29.3, §54).
 */
public class ErrorResponse {

    private boolean success = false;
    private ErrorDetail error;
    private Meta meta;

    public ErrorResponse() {
        this.meta = new Meta();
    }

    public ErrorResponse(String code, String message, String requestId, String correlationId) {
        this.success = false;
        this.error = new ErrorDetail(code, message);
        this.meta = new Meta(requestId, correlationId, System.currentTimeMillis());
    }

    public ErrorResponse(String code, String message, Object details, String requestId, String correlationId) {
        this.success = false;
        this.error = new ErrorDetail(code, message, details);
        this.meta = new Meta(requestId, correlationId, System.currentTimeMillis());
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public ErrorDetail getError() {
        return error;
    }

    public void setError(ErrorDetail error) {
        this.error = error;
    }

    public Meta getMeta() {
        return meta;
    }

    public void setMeta(Meta meta) {
        this.meta = meta;
    }

    public static class ErrorDetail {
        private String code;
        private String message;
        private Object details;

        public ErrorDetail() {}

        public ErrorDetail(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public ErrorDetail(String code, String message, Object details) {
            this.code = code;
            this.message = message;
            this.details = details;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getErrorCode() {
            return code;
        }

        public void setErrorCode(String errorCode) {
            this.code = errorCode;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Object getDetails() {
            return details;
        }

        public void setDetails(Object details) {
            this.details = details;
        }
    }

    public static class Meta {
        private String requestId;
        private String correlationId;
        private long timestamp;

        public Meta() {
            this.timestamp = System.currentTimeMillis();
        }

        public Meta(String requestId, String correlationId, long timestamp) {
            this.requestId = requestId;
            this.correlationId = correlationId;
            this.timestamp = timestamp;
        }

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        public String getCorrelationId() {
            return correlationId;
        }

        public void setCorrelationId(String correlationId) {
            this.correlationId = correlationId;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }
}
