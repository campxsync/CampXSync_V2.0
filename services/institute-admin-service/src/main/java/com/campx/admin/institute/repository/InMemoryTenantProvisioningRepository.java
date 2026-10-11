package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.InstituteNotFoundException;
import com.campx.admin.institute.model.InstituteModels.TenantProvisioning;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory implementation of {@link TenantProvisioningRepository}.
 */
public class InMemoryTenantProvisioningRepository implements TenantProvisioningRepository {

    private final Map<UUID, TenantProvisioning> storage = new ConcurrentHashMap<>();

    @Override
    public TenantProvisioning createProvisioningJob(UserSecurityContext context, TenantProvisioning job) {
        if (job == null) {
            throw new MalformedPayloadException("Provisioning job cannot be null");
        }
        UUID id = UUID.randomUUID();
        if (job.getProvisioningId() != null && !job.getProvisioningId().trim().isEmpty()) {
            try {
                id = UUID.fromString(job.getProvisioningId().trim());
            } catch (IllegalArgumentException ignored) {}
        }
        job.setProvisioningId(id.toString());
        if (job.getProvisioningStatus() == null) {
            job.setProvisioningStatus("REQUESTED");
        }
        storage.put(id, job);
        return job;
    }

    @Override
    public Optional<TenantProvisioning> getProvisioningJobById(UserSecurityContext context, UUID id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<TenantProvisioning> listProvisioningJobs(UserSecurityContext context, UUID tenantId) {
        List<TenantProvisioning> result = new ArrayList<>();
        for (TenantProvisioning job : storage.values()) {
            if (tenantId == null || tenantId.toString().equalsIgnoreCase(job.getTenantId())) {
                result.add(job);
            }
        }
        return result;
    }

    @Override
    public TenantProvisioning updateProvisioningStatus(UserSecurityContext context, UUID id, String status, String currentStep) {
        TenantProvisioning existing = storage.get(id);
        if (existing == null) {
            throw new InstituteNotFoundException("ProvisioningJob", id.toString());
        }
        existing.setProvisioningStatus(status);
        if ("COMPLETED".equalsIgnoreCase(status)) {
            existing.setCompletedAt(System.currentTimeMillis());
        }
        return existing;
    }
}
