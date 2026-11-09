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
     * Updates status and current step of a provisioning job using automatic current state detection.
     */
    TenantProvisioning updateProvisioningStatus(UserSecurityContext context, UUID id, String status, String currentStep);

    /**
     * Updates status and current step conditionally matching the expectedCurrentStatus (optimistic locking / state safety).
     * Lost races throw a conflict exception.
     */
    TenantProvisioning updateProvisioningStatus(UserSecurityContext context, UUID id, String expectedCurrentStatus, String newStatus, String currentStep);

    /**
     * Authorized platform-administrator retry operation transitioning a FAILED job back to IN_PROGRESS.
     * Records the retry attempt and details. Lost races return conflict, never silent success.
     */
    TenantProvisioning retryProvisioningJob(UserSecurityContext context, UUID id);
}
