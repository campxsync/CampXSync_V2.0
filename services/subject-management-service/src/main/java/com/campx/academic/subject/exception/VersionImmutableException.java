package com.campx.academic.subject.exception;

/**
 * Thrown when attempting to mutate an already published subject version.
 * Returns HTTP 409 Conflict per BR-06 and Exceptions §15.
 */
public class VersionImmutableException extends SubjectException {
    public VersionImmutableException(String subjectId, int versionNo) {
        super(409, "ACD_VERSION_IMMUTABLE",
                "Subject version " + versionNo + " of subject '" + subjectId + "' is published and immutable. Create a new version for revisions.");
    }
}
