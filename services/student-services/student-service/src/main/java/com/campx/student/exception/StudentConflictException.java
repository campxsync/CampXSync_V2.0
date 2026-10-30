package com.campx.student.exception;

/**
 * Thrown when attempting to register a duplicate enrollment number or primary email in the tenant.
 */
public class StudentConflictException extends RuntimeException {
    public StudentConflictException(String message) {
        super(message);
    }
}
