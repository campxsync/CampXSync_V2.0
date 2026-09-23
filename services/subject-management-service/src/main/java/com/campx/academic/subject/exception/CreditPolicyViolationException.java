package com.campx.academic.subject.exception;

/**
 * Thrown when subject credits or contact hours violate institutional policy.
 * Returns HTTP 422 Unprocessable Entity per BR-02 and BR-03.
 */
public class CreditPolicyViolationException extends SubjectException {
    public CreditPolicyViolationException(String message) {
        super(422, "ACD_CREDIT_POLICY_VIOLATION", message);
    }
}
