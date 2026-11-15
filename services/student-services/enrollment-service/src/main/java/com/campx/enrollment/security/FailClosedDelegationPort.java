package com.campx.enrollment.security;

import java.util.UUID;

public final class FailClosedDelegationPort implements DelegationPort {

    @Override
    public boolean hasActiveDelegation(UUID tenantId, UUID userId, String permissionKey) {
        return false;
    }
}