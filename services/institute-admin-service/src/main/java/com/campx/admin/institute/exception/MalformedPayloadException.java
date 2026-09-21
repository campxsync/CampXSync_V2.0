package com.campx.admin.institute.exception;

/**
 * Thrown when an incoming HTTP payload is malformed or lacks mandatory attributes (HTTP 400).
 */
public class MalformedPayloadException extends InstituteAdminException {

    public MalformedPayloadException(String message) {
        super(400, "ADM01_MALFORMED_PAYLOAD", message);
    }
}
