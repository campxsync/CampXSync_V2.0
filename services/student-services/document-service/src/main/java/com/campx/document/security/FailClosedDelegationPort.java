package com.campx.document.security;

import java.util.UUID;

/**
 * Default runtime port until a live IAM delegation adapter is owner-approved.
 * Always returns false (fail-closed for A/R).
 */
public final class FailClosedDelegationPort implements DelegationPort {

    @Override
    public boolean hasActiveDelegation(UUID tenantId, UUID userId, String permissionKey) {
        return false;
    }
}
