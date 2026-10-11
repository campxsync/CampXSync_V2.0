package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.NumberSequence;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class InMemoryNumberSequenceRepositoryTest {

    private InMemoryNumberSequenceRepository repository;
    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;
    private UUID tenantId;

    @Before
    public void setUp() {
        repository = new InMemoryNumberSequenceRepository();
        tenantId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(UUID.randomUUID(), tenantId);
        otherTenantContext = new UserSecurityContext(UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    public void testCreateAndRetrieveSequence() {
        NumberSequence seq = new NumberSequence();
        seq.setScopeKey("INVOICE_NUM");
        seq.setPrefix("INV-2026-");
        seq.setSuffix("-A");
        seq.setNextValue(1);
        seq.setPadding((short) 5);
        seq.setResetPolicy("YEARLY");

        NumberSequence created = repository.createSequence(tenantContext, seq);
        assertNotNull(created.getId());
        assertEquals("INVOICE_NUM", created.getScopeKey());
        assertEquals("INV-2026-", created.getPrefix());

        Optional<NumberSequence> byId = repository.findById(tenantContext, created.getId());
        assertTrue(byId.isPresent());
        assertEquals(created.getId(), byId.get().getId());

        Optional<NumberSequence> byScope = repository.findByScope(tenantContext, "invoice_num", null);
        assertTrue(byScope.isPresent());
        assertEquals(created.getId(), byScope.get().getId());

        List<NumberSequence> list = repository.listSequences(tenantContext);
        assertEquals(1, list.size());

        // Isolation
        assertFalse(repository.findById(otherTenantContext, created.getId()).isPresent());
        assertFalse(repository.findByScope(otherTenantContext, "INVOICE_NUM", null).isPresent());
    }

    @Test
    public void testGenerateNextNumber() {
        NumberSequence seq = new NumberSequence();
        seq.setScopeKey("STUDENT_ROLL");
        seq.setPrefix("STU-");
        seq.setNextValue(1);
        seq.setPadding((short) 4);
        repository.createSequence(tenantContext, seq);

        String num1 = repository.generateNextNumber(tenantContext, "STUDENT_ROLL", null);
        assertEquals("STU-0001", num1);

        String num2 = repository.generateNextNumber(tenantContext, "STUDENT_ROLL", null);
        assertEquals("STU-0002", num2);

        String num3 = repository.generateNextNumber(tenantContext, "STUDENT_ROLL", null);
        assertEquals("STU-0003", num3);
    }

    @Test
    public void testUpdateAndDeleteSequence() {
        NumberSequence seq = new NumberSequence();
        seq.setScopeKey("COURSE_SEQ");
        seq.setPrefix("CRS-");
        seq.setNextValue(100);
        NumberSequence created = repository.createSequence(tenantContext, seq);

        created.setPrefix("NEW-CRS-");
        NumberSequence updated = repository.updateSequence(tenantContext, created);
        assertEquals("NEW-CRS-", updated.getPrefix());
        assertEquals(2, updated.getRowVersion());

        repository.deleteSequence(tenantContext, created.getId());
        assertFalse(repository.findById(tenantContext, created.getId()).isPresent());
    }

    @Test(expected = MalformedPayloadException.class)
    public void testDuplicateScopeKeyRejection() {
        NumberSequence s1 = new NumberSequence();
        s1.setScopeKey("DUP_SCOPE");
        repository.createSequence(tenantContext, s1);

        NumberSequence s2 = new NumberSequence();
        s2.setScopeKey("DUP_SCOPE");
        repository.createSequence(tenantContext, s2);
    }

    @Test(expected = SecurityViolationException.class)
    public void testMissingSecurityContext() {
        repository.createSequence(null, new NumberSequence());
    }
}
