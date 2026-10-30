package com.campx.student.model;

import java.util.UUID;

/**
 * Ingress DTO for submitting a student profile correction request (STM01-US-010).
 */
public class SubmitCorrectionRequest {

    private String targetField;
    private String proposedValue;
    private String reason;
    private UUID evidenceDocumentId;

    public SubmitCorrectionRequest() {}

    public String getTargetField() { return targetField; }
    public void setTargetField(String targetField) { this.targetField = targetField; }

    public String getProposedValue() { return proposedValue; }
    public void setProposedValue(String proposedValue) { this.proposedValue = proposedValue; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public UUID getEvidenceDocumentId() { return evidenceDocumentId; }
    public void setEvidenceDocumentId(UUID evidenceDocumentId) { this.evidenceDocumentId = evidenceDocumentId; }
}
