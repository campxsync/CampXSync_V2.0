package com.campx.academic.assessment.exception;

/**
 * Thrown when publication of an assessment is forbidden due to unapproved status,
 * missing components, incomplete weightage, or pending approvals (HTTP 400).
 */
public class AssessmentPublishForbiddenException extends AssessmentException {

    public AssessmentPublishForbiddenException(String message) {
        super(400, "ACD_ASSESSMENT_PUBLISH_FORBIDDEN", message);
    }
}
