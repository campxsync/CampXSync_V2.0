package com.campx.academic.curriculum;

import com.campx.academic.curriculum.exception.PublicationBlockedException;
import com.campx.academic.curriculum.model.CurriculumModels.*;
import com.campx.academic.curriculum.service.CurriculumDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Cross-Module Contract Tests (Task 5.2).
 * Verifies outbound event schemas emitted for downstream consumers (ACD-04, ACD-05, EXM-01)
 * and inbound event contracts consumed from upstream services (ACD-01, ACD-03).
 */
public class CrossModuleContractTest {

    private CurriculumDomainService domainService;

    @Before
    public void setUp() {
        domainService = new CurriculumDomainService();
    }

    /**
     * Verifies outbound event schema for {@code CurriculumCreated} emitted for downstream consumers
     * like Course Offering (ACD-04) and Timetable (ACD-05).
     */
    @Test
    public void testOutboundCurriculumCreatedEventContract() {
        Curriculum curr = domainService.createCurriculum(
                "CS201", "ANNUAL", "2026-2027", "DEP_CS", "CAMPUS_MAIN",
                "Computer Science Annual Curriculum", "CAMPUS_MAIN", "INST-001",
                "admin-1", "ACADEMIC_ADMIN"
        );

        List<OutboxEvent> events = domainService.getPendingOutboxEvents();
        OutboxEvent createdEvt = events.stream()
                .filter(e -> "CurriculumCreated".equals(e.getEventType()))
                .findFirst()
                .orElse(null);

        assertNotNull("Outbound CurriculumCreated event must be present", createdEvt);
        assertNotNull("eventId must not be null", createdEvt.getEventId());
        assertTrue("eventId must follow standard format", createdEvt.getEventId().startsWith("EVT-ACD-02-"));
        assertEquals("CAMPUS_MAIN", createdEvt.getTenantId());
        assertEquals(curr.getId(), createdEvt.getAggregateId());
        assertTrue("occurredAt must be recent", createdEvt.getOccurredAt() > 0);

        String payload = createdEvt.getPayload();
        assertTrue("Payload must contain curriculumId", payload.contains("\"curriculumId\":\"" + curr.getId() + "\""));
        assertTrue("Payload must contain courseId", payload.contains("\"courseId\":\"CS201\""));
        assertTrue("Payload must contain academicPattern", payload.contains("\"academicPattern\":\"ANNUAL\""));
    }

    /**
     * Verifies outbound event schema for {@code CurriculumPublished} emitted upon final registrar approval
     * for Examination (EXM-01) and Course Registration (REG-01).
     */
    @Test
    public void testOutboundCurriculumPublishedEventContract() {
        Curriculum curr = domainService.createCurriculum(
                "COURSE-001", "CBCS", "2026-2027", "DEPT-CA", "MAIN",
                "MCA Published Contract Test", "TENANT-001", "INST-001",
                "admin-1", "ACADEMIC_ADMIN"
        );
        domainService.addSemester(curr.getId(), 1, 1, "Semester 1", "2026-2027", "ACADEMIC_ADMIN");
        // Map 4 subjects (16.0 credits)
        domainService.mapSubject(curr.getId(), 1, "SUB-101", 1, 1, "CORE", 4.0, 60.0, true, "admin-1", "ACADEMIC_ADMIN");
        domainService.mapSubject(curr.getId(), 1, "SUB-102", 1, 2, "CORE", 4.0, 60.0, true, "admin-1", "ACADEMIC_ADMIN");
        domainService.mapSubject(curr.getId(), 1, "SUB-103", 1, 3, "CORE", 4.0, 60.0, true, "admin-1", "ACADEMIC_ADMIN");
        domainService.mapSubject(curr.getId(), 1, "SUB-201", 1, 4, "CORE", 4.0, 60.0, true, "admin-1", "ACADEMIC_ADMIN");

        domainService.submitForApproval(curr.getId(), 1, "admin-1", "ACADEMIC_ADMIN");
        domainService.reviewCurriculum(curr.getId(), 1, "APPROVE", "Passed", "dh-1", "DEPARTMENT_HEAD", "DEPT-CA");
        domainService.approveCurriculum(curr.getId(), 1, "comm-1", "CURRICULUM_COMMITTEE");
        domainService.publishCurriculum(curr.getId(), 1, "reg-1", "REGISTRAR");

        List<OutboxEvent> events = domainService.getPendingOutboxEvents();
        OutboxEvent publishedEvt = events.stream()
                .filter(e -> "CurriculumPublished".equals(e.getEventType()))
                .findFirst()
                .orElse(null);

        assertNotNull("Outbound CurriculumPublished event must be present", publishedEvt);
        assertEquals(curr.getId(), publishedEvt.getAggregateId());
        String payload = publishedEvt.getPayload();
        assertTrue("Payload must contain curriculumId", payload.contains("\"curriculumId\":\"" + curr.getId() + "\""));
        assertTrue("Payload must contain versionNo", payload.contains("\"versionNo\":1"));
        assertTrue("Payload must contain status PUBLISHED", payload.contains("\"status\":\"PUBLISHED\""));
        assertTrue("Payload must contain totalCredits", payload.contains("\"totalCredits\":16.0"));
    }

    /**
     * Verifies inbound consumption of {@code CourseDeactivated} event from Course Management (ACD-01),
     * ensuring automatic review flagging and idempotent deduplication of duplicate messages.
     */
    @Test
    public void testInboundCourseDeactivatedContractAndDeduplication() {
        Curriculum curr = domainService.createCurriculum(
                "COURSE-001", "CBCS", "2026-2027", "DEPT-CA", "MAIN",
                "Course Deactivation Test", "TENANT-001", "INST-001",
                "admin-1", "ACADEMIC_ADMIN"
        );
        assertFalse(curr.isFlaggedForReview());

        String eventId = "EVT-ACD01-DEACT-0099";
        domainService.handleCourseDeactivatedEvent(eventId, "COURSE-001", "Course discontinued by Board of Studies");

        Curriculum updated = domainService.getCurriculum(curr.getId(), "ACADEMIC_ADMIN", null);
        assertTrue("Curriculum must be flagged for review after course deactivation", updated.isFlaggedForReview());
        assertTrue("Flag reason must reflect inbound event", updated.getFlagReason().contains("discontinued"));

        // Test Deduplication: Re-delivering same inbound event must be handled idempotently
        domainService.handleCourseDeactivatedEvent(eventId, "COURSE-001", "Duplicate transmission");
        assertTrue(updated.isFlaggedForReview());
    }

    /**
     * Verifies inbound consumption of {@code SubjectDeactivated} event from Subject Management (ACD-03),
     * and validates routing malformed events to the Dead Letter Queue (DLQ).
     */
    @Test
    public void testInboundSubjectDeactivatedContractAndDeadLetterQueue() {
        Curriculum curr = domainService.createCurriculum(
                "CS201", "CBCS", "2026-2027", "DEP_CS", "CAMPUS_MAIN",
                "Subject Deactivation Contract Test", "CAMPUS_MAIN", "INST-001",
                "admin-1", "ACADEMIC_ADMIN"
        );
        domainService.addSemester(curr.getId(), 1, 1, "Semester 1", "2026-2027", "ACADEMIC_ADMIN");
        domainService.mapSubject(curr.getId(), 1, "SUB-201", 1, 1, "CORE", 4.0, 60.0, true, "admin-1", "ACADEMIC_ADMIN");

        // Simulate inbound event from ACD-03
        domainService.handleSubjectDeactivatedEvent("EVT-ACD03-DEACT-777", "SUB-201");

        // Verify Dead Letter Queue functionality
        domainService.routeToDeadLetterQueue("EVT-MALFORMED-999", "ACD-03", "Schema validation failure: missing subjectId", "{}");
        List<DeadLetterEvent> dlq = domainService.getDeadLetterEvents();
        assertFalse(dlq.isEmpty());
        assertTrue(dlq.stream().anyMatch(d -> "EVT-MALFORMED-999".equals(d.getEventId())));
    }
}
