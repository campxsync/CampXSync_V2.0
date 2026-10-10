package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.TenantProvisioning;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access abstraction for tenant provisioning orchestration (ADM-01 Item 3).
 * Maps to plat.tenant_provisioning in PostgreSQL schema.
 */
public interface TenantProvisioningRepository {

    /**
     * Creates and records a new provisioning orchestration job.
     */
    TenantProvisioning createProvisioningJob(UserSecurityContext context, TenantProvisioning job);

    /**
     * Retrieves a provisioning job by its unique UUID identifier.
     */
    Optional<TenantProvisioning> getProvisioningJobById(UserSecurityContext context, UUID id);

    /**
     * Lists provisioning jobs scoped to the current tenant or all for platform admin.
     */
    List<TenantProvisioning> listProvisioningJobs(UserSecurityContext context, UUID tenantId);

    /**
     * Updates status and current step of a provisioning job.
     */
    TenantProvisioning updateProvisioningStatus(UserSecurityContext context, UUID id, String status, String currentStep);
}
