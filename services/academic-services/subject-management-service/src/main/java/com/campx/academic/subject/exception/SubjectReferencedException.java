package com.campx.academic.subject.exception;

/**
 * Thrown when attempting to hard delete a subject that is referenced by curriculum or has active batch offerings.
 * Returns HTTP 409 Conflict per BR-05 and Exceptions §15.
 */
public class SubjectReferencedException extends SubjectException {
    public SubjectReferencedException(String subjectId, String referenceReason) {
        super(409, "ACD_SUBJECT_REFERENCED",
                "Cannot delete subject '" + subjectId + "': it is referenced by " + referenceReason + ". Deactivate instead.");
    }
}
