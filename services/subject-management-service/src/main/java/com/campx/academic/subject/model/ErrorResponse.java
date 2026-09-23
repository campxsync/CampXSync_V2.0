package com.campx.academic.subject.model;

/**
 * Standardized RFC 7807 Problem Details and CampXSync Error Response Envelope (§29.3, §53).
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

        public ErrorDetail() {}

        public ErrorDetail(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
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

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"success\":false,");
        sb.append("\"error\":{");
        sb.append("\"code\":").append(quote(error != null ? error.getCode() : "INTERNAL_ERROR")).append(",");
        sb.append("\"message\":").append(quote(error != null ? error.getMessage() : "Unknown error"));
        sb.append("},");
        sb.append("\"meta\":{");
        sb.append("\"requestId\":").append(quote(meta != null ? meta.getRequestId() : "")).append(",");
        sb.append("\"correlationId\":").append(quote(meta != null ? meta.getCorrelationId() : "")).append(",");
        sb.append("\"timestamp\":").append(meta != null ? meta.getTimestamp() : System.currentTimeMillis());
        sb.append("}");
        sb.append("}");
        return sb.toString();
    }

    private static String quote(String val) {
        if (val == null) return "\"\"";
        return "\"" + val.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
