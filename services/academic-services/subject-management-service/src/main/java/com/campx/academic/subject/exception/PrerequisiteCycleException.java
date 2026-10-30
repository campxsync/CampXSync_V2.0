package com.campx.academic.subject.exception;

/**
 * Thrown when adding a prerequisite edge would create a cycle in the prerequisite DAG.
 * Returns HTTP 409 Conflict per Key Business Rules panel 12 and Failure Handling panel 13.
 */
public class PrerequisiteCycleException extends SubjectException {
    public PrerequisiteCycleException(String subjectId, String prerequisiteSubjectId) {
        super(409, "ACD_PREREQUISITE_CYCLE",
                "Circular prerequisite dependency detected between subject '" + subjectId + "' and prerequisite '" + prerequisiteSubjectId + "'.");
    }
}
