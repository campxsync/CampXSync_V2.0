package com.campx.lifecycle.security;

import java.util.UUID;

/**
 * D12a — IAM delegation port for STM-04 approve/reject. Unit tests use {@link FakeDelegationPort}.
 */
public interface DelegationPort {

    boolean hasActiveDelegation(UUID tenantId, UUID userId, String permissionKey);
}