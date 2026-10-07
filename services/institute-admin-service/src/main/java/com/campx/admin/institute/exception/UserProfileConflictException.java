package com.campx.admin.institute.exception;

/**
 * Thrown when an operation violates optimistic locking (stale row_version) or partial unique indexes
 * (duplicate email, duplicate username, duplicate person_id, or existing user profile).
 * Maps to HTTP 409 Conflict.
 */
public class UserProfileConflictException extends InstituteAdminException {

    public UserProfileConflictException(String message) {
        super(409, "ADM01_USER_CONFLICT", message);
    }

    public UserProfileConflictException(String errorCode, String message) {
        super(409, errorCode, message);
    }
}
