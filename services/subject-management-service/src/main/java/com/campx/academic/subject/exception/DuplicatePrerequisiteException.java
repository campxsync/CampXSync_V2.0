package com.campx.academic.subject.exception;

/**
 * Thrown when an active prerequisite relationship between the same subject pair already exists.
 * Returns HTTP 409 Conflict per Integrity/Consistency Rules §39.
 */
public class DuplicatePrerequisiteException extends SubjectException {
    public DuplicatePrerequisiteException(String subjectId, String prerequisiteSubjectId) {
        super(409, "ACD_DUPLICATE_PREREQUISITE",
                "An active prerequisite relationship already exists between subject '" + subjectId + "' and '" + prerequisiteSubjectId + "'.");
    }
}
