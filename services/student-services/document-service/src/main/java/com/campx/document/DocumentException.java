package com.campx.document;

/**
 * Domain exception mapped to RFC 7807 responses.
 */
public class DocumentException extends RuntimeException {

    private final int statusCode;
    private final String code;

    public DocumentException(int statusCode, String code, String message) {
        super(message);
        this.statusCode = statusCode;
        this.code = code;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getCode() {
        return code;
    }
}

