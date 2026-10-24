package com.campx.admin.institute.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Response payload representing the result of institutional college onboarding saga.
 */
public class OnboardCollegeResponse {

    private UUID tenantId;
    private String tenantCode;
    private String tenantName;
    private UUID collegeId;
    private String collegeCode;
    private String collegeName;
    private UUID adminUserId;
    private String adminEmail;
    private String status = "ACTIVE";
    private boolean idempotentReplay = false;
    private List<SagaStep> sagaSteps = new ArrayList<>();

    public static class SagaStep {
        private int step;
        private String name;
        private String status;
        private String title;

        public SagaStep() {}

        public SagaStep(int step, String name, String status, String title) {
            this.step = step;
            this.name = name;
            this.status = status;
            this.title = title;
        }

        public int getStep() { return step; }
        public void setStep(int step) { this.step = step; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }

    public OnboardCollegeResponse() {}

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getTenantCode() { return tenantCode; }
    public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }

    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }

    public UUID getCollegeId() { return collegeId; }
    public void setCollegeId(UUID collegeId) { this.collegeId = collegeId; }

    public String getCollegeCode() { return collegeCode; }
    public void setCollegeCode(String collegeCode) { this.collegeCode = collegeCode; }

    public String getCollegeName() { return collegeName; }
    public void setCollegeName(String collegeName) { this.collegeName = collegeName; }

    public UUID getAdminUserId() { return adminUserId; }
    public void setAdminUserId(UUID adminUserId) { this.adminUserId = adminUserId; }

    public String getAdminEmail() { return adminEmail; }
    public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isIdempotentReplay() { return idempotentReplay; }
    public void setIdempotentReplay(boolean idempotentReplay) { this.idempotentReplay = idempotentReplay; }

    public List<SagaStep> getSagaSteps() { return sagaSteps; }
    public void setSagaSteps(List<SagaStep> sagaSteps) { this.sagaSteps = sagaSteps; }

    public void addStep(int step, String name, String status, String title) {
        this.sagaSteps.add(new SagaStep(step, name, status, title));
    }
}
