package com.campx.student.model;

/**
 * Ingress DTO for initiating student profile verification (STM01-US-013).
 */
public class InitiateVerificationRequest {

    private String verificationType;
    private String evidenceReference;

    public InitiateVerificationRequest() {}

    public InitiateVerificationRequest(String verificationType, String evidenceReference) {
        this.verificationType = verificationType;
        this.evidenceReference = evidenceReference;
    }

    public String getVerificationType() { return verificationType; }
    public void setVerificationType(String verificationType) { this.verificationType = verificationType; }

    public String getEvidenceReference() { return evidenceReference; }
    public void setEvidenceReference(String evidenceReference) { this.evidenceReference = evidenceReference; }
}
