package com.campx.admin.college.exception;

/**
 * Thrown when an operational transition violates lifecycle integrity (HTTP 422).
 * For example: Attempting to retire a department that is still actively referenced by academic programs.
 */
public class CollegeLifecycleException extends CollegeAdminException {

    public CollegeLifecycleException(String message) {
        super(422, "ADM02_INVALID_LIFECYCLE_STATE", message);
    }
}
