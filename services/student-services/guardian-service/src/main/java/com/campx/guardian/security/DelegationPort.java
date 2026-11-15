package com.campx.guardian.security;

import java.util.UUID;

/** D12a — IAM delegation port for STM-05 verify/reject evidence. */
public interface DelegationPort {

    boolean hasActiveDelegation(UUID tenantId, UUID userId, String permissionKey);
}