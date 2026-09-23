package com.campx.admin.college.exception;

/**
 * Thrown when governance document registration or approval violates compliance rules (HTTP 400).
 */
public class DocumentGovernanceException extends CollegeAdminException {

    /**
     * Constructs a new {@code DocumentGovernanceException} with the specified compliance violation message.
     *
     * @param message the explanation of the document governance violation
     */
    public DocumentGovernanceException(String message) {
        super(400, "ADM02_DOCUMENT_GOVERNANCE_ERROR", message);
    }
}
