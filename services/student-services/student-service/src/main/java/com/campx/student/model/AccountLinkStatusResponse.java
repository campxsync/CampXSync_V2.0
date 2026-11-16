package com.campx.student.model;

import java.util.UUID;

/**
 * STM-07-US-001 / US-013 account-link status for staff and self-service portal.
 */
public class AccountLinkStatusResponse {

    private UUID studentId;
    private String linkStatus = "NONE";
    private UUID linkedIamUserId;
    private String studentStatus;
    private String verificationStatus;
    private boolean portalAccessActive;
    private long provisioningRequestCount;

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public String getLinkStatus() { return linkStatus; }
    public void setLinkStatus(String linkStatus) { this.linkStatus = linkStatus; }

    public UUID getLinkedIamUserId() { return linkedIamUserId; }
    public void setLinkedIamUserId(UUID linkedIamUserId) { this.linkedIamUserId = linkedIamUserId; }

    public String getStudentStatus() { return studentStatus; }
    public void setStudentStatus(String studentStatus) { this.studentStatus = studentStatus; }

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }

    public boolean isPortalAccessActive() { return portalAccessActive; }
    public void setPortalAccessActive(boolean portalAccessActive) {
        this.portalAccessActive = portalAccessActive;
    }

    public long getProvisioningRequestCount() { return provisioningRequestCount; }
    public void setProvisioningRequestCount(long provisioningRequestCount) {
        this.provisioningRequestCount = provisioningRequestCount;
    }
}
