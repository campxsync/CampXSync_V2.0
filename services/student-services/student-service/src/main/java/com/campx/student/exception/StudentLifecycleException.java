package com.campx.student.exception;

/**
 * Thrown when attempting an illegal or prohibited lifecycle state transition.
 */
public class StudentLifecycleException extends RuntimeException {
    public StudentLifecycleException(String message) {
        super(message);
    }
}
