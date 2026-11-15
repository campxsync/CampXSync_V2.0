package com.campx.enrollment.security;

import java.util.UUID;

/** D12a — IAM delegation port for STM-03 approve/reject. */
public interface DelegationPort {

    boolean hasActiveDelegation(UUID tenantId, UUID userId, String permissionKey);
}