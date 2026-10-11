package com.campx.academic.assessment.exception;

/**
 * Thrown when the caller lacks required RBAC/ABAC role or department/subject scope (HTTP 403).
 */
public class AssessmentForbiddenException extends AssessmentException {

    public AssessmentForbiddenException(String message) {
        super(403, "ACD_ASSESSMENT_FORBIDDEN", message);
    }
}
