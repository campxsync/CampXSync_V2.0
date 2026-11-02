package com.campx.admissions.model.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class IssueOfferRequest {

    private UUID decisionId;
    private String offerTermsVersion = "1.0.0";
    private OffsetDateTime validTo;

    public IssueOfferRequest() {
    }

    public UUID getDecisionId() {
        return decisionId;
    }

    public void setDecisionId(UUID decisionId) {
        this.decisionId = decisionId;
    }

    public String getOfferTermsVersion() {
        return offerTermsVersion;
    }

    public void setOfferTermsVersion(String offerTermsVersion) {
        this.offerTermsVersion = offerTermsVersion;
    }

    public OffsetDateTime getValidTo() {
        return validTo;
    }

    public void setValidTo(OffsetDateTime validTo) {
        this.validTo = validTo;
    }
}
