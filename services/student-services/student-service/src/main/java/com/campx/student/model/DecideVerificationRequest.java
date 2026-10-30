package com.campx.student.model;

import java.time.LocalDate;

/**
 * Ingress DTO for approving or rejecting verification (STM01-US-014, STM01-US-047).
 */
public class DecideVerificationRequest {

    private String status; // 'VERIFIED', 'REJECTED', 'CORRECTION_REQUIRED'
    private String reason;
    private String evidenceReference;
    private String validTo; // Optional ISO-8601 date string for expiry

    public DecideVerificationRequest() {}

    public DecideVerificationRequest(String status, String reason) {
        this.status = status;
        this.reason = reason;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getEvidenceReference() { return evidenceReference; }
    public void setEvidenceReference(String evidenceReference) { this.evidenceReference = evidenceReference; }

    public String getValidTo() { return validTo; }
    public void setValidTo(String validTo) { this.validTo = validTo; }

    public LocalDate getParsedValidTo() {
        if (validTo != null && !validTo.trim().isEmpty()) {
            try {
                return LocalDate.parse(validTo.trim());
            } catch (Exception ignored) {}
        }
        return null;
    }
}
