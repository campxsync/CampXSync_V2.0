package com.campx.admin.college.repository;

import com.campx.admin.college.model.CollegeModels.FeatureOverride;
import com.campx.admin.college.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, tenant-partitioned in-memory test double for {@link FeatureOverrideRepository}.
 * Guarantees that feature overrides are isolated per tenant context.
 */
public class InMemoryFeatureOverrideRepository implements FeatureOverrideRepository {

    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final Map<UUID, Map<String, FeatureOverride>> overridesByTenant = new ConcurrentHashMap<>();

    private UUID resolveTenantKey(UserSecurityContext context) {
        return (context != null && context.getTenantId() != null)
                ? context.getTenantId()
                : DEFAULT_TENANT_ID;
    }

    @Override
    public FeatureOverride overrideFeatureFlag(UserSecurityContext context, FeatureOverride override) {
        if (override == null || override.getFlagKey() == null) {
            throw new IllegalArgumentException("Feature override and flagKey must not be null");
        }
        if (override.getId() == null || override.getId().isEmpty()) {
            override.setId(UUID.randomUUID().toString());
        }
        if (override.getEffectiveFrom() == 0) {
            override.setEffectiveFrom(System.currentTimeMillis());
        }
        UUID tenantKey = resolveTenantKey(context);
        Map<String, FeatureOverride> tenantOverrides = overridesByTenant.computeIfAbsent(tenantKey, k -> new ConcurrentHashMap<>());
        tenantOverrides.put(override.getFlagKey(), override);
        return override;
    }

    @Override
    public List<FeatureOverride> listFeatureOverrides(UserSecurityContext context) {
        UUID tenantKey = resolveTenantKey(context);
        Map<String, FeatureOverride> tenantOverrides = overridesByTenant.get(tenantKey);
        if (tenantOverrides == null || tenantOverrides.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(tenantOverrides.values());
    }

    @Override
    public Optional<FeatureOverride> findByFlagKey(UserSecurityContext context, String flagKey) {
        if (flagKey == null) return Optional.empty();
        UUID tenantKey = resolveTenantKey(context);
        Map<String, FeatureOverride> tenantOverrides = overridesByTenant.get(tenantKey);
        if (tenantOverrides == null) return Optional.empty();
        return Optional.ofNullable(tenantOverrides.get(flagKey));
    }
}
