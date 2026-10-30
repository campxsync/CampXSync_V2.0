package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.InstituteNotFoundException;
import com.campx.admin.institute.exception.InvalidStatusTransitionException;
import com.campx.admin.institute.exception.UserProfileAccessDeniedException;
import com.campx.admin.institute.exception.UserProfileConflictException;
import com.campx.admin.institute.model.InstituteModels.TenantProvisioning;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.admin.institute.service.InstituteAdminDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Comprehensive validation and contract verification suite for MVP-ADM01-04:
 * Tenant Provisioning State Machine Transition Safety with authorized retry.
 */
public class TenantProvisioningValidationTest {

    private InMemoryTenantProvisioningRepository repository;
    private UserSecurityContext platformAdminContext;
    private UserSecurityContext tenantContext;
    private UserSecurityContext collegeAdminContext;

    private UUID platformAdminId;
    private UUID tenantId;
    private UUID otherTenantId;
    private UUID collegeAdminId;

    @Before
    public void setUp() {
        platformAdminId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        otherTenantId = UUID.randomUUID();
        collegeAdminId = UUID.randomUUID();

        repository = new InMemoryTenantProvisioningRepository(Collections.singleton(platformAdminId));

        platformAdminContext = UserSecurityContext.forPlatformAdmin(platformAdminId);
        tenantContext = new UserSecurityContext(UUID.randomUUID(), tenantId);
        // College-admin identity: tenant-scoped caller representing college administration (ADM-02 tier)
        collegeAdminContext = new UserSecurityContext(collegeAdminId, tenantId);
    }

    private TenantProvisioning createSampleJob(String initialStatus) {
        TenantProvisioning job = new TenantProvisioning();
        job.setTenantId(tenantId.toString());
        job.setTargetScope("CAMPUS_MAIN");
        job.setPlanId("ENTERPRISE_CAMPUS_2026");
        job.setProvisioningStatus(initialStatus);
        job.setCurrentStep("INITIAL_STEP");
        return repository.createProvisioningJob(platformAdminContext, job);
    }

    // =========================================================================
    // 1. Legal Transitions
    // =========================================================================

    @Test
    public void testLegalTransitionRequestedToInProgress() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());

        TenantProvisioning updated = repository.updateProvisioningStatus(
                platformAdminContext, id, "REQUESTED", "IN_PROGRESS", "DATABASE_CREATION");
        assertEquals("IN_PROGRESS", updated.getProvisioningStatus());
        assertEquals("DATABASE_CREATION", updated.getCurrentStep());
        assertEquals(2, updated.getRowVersion());
    }

    @Test
    public void testLegalTransitionInProgressToCompleted() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());

        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "RUNNING");
        TenantProvisioning completed = repository.updateProvisioningStatus(
                platformAdminContext, id, "IN_PROGRESS", "COMPLETED", "ALL_SERVICES_ONLINE");

        assertEquals("COMPLETED", completed.getProvisioningStatus());
        assertEquals("ALL_SERVICES_ONLINE", completed.getCurrentStep());
        assertTrue(completed.getCompletedAt() > 0);
    }

    @Test
    public void testLegalTransitionInProgressToFailed() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());

        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "RUNNING");
        TenantProvisioning failed = repository.updateProvisioningStatus(
                platformAdminContext, id, "IN_PROGRESS", "FAILED", "MIGRATION_TIMEOUT");

        assertEquals("FAILED", failed.getProvisioningStatus());
        assertEquals("MIGRATION_TIMEOUT", failed.getCurrentStep());
    }

    @Test
    public void testLegalTransitionFailedToInProgressViaRetry() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());

        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "RUNNING");
        repository.updateProvisioningStatus(platformAdminContext, id, "FAILED", "SCHEMA_FAILURE");

        TenantProvisioning retried = repository.retryProvisioningJob(platformAdminContext, id);
        assertEquals("IN_PROGRESS", retried.getProvisioningStatus());
        assertEquals("RETRY_INITIATED", retried.getCurrentStep());
        assertEquals(2, retried.getAttempt());
    }

    // =========================================================================
    // 2. Illegal Transition Pairs
    // =========================================================================

    @Test(expected = InvalidStatusTransitionException.class)
    public void testIllegalTransitionRequestedToCompleted() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());
        repository.updateProvisioningStatus(platformAdminContext, id, "REQUESTED", "COMPLETED", "STEP");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testIllegalTransitionRequestedToFailed() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());
        repository.updateProvisioningStatus(platformAdminContext, id, "REQUESTED", "FAILED", "STEP");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testIllegalTransitionRequestedToRequested() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());
        repository.updateProvisioningStatus(platformAdminContext, id, "REQUESTED", "REQUESTED", "STEP");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testIllegalTransitionInProgressToRequested() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());
        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "RUNNING");
        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "REQUESTED", "STEP");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testIllegalTransitionInProgressToInProgress() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());
        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "RUNNING");
        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "IN_PROGRESS", "STEP");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testIllegalTransitionFailedToCompleted() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());
        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "RUNNING");
        repository.updateProvisioningStatus(platformAdminContext, id, "FAILED", "TIMEOUT");

        repository.updateProvisioningStatus(platformAdminContext, id, "FAILED", "COMPLETED", "STEP");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testIllegalTransitionFailedToRequested() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());
        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "RUNNING");
        repository.updateProvisioningStatus(platformAdminContext, id, "FAILED", "TIMEOUT");

        repository.updateProvisioningStatus(platformAdminContext, id, "FAILED", "REQUESTED", "STEP");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testIllegalTransitionFailedToFailed() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());
        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "RUNNING");
        repository.updateProvisioningStatus(platformAdminContext, id, "FAILED", "TIMEOUT");

        repository.updateProvisioningStatus(platformAdminContext, id, "FAILED", "FAILED", "STEP");
    }

    // =========================================================================
    // 3. COMPLETED Immutability
    // =========================================================================

    @Test(expected = InvalidStatusTransitionException.class)
    public void testCompletedImmutabilityToInProgressRejected() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());
        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "RUNNING");
        repository.updateProvisioningStatus(platformAdminContext, id, "COMPLETED", "DONE");

        repository.updateProvisioningStatus(platformAdminContext, id, "COMPLETED", "IN_PROGRESS", "RESTART");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testCompletedImmutabilityToFailedRejected() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());
        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "RUNNING");
        repository.updateProvisioningStatus(platformAdminContext, id, "COMPLETED", "DONE");

        repository.updateProvisioningStatus(platformAdminContext, id, "COMPLETED", "FAILED", "FAIL");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testCompletedImmutabilityToRequestedRejected() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());
        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "RUNNING");
        repository.updateProvisioningStatus(platformAdminContext, id, "COMPLETED", "DONE");

        repository.updateProvisioningStatus(platformAdminContext, id, "COMPLETED", "REQUESTED", "REWIND");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testCompletedImmutabilityRetryRejected() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());
        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "RUNNING");
        repository.updateProvisioningStatus(platformAdminContext, id, "COMPLETED", "DONE");

        repository.retryProvisioningJob(platformAdminContext, id);
    }

    // =========================================================================
    // 4. FAILED -> IN_PROGRESS: Rejected by plain update, allowed via retry
    // =========================================================================

    @Test(expected = InvalidStatusTransitionException.class)
    public void testFailedToInProgressRejectedViaPlainUpdate() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());
        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "RUNNING");
        repository.updateProvisioningStatus(platformAdminContext, id, "FAILED", "CRASH");

        // Plain update must reject FAILED -> IN_PROGRESS
        repository.updateProvisioningStatus(platformAdminContext, id, "FAILED", "IN_PROGRESS", "PLAIN_UPDATE");
    }

    @Test
    public void testFailedToInProgressAllowedViaAuthorizedRetry() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());
        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "RUNNING");
        repository.updateProvisioningStatus(platformAdminContext, id, "FAILED", "DB_CONNECTION_LOST");

        TenantProvisioning retried = repository.retryProvisioningJob(platformAdminContext, id);
        assertEquals("IN_PROGRESS", retried.getProvisioningStatus());
        assertEquals("RETRY_INITIATED", retried.getCurrentStep());
        assertEquals(2, retried.getAttempt());
    }

    // =========================================================================
    // 5. Retry Recorded (who, when, attempt, prior failure reason)
    // =========================================================================

    @Test
    public void testRetryRecordsAttemptAndPriorFailureDetails() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        job.setFailureReason("IO_TIMEOUT_ON_SEED");
        UUID id = UUID.fromString(job.getProvisioningId());

        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "RUNNING");
        repository.updateProvisioningStatus(platformAdminContext, id, "FAILED", "STEP_FAILED");

        long beforeRetry = System.currentTimeMillis();
        TenantProvisioning retried = repository.retryProvisioningJob(platformAdminContext, id);
        long afterRetry = System.currentTimeMillis();

        assertEquals("IN_PROGRESS", retried.getProvisioningStatus());
        assertEquals(2, retried.getAttempt());
        assertEquals(platformAdminId.toString(), retried.getUpdatedBy());
        assertTrue(retried.getUpdatedAt() >= beforeRetry && retried.getUpdatedAt() <= afterRetry);
        assertEquals("RETRY_INITIATED", retried.getCurrentStep());

        // Subsequent failure and retry increments to attempt 3
        repository.updateProvisioningStatus(platformAdminContext, id, "FAILED", "RETRY_FAILED");
        TenantProvisioning secondRetry = repository.retryProvisioningJob(platformAdminContext, id);
        assertEquals(3, secondRetry.getAttempt());
        assertEquals("IN_PROGRESS", secondRetry.getProvisioningStatus());
    }

    // =========================================================================
    // 6. Non-Platform-Admin Denied
    // =========================================================================

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testTenantScopedCallerCannotCreateProvisioningJob() {
        TenantProvisioning req = new TenantProvisioning();
        req.setTenantId(tenantId.toString());
        repository.createProvisioningJob(tenantContext, req);
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testTenantScopedCallerCannotUpdateStatus() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());

        repository.updateProvisioningStatus(tenantContext, id, "IN_PROGRESS", "HACK");
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testTenantScopedCallerCannotRetry() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());
        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "RUNNING");
        repository.updateProvisioningStatus(platformAdminContext, id, "FAILED", "ERR");

        repository.retryProvisioningJob(tenantContext, id);
    }

    // =========================================================================
    // 7. ADM-01 vs ADM-02 Boundary Check
    // =========================================================================

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testCollegeAdminCannotTriggerProvisioningTransitions() {
        // College-admin identity operates at tenant/college tier (ADM-02), NOT platform tier (ADM-01)
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());

        repository.updateProvisioningStatus(collegeAdminContext, id, "IN_PROGRESS", "COLLEGE_ATTEMPT");
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testCollegeAdminCannotTriggerProvisioningRetry() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());
        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "RUNNING");
        repository.updateProvisioningStatus(platformAdminContext, id, "FAILED", "ERR");

        repository.retryProvisioningJob(collegeAdminContext, id);
    }

    // =========================================================================
    // 8. Concurrent Double-Retry (one succeeds, one conflicts)
    // =========================================================================

    @Test
    public void testConcurrentDoubleRetryOneSucceedsOneConflicts() throws Exception {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());
        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "RUNNING");
        repository.updateProvisioningStatus(platformAdminContext, id, "FAILED", "CRITICAL_ERROR");

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    barrier.await();
                    repository.retryProvisioningJob(platformAdminContext, id);
                    successCount.incrementAndGet();
                } catch (UserProfileConflictException e) {
                    conflictCount.incrementAndGet();
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertTrue("Unexpected errors during concurrent retry: " + errors, errors.isEmpty());
        assertEquals("Exactly one retry must succeed", 1, successCount.get());
        assertEquals("Exactly one retry must conflict", 1, conflictCount.get());

        // Verify final record state
        Optional<TenantProvisioning> fetched = repository.getProvisioningJobById(platformAdminContext, id);
        assertTrue(fetched.isPresent());
        assertEquals("IN_PROGRESS", fetched.get().getProvisioningStatus());
        assertEquals(2, fetched.get().getAttempt());
    }

    // =========================================================================
    // 9. Soft-Deleted Record
    // =========================================================================

    @Test
    public void testSoftDeletedRecordInvisibleToLookup() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());

        repository.softDelete(id);

        Optional<TenantProvisioning> fetched = repository.getProvisioningJobById(platformAdminContext, id);
        assertFalse("Soft-deleted job must not be visible", fetched.isPresent());

        List<TenantProvisioning> list = repository.listProvisioningJobs(platformAdminContext, tenantId);
        assertTrue("Soft-deleted job must not appear in list", list.isEmpty());
    }

    @Test(expected = InstituteNotFoundException.class)
    public void testUpdateOnSoftDeletedRecordThrowsNotFound() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());

        repository.softDelete(id);
        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "STEP");
    }

    @Test(expected = InstituteNotFoundException.class)
    public void testRetryOnSoftDeletedRecordThrowsNotFound() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());
        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "RUNNING");
        repository.updateProvisioningStatus(platformAdminContext, id, "FAILED", "ERR");

        repository.softDelete(id);
        repository.retryProvisioningJob(platformAdminContext, id);
    }

    // =========================================================================
    // 10. Conditional Update Lost Race / Optimistic Locking
    // =========================================================================

    @Test(expected = UserProfileConflictException.class)
    public void testConditionalUpdateLostRaceThrowsConflict() {
        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());

        // Concurrent update transitions REQUESTED -> IN_PROGRESS
        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "RUNNING");

        // Stale thread attempts to transition expecting REQUESTED -> throws 409 conflict
        repository.updateProvisioningStatus(platformAdminContext, id, "REQUESTED", "IN_PROGRESS", "STALE_THREAD");
    }

    // =========================================================================
    // 11. Tenant Isolation: No Cross-Tenant Leakage
    // =========================================================================

    @Test
    public void testTenantIsolationNoCrossTenantAccess() {
        TenantProvisioning jobA = createSampleJob("REQUESTED");
        UUID idA = UUID.fromString(jobA.getProvisioningId());

        UserSecurityContext tenantBContext = new UserSecurityContext(UUID.randomUUID(), otherTenantId);

        // Tenant B cannot retrieve Tenant A's provisioning job
        Optional<TenantProvisioning> crossFetch = repository.getProvisioningJobById(tenantBContext, idA);
        assertFalse("Tenant B must not access Tenant A's provisioning job", crossFetch.isPresent());

        // Tenant B's list is empty
        List<TenantProvisioning> listB = repository.listProvisioningJobs(tenantBContext, otherTenantId);
        assertTrue("Tenant B must not see Tenant A's provisioning jobs in list", listB.isEmpty());
    }

    // =========================================================================
    // 12. Domain Service Integration with Outbox Propagation
    // =========================================================================

    @Test
    public void testDomainServiceRetryEmitsOutboxAndAudit() {
        InstituteAdminDomainService domainService = new InstituteAdminDomainService(
                new InMemoryTenantRepository(), new InMemoryCollegeRepository(), repository, new InMemoryGlobalSettingRepository());

        TenantProvisioning job = createSampleJob("REQUESTED");
        UUID id = UUID.fromString(job.getProvisioningId());
        repository.updateProvisioningStatus(platformAdminContext, id, "IN_PROGRESS", "RUNNING");
        repository.updateProvisioningStatus(platformAdminContext, id, "FAILED", "NETWORK_PARTITION");

        int outboxBefore = domainService.listOutboxEvents().size();
        TenantProvisioning retried = domainService.retryProvisioningJob(platformAdminContext, id);

        assertEquals("IN_PROGRESS", retried.getProvisioningStatus());
        assertEquals(2, retried.getAttempt());

        int outboxAfter = domainService.listOutboxEvents().size();
        assertEquals("Outbox event TenantProvisioningRetried must be emitted", outboxBefore + 1, outboxAfter);
    }
}
