package com.campx.admin.college.repository;

import com.campx.admin.college.model.CollegeModels.FeatureOverride;
import com.campx.admin.college.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing feature flag overrides in {@code cfg.feature_overrides}.
 */
public interface FeatureOverrideRepository {

    /**
     * Applies a feature flag override at the college tier.
     *
     * @param context  caller security context
     * @param override feature override entity
     * @return persisted feature override
     */
    FeatureOverride overrideFeatureFlag(UserSecurityContext context, FeatureOverride override);

    /**
     * Lists active feature flag overrides for the caller's tenant / college.
     *
     * @param context caller security context
     * @return list of feature overrides
     */
    List<FeatureOverride> listFeatureOverrides(UserSecurityContext context);

    /**
     * Finds an override by the target feature flag key.
     *
     * @param context caller security context
     * @param flagKey feature flag key
     * @return optional containing override if found
     */
    Optional<FeatureOverride> findByFlagKey(UserSecurityContext context, String flagKey);
}
