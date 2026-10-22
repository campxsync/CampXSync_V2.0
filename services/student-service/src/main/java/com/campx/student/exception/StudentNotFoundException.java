package com.campx.student.exception;

/**
 * Thrown when a requested student, person, or relationship cannot be found in the active tenant.
 */
public class StudentNotFoundException extends RuntimeException {
    public StudentNotFoundException(String message) {
        super(message);
    }
}
