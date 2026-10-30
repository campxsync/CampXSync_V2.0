package com.campx.academic.subject.exception;

/**
 * Thrown when subjectType or classification violates configured taxonomy.
 * Returns HTTP 422 Unprocessable Entity per BR-04 and Exceptions §15.
 */
public class InvalidTaxonomyException extends SubjectException {
    public InvalidTaxonomyException(String message) {
        super(422, "ACD_INVALID_TAXONOMY", message);
    }
}
