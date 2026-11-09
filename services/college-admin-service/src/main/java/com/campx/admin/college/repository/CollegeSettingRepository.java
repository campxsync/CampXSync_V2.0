package com.campx.admin.college.repository;

import com.campx.admin.college.model.CollegeModels.CollegeSetting;
import com.campx.admin.college.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing college settings in {@code cfg.setting_values}.
 */
public interface CollegeSettingRepository {

    /**
     * Stores or updates a college setting.
     *
     * @param context caller security context
     * @param setting setting entity to persist
     * @return persisted setting entity
     */
    CollegeSetting storeSetting(UserSecurityContext context, CollegeSetting setting);

    /**
     * Lists college settings, optionally filtering to effective settings only.
     *
     * @param context       caller security context
     * @param effectiveOnly true to exclude pending or expired settings
     * @return list of college settings
     */
    List<CollegeSetting> getCollegeSettings(UserSecurityContext context, boolean effectiveOnly);

    /**
     * Finds a setting by its configuration key.
     *
     * @param context caller security context
     * @param key     setting key
     * @return optional containing setting if found
     */
    Optional<CollegeSetting> findByKey(UserSecurityContext context, String key);
}
