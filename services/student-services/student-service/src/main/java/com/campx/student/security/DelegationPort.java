package com.campx.student.security;

import java.util.UUID;

/**
 * D12a Path C — IAM delegation port (STM-07 account actions use {@code STM-07.ACCOUNT.A}).
 * Unit tests use {@link FakeDelegationPort}; live uses {@link HttpIamDelegationPort}.
 */
public interface DelegationPort {

    boolean hasActiveDelegation(UUID tenantId, UUID userId, String permissionKey);
}