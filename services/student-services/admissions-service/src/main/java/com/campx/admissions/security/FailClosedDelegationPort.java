package com.campx.admissions.security;

import java.util.UUID;

public final class FailClosedDelegationPort implements DelegationPort {

    @Override
    public boolean hasActiveDelegation(UUID tenantId, UUID userId, String permissionKey) {
        return false;
    }
}