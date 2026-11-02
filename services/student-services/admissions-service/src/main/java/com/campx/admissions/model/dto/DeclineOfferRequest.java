package com.campx.admissions.model.dto;

public class DeclineOfferRequest {

    private String declineReason;

    public DeclineOfferRequest() {
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public void setDeclineReason(String declineReason) {
        this.declineReason = declineReason;
    }
}
