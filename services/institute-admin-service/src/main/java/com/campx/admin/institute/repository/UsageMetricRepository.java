package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.UsageMetric;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository abstraction for managing tenant consumption and usage metrics in {@code plat.usage_metrics}.
 */
public interface UsageMetricRepository {

    /**
     * Records or upserts a usage metric record.
     *
     * @param context the authenticated user/tenant security context
     * @param metric  the usage metric to persist
     * @return the persisted usage metric
     */
    UsageMetric recordUsageMetric(UserSecurityContext context, UsageMetric metric);

    /**
     * Finds usage metrics for a specific tenant and monthly period (e.g. "2026-10").
     *
     * @param context  the security context
     * @param tenantId the tenant UUID
     * @param period   the billing period
     * @return matching usage metrics
     */
    List<UsageMetric> findByTenantAndPeriod(UserSecurityContext context, UUID tenantId, String period);

    /**
     * Finds all usage metrics recorded for a specific tenant.
     *
     * @param context  the security context
     * @param tenantId the tenant UUID
     * @return all usage metrics for the tenant
     */
    List<UsageMetric> findByTenant(UserSecurityContext context, UUID tenantId);

    /**
     * Finds a single usage metric by its primary key.
     *
     * @param context the security context
     * @param id      the metric UUID
     * @return the usage metric if found
     */
    Optional<UsageMetric> findById(UserSecurityContext context, UUID id);

    /**
     * Soft-deletes a usage metric by ID.
     *
     * @param context the security context
     * @param id      the metric UUID
     */
    void deleteById(UserSecurityContext context, UUID id);
}
