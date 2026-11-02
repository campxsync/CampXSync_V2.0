package com.campx.admissions.model.dto;

import java.util.UUID;

public class CreateApplicationRequest {

    private UUID intakeId;
    private String applicantData = "{}";

    public CreateApplicationRequest() {
    }

    public UUID getIntakeId() {
        return intakeId;
    }

    public void setIntakeId(UUID intakeId) {
        this.intakeId = intakeId;
    }

    public String getApplicantData() {
        return applicantData;
    }

    public void setApplicantData(String applicantData) {
        this.applicantData = applicantData;
    }
}
