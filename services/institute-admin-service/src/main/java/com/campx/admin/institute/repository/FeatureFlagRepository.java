package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.FeatureFlag;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing platform feature flags in {@code cfg.feature_flags}.
 */
public interface FeatureFlagRepository {

    /**
     * Persists or updates a platform feature flag definition.
     *
     * @param context caller security context
     * @param flag    feature flag definition
     * @return saved feature flag
     */
    FeatureFlag saveFlag(UserSecurityContext context, FeatureFlag flag);

    /**
     * Resolves a feature flag by unique key.
     *
     * @param context caller security context
     * @param flagKey flag key (e.g. "auth.mfa_enforced")
     * @return optional containing flag if found
     */
    Optional<FeatureFlag> getFlagByKey(UserSecurityContext context, String flagKey);

    /**
     * Lists all active feature flags.
     *
     * @param context caller security context
     * @return list of feature flags
     */
    List<FeatureFlag> listFlags(UserSecurityContext context);

    /**
     * Deletes a feature flag (for test teardown).
     *
     * @param context caller security context
     * @param flagKey flag key
     */
    void deleteFlag(UserSecurityContext context, String flagKey);
}
