package com.campx.admin.institute.exception;

/**
 * Thrown when a requested user profile is not found or is invisible to the caller under RLS / tenant isolation.
 * Maps to HTTP 404 Not Found.
 */
public class UserProfileNotFoundException extends InstituteAdminException {

    public UserProfileNotFoundException(String message) {
        super(404, "ADM01_USER_NOT_FOUND", message);
    }

    public UserProfileNotFoundException(String userId, String tenantId) {
        super(404, "ADM01_USER_NOT_FOUND", "User profile not found: " + userId);
    }
}
