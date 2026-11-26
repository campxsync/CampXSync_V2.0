package com.campx.iadm.ops.exception;

/**
 * Thrown when the authenticated caller lacks required permission.
 * Maps to HTTP 403 Forbidden.
 */
public class UserProfileAccessDeniedException extends InstituteAdminException {

    public UserProfileAccessDeniedException(String message) {
        super(403, "Iadm08_ACCESS_DENIED", message);
    }

    public UserProfileAccessDeniedException(String errorCode, String message) {
        super(403, errorCode, message);
    }
}
