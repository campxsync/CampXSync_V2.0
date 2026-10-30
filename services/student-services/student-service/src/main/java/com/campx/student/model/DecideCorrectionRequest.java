package com.campx.student.model;

/**
 * Ingress DTO for approving or rejecting a profile correction request (STM01-US-011).
 */
public class DecideCorrectionRequest {

    private String decision; // 'APPROVED' or 'REJECTED'
    private String reason;

    public DecideCorrectionRequest() {}

    public DecideCorrectionRequest(String decision, String reason) {
        this.decision = decision;
        this.reason = reason;
    }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isApproved() {
        return "APPROVED".equalsIgnoreCase(decision);
    }
}
