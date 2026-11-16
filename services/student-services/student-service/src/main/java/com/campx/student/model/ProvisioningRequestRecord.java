package com.campx.student.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * STM-07 provisioning request snapshot (stu.account_provisioning_requests).
 */
public class ProvisioningRequestRecord {

    private UUID id;
    private UUID studentId;
    private UUID requestedIamUserId;
    private String requestStatus;
    private String failureReason;
    private OffsetDateTime requestedAt;
    private OffsetDateTime completedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public UUID getRequestedIamUserId() { return requestedIamUserId; }
    public void setRequestedIamUserId(UUID requestedIamUserId) { this.requestedIamUserId = requestedIamUserId; }

    public String getRequestStatus() { return requestStatus; }
    public void setRequestStatus(String requestStatus) { this.requestStatus = requestStatus; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(OffsetDateTime requestedAt) { this.requestedAt = requestedAt; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
}
