package com.campx.admin.college.exception;

/**
 * Thrown when an incoming HTTP payload in College Admin Service lacks mandatory fields or is malformed (HTTP 400).
 */
public class CollegeMalformedPayloadException extends CollegeAdminException {

    public CollegeMalformedPayloadException(String message) {
        super(400, "ADM02_MALFORMED_PAYLOAD", message);
    }
}
