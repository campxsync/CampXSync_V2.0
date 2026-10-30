package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.AccessEvent;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class InMemoryAccessEventRepositoryTest {

    private InMemoryAccessEventRepository repository;
    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;
    private UUID tenantId;

    @Before
    public void setUp() {
        repository = new InMemoryAccessEventRepository();
        tenantId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(UUID.randomUUID(), tenantId);
        otherTenantContext = new UserSecurityContext(UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    public void testRecordAndRetrieveEvent() {
        AccessEvent event = new AccessEvent();
        event.setResourceType("STUDENT_RECORD");
        event.setResourceId(UUID.randomUUID().toString());
        event.setAccessType("READ");
        event.setSensitivity("RESTRICTED");
        event.setIp("10.0.0.15");
        event.setReason("Grade inspection");

        AccessEvent recorded = repository.recordEvent(tenantContext, event);
        assertNotNull(recorded.getId());
        assertEquals("STUDENT_RECORD", recorded.getResourceType());
        assertEquals("READ", recorded.getAccessType());

        Optional<AccessEvent> byId = repository.findById(tenantContext, recorded.getId());
        assertTrue(byId.isPresent());
        assertEquals(recorded.getId(), byId.get().getId());

        List<AccessEvent> byResource = repository.listEventsByResource(tenantContext, "STUDENT_RECORD", event.getResourceId());
        assertEquals(1, byResource.size());

        List<AccessEvent> recent = repository.listRecentEvents(tenantContext, 10);
        assertEquals(1, recent.size());

        // Isolation
        assertFalse(repository.findById(otherTenantContext, recorded.getId()).isPresent());
    }

    @Test(expected = MalformedPayloadException.class)
    public void testInvalidAccessTypeRejection() {
        AccessEvent event = new AccessEvent();
        event.setResourceType("DOCUMENT");
        event.setAccessType("INVALID_TYPE");
        repository.recordEvent(tenantContext, event);
    }

    @Test(expected = SecurityViolationException.class)
    public void testMissingSecurityContext() {
        repository.recordEvent(null, new AccessEvent());
    }
}
