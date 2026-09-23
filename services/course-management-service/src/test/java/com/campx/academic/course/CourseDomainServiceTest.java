package com.campx.academic.course;

import com.campx.academic.course.exception.*;
import com.campx.academic.course.model.CourseModels.*;
import com.campx.academic.course.service.CourseDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit test suite verifying domain logic, validations, lifecycle state machine,
 * DAG cycle detection, batch offerings guard, outbox events, and RBAC matrix for ACD-01.
 */
public class CourseDomainServiceTest {

    /**
     * Fresh domain service instance for each test.
     */
    private CourseDomainService domainService;

    /**
     * Initializes a fresh {@link CourseDomainService} before each test executes.
     */
    @Before
    public void setUp() {
        domainService = new CourseDomainService();
    }

    @Test
    public void testCreateDraftCourseSuccess() {
        Course course = new Course();
        course.setCourseCode("CS201");
        course.setCourseName("Data Structures and Algorithms");
        course.setDescription("Study of linear and non-linear data structures.");
        course.setDepartmentId("DEP_CS");
        course.setTotalCredits(4.0);
        course.setDurationYears(1);

        Course created = domainService.createDraftCourse(course);

        assertNotNull(created.getId());
        assertEquals("CS201", created.getCourseCode());
        assertEquals("DRAFT", created.getStatus());
        assertEquals(1, created.getCurrentVersion());

        // Verify audit history
        List<CourseHistory> history = domainService.getCourseHistory(created.getId());
        assertFalse(history.isEmpty());
        assertEquals("CREATE", history.get(0).getAction());

        // Verify outbox event emitted
        List<OutboxEvent> outbox = domainService.getOutboxEvents();
        boolean found = outbox.stream().anyMatch(e -> "CourseCreated".equals(e.getEventType()) && e.getAggregateId().equals(created.getId()));
        assertTrue("Outbox must contain CourseCreated event", found);
    }

    @Test
    public void testCourseCodeNormalizationAndUniqueness() {
        Course c1 = new Course();
        c1.setCourseCode("  cs301  "); // Needs normalization to CS301
        c1.setCourseName("Database Systems");
        c1.setDepartmentId("DEP_CS");
        c1.setTotalCredits(3.5);
        domainService.createDraftCourse(c1);

        assertEquals("CS301", c1.getCourseCode());

        // Attempting to create course with same code in different casing/spacing must throw 409 Conflict
        Course c2 = new Course();
        c2.setCourseCode("CS301");
        c2.setCourseName("Database Systems Duplicate");
        c2.setDepartmentId("DEP_CS");
        c2.setTotalCredits(3.5);

        try {
            domainService.createDraftCourse(c2);
            fail("Expected CourseCodeConflictException on duplicate courseCode");
        } catch (CourseCodeConflictException e) {
            assertEquals(409, e.getStatus());
            assertEquals("ACD_COURSE_CODE_EXISTS", e.getErrorCode());
        }
    }

    @Test
    public void testTotalCreditsMustBeGreaterThanZero() {
        Course c = new Course();
        c.setCourseCode("CS_INVALID_CREDITS");
        c.setCourseName("Zero Credit Course");
        c.setDepartmentId("DEP_CS");
        c.setTotalCredits(0.0);

        try {
            domainService.createDraftCourse(c);
            fail("Expected CourseValidationException on totalCredits <= 0");
        } catch (CourseValidationException e) {
            assertEquals(400, e.getStatus());
            assertTrue(e.getMessage().contains("totalCredits must be greater than zero"));
        }
    }

    @Test
    public void testDepartmentValidation() {
        Course c = new Course();
        c.setCourseCode("CS_INVALID_DEPT");
        c.setCourseName("Course with Invalid Department");
        c.setDepartmentId("DEP_NON_EXISTENT");
        c.setTotalCredits(4.0);

        try {
            domainService.createDraftCourse(c);
            fail("Expected CourseValidationException on non-existent department");
        } catch (CourseValidationException e) {
            assertEquals(400, e.getStatus());
            assertEquals("ACD_INVALID_DEPARTMENT", e.getErrorCode());
        }
    }

    @Test
    public void testDraftUpdateSuccessAndActiveUpdateBlocked() {
        Course c = new Course();
        c.setCourseCode("CS401");
        c.setCourseName("Computer Networks");
        c.setDepartmentId("DEP_CS");
        c.setTotalCredits(4.0);
        domainService.createDraftCourse(c);

        // Update draft course succeeds
        Course update = new Course();
        update.setCourseName("Advanced Computer Networks");
        update.setTotalCredits(4.5);
        Course updated = domainService.updateDraftCourse(c.getId(), update);
        assertEquals("Advanced Computer Networks", updated.getCourseName());
        assertEquals(4.5, updated.getTotalCredits(), 0.001);

        // Transition course to ACTIVE
        domainService.submitForApproval(c.getId());
        domainService.approveCourse(c.getId());
        domainService.publishCourse(c.getId());
        assertEquals("ACTIVE", c.getStatus());

        // Attempting direct in-place update on ACTIVE course must fail (BR-11)
        try {
            domainService.updateDraftCourse(c.getId(), update);
            fail("Expected InvalidCourseStateException when mutating published course directly");
        } catch (InvalidCourseStateException e) {
            assertEquals(422, e.getStatus());
            assertTrue(e.getMessage().contains("new version workflow"));
        }
    }

    @Test
    public void testVersioningSnapshotCreation() {
        Course c = new Course();
        c.setCourseCode("CS501");
        c.setCourseName("Distributed Computing");
        c.setDepartmentId("DEP_CS");
        c.setTotalCredits(4.0);
        domainService.createDraftCourse(c);

        CourseVersion v2 = domainService.createNewVersion(c.getId(), "Major curriculum revision", 0L);
        assertNotNull(v2.getId());
        assertEquals(2, v2.getVersionNo());
        assertEquals("DRAFT", v2.getStatus());
        assertEquals(2, c.getCurrentVersion());

        List<CourseVersion> versions = domainService.getCourseVersions(c.getId());
        assertEquals(1, versions.size()); // initial seed had none, now 1
    }

    @Test
    public void testPrerequisiteAdditionAndCycleDetection() {
        // Create 3 courses: A, B, C
        Course a = new Course();
        a.setCourseCode("MATH101");
        a.setCourseName("Calculus I");
        a.setDepartmentId("DEP_MATH");
        a.setTotalCredits(4.0);
        domainService.createDraftCourse(a);

        Course b = new Course();
        b.setCourseCode("MATH201");
        b.setCourseName("Calculus II");
        b.setDepartmentId("DEP_MATH");
        b.setTotalCredits(4.0);
        domainService.createDraftCourse(b);

        Course c = new Course();
        c.setCourseCode("MATH301");
        c.setCourseName("Differential Equations");
        c.setDepartmentId("DEP_MATH");
        c.setTotalCredits(4.0);
        domainService.createDraftCourse(c);

        // B depends on A (MATH201 -> MATH101)
        domainService.addPrerequisite(b.getId(), a.getId(), "MANDATORY", "C");

        // C depends on B (MATH301 -> MATH201)
        domainService.addPrerequisite(c.getId(), b.getId(), "MANDATORY", "C");

        // Self-dependency cycle check: A -> A
        try {
            domainService.addPrerequisite(a.getId(), a.getId(), "MANDATORY", "C");
            fail("Expected PrerequisiteCycleException on self prerequisite");
        } catch (PrerequisiteCycleException e) {
            assertEquals(422, e.getStatus());
            assertEquals("ACD_PREREQUISITE_CYCLE", e.getErrorCode());
        }

        // Transitive cycle check: adding A -> C (Cycle: A -> C -> B -> A)
        try {
            domainService.addPrerequisite(a.getId(), c.getId(), "MANDATORY", "C");
            fail("Expected PrerequisiteCycleException on transitive cycle");
        } catch (PrerequisiteCycleException e) {
            assertEquals(422, e.getStatus());
            assertEquals("ACD_PREREQUISITE_CYCLE", e.getErrorCode());
            assertTrue(e.getMessage().contains("MATH101") && e.getMessage().contains("MATH301"));
        }

        // Verify valid prerequisites list for C
        List<CoursePrerequisite> prereqs = domainService.getPrerequisites(c.getId());
        assertEquals(1, prereqs.size());
        assertEquals(b.getId(), prereqs.get(0).getPrerequisiteCourseId());
    }

    @Test
    public void testBatchOfferingsBlocksDeactivationUntilClosed() {
        Course c = new Course();
        c.setCourseCode("EC101");
        c.setCourseName("Basic Electronics");
        c.setDepartmentId("DEP_EC");
        c.setTotalCredits(3.0);
        domainService.createDraftCourse(c);
        domainService.submitForApproval(c.getId());
        domainService.approveCourse(c.getId());
        domainService.publishCourse(c.getId());
        assertEquals("ACTIVE", c.getStatus());

        // Add active batch offering
        CourseBatchOffering bo = domainService.addBatchOffering(c.getId(), "BATCH_2026_ECE", "TERM_1");
        assertEquals("ACTIVE", bo.getStatus());

        // Deactivation must be blocked while active batch offerings exist (BR-08, Story 44, 83)
        try {
            domainService.deactivateCourse(c.getId());
            fail("Expected ActiveBatchOfferingsException when active batch offerings exist");
        } catch (ActiveBatchOfferingsException e) {
            assertEquals(422, e.getStatus());
            assertEquals("ACD_ACTIVE_BATCH_EXISTS", e.getErrorCode());
        }

        // Close the batch offering
        domainService.closeBatchOffering(bo.getId());

        // Deactivation should now succeed!
        Course deactivated = domainService.deactivateCourse(c.getId());
        assertEquals("DEACTIVATED", deactivated.getStatus());
    }

    @Test
    public void testMandatoryAccreditationForRegulatoryCourse() {
        Course c = new Course();
        c.setCourseCode("LAW101");
        c.setCourseName("Constitutional Law");
        c.setDepartmentId("DEP_CS");
        c.setCourseCategory("REGULATORY"); // Flagged regulatory (Story 36)
        c.setTotalCredits(4.0);
        domainService.createDraftCourse(c);
        domainService.submitForApproval(c.getId());
        domainService.approveCourse(c.getId());

        // Publish without accreditation must be blocked
        try {
            domainService.publishCourse(c.getId());
            fail("Expected CourseValidationException when publishing regulatory course without accreditation");
        } catch (CourseValidationException e) {
            assertEquals(400, e.getStatus());
            assertEquals("ACD_MANDATORY_ACCREDITATION_MISSING", e.getErrorCode());
        }

        // Add accreditation
        domainService.addAccreditation(c.getId(), "BCI", "BCI/REG/2026", 0L);

        // Now publish succeeds!
        Course published = domainService.publishCourse(c.getId());
        assertEquals("ACTIVE", published.getStatus());
    }

    @Test
    public void testRbacMatrixEnforcement() {
        // Academic Admin can CREATE & UPDATE
        domainService.enforceRbac("ACADEMIC_ADMIN", "CREATE", "DEP_CS");
        domainService.enforceRbac("ACADEMIC_ADMIN", "EDIT", "DEP_CS");

        // Student can view catalog
        domainService.enforceRbac("STUDENT", "VIEW_CATALOG", null);

        // Student cannot CREATE or EDIT
        try {
            domainService.enforceRbac("STUDENT", "CREATE", null);
            fail("Student must not be allowed to CREATE course");
        } catch (CourseSecurityException e) {
            assertEquals(403, e.getStatus());
            assertEquals("ACD_FORBIDDEN", e.getErrorCode());
        }

        // Auditor can view history
        domainService.enforceRbac("AUDITOR", "VIEW_HISTORY", null);

        // Auditor cannot EDIT
        try {
            domainService.enforceRbac("AUDITOR", "EDIT", null);
            fail("Auditor must not be allowed to EDIT course");
        } catch (CourseSecurityException e) {
            assertEquals(403, e.getStatus());
            assertEquals("ACD_FORBIDDEN", e.getErrorCode());
        }
    }

    @Test
    public void testSearchAndCatalogFiltering() {
        Course c = new Course();
        c.setCourseCode("CS601");
        c.setCourseName("Advanced Machine Learning");
        c.setDescription("Deep neural networks and generative AI.");
        c.setDepartmentId("DEP_CS");
        c.setTotalCredits(4.0);
        c.getTags().add("AI");
        domainService.createDraftCourse(c);

        // Keyword search finds draft
        List<Course> found = domainService.searchCourses("DEP_CS", null, null, "machine learning", null);
        assertFalse(found.isEmpty());
        assertEquals("CS601", found.get(0).getCourseCode());

        // Draft is not in public catalog
        List<CourseCatalogItem> catalog = domainService.getPublishedCatalog();
        boolean inCatalog = catalog.stream().anyMatch(item -> "CS601".equals(item.getCourseCode()));
        assertFalse("Draft course must not appear in published catalog", inCatalog);

        // Publish course
        domainService.submitForApproval(c.getId());
        domainService.approveCourse(c.getId());
        domainService.publishCourse(c.getId());

        // Now appears in catalog
        catalog = domainService.getPublishedCatalog();
        inCatalog = catalog.stream().anyMatch(item -> "CS601".equals(item.getCourseCode()));
        assertTrue("Published active course must appear in catalog", inCatalog);
    }

    // =========================================================================
    // Phase 2: Transactional Reliability Tests (User Story Lines 67–68)
    // =========================================================================

    @Test
    public void testInboxDeduplication() {
        String eventId = "ACD_INBOX_EVT_555";
        InboxEvent first = domainService.deduplicateInboundEvent(eventId, "ADM-02", "ACD-01", "{\"courseId\":\"CS101\"}");
        assertNotNull(first.getId());
        assertEquals("PROCESSED", first.getStatus());
        assertEquals(eventId, first.getEventId());

        // Duplicate consumption
        InboxEvent second = domainService.deduplicateInboundEvent(eventId, "ADM-02", "ACD-01", "{\"courseId\":\"CS101\"}");
        assertEquals(first.getId(), second.getId());
        assertEquals("PROCESSED", second.getStatus());

        List<InboxEvent> allInbox = domainService.listInboxEvents();
        long count = allInbox.stream().filter(e -> eventId.equals(e.getEventId())).count();
        assertEquals("Event must be deduplicated so only 1 record exists", 1, count);
    }

    @Test
    public void testDeadLetterQueueRoutingAndReplay() {
        DeadLetterEvent dlq = domainService.routeToDeadLetter("EVT_FAIL_999", "PrerequisiteCheck", "DEPENDENCY_UNRESOLVED", 3, "{\"course\":\"CS301\"}");
        assertNotNull(dlq.getId());
        assertEquals("OPEN", dlq.getDisposition());
        assertEquals("DEPENDENCY_UNRESOLVED", dlq.getFailureCode());
        assertEquals(3, dlq.getRetryCount());

        // Replay DLQ event
        DeadLetterEvent replayed = domainService.replayDeadLetterEvent(dlq.getId());
        assertEquals("REPLAYED", replayed.getDisposition());

        // Verify replayed event appears in outbox
        List<OutboxEvent> outbox = domainService.getOutboxEvents();
        boolean foundReplay = outbox.stream().anyMatch(e -> e.getEventType().contains("Replayed") && "EVT_FAIL_999".equals(e.getAggregateId()));
        assertTrue("Outbox must receive replayed event", foundReplay);
    }

    @Test
    public void testIdempotencyHashMismatch() {
        String key = "ACD-IDEMP-KEY-100";
        domainService.checkOrRecordIdempotency(key, "CREATE_COURSE", "HASH_ALPHA", 3600000L);

        // Same key, same hash -> returns existing record
        IdempotencyRecord same = domainService.checkOrRecordIdempotency(key, "CREATE_COURSE", "HASH_ALPHA", 3600000L);
        assertNotNull(same);

        // Same key, different hash -> conflict exception
        try {
            domainService.checkOrRecordIdempotency(key, "CREATE_COURSE", "HASH_BETA", 3600000L);
            fail("Expected CourseCodeConflictException on payload hash mismatch");
        } catch (CourseCodeConflictException e) {
            assertTrue(e.getMessage().contains("Payload hash mismatch"));
        }
    }

    // =========================================================================
    // Phase 5 Tests: Bulk Ops, Reassignment, Mappings, Accreditations & Archiving
    // =========================================================================

    @Test
    public void testBulkImportCoursesWithRowLevelErrorIsolation() {
        Course valid1 = new Course();
        valid1.setCourseCode("CS501");
        valid1.setCourseName("Advanced Algorithms");
        valid1.setDepartmentId("DEP_CS");
        valid1.setTotalCredits(4.0);

        Course invalid = new Course();
        invalid.setCourseCode(null); // Invalid: missing courseCode
        invalid.setCourseName("Missing Code Course");
        invalid.setDepartmentId("DEP_CS");

        Course valid2 = new Course();
        valid2.setCourseCode("CS502");
        valid2.setCourseName("Distributed Systems");
        valid2.setDepartmentId("DEP_CS");
        valid2.setTotalCredits(3.0);

        Map<String, Object> result = domainService.bulkImportCourses(Arrays.asList(valid1, invalid, valid2));
        assertEquals(3, result.get("totalSubmitted"));
        assertEquals(2, result.get("importedCount"));
        assertEquals(1, result.get("failedCount"));

        List<?> errors = (List<?>) result.get("errors");
        assertEquals(1, errors.size());
        Map<?, ?> errMap = (Map<?, ?>) errors.get(0);
        assertEquals(1, errMap.get("rowIndex"));

        List<?> ids = (List<?>) result.get("importedCourseIds");
        assertEquals(2, ids.size());
    }

    @Test
    public void testBulkExportCoursesFiltering() {
        Course c1 = new Course();
        c1.setCourseCode("EC101");
        c1.setCourseName("Basic Electronics");
        c1.setDepartmentId("DEP_EC");
        c1.setTotalCredits(3.0);
        Course created1 = domainService.createDraftCourse(c1);

        Course c2 = new Course();
        c2.setCourseCode("CS105");
        c2.setCourseName("Intro to Programming");
        c2.setDepartmentId("DEP_CS");
        c2.setTotalCredits(4.0);
        Course created2 = domainService.createDraftCourse(c2);
        domainService.submitForApproval(created2.getId());
        domainService.approveCourse(created2.getId());
        domainService.publishCourse(created2.getId()); // ACTIVE

        // Filter by status ACTIVE
        List<Course> activeCourses = domainService.bulkExportCourses("ACTIVE", null);
        assertTrue(activeCourses.stream().anyMatch(c -> c.getCourseCode().equals("CS105")));
        assertFalse(activeCourses.stream().anyMatch(c -> c.getCourseCode().equals("EC101")));

        // Filter by department DEP_EC
        List<Course> ecCourses = domainService.bulkExportCourses(null, "DEP_EC");
        assertTrue(ecCourses.stream().anyMatch(c -> c.getCourseCode().equals("EC101")));
        assertFalse(ecCourses.stream().anyMatch(c -> c.getCourseCode().equals("CS105")));
    }

    @Test
    public void testDepartmentReassignmentAndHistory() {
        Course course = new Course();
        course.setCourseCode("ME201");
        course.setCourseName("Thermodynamics");
        course.setDepartmentId("DEP_MECH");
        course.setTotalCredits(3.5);
        Course created = domainService.createDraftCourse(course);

        // Reassign to DEP_ENERGY
        CourseDepartmentAssociation assoc1 = domainService.reassignCourseDepartment(
                created.getId(), "DEP_ENERGY", "DEAN_ACADEMICS", "Interdisciplinary re-org");
        assertNotNull(assoc1.getId());
        assertEquals("DEP_ENERGY", assoc1.getDepartmentId());
        assertEquals("DEP_ENERGY", domainService.getCourse(created.getId()).getDepartmentId());

        // Reassign again to DEP_SUSTAINABILITY
        CourseDepartmentAssociation assoc2 = domainService.reassignCourseDepartment(
                created.getId(), "DEP_SUSTAIN", "DEAN_ACADEMICS", "Sustainability cluster consolidation");
        assertEquals("DEP_SUSTAIN", assoc2.getDepartmentId());
        assertEquals("DEP_SUSTAIN", domainService.getCourse(created.getId()).getDepartmentId());

        // Check history
        List<CourseDepartmentAssociation> history = domainService.getDepartmentAssociationHistory(created.getId());
        assertEquals(2, history.size());
        assertTrue(history.get(0).getEffectiveTo() > 0); // first association retired
        assertEquals(0, history.get(1).getEffectiveTo()); // second association active
    }

    @Test
    public void testCurriculumAndSubjectMapping() {
        Course course = new Course();
        course.setCourseCode("CS401");
        course.setCourseName("Machine Learning");
        course.setDepartmentId("DEP_CS");
        course.setTotalCredits(4.0);
        Course created = domainService.createDraftCourse(course);

        // Link curriculum
        CourseCurriculumMap ccm = domainService.linkCurriculum(created.getId(), "CURR_AI_2026");
        assertNotNull(ccm.getId());
        assertEquals("CURR_AI_2026", ccm.getCurriculumId());
        List<CourseCurriculumMap> links = domainService.getCurriculumLinks(created.getId());
        assertEquals(1, links.size());

        // Map subject
        CourseSubjectMapping csm = domainService.mapSubject(created.getId(), "SUB_ML_THEORY", 7);
        assertNotNull(csm.getId());
        assertEquals("SUB_ML_THEORY", csm.getSubjectId());
        assertEquals(7, csm.getSemesterNo());
        List<CourseSubjectMapping> subjects = domainService.getSubjectMappings(created.getId());
        assertEquals(1, subjects.size());
    }

    @Test
    public void testAccreditationExpiryCheck() {
        Course course = new Course();
        course.setCourseCode("ME301");
        course.setCourseName("Structural Mechanics");
        course.setDepartmentId("DEP_MECH");
        course.setTotalCredits(4.0);
        Course created = domainService.createDraftCourse(course);

        long now = System.currentTimeMillis();
        // Expiring in 15 days
        long soonExpiry = now + (15L * 86400000L);
        domainService.addAccreditation(created.getId(), "ABET", "ABET-2026-SOON", soonExpiry);

        // Expiring in 180 days
        long distantExpiry = now + (180L * 86400000L);
        domainService.addAccreditation(created.getId(), "NBA", "NBA-2026-DISTANT", distantExpiry);

        // Check expiring within 30 days
        List<CourseAccreditation> expiring = domainService.checkAccreditationExpiry(30);
        assertTrue(expiring.stream().anyMatch(a -> "ABET".equals(a.getAuthority())));
        assertFalse(expiring.stream().anyMatch(a -> "NBA".equals(a.getAuthority())));
    }

    @Test
    public void testCourseArchiveSnapshotAndDocumentVersioning() {
        Course course = new Course();
        course.setCourseCode("MA101");
        course.setCourseName("Discrete Mathematics");
        course.setDepartmentId("DEP_MATH");
        course.setTotalCredits(3.0);
        Course created = domainService.createDraftCourse(course);

        // Archive snapshot
        CourseArchiveRecord record = domainService.archiveCourseToCollection(created.getId(), "AUDITOR_ALICE");
        assertNotNull(record.getId());
        assertEquals(created.getId(), record.getCourseId());
        assertEquals("ARCHIVED", domainService.getCourse(created.getId()).getStatus());
        assertTrue(record.getSnapshotJson().contains("MA101"));

        // Document versioning
        CourseDocument d1 = new CourseDocument();
        d1.setCourseId(created.getId());
        d1.setDocType("SYLLABUS");
        d1.setTitle("Syllabus v1");
        d1.setFileUrl("https://campx.io/docs/v1.pdf");
        CourseDocument attached1 = domainService.attachDocument(d1);
        assertEquals(1, attached1.getVersion());
        assertTrue(attached1.isCurrent());

        CourseDocument d2 = new CourseDocument();
        d2.setCourseId(created.getId());
        d2.setDocType("SYLLABUS");
        d2.setTitle("Syllabus v2");
        d2.setFileUrl("https://campx.io/docs/v2.pdf");
        CourseDocument attached2 = domainService.attachDocument(d2);
        assertEquals(2, attached2.getVersion());
        assertTrue(attached2.isCurrent());
        assertFalse(attached1.isCurrent()); // v1 should now be superseded
    }
}
