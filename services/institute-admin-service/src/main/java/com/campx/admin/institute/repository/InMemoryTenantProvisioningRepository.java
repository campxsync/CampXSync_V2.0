package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.InstituteNotFoundException;
import com.campx.admin.institute.exception.InvalidStatusTransitionException;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.exception.UserProfileAccessDeniedException;
import com.campx.admin.institute.exception.UserProfileConflictException;
import com.campx.admin.institute.model.InstituteModels.TenantProvisioning;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, tenant-isolated in-memory implementation of {@link TenantProvisioningRepository}.
 * <p>
 * Enforces:
 * <ul>
 *   <li>Tenant isolation: data is partitioned by {@code tenant_id} and never shared across tenants.</li>
 *   <li>Authorization: platform administrator identity required for mutations and retry.</li>
 *   <li>Immutability: COMPLETED state cannot be transitioned or retried.</li>
 *   <li>Conditional updates: lost races throw {@link UserProfileConflictException}.</li>
 *   <li>Single transition table: governed by {@link TenantProvisioningStateMachine}.</li>
 *   <li>Soft-delete respect: records with non-null {@code deletedAt} are invisible.</li>
 * </ul>
 */
public class InMemoryTenantProvisioningRepository implements TenantProvisioningRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(InMemoryTenantProvisioningRepository.class);

    // Tenant isolation: Map<TenantId, Map<JobId, TenantProvisioning>>
    private final Map<UUID, Map<UUID, TenantProvisioning>> tenantStorage = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> jobToTenantIndex = new ConcurrentHashMap<>();
    private final Set<UUID> platformAdmins = Collections.synchronizedSet(new HashSet<>());

    public InMemoryTenantProvisioningRepository() {
    }

    public InMemoryTenantProvisioningRepository(Collection<UUID> initialPlatformAdmins) {
        if (initialPlatformAdmins != null) {
            platformAdmins.addAll(initialPlatformAdmins);
        }
    }

    public void addPlatformAdmin(UUID userId) {
        if (userId != null) {
            platformAdmins.add(userId);
        }
    }

    private void checkContext(UserSecurityContext context) {
        if (context == null || context.getUserId() == null) {
            throw new SecurityViolationException("Missing required security context: userId");
        }
    }

    private void checkPlatformAdminContext(UserSecurityContext context) {
        checkContext(context);
        if (!context.isPlatformScope()) {
            throw new UserProfileAccessDeniedException("Platform administrator privileges required for tenant provisioning operations");
        }
        if (!platformAdmins.isEmpty() && !platformAdmins.contains(context.getUserId())) {
            throw new UserProfileAccessDeniedException("Caller '" + context.getUserId() + "' lacks required platform administrator privilege");
        }
    }

    @Override
    public TenantProvisioning createProvisioningJob(UserSecurityContext context, TenantProvisioning job) {
        checkPlatformAdminContext(context);
        if (job == null) {
            throw new MalformedPayloadException("Provisioning job cannot be null");
        }

        UUID tenantId;
        if (job.getTenantId() != null && !job.getTenantId().trim().isEmpty()) {
            try {
                tenantId = UUID.fromString(job.getTenantId().trim());
            } catch (IllegalArgumentException e) {
                tenantId = (context.getTenantId() != null) ? context.getTenantId() : UUID.randomUUID();
            }
        } else if (context.getTenantId() != null) {
            tenantId = context.getTenantId();
        } else {
            tenantId = UUID.randomUUID();
        }

        UUID id = UUID.randomUUID();
        if (job.getProvisioningId() != null && !job.getProvisioningId().trim().isEmpty()) {
            try {
                id = UUID.fromString(job.getProvisioningId().trim());
            } catch (IllegalArgumentException ignored) {}
        }

        TenantProvisioning copy = copyJob(job);
        copy.setProvisioningId(id.toString());
        copy.setTenantId(tenantId.toString());
        if (copy.getProvisioningStatus() == null || copy.getProvisioningStatus().trim().isEmpty()) {
            copy.setProvisioningStatus(TenantProvisioningStateMachine.STATUS_REQUESTED);
        } else {
            copy.setProvisioningStatus(TenantProvisioningStateMachine.normalizeStatus(copy.getProvisioningStatus()));
        }
        if (copy.getCurrentStep() == null || copy.getCurrentStep().trim().isEmpty()) {
            copy.setCurrentStep("INITIAL_VALIDATION");
        }
        if (copy.getAttempt() <= 0) {
            copy.setAttempt(1);
        }
        copy.setRequestedBy(context.getUserId().toString());
        copy.setRequestedAt(System.currentTimeMillis());
        copy.setRowVersion(1);

        tenantStorage.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>()).put(id, copy);
        jobToTenantIndex.put(id, tenantId);

        return copyJob(copy);
    }

    @Override
    public Optional<TenantProvisioning> getProvisioningJobById(UserSecurityContext context, UUID id) {
        checkContext(context);
        if (id == null) {
            return Optional.empty();
        }

        UUID tenantId = jobToTenantIndex.get(id);
        if (tenantId == null) {
            return Optional.empty();
        }

        // Tenant isolation: non-platform caller can ONLY access their own tenant's jobs
        if (!context.isPlatformScope()) {
            if (context.getTenantId() == null || !tenantId.equals(context.getTenantId())) {
                return Optional.empty();
            }
        }

        Map<UUID, TenantProvisioning> map = tenantStorage.get(tenantId);
        if (map == null) {
            return Optional.empty();
        }
        TenantProvisioning job = map.get(id);
        if (job == null || job.getDeletedAt() != null) {
            return Optional.empty();
        }
        return Optional.of(copyJob(job));
    }

    @Override
    public List<TenantProvisioning> listProvisioningJobs(UserSecurityContext context, UUID tenantId) {
        checkContext(context);
        List<TenantProvisioning> result = new ArrayList<>();

        if (!context.isPlatformScope()) {
            // Strictly bound to caller's security context tenant
            UUID callerTenant = context.getTenantId();
            if (callerTenant != null) {
                Map<UUID, TenantProvisioning> map = tenantStorage.get(callerTenant);
                if (map != null) {
                    for (TenantProvisioning job : map.values()) {
                        if (job.getDeletedAt() == null) {
                            result.add(copyJob(job));
                        }
                    }
                }
            }
            return result;
        }

        // Platform administrator scope
        if (tenantId != null) {
            Map<UUID, TenantProvisioning> map = tenantStorage.get(tenantId);
            if (map != null) {
                for (TenantProvisioning job : map.values()) {
                    if (job.getDeletedAt() == null) {
                        result.add(copyJob(job));
                    }
                }
            }
        } else {
            for (Map<UUID, TenantProvisioning> map : tenantStorage.values()) {
                for (TenantProvisioning job : map.values()) {
                    if (job.getDeletedAt() == null) {
                        result.add(copyJob(job));
                    }
                }
            }
        }
        return result;
    }

    @Override
    public TenantProvisioning updateProvisioningStatus(UserSecurityContext context, UUID id, String status, String currentStep) {
        return updateProvisioningStatus(context, id, null, status, currentStep);
    }

    @Override
    public TenantProvisioning updateProvisioningStatus(UserSecurityContext context, UUID id, String expectedCurrentStatus, String newStatus, String currentStep) {
        checkPlatformAdminContext(context);
        if (id == null) {
            throw new MalformedPayloadException("Job ID cannot be null");
        }
        if (newStatus == null || newStatus.trim().isEmpty()) {
            throw new MalformedPayloadException("New status cannot be null or empty");
        }

        UUID tenantId = jobToTenantIndex.get(id);
        if (tenantId == null) {
            throw new InstituteNotFoundException("ProvisioningJob", id.toString());
        }

        Map<UUID, TenantProvisioning> map = tenantStorage.get(tenantId);
        if (map == null) {
            throw new InstituteNotFoundException("ProvisioningJob", id.toString());
        }

        TenantProvisioning job = map.get(id);
        if (job == null || job.getDeletedAt() != null) {
            throw new InstituteNotFoundException("ProvisioningJob", id.toString());
        }

        synchronized (job) {
            if (job.getDeletedAt() != null) {
                throw new InstituteNotFoundException("ProvisioningJob", id.toString());
            }

            String currentStatus = job.getProvisioningStatus();

            // Conditional update / optimistic concurrency check
            if (expectedCurrentStatus != null && !expectedCurrentStatus.trim().equalsIgnoreCase(currentStatus)) {
                throw new UserProfileConflictException("ADM01_PROVISIONING_CONFLICT",
                        "Conditional update conflict: provisioning job " + id + " is in state '" + currentStatus + "', expected '" + expectedCurrentStatus + "'");
            }

            // State machine validation (non-retry path)
            TenantProvisioningStateMachine.validateTransition(currentStatus, newStatus, false);

            String targetNorm = TenantProvisioningStateMachine.normalizeStatus(newStatus);
            job.setProvisioningStatus(targetNorm);
            if (currentStep != null && !currentStep.trim().isEmpty()) {
                job.setCurrentStep(currentStep.trim());
            }
            job.setUpdatedAt(System.currentTimeMillis());
            job.setUpdatedBy(context.getUserId().toString());
            job.setRowVersion(job.getRowVersion() + 1);

            if (TenantProvisioningStateMachine.STATUS_COMPLETED.equals(targetNorm)) {
                job.setCompletedAt(System.currentTimeMillis());
            }

            return copyJob(job);
        }
    }

    @Override
    public TenantProvisioning retryProvisioningJob(UserSecurityContext context, UUID id) {
        checkPlatformAdminContext(context);
        if (id == null) {
            throw new MalformedPayloadException("Job ID cannot be null");
        }

        UUID tenantId = jobToTenantIndex.get(id);
        if (tenantId == null) {
            throw new InstituteNotFoundException("ProvisioningJob", id.toString());
        }

        Map<UUID, TenantProvisioning> map = tenantStorage.get(tenantId);
        if (map == null) {
            throw new InstituteNotFoundException("ProvisioningJob", id.toString());
        }

        TenantProvisioning job = map.get(id);
        if (job == null || job.getDeletedAt() != null) {
            throw new InstituteNotFoundException("ProvisioningJob", id.toString());
        }

        synchronized (job) {
            if (job.getDeletedAt() != null) {
                throw new InstituteNotFoundException("ProvisioningJob", id.toString());
            }

            String currentStatus = job.getProvisioningStatus();

            if (TenantProvisioningStateMachine.STATUS_COMPLETED.equalsIgnoreCase(currentStatus)) {
                throw new InvalidStatusTransitionException(
                        "State 'COMPLETED' is terminal and immutable. No further transitions are permitted");
            }

            if (TenantProvisioningStateMachine.STATUS_IN_PROGRESS.equalsIgnoreCase(currentStatus)) {
                // Lost race during concurrent double-retry
                throw new UserProfileConflictException("ADM01_PROVISIONING_CONFLICT",
                        "Concurrent double-retry conflict for provisioning job: " + id);
            }

            if (!TenantProvisioningStateMachine.STATUS_FAILED.equalsIgnoreCase(currentStatus)) {
                throw new InvalidStatusTransitionException(currentStatus, TenantProvisioningStateMachine.STATUS_IN_PROGRESS);
            }

            // Validates FAILED -> IN_PROGRESS for retry
            TenantProvisioningStateMachine.validateTransition(currentStatus, TenantProvisioningStateMachine.STATUS_IN_PROGRESS, true);

            int newAttempt = job.getAttempt() + 1;
            String priorFailureReason = job.getFailureReason();

            job.setProvisioningStatus(TenantProvisioningStateMachine.STATUS_IN_PROGRESS);
            job.setCurrentStep("RETRY_INITIATED");
            job.setAttempt(newAttempt);
            job.setUpdatedAt(System.currentTimeMillis());
            job.setUpdatedBy(context.getUserId().toString());
            job.setRowVersion(job.getRowVersion() + 1);

            // Record the retry (who, when, attempt, prior failure reason) via audit mechanism
            AuditEvent audit = AuditEvent.builder()
                    .action("TENANT_PROVISIONING_RETRIED")
                    .principalId(context.getUserId().toString())
                    .principalRole("PLATFORM_ADMIN")
                    .resourceType("TENANT_PROVISIONING")
                    .resourceId(id.toString())
                    .status("SUCCESS")
                    .description(String.format("Retried tenant provisioning job: %s (attempt %d, prior failure reason: '%s')",
                            id, newAttempt, priorFailureReason != null ? priorFailureReason : "N/A"))
                    .build();
            logger.audit(audit);

            return copyJob(job);
        }
    }

    /**
     * Soft-deletes a provisioning job for testing soft-delete boundary semantics.
     */
    public void softDelete(UUID id) {
        if (id == null) return;
        UUID tenantId = jobToTenantIndex.get(id);
        if (tenantId != null) {
            Map<UUID, TenantProvisioning> map = tenantStorage.get(tenantId);
            if (map != null) {
                TenantProvisioning job = map.get(id);
                if (job != null) {
                    synchronized (job) {
                        job.setDeletedAt(System.currentTimeMillis());
                    }
                }
            }
        }
    }

    private TenantProvisioning copyJob(TenantProvisioning source) {
        if (source == null) return null;
        TenantProvisioning copy = new TenantProvisioning();
        copy.setProvisioningId(source.getProvisioningId());
        copy.setTenantId(source.getTenantId());
        copy.setTargetScope(source.getTargetScope());
        copy.setPlanId(source.getPlanId());
        copy.setProvisioningStatus(source.getProvisioningStatus());
        copy.setCurrentStep(source.getCurrentStep());
        copy.setAttempt(source.getAttempt());
        copy.setFailureReason(source.getFailureReason());
        copy.setRequestedBy(source.getRequestedBy());
        copy.setUpdatedBy(source.getUpdatedBy());
        copy.setIdempotencyKey(source.getIdempotencyKey());
        copy.setRequestedAt(source.getRequestedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setCompletedAt(source.getCompletedAt());
        copy.setDeletedAt(source.getDeletedAt());
        copy.setRowVersion(source.getRowVersion());
        return copy;
    }
}
