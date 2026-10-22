package com.campx.student.exception;

/**
 * Thrown when tenant boundary, authorization, or role scopes are violated.
 */
public class StudentSecurityViolationException extends RuntimeException {
    public StudentSecurityViolationException(String message) {
        super(message);
    }
}
