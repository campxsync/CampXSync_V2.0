package com.campx.academic.curriculum.exception;

/**
 * Thrown when curriculum aggregated credits violate institutional academic credit policy (HTTP 422).
 */
public class CreditPolicyViolationException extends CurriculumException {

    public static final String ERROR_CODE = "ACD2_CREDIT_POLICY";

    public CreditPolicyViolationException(String message) {
        super(422, ERROR_CODE, message);
    }
}
