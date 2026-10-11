package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.model.InstituteModels.GlobalSetting;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe implementation of {@link GlobalSettingRepository}.
 */
public class InMemoryGlobalSettingRepository implements GlobalSettingRepository {

    private final Map<String, GlobalSetting> settings = new ConcurrentHashMap<>();

    @Override
    public GlobalSetting saveSetting(UserSecurityContext context, GlobalSetting setting) {
        if (setting == null || setting.getKey() == null || setting.getKey().trim().isEmpty()) {
            throw new MalformedPayloadException("Setting and setting key cannot be null");
        }
        settings.put(setting.getKey(), setting);
        return setting;
    }

    @Override
    public Optional<GlobalSetting> getSettingByKey(UserSecurityContext context, String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(settings.get(key));
    }

    @Override
    public List<GlobalSetting> listSettings(UserSecurityContext context) {
        return new ArrayList<>(settings.values());
    }
}
