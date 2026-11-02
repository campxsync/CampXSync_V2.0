package com.campx.admissions.model.dto;

public class AcceptOfferRequest {

    private boolean confirmAcceptance = true;

    public AcceptOfferRequest() {
    }

    public boolean isConfirmAcceptance() {
        return confirmAcceptance;
    }

    public void setConfirmAcceptance(boolean confirmAcceptance) {
        this.confirmAcceptance = confirmAcceptance;
    }
}
