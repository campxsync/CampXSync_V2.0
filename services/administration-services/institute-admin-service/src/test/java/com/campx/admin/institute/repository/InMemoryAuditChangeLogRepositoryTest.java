package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.AuditChangeLog;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class InMemoryAuditChangeLogRepositoryTest {

    private InMemoryAuditChangeLogRepository repository;
    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;
    private UUID tenantId;

    @Before
    public void setUp() {
        repository = new InMemoryAuditChangeLogRepository();
        tenantId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(UUID.randomUUID(), tenantId);
        otherTenantContext = new UserSecurityContext(UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    public void testRecordAndRetrieveChangeLog() {
        UUID recordId = UUID.randomUUID();
        AuditChangeLog log = new AuditChangeLog();
        log.setTableSchema("core");
        log.setTableName("departments");
        log.setRecordId(recordId.toString());
        log.setAction("U");
        log.setChangedFields(Arrays.asList("name", "status"));
        log.setOldData("{\"name\": \"Electrical\"}");
        log.setNewData("{\"name\": \"Electrical and Electronics\"}");
        log.setRequestId("REQ-12345");

        AuditChangeLog recorded = repository.recordChange(tenantContext, log);
        assertNotNull(recorded.getId());
        assertEquals("core", recorded.getTableSchema());
        assertEquals("departments", recorded.getTableName());
        assertEquals("U", recorded.getAction());
        assertEquals(2, recorded.getChangedFields().size());

        Optional<AuditChangeLog> byId = repository.findById(tenantContext, recorded.getId());
        assertTrue(byId.isPresent());
        assertEquals(recorded.getId(), byId.get().getId());

        List<AuditChangeLog> byRecord = repository.listChangesByRecord(tenantContext, "core", "departments", recordId.toString());
        assertEquals(1, byRecord.size());

        List<AuditChangeLog> recent = repository.listRecentChanges(tenantContext, 10);
        assertEquals(1, recent.size());

        // Isolation
        assertFalse(repository.findById(otherTenantContext, recorded.getId()).isPresent());
    }

    @Test(expected = MalformedPayloadException.class)
    public void testInvalidActionRejection() {
        AuditChangeLog log = new AuditChangeLog();
        log.setTableSchema("core");
        log.setTableName("colleges");
        log.setAction("X");
        repository.recordChange(tenantContext, log);
    }

    @Test(expected = SecurityViolationException.class)
    public void testMissingSecurityContext() {
        repository.recordChange(null, new AuditChangeLog());
    }
}
