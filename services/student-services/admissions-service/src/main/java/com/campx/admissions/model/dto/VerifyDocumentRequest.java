package com.campx.admissions.model.dto;

import com.campx.admissions.model.DocumentVerificationStatus;

public class VerifyDocumentRequest {

    private DocumentVerificationStatus status;
    private String rejectionReason;
    private boolean fraudulent = false;
    private String fraudNotes;

    public VerifyDocumentRequest() {
    }

    public DocumentVerificationStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentVerificationStatus status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public boolean isFraudulent() {
        return fraudulent;
    }

    public void setFraudulent(boolean fraudulent) {
        this.fraudulent = fraudulent;
    }

    public String getFraudNotes() {
        return fraudNotes;
    }

    public void setFraudNotes(String fraudNotes) {
        this.fraudNotes = fraudNotes;
    }
}
