package com.campx.admin.college.repository;

import com.campx.admin.college.model.CollegeModels.CollegeProfile;
import com.campx.admin.college.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, tenant-partitioned in-memory test double for {@link CollegeProfileRepository}.
 * Prevents cross-tenant state leakage between distinct tenant contexts.
 */
public class InMemoryCollegeProfileRepository implements CollegeProfileRepository {

    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final Map<UUID, Map<String, CollegeProfile>> profilesByTenant = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, List<String>>> accreditationsByTenant = new ConcurrentHashMap<>();

    public InMemoryCollegeProfileRepository() {
        // Seed default profile only in default tenant space
        CollegeProfile def = new CollegeProfile();
        def.setCollegeCode("COL_ENGG_01");
        def.setLegalName("National College of Engineering & Technology");
        def.setDisplayName("NCET Campus");
        def.getAccreditationRefs().addAll(Arrays.asList("NAAC_A_PLUS", "NBA_TIER_1"));
        def.setAddress("Campus Road, Knowledge Park, Bangalore");
        def.setStatus("ACTIVE");
        def.setCurrentVersion(1);
        def.setUpdatedAt(System.currentTimeMillis());

        Map<String, CollegeProfile> defMap = new ConcurrentHashMap<>();
        defMap.put(def.getCollegeCode(), def);
        profilesByTenant.put(DEFAULT_TENANT_ID, defMap);

        Map<String, List<String>> defAcc = new ConcurrentHashMap<>();
        defAcc.put(def.getCollegeCode(), new ArrayList<>(def.getAccreditationRefs()));
        accreditationsByTenant.put(DEFAULT_TENANT_ID, defAcc);
    }

    private UUID resolveTenantKey(UserSecurityContext context) {
        return (context != null && context.getTenantId() != null)
                ? context.getTenantId()
                : DEFAULT_TENANT_ID;
    }

    @Override
    public Optional<CollegeProfile> getProfile(UserSecurityContext context, String collegeCode) {
        UUID tenantKey = resolveTenantKey(context);
        Map<String, CollegeProfile> tenantProfiles = profilesByTenant.get(tenantKey);
        if (tenantProfiles == null || tenantProfiles.isEmpty()) {
            return Optional.empty();
        }
        if (collegeCode == null || collegeCode.trim().isEmpty()) {
            // Return primary/first profile for tenant
            return tenantProfiles.values().stream().findFirst();
        }
        CollegeProfile p = tenantProfiles.get(collegeCode.trim().toUpperCase(Locale.ROOT));
        if (p == null) {
            p = tenantProfiles.get(collegeCode.trim());
        }
        return Optional.ofNullable(p);
    }

    @Override
    public CollegeProfile saveOrUpdateProfile(UserSecurityContext context, CollegeProfile profile) {
        if (profile == null || profile.getCollegeCode() == null) {
            throw new IllegalArgumentException("College profile and collegeCode must not be null");
        }
        UUID tenantKey = resolveTenantKey(context);
        Map<String, CollegeProfile> tenantProfiles = profilesByTenant.computeIfAbsent(tenantKey, k -> new ConcurrentHashMap<>());
        tenantProfiles.put(profile.getCollegeCode(), profile);

        if (profile.getAccreditationRefs() != null) {
            Map<String, List<String>> tenantAcc = accreditationsByTenant.computeIfAbsent(tenantKey, k -> new ConcurrentHashMap<>());
            tenantAcc.put(profile.getCollegeCode(), new ArrayList<>(profile.getAccreditationRefs()));
        }
        return profile;
    }

    @Override
    public List<String> listAccreditationRefs(UserSecurityContext context, String collegeId) {
        if (collegeId == null) return Collections.emptyList();
        UUID tenantKey = resolveTenantKey(context);
        Map<String, List<String>> tenantAcc = accreditationsByTenant.get(tenantKey);
        if (tenantAcc == null) return Collections.emptyList();
        List<String> list = tenantAcc.get(collegeId);
        return list != null ? new ArrayList<>(list) : Collections.emptyList();
    }

    @Override
    public void syncAccreditations(UserSecurityContext context, String collegeId, List<String> accreditationRefs) {
        if (collegeId != null && accreditationRefs != null) {
            UUID tenantKey = resolveTenantKey(context);
            Map<String, List<String>> tenantAcc = accreditationsByTenant.computeIfAbsent(tenantKey, k -> new ConcurrentHashMap<>());
            tenantAcc.put(collegeId, new ArrayList<>(accreditationRefs));
        }
    }
}
