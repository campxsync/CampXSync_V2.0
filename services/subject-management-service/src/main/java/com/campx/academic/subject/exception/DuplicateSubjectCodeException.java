package com.campx.academic.subject.exception;

/**
 * Thrown when attempting to register or update a subject with a subjectCode that already exists within tenant + institution.
 * Returns HTTP 409 Conflict per BR-01 and §29.3.
 */
public class DuplicateSubjectCodeException extends SubjectException {
    public DuplicateSubjectCodeException(String subjectCode, String institutionId) {
        super(409, "ACD_SUBJECT_CODE_DUPLICATE",
                "Subject code '" + subjectCode + "' already exists within institution '" + institutionId + "'.");
    }
}
