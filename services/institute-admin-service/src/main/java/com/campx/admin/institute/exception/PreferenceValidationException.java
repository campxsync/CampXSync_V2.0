package com.campx.admin.institute.exception;

/**
 * Thrown when a user preference payload violates validation constraints,
 * unsupported schema versions, or disallowed enumeration values (HTTP 422).
 */
public class PreferenceValidationException extends InstituteAdminException {

    public PreferenceValidationException(String message) {
        super(422, "INVALID_PREFERENCE_PAYLOAD", message);
    }

    public PreferenceValidationException(String errorCode, String message) {
        super(422, errorCode, message);
    }
}
