package com.campx.admissions.model.dto;

import com.campx.admissions.model.DecisionType;

public class RecordDecisionRequest {

    private DecisionType decisionType;
    private String decisionReasonCode;
    private String decisionNotes;

    public RecordDecisionRequest() {
    }

    public DecisionType getDecisionType() {
        return decisionType;
    }

    public void setDecisionType(DecisionType decisionType) {
        this.decisionType = decisionType;
    }

    public String getDecisionReasonCode() {
        return decisionReasonCode;
    }

    public void setDecisionReasonCode(String decisionReasonCode) {
        this.decisionReasonCode = decisionReasonCode;
    }

    public String getDecisionNotes() {
        return decisionNotes;
    }

    public void setDecisionNotes(String decisionNotes) {
        this.decisionNotes = decisionNotes;
    }
}
