package com.campx.student.model;

import java.util.UUID;

/**
 * Ingress DTO for linking an authenticated IAM user identity to student profile (STM01-US-023).
 */
public class IamLinkageRequest {

    private UUID iamUserId;

    public IamLinkageRequest() {}

    public IamLinkageRequest(UUID iamUserId) {
        this.iamUserId = iamUserId;
    }

    public UUID getIamUserId() { return iamUserId; }
    public void setIamUserId(UUID iamUserId) { this.iamUserId = iamUserId; }
}
