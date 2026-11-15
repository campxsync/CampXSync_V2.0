package com.campx.lifecycle.security;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class FakeDelegationPort implements DelegationPort {

    private final Set<String> grants = new HashSet<String>();

    public void grant(UUID tenantId, UUID userId, String permissionKey) {
        grants.add(key(tenantId, userId, permissionKey));
    }

    public void revokeAll() {
        grants.clear();
    }

    @Override
    public boolean hasActiveDelegation(UUID tenantId, UUID userId, String permissionKey) {
        if (tenantId == null || userId == null || permissionKey == null) {
            return false;
        }
        return grants.contains(key(tenantId, userId, permissionKey));
    }

    private static String key(UUID tenantId, UUID userId, String permissionKey) {
        return tenantId + "|" + userId + "|" + permissionKey.trim().toUpperCase(Locale.ROOT);
    }
}