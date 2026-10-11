package com.campx.academic.curriculum.exception;

/**
 * Thrown when curriculum publication is blocked due to missing approval, deactivated subjects,
 * credit violations, or unfulfilled invariants (HTTP 409).
 */
public class PublicationBlockedException extends CurriculumException {

    public static final String ERROR_CODE = "ACD2_PUBLICATION_BLOCKED";

    public PublicationBlockedException(String message) {
        super(409, ERROR_CODE, message);
    }
}
