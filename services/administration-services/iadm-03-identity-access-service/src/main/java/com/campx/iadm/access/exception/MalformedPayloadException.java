package com.campx.iadm.access.exception;

/**
 * Thrown when an incoming HTTP request payload is malformed or lacks mandatory attributes.
 */
public class MalformedPayloadException extends InstituteAdminException {

    public MalformedPayloadException(String message) {
        super(400, "IADM03_MALFORMED_PAYLOAD", message);
    }
}
