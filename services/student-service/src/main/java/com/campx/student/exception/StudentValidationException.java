package com.campx.student.exception;

/**
 * Thrown when client input fails syntactic or business validation rules.
 */
public class StudentValidationException extends RuntimeException {
    public StudentValidationException(String message) {
        super(message);
    }
}
