package com.campx.admin.institute.exception;

/**
 * Thrown when an incoming HTTP request payload is malformed or lacks mandatory attributes, triggering HTTP 400 Bad Request.
 */
public class MalformedPayloadException extends InstituteAdminException {

    /**
     * Constructs a malformed payload exception with an explanatory validation message.
     *
     * @param message descriptive explanation of missing or malformed attributes
     */
    public MalformedPayloadException(String message) {
        super(400, "ADM01_MALFORMED_PAYLOAD", message);
    }
}
