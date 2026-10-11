package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.DataClassification;
import com.campx.admin.institute.model.InstituteModels.DataRetentionPolicy;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class InMemoryDataGovernanceRepositoryTest {

    private InMemoryDataGovernanceRepository repository;
    private UserSecurityContext tenantContext;
    private UUID tenantId;

    @Before
    public void setUp() {
        repository = new InMemoryDataGovernanceRepository();
        tenantId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(UUID.randomUUID(), tenantId);
    }

    @Test
    public void testRetentionPolicyLifecycle() {
        DataRetentionPolicy policy = new DataRetentionPolicy();
        policy.setPolicyCode("RET_STUDENT_PII");
        policy.setEntityType("STUDENT_RECORD");
        policy.setRetentionDays(1825); // 5 years
        policy.setAction("ANONYMIZE");

        DataRetentionPolicy created = repository.createRetentionPolicy(tenantContext, policy);
        assertNotNull(created.getId());
        assertEquals("RET_STUDENT_PII", created.getPolicyCode());
        assertEquals("ANONYMIZE", created.getAction());

        Optional<DataRetentionPolicy> byId = repository.findRetentionPolicyById(tenantContext, created.getId());
        assertTrue(byId.isPresent());
        assertEquals(created.getId(), byId.get().getId());

        Optional<DataRetentionPolicy> byCode = repository.findRetentionPolicyByCode(tenantContext, "ret_student_pii");
        assertTrue(byCode.isPresent());
        assertEquals(created.getId(), byCode.get().getId());

        created.setRetentionDays(2190); // 6 years
        DataRetentionPolicy updated = repository.updateRetentionPolicy(tenantContext, created);
        assertEquals(2190, updated.getRetentionDays());
        assertEquals(2, updated.getRowVersion());

        List<DataRetentionPolicy> list = repository.listRetentionPolicies(tenantContext);
        assertEquals(1, list.size());

        repository.deleteRetentionPolicy(tenantContext, created.getId());
        assertFalse(repository.findRetentionPolicyById(tenantContext, created.getId()).isPresent());
    }

    @Test
    public void testClassificationLifecycle() {
        DataClassification classification = new DataClassification();
        classification.setClassificationCode("STUDENT_HEALTH");
        classification.setSensitivityLevel("RESTRICTED");
        classification.setEncryptionRequired(true);

        DataClassification created = repository.createClassification(tenantContext, classification);
        assertNotNull(created.getId());
        assertEquals("STUDENT_HEALTH", created.getClassificationCode());
        assertEquals("RESTRICTED", created.getSensitivityLevel());
        assertTrue(created.isEncryptionRequired());

        Optional<DataClassification> byId = repository.findClassificationById(tenantContext, created.getId());
        assertTrue(byId.isPresent());
        assertEquals(created.getId(), byId.get().getId());

        Optional<DataClassification> byCode = repository.findClassificationByCode(tenantContext, "student_health");
        assertTrue(byCode.isPresent());
        assertEquals(created.getId(), byCode.get().getId());

        created.setSensitivityLevel("HIGH");
        DataClassification updated = repository.updateClassification(tenantContext, created);
        assertEquals("HIGH", updated.getSensitivityLevel());
        assertEquals(2, updated.getRowVersion());

        List<DataClassification> list = repository.listClassifications(tenantContext);
        assertEquals(1, list.size());

        repository.deleteClassification(tenantContext, created.getId());
        assertFalse(repository.findClassificationById(tenantContext, created.getId()).isPresent());
    }

    @Test(expected = MalformedPayloadException.class)
    public void testInvalidRetentionDaysRejection() {
        DataRetentionPolicy policy = new DataRetentionPolicy();
        policy.setPolicyCode("INVALID_RET");
        policy.setRetentionDays(0);
        repository.createRetentionPolicy(tenantContext, policy);
    }

    @Test(expected = SecurityViolationException.class)
    public void testMissingSecurityContext() {
        repository.createClassification(null, new DataClassification());
    }
}
