package com.campx.student;

import com.campx.platform.outbox.OutboxEvent;
import com.campx.student.model.*;
import com.campx.student.repository.InMemoryGuardianshipRepository;
import com.campx.student.repository.InMemoryPersonRepository;
import com.campx.student.repository.InMemoryStudentRepository;
import com.campx.student.security.UserSecurityContext;
import com.campx.student.service.StudentDomainService;
import com.campx.student.service.StudentEventPublisher;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Contract verification test for Wave 1 Transactional Outbox integration.
 * Verifies that all student state mutations emit versioned outbox events.
 */
public class StudentDomainEventTest {

    private StudentDomainService service;
    private StudentEventPublisher.InMemory eventPublisher;
    private UserSecurityContext context;
    private UUID tenantId;
    private UUID collegeId;

    @Before
    public void setUp() {
        tenantId = UUID.randomUUID();
        collegeId = UUID.randomUUID();
        context = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "college_admin", false);

        InMemoryPersonRepository personRepo = new InMemoryPersonRepository();
        InMemoryStudentRepository studentRepo = new InMemoryStudentRepository(personRepo);
        InMemoryGuardianshipRepository guardianshipRepo = new InMemoryGuardianshipRepository();
        eventPublisher = new StudentEventPublisher.InMemory();

        service = new StudentDomainService(studentRepo, personRepo, guardianshipRepo, eventPublisher);
    }

    @Test
    public void testEnrollmentPublishesOutboxEvent() {
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setEnrollmentNo("EVT2026001");
        req.setFirstName("Esha");
        req.setLastName("Nair");
        req.setPrimaryEmail("esha.nair@campx.edu");
        req.setCollegeId(collegeId);

        StudentProfileResponse resp = service.enrollStudent(context, req);
        assertNotNull(resp);

        List<OutboxEvent> events = eventPublisher.getPublishedEvents();
        assertEquals(1, events.size());

        OutboxEvent event = events.get(0);
        assertEquals(tenantId, event.getTenantId());
        assertEquals("student-service", event.getService());
        assertEquals("STUDENT", event.getAggregateType());
        assertEquals(resp.getId(), event.getAggregateId());
        assertEquals("student.enrolled.v1", event.getEventType());
        assertEquals("1.0.0", event.getHeaders().getSchemaVersion());
        assertEquals(context.getUserId().toString(), event.getHeaders().getActorId());
        assertNotNull(event.getHeaders().getCorrelationId());

        // Verify payload content and data minimization
        assertTrue(event.getPayload().contains("EVT2026001"));
        assertFalse(event.getPayload().toLowerCase().contains("password"));
        assertFalse(event.getPayload().toLowerCase().contains("token"));
        assertFalse(event.getPayload().toLowerCase().contains("secret"));
    }

    @Test
    public void testLifecycleTransitionPublishesOutboxEvent() {
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setEnrollmentNo("EVT2026002");
        req.setFirstName("Gaurav");
        req.setLastName("Kumar");
        req.setPrimaryEmail("gaurav.kumar@campx.edu");
        req.setCollegeId(collegeId);

        StudentProfileResponse resp = service.enrollStudent(context, req);
        eventPublisher.clear();

        service.transitionLifecycle(context, resp.getId(),
                new UpdateStudentStatusRequest(StudentStatus.SUSPENDED, "Administrative inquiry"));

        List<OutboxEvent> events = eventPublisher.getPublishedEvents();
        assertEquals(1, events.size());

        OutboxEvent event = events.get(0);
        assertEquals("student.status_changed.v1", event.getEventType());
        assertEquals(resp.getId(), event.getAggregateId());
        assertTrue(event.getPayload().contains("ACTIVE"));
        assertTrue(event.getPayload().contains("SUSPENDED"));
        assertTrue(event.getPayload().contains("Administrative inquiry"));
    }
}
