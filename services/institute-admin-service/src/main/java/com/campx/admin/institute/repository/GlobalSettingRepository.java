package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.GlobalSetting;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Data access abstraction for global configuration settings (ADM-01 Item 4).
 * Maps to cfg.setting_values in PostgreSQL schema.
 */
public interface GlobalSettingRepository {

    /**
     * Persists or updates a configuration setting value.
     */
    GlobalSetting saveSetting(UserSecurityContext context, GlobalSetting setting);

    /**
     * Retrieves a configuration setting by setting key.
     */
    Optional<GlobalSetting> getSettingByKey(UserSecurityContext context, String key);

    /**
     * Lists all global configuration settings within caller tenant scope.
     */
    List<GlobalSetting> listSettings(UserSecurityContext context);
}
