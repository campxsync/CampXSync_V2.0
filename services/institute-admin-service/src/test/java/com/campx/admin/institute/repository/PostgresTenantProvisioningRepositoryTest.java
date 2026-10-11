package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.TenantProvisioning;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link InMemoryTenantProvisioningRepository} and contract verification.
 */
public class PostgresTenantProvisioningRepositoryTest {

    private InMemoryTenantProvisioningRepository repository;
    private UserSecurityContext context;
    private UUID tenantId;

    @Before
    public void setUp() {
        repository = new InMemoryTenantProvisioningRepository();
        tenantId = UUID.randomUUID();
        context = new UserSecurityContext(UUID.randomUUID(), tenantId);
    }

    @Test
    public void testCreateAndRetrieveProvisioningJob() {
        TenantProvisioning job = new TenantProvisioning();
        job.setTenantId(tenantId.toString());
        job.setProvisioningStatus("REQUESTED");
        job.setIdempotencyKey("KEY-" + System.currentTimeMillis());

        TenantProvisioning created = repository.createProvisioningJob(context, job);
        assertNotNull(created.getProvisioningId());
        assertEquals("REQUESTED", created.getProvisioningStatus());

        UUID jobId = UUID.fromString(created.getProvisioningId());
        Optional<TenantProvisioning> fetched = repository.getProvisioningJobById(context, jobId);
        assertTrue(fetched.isPresent());
        assertEquals(created.getProvisioningId(), fetched.get().getProvisioningId());
    }

    @Test
    public void testListProvisioningJobs() {
        TenantProvisioning job1 = new TenantProvisioning();
        job1.setTenantId(tenantId.toString());
        job1.setProvisioningStatus("REQUESTED");
        repository.createProvisioningJob(context, job1);

        TenantProvisioning job2 = new TenantProvisioning();
        job2.setTenantId(tenantId.toString());
        job2.setProvisioningStatus("IN_PROGRESS");
        repository.createProvisioningJob(context, job2);

        List<TenantProvisioning> list = repository.listProvisioningJobs(context, tenantId);
        assertEquals(2, list.size());
    }

    @Test
    public void testUpdateProvisioningStatus() {
        TenantProvisioning job = new TenantProvisioning();
        job.setTenantId(tenantId.toString());
        job.setProvisioningStatus("REQUESTED");
        TenantProvisioning created = repository.createProvisioningJob(context, job);

        UUID jobId = UUID.fromString(created.getProvisioningId());
        TenantProvisioning updated = repository.updateProvisioningStatus(context, jobId, "COMPLETED", "FINISHED");
        assertEquals("COMPLETED", updated.getProvisioningStatus());
    }
}
