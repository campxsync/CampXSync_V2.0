package com.campx.admissions.security;

import java.util.UUID;

/** D12a — IAM delegation port for STM-02 admission decision approve/reject. */
public interface DelegationPort {

    boolean hasActiveDelegation(UUID tenantId, UUID userId, String permissionKey);
}