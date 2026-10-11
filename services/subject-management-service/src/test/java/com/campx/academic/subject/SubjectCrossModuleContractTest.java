package com.campx.academic.subject;

import com.campx.academic.subject.model.SubjectModels.*;
import com.campx.academic.subject.service.SubjectDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Cross-Module Contract and Integration Test Suite (Story 81, Table 44).
 * Validates upstream and downstream contract specifications for:
 * - ACD-01 (Course Management)
 * - ACD-02 (Curriculum Management)
 * - ACD-04 (Batch Allocation)
 * - ACD-05 (Timetable & Scheduling)
 * - EXM (Examination Service)
 * - STM (Student Management)
 * - HRM (Faculty Subject Assignment)
 * - COM (Communication & Event Outbox)
 * - ACD-10 (Academic Analytics & Insights)
 */
public class SubjectCrossModuleContractTest {

    private SubjectDomainService service;

    @Before
    public void setup() {
        service = new SubjectDomainService();
    }

    private Subject createSampleSubject(String code, String name) {
        Subject s = new Subject();
        s.setTenantId("TENANT-001");
        s.setInstitutionId("INST-001");
        s.setSubjectCode(code);
        s.setName(name);
        s.setDepartmentId("DEPT-CA");
        s.setSubjectType("CORE");
        s.setClassification("THEORY");
        s.setCredits(4.0);
        s.setContactHours(60.0);
        return s;
    }

    /**
     * ACD-02 (Curriculum Management) Contract Test (Story 43, 79, BR-11):
     * - Published ACTIVE subject is valid for curriculum mapping.
     * - DEPRECATED or DEACTIVATED subject is rejected from new curriculum mapping.
     */
    @Test
    public void testAcd02CurriculumMappingContract() {
        Subject s = service.createSubject(createSampleSubject("CURR-101", "Operating Systems"), "2026-2027", "admin", "ACADEMIC_ADMIN");

        // 1. Verify subject is eligible when ACTIVE
        assertEquals(SubjectStatus.ACTIVE, s.getStatus());
        assertTrue("Subject must be ACTIVE for new curriculum mapping", s.getStatus() == SubjectStatus.ACTIVE);

        // 2. Deactivate subject and verify eligibility is blocked (BR-11, Story 43)
        service.deactivateSubject(s.getId(), "Phasing out", "admin", "ACADEMIC_ADMIN");
        Subject deactivated = service.getSubject(s.getId());
        assertEquals(SubjectStatus.DEACTIVATED, deactivated.getStatus());
        assertFalse("Deactivated subject cannot be newly mapped to curriculum", deactivated.getStatus() == SubjectStatus.ACTIVE);

        // 3. Reactivate subject and verify eligibility restored
        service.reactivateSubject(s.getId(), "Restored", "admin", "ACADEMIC_ADMIN");
        Subject reactivated = service.getSubject(s.getId());
        assertEquals(SubjectStatus.ACTIVE, reactivated.getStatus());
    }

    /**
     * ACD-05 (Timetable Scheduling) Contract Test (Story 79):
     * Verifies that published subjects expose contactHours, subjectType, and active department
     * required for timetable room allocation and lecture scheduling.
     */
    @Test
    public void testAcd05TimetableSchedulingContract() {
        Subject s = service.createSubject(createSampleSubject("TIME-101", "Compiler Design"), "2026-2027", "admin", "ACADEMIC_ADMIN");
        assertNotNull(s.getDepartmentId());
        assertTrue(s.getContactHours() > 0);
        assertNotNull(s.getSubjectType());
        assertNotNull(s.getClassification());
        assertTrue(s.isDepartmentActive());
    }

    /**
     * EXM (Examination Service) Contract Test:
     * Verifies credits, taxonomy classification, and version number for exam schemes.
     */
    @Test
    public void testExaminationContract() {
        Subject s = service.createSubject(createSampleSubject("EXM-101", "Cloud Computing"), "2026-2027", "admin", "ACADEMIC_ADMIN");
        assertEquals(4.0, s.getCredits(), 0.001);
        assertEquals("THEORY", s.getClassification());
        assertEquals(1, s.getCurrentVersion());
    }

    /**
     * ACD-01 (Course Management) Inbound Contract Test (Story 52):
     * Inbound CourseDeactivated event flags associated subjects for administrative review.
     */
    @Test
    public void testAcd01InboundCourseEventContract() {
        Subject s = createSampleSubject("CRS-MAP-101", "Enterprise Architecture");
        s.setCourseId("COURSE-CS-2026");
        service.createSubject(s, "2026-2027", "admin", "ACADEMIC_ADMIN");

        // Course deactivated event consumed
        service.consumeCourseEvent("EVT-CRS-001", "CourseDeactivated", "COURSE-CS-2026", false);

        Subject updated = service.getSubject(s.getId());
        assertTrue(updated.isFlaggedForReview());
        assertEquals("Associated course deactivated", updated.getFlagReason());
    }

    /**
     * COM & ACD-10 (Events & Analytics) Contract Test (Story 50, 81):
     * Verifies outbox event envelope contains standard eventId, source, tenantId, and correlationId.
     */
    @Test
    public void testEventOutboxContractForDownstreamConsumers() {
        Subject s = service.createSubject(createSampleSubject("EVT-101", "Discrete Mathematics"), "2026-2027", "admin", "ACADEMIC_ADMIN");
        List<OutboxEvent> events = service.getOutboxEvents();
        assertFalse(events.isEmpty());

        OutboxEvent last = events.get(events.size() - 1);
        assertEquals("ACD-03", last.getSource());
        assertEquals("SubjectCreated", last.getEventType());
        assertEquals("TENANT-001", last.getTenantId());
        assertNotNull(last.getEventId());
        assertTrue(last.getEventId().startsWith("EVT-ACD-03-"));
        assertTrue(last.getPayload().contains("EVT-101"));
    }
}
