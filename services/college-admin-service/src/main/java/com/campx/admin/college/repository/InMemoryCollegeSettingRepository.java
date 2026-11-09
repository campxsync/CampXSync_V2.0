package com.campx.admin.college.repository;

import com.campx.admin.college.model.CollegeModels.CollegeSetting;
import com.campx.admin.college.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, tenant-partitioned in-memory test double for {@link CollegeSettingRepository}.
 * Guarantees that settings for Tenant A are strictly invisible to Tenant B.
 */
public class InMemoryCollegeSettingRepository implements CollegeSettingRepository {

    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final Map<UUID, Map<String, CollegeSetting>> settingsByTenant = new ConcurrentHashMap<>();

    private UUID resolveTenantKey(UserSecurityContext context) {
        return (context != null && context.getTenantId() != null)
                ? context.getTenantId()
                : DEFAULT_TENANT_ID;
    }

    @Override
    public CollegeSetting storeSetting(UserSecurityContext context, CollegeSetting setting) {
        if (setting == null || setting.getKey() == null) {
            throw new IllegalArgumentException("Setting and key must not be null");
        }
        UUID tenantKey = resolveTenantKey(context);
        Map<String, CollegeSetting> tenantSettings = settingsByTenant.computeIfAbsent(tenantKey, k -> new ConcurrentHashMap<>());

        CollegeSetting existing = tenantSettings.get(setting.getKey());
        if (existing != null) {
            setting.setVersion(existing.getVersion() + 1);
        } else if (setting.getVersion() <= 0) {
            setting.setVersion(1);
        }
        if (setting.getId() == null || setting.getId().isEmpty()) {
            setting.setId(UUID.randomUUID().toString());
        }
        if (setting.getEffectiveFrom() == 0) {
            setting.setEffectiveFrom(System.currentTimeMillis());
        }
        tenantSettings.put(setting.getKey(), setting);
        return setting;
    }

    @Override
    public List<CollegeSetting> getCollegeSettings(UserSecurityContext context, boolean effectiveOnly) {
        UUID tenantKey = resolveTenantKey(context);
        Map<String, CollegeSetting> tenantSettings = settingsByTenant.get(tenantKey);
        if (tenantSettings == null || tenantSettings.isEmpty()) {
            return Collections.emptyList();
        }

        long now = System.currentTimeMillis();
        List<CollegeSetting> results = new ArrayList<>();
        for (CollegeSetting s : tenantSettings.values()) {
            if (effectiveOnly) {
                if (now < s.getEffectiveFrom()) continue;
                if (s.getEffectiveTo() > 0 && now > s.getEffectiveTo()) continue;
            }
            CollegeSetting safe = new CollegeSetting();
            safe.setId(s.getId());
            safe.setKey(s.getKey());
            safe.setValue(s.isSecret() ? "********" : s.getValue());
            safe.setDataType(s.getDataType());
            safe.setSecret(s.isSecret());
            safe.setVersion(s.getVersion());
            safe.setEffectiveFrom(s.getEffectiveFrom());
            safe.setEffectiveTo(s.getEffectiveTo());
            results.add(safe);
        }
        return results;
    }

    @Override
    public Optional<CollegeSetting> findByKey(UserSecurityContext context, String key) {
        if (key == null) return Optional.empty();
        UUID tenantKey = resolveTenantKey(context);
        Map<String, CollegeSetting> tenantSettings = settingsByTenant.get(tenantKey);
        if (tenantSettings == null) return Optional.empty();
        CollegeSetting s = tenantSettings.get(key);
        if (s == null) return Optional.empty();

        CollegeSetting safe = new CollegeSetting();
        safe.setId(s.getId());
        safe.setKey(s.getKey());
        safe.setValue(s.isSecret() ? "********" : s.getValue());
        safe.setDataType(s.getDataType());
        safe.setSecret(s.isSecret());
        safe.setVersion(s.getVersion());
        safe.setEffectiveFrom(s.getEffectiveFrom());
        safe.setEffectiveTo(s.getEffectiveTo());
        return Optional.of(safe);
    }
}
