package com.campx.admissions.model.dto;

public class SubmitApplicationRequest {

    private boolean declarationAccepted = true;
    private boolean privacyConsentAccepted = true;

    public SubmitApplicationRequest() {
    }

    public boolean isDeclarationAccepted() {
        return declarationAccepted;
    }

    public void setDeclarationAccepted(boolean declarationAccepted) {
        this.declarationAccepted = declarationAccepted;
    }

    public boolean isPrivacyConsentAccepted() {
        return privacyConsentAccepted;
    }

    public void setPrivacyConsentAccepted(boolean privacyConsentAccepted) {
        this.privacyConsentAccepted = privacyConsentAccepted;
    }
}
