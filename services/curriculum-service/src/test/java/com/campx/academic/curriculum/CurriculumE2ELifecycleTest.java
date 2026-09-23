package com.campx.academic.curriculum;

import com.campx.academic.curriculum.exception.CurriculumValidationException;
import com.campx.academic.curriculum.exception.PrerequisiteCycleException;
import com.campx.academic.curriculum.exception.PublicationBlockedException;
import com.campx.academic.curriculum.model.CurriculumModels.*;
import com.campx.academic.curriculum.service.CurriculumDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * End-to-End lifecycle tests verifying the complete Curriculum lifecycle (Task 5.1):
 * DRAFT -> SEMESTERS -> SUBJECTS -> SYLLABUS -> OUTCOMES -> PREREQUISITES (with DAG cycle detection)
 * -> SUBMITTED -> REVIEWED -> APPROVED -> PUBLISHED -> ANNUAL REVISION (SUPERSEDED)
 * -> INBOUND ACD-03 SubjectDeactivated BLOCKING PUBLICATION.
 */
public class CurriculumE2ELifecycleTest {

    private CurriculumDomainService domainService;

    @Before
    public void setUp() {
        domainService = new CurriculumDomainService();
    }

    /**
     * Executes the complete curriculum end-to-end lifecycle from creation to annual revision:
     * Draft -> Semesters -> Subject Mapping -> Syllabus -> Outcomes -> Prerequisites ->
     * Approval Workflow -> Publishing -> Annual Revision (Clone & Version Increment).
     */
    @Test
    public void testFullCurriculumEndToEndLifecycle() {
        String actorId = "user-academic-admin";
        String adminRole = "ACADEMIC_ADMIN";
        String deptHeadRole = "DEPARTMENT_HEAD";
        String committeeRole = "CURRICULUM_COMMITTEE";
        String registrarRole = "REGISTRAR";

        // 1. Create Draft Curriculum (Epic 1 & 2)
        Curriculum curriculum = domainService.createCurriculum(
                "COURSE-001", "CBCS", "2026-2027", "DEPT-CA", "MAIN",
                "Master of Computer Applications 2026", "TENANT-001", "INST-001",
                actorId, adminRole
        );
        assertNotNull(curriculum);
        assertEquals("COURSE-001", curriculum.getCourseId());
        assertEquals(CurriculumStatus.DRAFT, curriculum.getStatus());
        assertEquals(1, curriculum.getCurrentVersion());

        String currId = curriculum.getId();

        // Verify CurriculumCreated outbox event
        List<OutboxEvent> outbox = domainService.getPendingOutboxEvents();
        assertTrue(outbox.stream().anyMatch(e -> "CurriculumCreated".equals(e.getEventType()) && currId.equals(e.getAggregateId())));

        // 2. Add Semesters (Epic 4)
        Semester sem1 = domainService.addSemester(currId, 1, 1, "Semester 1", "2026-2027", adminRole);
        Semester sem2 = domainService.addSemester(currId, 1, 2, "Semester 2", "2026-2027", adminRole);
        assertNotNull(sem1);
        assertNotNull(sem2);

        // 3. Map Active Subjects meeting Min Credit Policy (16.0 credits) (Epic 4 & 5)
        CurriculumSubject sub1 = domainService.mapSubject(currId, 1, "SUB-101", 1, 1, "CORE", 4.0, 60.0, true, actorId, adminRole);
        CurriculumSubject sub2 = domainService.mapSubject(currId, 1, "SUB-102", 1, 2, "CORE", 4.0, 60.0, true, actorId, adminRole);
        CurriculumSubject sub3 = domainService.mapSubject(currId, 1, "SUB-103", 2, 1, "CORE", 4.0, 60.0, true, actorId, adminRole);
        CurriculumSubject sub4 = domainService.mapSubject(currId, 1, "SUB-201", 2, 2, "ELECTIVE", 4.0, 60.0, false, actorId, adminRole);
        assertNotNull(sub1);
        assertNotNull(sub2);
        assertNotNull(sub3);
        assertNotNull(sub4);

        CurriculumVersion ver1 = domainService.getVersion(currId, 1);
        assertEquals(16.0, ver1.getTotalCredits(), 0.001);

        // 4. Set Syllabus Modules (Epic 5)
        SyllabusModule mod1 = new SyllabusModule("MOD-1", "Advanced Java Fundamentals", 1,
                Arrays.asList("Streams", "Concurrency", "Generics"), 15.0);
        SyllabusModule mod2 = new SyllabusModule("MOD-2", "Database Engineering", 2,
                Arrays.asList("ACID", "Distributed Transactions", "Sharding"), 15.0);
        domainService.updateSyllabus(currId, 1, Arrays.asList(mod1, mod2), actorId, adminRole);
        assertEquals(2, domainService.getSyllabus(currId, 1).size());

        // 5. Add Learning Outcomes (Bloom's Taxonomy) (Epic 6)
        CurriculumOutcome co1 = domainService.addOutcome(currId, 1, "CO-01",
                "Apply distributed systems design patterns", "APPLY", "COURSE_OUTCOME",
                "SUBJECT", "SUB-101", actorId, adminRole);
        assertNotNull(co1);
        assertEquals("CO-01", co1.getOutcomeCode());
        assertEquals(1, domainService.listOutcomes(currId, 1).size());

        // 6. Prerequisite Management & Cycle Detection (Epic 7)
        Curriculum prerequisiteCurr = domainService.createCurriculum(
                "CS101", "SEMESTER", "2026-2027", "DEPT-CA", "MAIN",
                "BSc Computer Science", "CAMPUS_MAIN", "INST-001",
                actorId, adminRole
        );
        domainService.addPrerequisite(currId, "CS101", prerequisiteCurr.getId(), "MANDATORY", "PASS", actorId, adminRole);
        assertEquals(1, domainService.listPrerequisites(currId).size());

        // Verify DAG cycle detection rejects self-referential or circular dependencies
        try {
            domainService.addPrerequisite(prerequisiteCurr.getId(), "COURSE-001", currId, "MANDATORY", "PASS", actorId, adminRole);
            fail("Expected PrerequisiteCycleException when adding circular dependency");
        } catch (PrerequisiteCycleException expected) {
            assertTrue(expected.getMessage().contains("cyclic dependency"));
        }

        // 7. Submit Curriculum Version for Approval (Epic 8)
        CurriculumVersion submittedVer = domainService.submitForApproval(currId, 1, actorId, adminRole);
        assertEquals(VersionStatus.REVIEW, submittedVer.getStatus());
        assertEquals(CurriculumStatus.REVIEW, domainService.getCurriculum(currId, adminRole, null).getStatus());

        // 8. Review Curriculum by Department Head (Epic 8)
        CurriculumVersion reviewedVer = domainService.reviewCurriculum(
                currId, 1, "APPROVE", "Meets all NBA accreditation criteria", "dept-head-1", deptHeadRole, "DEPT-CA"
        );
        assertEquals(VersionStatus.REVIEW, reviewedVer.getStatus());

        // 9. Approve Curriculum by Curriculum Committee (Epic 8)
        CurriculumVersion approvedVer = domainService.approveCurriculum(
                currId, 1, "committee-chair-1", committeeRole
        );
        assertEquals(VersionStatus.APPROVED, approvedVer.getStatus());
        assertEquals(CurriculumStatus.APPROVED, domainService.getCurriculum(currId, adminRole, null).getStatus());

        // 10. Publish Curriculum by Registrar (Epic 8)
        CurriculumVersion publishedVer = domainService.publishCurriculum(currId, 1, "registrar-1", registrarRole);
        assertEquals(VersionStatus.PUBLISHED, publishedVer.getStatus());
        assertEquals(CurriculumStatus.PUBLISHED, domainService.getCurriculum(currId, adminRole, null).getStatus());

        // Verify active list includes published curriculum
        List<Curriculum> activeList = domainService.listActiveCurricula();
        assertTrue(activeList.stream().anyMatch(c -> currId.equals(c.getId())));

        // 11. Annual Revision: Clone published version into Draft Version 2 (Epic 3)
        CurriculumVersion clonedDraft = domainService.cloneForAnnualRevision(currId, "2027-2028", actorId, adminRole);
        assertNotNull(clonedDraft);
        assertEquals(2, clonedDraft.getVersionNo());
        assertEquals(VersionStatus.DRAFT, clonedDraft.getStatus());
        assertEquals("2027-2028", clonedDraft.getAcademicYear());

        // Prior version remains published until new version is published
        assertEquals(VersionStatus.PUBLISHED, domainService.getVersion(currId, 1).getStatus());

        // 12. Inbound ACD-03 Event: SubjectDeactivated blocks publishing
        // Progress version 2 to APPROVED
        domainService.submitForApproval(currId, 2, actorId, adminRole);
        domainService.reviewCurriculum(currId, 2, "APPROVE", "Approved revision", "dept-head-1", deptHeadRole, "DEPT-CA");
        domainService.approveCurriculum(currId, 2, "committee-chair-1", committeeRole);

        // Deactivate mapped subject SUB-101 via inbound event from ACD-03
        domainService.handleSubjectDeactivatedEvent("EVT-ACD03-DEACT-01", "SUB-101");

        // Attempting to publish version 2 must be blocked with PublicationBlockedException
        try {
            domainService.publishCurriculum(currId, 2, "registrar-1", registrarRole);
            fail("Expected PublicationBlockedException due to deactivated subject");
        } catch (PublicationBlockedException pbe) {
            assertTrue(pbe.getMessage().contains("deactivated in ACD-03"));
        }
    }
}
