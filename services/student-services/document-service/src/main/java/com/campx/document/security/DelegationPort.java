package com.campx.document.security;

import java.util.UUID;

/**
 * D12a — small port for STM approve/reject delegation checks against IAM delegations.
 * Unit tests use {@link FakeDelegationPort}. Live wiring that needs cross-schema reads
 * or new infrastructure must STOP for owner design approval (fail-closed here).
 */
public interface DelegationPort {

    /**
     * @param permissionKey e.g. {@code STM-06.DOCUMENTS.A}
     * @return true only when an active delegation covers this actor for the key
     */
    boolean hasActiveDelegation(UUID tenantId, UUID userId, String permissionKey);
}
