package com.campx.student.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * STM-07-US-002 eligibility evaluation for IAM link / provisioning.
 */
public class ProvisioningEligibilityResult {

    private UUID studentId;
    private boolean eligible;
    private String studentStatus;
    private String verificationStatus;
    private String linkStatus = "NONE";
    private UUID linkedIamUserId;
    private long provisioningRequestCount;
    private List<String> failures = new ArrayList<String>();

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public boolean isEligible() { return eligible; }
    public void setEligible(boolean eligible) { this.eligible = eligible; }

    public String getStudentStatus() { return studentStatus; }
    public void setStudentStatus(String studentStatus) { this.studentStatus = studentStatus; }

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }

    public String getLinkStatus() { return linkStatus; }
    public void setLinkStatus(String linkStatus) { this.linkStatus = linkStatus; }

    public UUID getLinkedIamUserId() { return linkedIamUserId; }
    public void setLinkedIamUserId(UUID linkedIamUserId) { this.linkedIamUserId = linkedIamUserId; }

    public long getProvisioningRequestCount() { return provisioningRequestCount; }
    public void setProvisioningRequestCount(long provisioningRequestCount) {
        this.provisioningRequestCount = provisioningRequestCount;
    }

    public List<String> getFailures() {
        return failures == null ? Collections.<String>emptyList() : failures;
    }

    public void setFailures(List<String> failures) {
        this.failures = failures != null ? failures : new ArrayList<String>();
    }

    public void addFailure(String code) {
        if (code != null && !code.trim().isEmpty()) {
            if (this.failures == null) {
                this.failures = new ArrayList<String>();
            }
            this.failures.add(code.trim());
        }
    }
}
