package com.campx.admin.college.repository;

import com.campx.admin.college.model.CollegeModels.LocalPolicy;
import com.campx.admin.college.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, tenant-partitioned in-memory test double for {@link LocalPolicyRepository}.
 * Guarantees that local policies for Tenant A are strictly invisible to Tenant B.
 */
public class InMemoryLocalPolicyRepository implements LocalPolicyRepository {

    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final Map<UUID, Map<String, LocalPolicy>> policiesByTenant = new ConcurrentHashMap<>();

    private UUID resolveTenantKey(UserSecurityContext context) {
        return (context != null && context.getTenantId() != null)
                ? context.getTenantId()
                : DEFAULT_TENANT_ID;
    }

    @Override
    public LocalPolicy createLocalPolicy(UserSecurityContext context, LocalPolicy policy) {
        if (policy == null || policy.getPolicyCode() == null) {
            throw new IllegalArgumentException("Policy and policyCode must not be null");
        }
        if (policy.getId() == null || policy.getId().isEmpty()) {
            policy.setId(UUID.randomUUID().toString());
        }
        if (policy.getStatus() == null || policy.getStatus().isEmpty()) {
            policy.setStatus("DRAFT");
        }
        if (policy.getVersion() <= 0) {
            policy.setVersion(1);
        }
        UUID tenantKey = resolveTenantKey(context);
        Map<String, LocalPolicy> tenantPolicies = policiesByTenant.computeIfAbsent(tenantKey, k -> new ConcurrentHashMap<>());
        tenantPolicies.put(policy.getPolicyCode().toUpperCase(Locale.ROOT), policy);
        return policy;
    }

    @Override
    public LocalPolicy updateLocalPolicy(UserSecurityContext context, LocalPolicy policy) {
        if (policy == null || policy.getPolicyCode() == null) {
            throw new IllegalArgumentException("Policy and policyCode must not be null");
        }
        UUID tenantKey = resolveTenantKey(context);
        Map<String, LocalPolicy> tenantPolicies = policiesByTenant.computeIfAbsent(tenantKey, k -> new ConcurrentHashMap<>());
        tenantPolicies.put(policy.getPolicyCode().toUpperCase(Locale.ROOT), policy);
        return policy;
    }

    @Override
    public Optional<LocalPolicy> findByCode(UserSecurityContext context, String policyCode) {
        if (policyCode == null) return Optional.empty();
        UUID tenantKey = resolveTenantKey(context);
        Map<String, LocalPolicy> tenantPolicies = policiesByTenant.get(tenantKey);
        if (tenantPolicies == null) return Optional.empty();
        return Optional.ofNullable(tenantPolicies.get(policyCode.toUpperCase(Locale.ROOT)));
    }

    @Override
    public List<LocalPolicy> listPolicies(UserSecurityContext context) {
        UUID tenantKey = resolveTenantKey(context);
        Map<String, LocalPolicy> tenantPolicies = policiesByTenant.get(tenantKey);
        if (tenantPolicies == null || tenantPolicies.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(tenantPolicies.values());
    }
}
