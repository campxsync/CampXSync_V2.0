package com.campx.academic.curriculum;

import com.campx.academic.curriculum.exception.*;
import com.campx.academic.curriculum.model.CurriculumModels.*;
import com.campx.academic.curriculum.service.CurriculumDomainService;
import com.campx.academic.curriculum.service.OutboxRelayService;
import com.campx.academic.curriculum.service.RetryPolicy;
import com.campx.academic.curriculum.service.TransactionContext;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * Comprehensive Unit Test Suite for ACD-02 Curriculum Domain Service.
 * Validates all 15 Business Rules (BR-01 to BR-15), Epics, and Workflows.
 */
public class CurriculumDomainServiceTest {

    private CurriculumDomainService service;

    @Before
    public void setUp() {
        service = new CurriculumDomainService();
    }

    /**
     * FR-01, UC-01: Verifies successful creation of draft curriculum aggregate and initial version 1.
     */
    @Test
    public void testCreateCurriculumSuccess() {
        Curriculum curr = service.createCurriculum("COURSE-001", "CBCS", "2026-2027",
                "DEPT-CA", "MAIN", "Master of Computer Applications",
                "TENANT-001", "INST-001", "admin-1", "ACADEMIC_ADMIN");

        assertNotNull(curr);
        assertNotNull(curr.getId());
        assertEquals("COURSE-001", curr.getCourseId());
        assertEquals(CurriculumStatus.DRAFT, curr.getStatus());
        assertEquals(1, curr.getCurrentVersion());

        List<CurriculumVersion> versions = service.listVersions(curr.getId());
        assertEquals(1, versions.size());
        assertEquals(VersionStatus.DRAFT, versions.get(0).getStatus());

        List<OutboxEvent> outbox = service.getOutboxEvents();
        assertFalse(outbox.isEmpty());
        assertEquals("CurriculumCreated", outbox.get(0).getEventType());
    }

    /**
     * BR-05, Story 4: Verifies courseId must resolve to an active course in ACD-01.
     */
    @Test(expected = InvalidCourseException.class)
    public void testCreateCurriculumInvalidCourse() {
        service.createCurriculum("INVALID-CRS-999", "CBCS", "2026-2027",
                "DEPT-CA", "MAIN", "Invalid Curriculum",
                "TENANT-001", "INST-001", "admin-1", "ACADEMIC_ADMIN");
    }

    /**
     * BR-13, Story 5: Enforces tenant/campus scope consistency between curriculum and course.
     */
    @Test(expected = TenantMismatchException.class)
    public void testCreateCurriculumTenantMismatch() {
        service.createCurriculum("COURSE-001", "CBCS", "2026-2027",
                "DEPT-CA", "MAIN", "Mismatch Curriculum",
                "DIFFERENT_TENANT", "INST-001", "admin-1", "ACADEMIC_ADMIN");
    }

    /**
     * BR-02, BR-12, Story 13, 15: Published curriculum versions are immutable.
     */
    @Test(expected = VersionImmutableException.class)
    public void testPublishedVersionImmutability() {
        Curriculum curr = service.createCurriculum("COURSE-001", "CBCS", "2026-2027",
                "DEPT-CA", "MAIN", "MCA 2026", "TENANT-001", "INST-001", "admin-1", "ACADEMIC_ADMIN");

        // Add semester and subjects so it has valid structure and credits
        service.addSemester(curr.getId(), 1, 1, "Semester 1", "2026-2027", "ACADEMIC_ADMIN");
        for (int i = 0; i < 5; i++) {
            service.registerSubjectReference("SUB-TEST-" + i, "TENANT-001", "Test Subject " + i, true);
            service.mapSubject(curr.getId(), 1, "SUB-TEST-" + i, 1, i + 1, "CORE", 4.0, 60.0, true, "admin-1", "ACADEMIC_ADMIN");
        }

        // Move through approval
        service.submitForApproval(curr.getId(), 1, "admin-1", "ACADEMIC_ADMIN");
        service.approveCurriculum(curr.getId(), 1, "registrar-1", "REGISTRAR");
        service.publishCurriculum(curr.getId(), 1, "registrar-1", "REGISTRAR");

        // Attempt to update published version directly -> must throw VersionImmutableException
        service.updateDraftVersion(curr.getId(), 1, "2027-2028", null, null, null,
                "Illegal Edit", null, "admin-1", "ACADEMIC_ADMIN");
    }

    /**
     * Story 19: Rejects stale writes with optimistic version locking.
     */
    @Test(expected = VersionConflictException.class)
    public void testOptimisticLockingOnVersionUpdate() {
        Curriculum curr = service.createCurriculum("COURSE-001", "CBCS", "2026-2027",
                "DEPT-CA", "MAIN", "MCA 2026", "TENANT-001", "INST-001", "admin-1", "ACADEMIC_ADMIN");

        // Stale expected version (e.g. expected 999 while current is 1)
        service.updateDraftVersion(curr.getId(), 1, "2026-2027", "2026-08-01", null,
                "REG-2026", "Valid update", 999L, "admin-1", "ACADEMIC_ADMIN");
    }

    /**
     * BR-07, Story 21, 22: Enforces unique semester sequence numbers within a version.
     */
    @Test(expected = DuplicateMappingException.class)
    public void testUniqueSemesterSequenceNumbers() {
        Curriculum curr = service.createCurriculum("COURSE-001", "CBCS", "2026-2027",
                "DEPT-CA", "MAIN", "MCA 2026", "TENANT-001", "INST-001", "admin-1", "ACADEMIC_ADMIN");

        service.addSemester(curr.getId(), 1, 1, "Semester 1", "2026-2027", "ACADEMIC_ADMIN");
        // Duplicate semester 1 in version 1
        service.addSemester(curr.getId(), 1, 1, "Duplicate Semester 1", "2026-2027", "ACADEMIC_ADMIN");
    }

    /**
     * BR-03, BR-06, Story 23, 24: Validates subjectId resolves to an active subject in ACD-03.
     */
    @Test(expected = InvalidSubjectException.class)
    public void testSubjectMappingValidationAgainstACD03() {
        Curriculum curr = service.createCurriculum("COURSE-001", "CBCS", "2026-2027",
                "DEPT-CA", "MAIN", "MCA 2026", "TENANT-001", "INST-001", "admin-1", "ACADEMIC_ADMIN");
        service.addSemester(curr.getId(), 1, 1, "Semester 1", "2026-2027", "ACADEMIC_ADMIN");

        service.mapSubject(curr.getId(), 1, "NON_EXISTENT_SUB", 1, 1, "CORE", 4.0, 60.0, true, "admin-1", "ACADEMIC_ADMIN");
    }

    /**
     * BR-08, Story 25: Prevents duplicate subject mapping in the same semester.
     */
    @Test(expected = DuplicateMappingException.class)
    public void testDuplicateSubjectMappingInSemester() {
        Curriculum curr = service.createCurriculum("COURSE-001", "CBCS", "2026-2027",
                "DEPT-CA", "MAIN", "MCA 2026", "TENANT-001", "INST-001", "admin-1", "ACADEMIC_ADMIN");
        service.addSemester(curr.getId(), 1, 1, "Semester 1", "2026-2027", "ACADEMIC_ADMIN");

        service.mapSubject(curr.getId(), 1, "SUB-101", 1, 1, "CORE", 4.0, 60.0, true, "admin-1", "ACADEMIC_ADMIN");
        // Duplicate mapping
        service.mapSubject(curr.getId(), 1, "SUB-101", 1, 2, "CORE", 4.0, 60.0, true, "admin-1", "ACADEMIC_ADMIN");
    }

    /**
     * Story 26: Removes a subject mapping and updates aggregated credits.
     */
    @Test
    public void testRemoveSubjectMapping() {
        Curriculum curr = service.createCurriculum("COURSE-001", "CBCS", "2026-2027",
                "DEPT-CA", "MAIN", "MCA 2026", "TENANT-001", "INST-001", "admin-1", "ACADEMIC_ADMIN");
        service.addSemester(curr.getId(), 1, 1, "Semester 1", "2026-2027", "ACADEMIC_ADMIN");

        CurriculumSubject sub = service.mapSubject(curr.getId(), 1, "SUB-101", 1, 1, "CORE", 4.0, 60.0, true, "admin-1", "ACADEMIC_ADMIN");
        assertEquals(4.0, service.getVersion(curr.getId(), 1).getTotalCredits(), 0.001);

        service.removeSubjectMapping(curr.getId(), 1, sub.getId(), "admin-1", "ACADEMIC_ADMIN");
        assertEquals(0.0, service.getVersion(curr.getId(), 1).getTotalCredits(), 0.001);
    }

    /**
     * BR-09, Story 31: Validates credit policy bounds on publish.
     */
    @Test(expected = CreditPolicyViolationException.class)
    public void testCreditPolicyViolationOnPublish() {
        Curriculum curr = service.createCurriculum("COURSE-001", "CBCS", "2026-2027",
                "DEPT-CA", "MAIN", "MCA 2026", "TENANT-001", "INST-001", "admin-1", "ACADEMIC_ADMIN");
        service.addSemester(curr.getId(), 1, 1, "Semester 1", "2026-2027", "ACADEMIC_ADMIN");

        // Map only 1 subject with 4 credits (min policy is 16)
        service.mapSubject(curr.getId(), 1, "SUB-101", 1, 1, "CORE", 4.0, 60.0, true, "admin-1", "ACADEMIC_ADMIN");

        service.submitForApproval(curr.getId(), 1, "admin-1", "ACADEMIC_ADMIN");
        service.approveCurriculum(curr.getId(), 1, "registrar-1", "REGISTRAR");

        // Attempt publish with only 4 credits -> CreditPolicyViolationException
        service.publishCurriculum(curr.getId(), 1, "registrar-1", "REGISTRAR");
    }

    /**
     * BR-10, Story 36, 37: Detects and rejects DAG cycles in prerequisite relationships.
     */
    @Test(expected = PrerequisiteCycleException.class)
    public void testPrerequisiteCycleDetection() {
        Curriculum c1 = service.createCurriculum("COURSE-001", "CBCS", "2026-2027",
                "DEPT-CA", "MAIN", "Curriculum 1", "TENANT-001", "INST-001", "admin-1", "ACADEMIC_ADMIN");
        Curriculum c2 = service.createCurriculum("CS101", "CBCS", "2026-2027",
                "DEP_CS", "CAMPUS_MAIN", "Curriculum 2", "CAMPUS_MAIN", "INST-001", "admin-1", "ACADEMIC_ADMIN");

        // C1 depends on C2
        service.addPrerequisite(c1.getId(), null, c2.getId(), "MANDATORY", "PASS", "admin-1", "ACADEMIC_ADMIN");

        // C2 depends on C1 -> Cycle detected!
        service.addPrerequisite(c2.getId(), null, c1.getId(), "MANDATORY", "PASS", "admin-1", "ACADEMIC_ADMIN");
    }

    /**
     * BR-04, BR-14, UC-06 to UC-09, Story 40-44: Full approval and publication lifecycle.
     */
    @Test
    public void testApprovalAndPublicationLifecycle() {
        Curriculum curr = service.createCurriculum("COURSE-001", "CBCS", "2026-2027",
                "DEPT-CA", "MAIN", "MCA 2026", "TENANT-001", "INST-001", "admin-1", "ACADEMIC_ADMIN");

        service.addSemester(curr.getId(), 1, 1, "Semester 1", "2026-2027", "ACADEMIC_ADMIN");
        for (int i = 0; i < 5; i++) {
            service.registerSubjectReference("SUB-P-" + i, "TENANT-001", "P Subject " + i, true);
            service.mapSubject(curr.getId(), 1, "SUB-P-" + i, 1, i + 1, "CORE", 4.0, 60.0, true, "admin-1", "ACADEMIC_ADMIN");
        }

        // 1. Submit for approval
        CurriculumVersion reviewVer = service.submitForApproval(curr.getId(), 1, "admin-1", "ACADEMIC_ADMIN");
        assertEquals(VersionStatus.REVIEW, reviewVer.getStatus());

        // 2. Department Head review
        service.reviewCurriculum(curr.getId(), 1, "APPROVE", "Looks great", "dept-head-1", "DEPARTMENT_HEAD", "DEPT-CA");

        // 3. Approve
        CurriculumVersion appVer = service.approveCurriculum(curr.getId(), 1, "registrar-1", "REGISTRAR");
        assertEquals(VersionStatus.APPROVED, appVer.getStatus());

        // 4. Publish
        CurriculumVersion pubVer = service.publishCurriculum(curr.getId(), 1, "registrar-1", "REGISTRAR");
        assertEquals(VersionStatus.PUBLISHED, pubVer.getStatus());
        assertEquals(CurriculumStatus.PUBLISHED, service.getCurriculum(curr.getId(), "ACADEMIC_ADMIN", null).getStatus());

        // Verify outbox has CurriculumPublished
        boolean hasPublishedEvent = service.getOutboxEvents().stream()
                .anyMatch(e -> "CurriculumPublished".equals(e.getEventType()));
        assertTrue(hasPublishedEvent);
    }

    /**
     * BR-01, BR-11, Story 14, 48: Atomic superseding of prior version on new publish.
     */
    @Test
    public void testAtomicSupersedingPriorVersionOnPublish() {
        Curriculum curr = service.createCurriculum("COURSE-001", "CBCS", "2026-2027",
                "DEPT-CA", "MAIN", "MCA 2026", "TENANT-001", "INST-001", "admin-1", "ACADEMIC_ADMIN");

        // Version 1 setup & publish
        service.addSemester(curr.getId(), 1, 1, "Semester 1", "2026-2027", "ACADEMIC_ADMIN");
        for (int i = 0; i < 5; i++) {
            service.registerSubjectReference("SUB-V1-" + i, "TENANT-001", "V1 Subject " + i, true);
            service.mapSubject(curr.getId(), 1, "SUB-V1-" + i, 1, i + 1, "CORE", 4.0, 60.0, true, "admin-1", "ACADEMIC_ADMIN");
        }
        service.submitForApproval(curr.getId(), 1, "admin-1", "ACADEMIC_ADMIN");
        service.approveCurriculum(curr.getId(), 1, "registrar-1", "REGISTRAR");
        service.publishCurriculum(curr.getId(), 1, "registrar-1", "REGISTRAR");
        assertEquals(VersionStatus.PUBLISHED, service.getVersion(curr.getId(), 1).getStatus());

        // Create Version 2 via Annual Revision (Workflow 14.2)
        CurriculumVersion v2 = service.cloneForAnnualRevision(curr.getId(), "2027-2028", "admin-1", "ACADEMIC_ADMIN");
        assertEquals(2, v2.getVersionNo());
        assertEquals(VersionStatus.DRAFT, v2.getStatus());

        // Publish Version 2
        service.submitForApproval(curr.getId(), 2, "admin-1", "ACADEMIC_ADMIN");
        service.approveCurriculum(curr.getId(), 2, "registrar-1", "REGISTRAR");
        service.publishCurriculum(curr.getId(), 2, "registrar-1", "REGISTRAR");

        // Verify Version 2 is now PUBLISHED, and Version 1 is atomically SUPERSEDED
        assertEquals(VersionStatus.PUBLISHED, service.getVersion(curr.getId(), 2).getStatus());
        assertEquals(VersionStatus.SUPERSEDED, service.getVersion(curr.getId(), 1).getStatus());
        assertEquals(2, service.getCurriculum(curr.getId(), "ACADEMIC_ADMIN", null).getCurrentVersion());
    }

    /**
     * Story 27, 52: SubjectDeactivated event consumption flags mappings and blocks publication.
     */
    @Test(expected = PublicationBlockedException.class)
    public void testSubjectDeactivatedEventBlocksPublication() {
        Curriculum curr = service.createCurriculum("COURSE-001", "CBCS", "2026-2027",
                "DEPT-CA", "MAIN", "MCA 2026", "TENANT-001", "INST-001", "admin-1", "ACADEMIC_ADMIN");
        service.addSemester(curr.getId(), 1, 1, "Semester 1", "2026-2027", "ACADEMIC_ADMIN");

        for (int i = 0; i < 5; i++) {
            service.registerSubjectReference("SUB-DEACT-" + i, "TENANT-001", "Subject " + i, true);
            service.mapSubject(curr.getId(), 1, "SUB-DEACT-" + i, 1, i + 1, "CORE", 4.0, 60.0, true, "admin-1", "ACADEMIC_ADMIN");
        }

        // Simulate incoming SubjectDeactivated from ACD-03 for SUB-DEACT-0
        service.handleSubjectDeactivatedEvent("EVT-ACD-03-DEACT-1", "SUB-DEACT-0");

        service.submitForApproval(curr.getId(), 1, "admin-1", "ACADEMIC_ADMIN");
        service.approveCurriculum(curr.getId(), 1, "registrar-1", "REGISTRAR");

        // Publish must be blocked because SUB-DEACT-0 is deactivated!
        service.publishCurriculum(curr.getId(), 1, "registrar-1", "REGISTRAR");
    }

    /**
     * Story 56: Idempotent requests return original response without duplicate side effects.
     */
    @Test
    public void testIdempotencyHandling() {
        String payload = "{\"courseId\":\"COURSE-001\",\"academicYear\":\"2026-2027\"}";
        service.saveIdempotency("TENANT-001", "KEY-IDEM-001", payload, 201, "{\"status\":\"DRAFT\"}");

        IdempotencyRecord rec = service.checkIdempotency("TENANT-001", "KEY-IDEM-001", payload);
        assertNotNull(rec);
        assertEquals(201, rec.getStatusCode());
        assertEquals("{\"status\":\"DRAFT\"}", rec.getResponseBody());
    }

    /**
     * Story 60, 61: Enforces RBAC permissions and Department Head ABAC scoping.
     */
    @Test(expected = CurriculumForbiddenException.class)
    public void testDepartmentHeadCannotAccessOtherDepartmentCurriculum() {
        Curriculum curr = service.createCurriculum("COURSE-001", "CBCS", "2026-2027",
                "DEPT-CA", "MAIN", "MCA 2026", "TENANT-001", "INST-001", "admin-1", "ACADEMIC_ADMIN");

        // Department Head from DEPT-EE attempting to view DEPT-CA curriculum
        service.getCurriculum(curr.getId(), "DEPARTMENT_HEAD", "DEPT-EE");
    }

    /**
     * BR-15, Story 47: Prevents deletion of curriculum with downstream references.
     */
    @Test(expected = PublicationBlockedException.class)
    public void testPreventDeletionWithDownstreamReferences() {
        Curriculum curr = service.createCurriculum("COURSE-001", "CBCS", "2026-2027",
                "DEPT-CA", "MAIN", "MCA 2026", "TENANT-001", "INST-001", "admin-1", "ACADEMIC_ADMIN");

        // Register downstream academic reference (exam assessment or enrollment)
        service.addDownstreamAcademicReference(curr.getId());

        // Attempt delete -> must be rejected with BR-15 violation
        service.deleteCurriculum(curr.getId(), "ACADEMIC_ADMIN");
    }

    /**
     * Story 62: Verifies accreditation compliance view retrieval for authorized roles and forbidden for student.
     */
    @Test
    public void testGetComplianceViewSuccess() {
        Curriculum curr = service.createCurriculum("COURSE-001", "CBCS", "2026-2027",
                "DEPT-CA", "MAIN", "MCA Compliance View Test", "TENANT-001", "INST-001", "admin-1", "ACADEMIC_ADMIN");

        service.addSemester(curr.getId(), 1, 1, "Semester 1", "2026-2027", "ACADEMIC_ADMIN");
        service.mapSubject(curr.getId(), 1, "SUB-101", 1, 1, "CORE", 4.0, 4.0, true, "admin-1", "ACADEMIC_ADMIN");
        service.addOutcome(curr.getId(), 1, "CO-01", "Understand object-oriented concepts", "UNDERSTAND", "CO",
                "SEMESTER", "1", "admin-1", "ACADEMIC_ADMIN");

        ComplianceView view = service.getComplianceView(curr.getId(), "auditor-1", "ACCREDITATION_TEAM");
        assertNotNull(view);
        assertNotNull(view.getCurriculum());
        assertEquals(curr.getId(), view.getCurriculum().getId());
        assertEquals(1, view.getVersions().size());
        assertEquals(1, view.getSubjectMappings().size());
        assertEquals("SUB-101", view.getSubjectMappings().get(0).getSubjectId());
        assertEquals(1, view.getOutcomes().size());
        assertEquals("CO-01", view.getOutcomes().get(0).getOutcomeCode());
        assertFalse(view.getAuditHistory().isEmpty());
    }

    @Test(expected = CurriculumForbiddenException.class)
    public void testGetComplianceViewForbiddenForStudent() {
        Curriculum curr = service.createCurriculum("COURSE-001", "CBCS", "2026-2027",
                "DEPT-CA", "MAIN", "MCA Test", "TENANT-001", "INST-001", "admin-1", "ACADEMIC_ADMIN");

        service.getComplianceView(curr.getId(), "student-1", "STUDENT");
    }

    /**
     * Story 65: Verifies data classification resolution per RBAC role.
     */
    @Test
    public void testDataClassificationLevels() {
        assertEquals(DataClassification.L1_PUBLIC, service.getClassificationLevel("STUDENT"));
        assertEquals(DataClassification.L1_PUBLIC, service.getClassificationLevel("EXTERNAL_API"));
        assertEquals(DataClassification.L2_INTERNAL, service.getClassificationLevel("FACULTY"));
        assertEquals(DataClassification.L2_INTERNAL, service.getClassificationLevel("DEPARTMENT_HEAD"));
        assertEquals(DataClassification.L3_CONFIDENTIAL, service.getClassificationLevel("ACADEMIC_ADMIN"));
        assertEquals(DataClassification.L3_CONFIDENTIAL, service.getClassificationLevel("REGISTRAR"));
        assertEquals(DataClassification.L4_RESTRICTED, service.getClassificationLevel("SUPER_ADMIN"));
    }

    /**
     * Story 63: Verifies external API key registry, active check, tenant scoping, and expiry.
     */
    @Test
    public void testApiKeyValidation() {
        // Valid key
        ApiKeyRecord valid = service.validateApiKey("ak_live_campx_valid_12345", "TENANT-001");
        assertNotNull(valid);
        assertEquals("KEY-EXT-001", valid.getKeyId());

        // Expired key
        ApiKeyRecord expired = service.validateApiKey("ak_live_campx_expired_99999", "TENANT-001");
        assertNull(expired);

        // Tenant mismatch
        ApiKeyRecord wrongTenant = service.validateApiKey("ak_live_campx_valid_12345", "TENANT-WRONG");
        assertNull(wrongTenant);

        // Nonexistent key
        ApiKeyRecord notFound = service.validateApiKey("ak_live_nonexistent", "TENANT-001");
        assertNull(notFound);
    }

    /**
     * Story 54, 71: Verifies RetryPolicy exponential backoff calculations and classification.
     */
    @Test
    public void testRetryPolicyCalculations() {
        RetryPolicy policy = new RetryPolicy(5, 1000L, 30000L, 0.1);

        assertTrue(policy.isRetryable(500));
        assertTrue(policy.isRetryable(502));
        assertTrue(policy.isRetryable(503));
        assertTrue(policy.isRetryable(408));
        assertTrue(policy.isRetryable(429));
        assertTrue(policy.isRetryable(new IOException("Connection reset")));

        assertFalse(policy.isRetryable(400));
        assertFalse(policy.isRetryable(401));
        assertFalse(policy.isRetryable(403));
        assertFalse(policy.isRetryable(404));
        assertFalse(policy.isRetryable(409));
        assertFalse(policy.isRetryable(422));

        assertTrue(policy.getDelayMs(0) >= 1000L);
        assertTrue(policy.getDelayMs(1) >= 2000L);
        assertTrue(policy.getDelayMs(10) <= 33000L); // bounded by maxDelayMs + jitter
    }

    /**
     * Story 51: Verifies OutboxRelayService successfully publishes pending events to broker.
     */
    @Test
    public void testOutboxRelayServiceSuccess() {
        Curriculum curr = service.createCurriculum("COURSE-001", "CBCS", "2026-2027",
                "DEPT-CA", "MAIN", "Relay Test", "TENANT-001", "INST-001", "admin-1", "ACADEMIC_ADMIN");

        AtomicBoolean published = new AtomicBoolean(false);
        OutboxRelayService relay = new OutboxRelayService(service, new RetryPolicy(), null,
                event -> {
                    published.set(true);
                    return true;
                }, 10000L);

        int count = relay.relayPendingEvents();
        assertTrue(count >= 1);
        assertTrue(published.get());
        assertEquals(0, service.getPendingOutboxEvents().size());
    }

    /**
     * Story 54, 71: Verifies OutboxRelayService retries on failure and routes to DLQ after exhaustion.
     */
    @Test
    public void testOutboxRelayServiceRetryAndDLQOnFailure() {
        Curriculum curr = service.createCurriculum("COURSE-001", "CBCS", "2026-2027",
                "DEPT-CA", "MAIN", "DLQ Test", "TENANT-001", "INST-001", "admin-1", "ACADEMIC_ADMIN");

        RetryPolicy fastRetry = new RetryPolicy(2, 10L, 50L, 0.0);
        OutboxRelayService relay = new OutboxRelayService(service, fastRetry, null,
                event -> {
                    throw new IOException("Broker temporarily offline");
                }, 10000L);

        int count = relay.relayPendingEvents();
        assertEquals(0, count);
        assertTrue(relay.getFailedCount() >= 1);
        assertFalse(service.getDeadLetterEvents().isEmpty());
    }

    /**
     * Story 57: Verifies proactive background purging of expired idempotency records.
     */
    @Test
    public void testPurgeExpiredIdempotencyRecords() {
        service.saveIdempotency("TENANT-001", "KEY-EXP-001", "{\"op\":\"1\"}", 200, "{\"ok\":true}");
        service.saveIdempotency("TENANT-001", "KEY-LIVE-002", "{\"op\":\"2\"}", 200, "{\"ok\":true}");

        // Manually expire KEY-EXP-001 in memory
        IdempotencyRecord rec = service.checkIdempotency("TENANT-001", "KEY-EXP-001", "{\"op\":\"1\"}");
        assertNotNull(rec);
        rec.setExpiresAt(System.currentTimeMillis() - 1000L);

        int purged = service.purgeExpiredIdempotencyRecords();
        assertEquals(1, purged);
    }

    /**
     * Story 58: Verifies TransactionContext rollback execution on abort.
     */
    @Test
    public void testTransactionContextRollback() {
        TransactionContext tx = new TransactionContext("TX-TEST-001");
        AtomicBoolean rolledBack = new AtomicBoolean(false);

        tx.begin();
        assertTrue(tx.isActive());
        tx.addRollback(() -> rolledBack.set(true));
        tx.rollback();

        assertFalse(tx.isActive());
        assertTrue(rolledBack.get());
    }
}
