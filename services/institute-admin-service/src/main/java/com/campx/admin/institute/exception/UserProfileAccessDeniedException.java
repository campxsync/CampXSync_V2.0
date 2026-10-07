package com.campx.admin.institute.exception;

/**
 * Thrown when the authenticated caller lacks required permission (e.g. adm08.view.all or adm08.write)
 * or attempts an unauthorized action outside granted scope.
 * Maps to HTTP 403 Forbidden.
 */
public class UserProfileAccessDeniedException extends InstituteAdminException {

    public UserProfileAccessDeniedException(String message) {
        super(403, "ADM01_ACCESS_DENIED", message);
    }

    public UserProfileAccessDeniedException(String errorCode, String message) {
        super(403, errorCode, message);
    }
}
