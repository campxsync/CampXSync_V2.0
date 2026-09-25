package com.campx.academic.batch;

import com.campx.academic.batch.exception.*;
import com.campx.academic.batch.model.BatchModels.*;
import com.campx.academic.batch.service.BatchDomainService;
import com.campx.academic.batch.service.MetricsCollector;
import com.campx.academic.batch.service.RateLimiter;
import com.campx.academic.batch.service.TransactionContext;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Comprehensive Unit and Domain Service Test Suite for ACD-04 Batch Management Service.
 * Covers all 8 epics and 77 user stories across:
 * - Batch Definition & Identity Management
 * - Batch Search & Catalog
 * - Batch Section Management
 * - Capacity Management & Overrides
 * - Roster & Student Enrollment
 * - Batch Lifecycle Governance
 * - Batch Split & Merge Cross-Module Integration (with ADM-02)
 * - Event-Driven Reliability, Outbox, Idempotency, RBAC, and Metrics
 */
public class BatchServiceTest {

    private BatchDomainService domainService;
    private RateLimiter rateLimiter;

    @Before
    public void setUp() {
        domainService = new BatchDomainService();
        rateLimiter = new RateLimiter(5, 60000); // 5 requests per window for testing
    }

    // =========================================================================
    // 1. Batch Definition & Identity Management Tests (Stories 3-7)
    // =========================================================================

    @Test
    public void testCreateBatchDraftSuccess() {
        Batch b = new Batch();
        b.setBatchCode("CS-2024-A");
        b.setName("Computer Science Cohort 2024 Section A");
        b.setCourseId("CRS-CS101");
        b.setDepartmentId("DEP-CS");
        b.setCampusId("CAMPUS-MAIN");
        b.setAcademicYear("2024-2025");
        b.setSemesterNo(1);
        b.setCapacity(60);

        Batch created = domainService.createBatch(b, "admin-1", "ACADEMIC_ADMIN", "IDEM-001");
        assertNotNull(created.getId());
        assertEquals("CS-2024-A", created.getBatchCode());
        assertEquals(BatchStatus.DRAFT, created.getStatus());
        assertEquals(0, created.getRosterCount());
        assertEquals(60, created.getCapacity());
        assertEquals(1L, created.getVersion());

        // Verify outbox event emitted
        assertTrue(domainService.getPendingOutboxCount() > 0);

        // Verify audit history recorded
        List<BatchHistory> histories = domainService.getBatchHistory(created.getId());
        assertFalse(histories.isEmpty());
        assertEquals("CREATE", histories.get(0).getAction());
    }

    @Test(expected = BatchValidationException.class)
    public void testCreateBatchInactiveCourseRejected() {
        Batch b = new Batch();
        b.setBatchCode("CS-INVAL");
        b.setName("Invalid Course Batch");
        b.setCourseId("CRS-INACTIVE"); // Inactive course
        b.setAcademicYear("2024-2025");
        b.setSemesterNo(1);
        b.setCapacity(50);

        domainService.createBatch(b, "admin-1", "ACADEMIC_ADMIN", null);
    }

    @Test(expected = BatchConflictException.class)
    public void testCreateBatchDuplicateBatchCodeRejected() {
        Batch b1 = new Batch();
        b1.setBatchCode("DUP-CODE-01");
        b1.setName("Batch 1");
        b1.setCourseId("CRS-CS101");
        b1.setAcademicYear("2024-2025");
        b1.setSemesterNo(1);
        b1.setCapacity(40);
        domainService.createBatch(b1, "admin-1", "ACADEMIC_ADMIN", null);

        Batch b2 = new Batch();
        b2.setBatchCode("DUP-CODE-01"); // Duplicate code
        b2.setName("Batch 2");
        b2.setCourseId("CRS-CS101");
        b2.setAcademicYear("2024-2025");
        b2.setSemesterNo(1);
        b2.setCapacity(40);
        domainService.createBatch(b2, "admin-1", "ACADEMIC_ADMIN", null);
    }

    @Test(expected = BatchValidationException.class)
    public void testCreateBatchInvalidSemesterRejected() {
        Batch b = new Batch();
        b.setBatchCode("CS-SEM-INVAL");
        b.setName("Invalid Sem Batch");
        b.setCourseId("CRS-CS101");
        b.setAcademicYear("2024-2025");
        b.setSemesterNo(15); // > 12 invalid
        b.setCapacity(40);

        domainService.createBatch(b, "admin-1", "ACADEMIC_ADMIN", null);
    }

    @Test
    public void testUpdateBatchDraftAndOptimisticLocking() {
        Batch b = new Batch();
        b.setBatchCode("CS-UPDATE-01");
        b.setName("Original Batch");
        b.setCourseId("CRS-CS101");
        b.setAcademicYear("2024-2025");
        b.setSemesterNo(1);
        b.setCapacity(50);

        Batch created = domainService.createBatch(b, "admin-1", "ACADEMIC_ADMIN", null);

        // Update draft fields
        Batch updateReq = new Batch();
        updateReq.setName("Updated Batch Name");
        updateReq.setDepartmentId("DEP-IT");

        Batch updated = domainService.updateBatch(created.getId(), updateReq, 1L, "admin-1", "ACADEMIC_ADMIN");
        assertEquals("Updated Batch Name", updated.getName());
        assertEquals("DEP-IT", updated.getDepartmentId());
        assertEquals(2L, updated.getVersion());

        // Stale version update must fail with 409
        try {
            domainService.updateBatch(created.getId(), updateReq, 1L, "admin-1", "ACADEMIC_ADMIN");
            fail("Expected BatchConflictException on stale version lock");
        } catch (BatchConflictException ex) {
            assertEquals("ACD_BATCH_VERSION_CONFLICT", ex.getErrorCode());
        }
    }

    // =========================================================================
    // 2. Batch Search & Catalog & RBAC Scoping (Stories 8, 10, 11)
    // =========================================================================

    @Test
    public void testSearchBatchesFilterAndPagination() {
        Batch b1 = new Batch();
        b1.setBatchCode("CAT-01");
        b1.setName("Catalog 1");
        b1.setCourseId("CRS-CS101");
        b1.setDepartmentId("DEP-CS");
        b1.setAcademicYear("2024-2025");
        b1.setSemesterNo(1);
        b1.setCapacity(50);
        domainService.createBatch(b1, "admin-1", "ACADEMIC_ADMIN", null);

        Batch b2 = new Batch();
        b2.setBatchCode("CAT-02");
        b2.setName("Catalog 2");
        b2.setCourseId("CRS-CS102");
        b2.setDepartmentId("DEP-EE");
        b2.setAcademicYear("2024-2025");
        b2.setSemesterNo(2);
        b2.setCapacity(50);
        domainService.createBatch(b2, "admin-1", "ACADEMIC_ADMIN", null);

        Map<String, String> filters = new HashMap<>();
        filters.put("courseId", "CRS-CS101");
        List<Batch> found = domainService.searchBatches(filters, 1, 10, "ACADEMIC_ADMIN", "admin-1");
        assertEquals(1, found.size());
        assertEquals("CAT-01", found.get(0).getBatchCode());
    }

    @Test
    public void testStudentVisibilityRestrictedToOwnBatch() {
        Batch b = new Batch();
        b.setBatchCode("STU-RESTRICT-01");
        b.setName("Cohort CS");
        b.setCourseId("CRS-CS101");
        b.setAcademicYear("2024-2025");
        b.setSemesterNo(1);
        b.setCapacity(50);
        Batch created = domainService.createBatch(b, "admin-1", "ACADEMIC_ADMIN", null);
        domainService.openBatch(created.getId(), "admin-1", "ACADEMIC_ADMIN");

        // Enroll STU-1001 in this batch
        domainService.addStudentToBatch(created.getId(), "STU-1001", null, MembershipType.REGULAR, "2024-09-01", "admin-1", "ACADEMIC_ADMIN");

        // STU-1001 can view own batch
        Batch viewSelf = domainService.getBatch(created.getId(), "STUDENT", "STU-1001");
        assertNotNull(viewSelf);

        // STU-1002 (not enrolled) cannot view this batch
        try {
            domainService.getBatch(created.getId(), "STUDENT", "STU-1002");
            fail("Expected BatchForbiddenException for unenrolled student");
        } catch (BatchForbiddenException ex) {
            assertEquals("ACD_FORBIDDEN", ex.getErrorCode());
        }
    }

    // =========================================================================
    // 3. Batch Section Management (Stories 13-16)
    // =========================================================================

    @Test
    public void testSectionLifecycleAndFacultyAssignment() {
        Batch b = new Batch();
        b.setBatchCode("SEC-BATCH-01");
        b.setName("Batch for Sections");
        b.setCourseId("CRS-CS101");
        b.setAcademicYear("2024-2025");
        b.setSemesterNo(1);
        b.setCapacity(80);
        Batch created = domainService.createBatch(b, "admin-1", "ACADEMIC_ADMIN", null);

        // Create Section A
        BatchSection secA = new BatchSection();
        secA.setSectionCode("A");
        secA.setSectionName("Section Alpha");
        secA.setCapacity(40);
        BatchSection createdSecA = domainService.createSection(created.getId(), secA, "admin-1", "ACADEMIC_ADMIN");
        assertEquals("A", createdSecA.getSectionCode());

        // Create duplicate sectionCode in same batch -> rejected
        try {
            BatchSection secADup = new BatchSection();
            secADup.setSectionCode("A");
            secADup.setSectionName("Section Alpha Duplicate");
            secADup.setCapacity(40);
            domainService.createSection(created.getId(), secADup, "admin-1", "ACADEMIC_ADMIN");
            fail("Expected duplicate section code conflict");
        } catch (BatchConflictException ex) {
            assertEquals("ACD_BATCH_SECTION_CODE_DUPLICATE", ex.getErrorCode());
        }

        // List sections
        List<BatchSection> sections = domainService.listSections(created.getId());
        assertEquals(1, sections.size());

        // Assign faculty to section
        BatchSection assigned = domainService.assignFacultyToSection(created.getId(), createdSecA.getId(), "FAC-501", "admin-1", "ACADEMIC_ADMIN");
        assertEquals("FAC-501", assigned.getFacultyId());
    }

    @Test
    public void testSectionActivationEnforcesFacultyAssignmentWhenRequired() {
        Batch b = new Batch();
        b.setBatchCode("SEC-REQ-FAC");
        b.setName("Section Activation Faculty Check");
        b.setCourseId("CRS-CS101");
        b.setAcademicYear("2024-2025");
        b.setSemesterNo(1);
        b.setCapacity(60);
        Batch created = domainService.createBatch(b, "admin-1", "ACADEMIC_ADMIN", null);

        BatchSection sec = new BatchSection();
        sec.setSectionCode("A");
        sec.setSectionName("Section A");
        sec.setCapacity(30);
        domainService.createSection(created.getId(), sec, "admin-1", "ACADEMIC_ADMIN");

        // Configure policy: faculty required for activation
        domainService.setFacultyRequiredForActivation(true);

        try {
            domainService.openBatch(created.getId(), "admin-1", "ACADEMIC_ADMIN");
            fail("Expected BatchValidationException due to missing faculty on section");
        } catch (BatchValidationException ex) {
            assertEquals("ACD_BATCH_FACULTY_REQUIRED", ex.getErrorCode());
        } finally {
            domainService.setFacultyRequiredForActivation(false);
        }
    }

    // =========================================================================
    // 4. Capacity Management & Overrides (Stories 18-21, 72)
    // =========================================================================

    @Test
    public void testCapacityExceededAndOverrideLifecycle() {
        Batch b = new Batch();
        b.setBatchCode("CAP-TEST-01");
        b.setName("Capacity Test Batch");
        b.setCourseId("CRS-CS101");
        b.setAcademicYear("2024-2025");
        b.setSemesterNo(1);
        b.setCapacity(2); // Low capacity for testing
        Batch created = domainService.createBatch(b, "admin-1", "ACADEMIC_ADMIN", null);
        domainService.openBatch(created.getId(), "admin-1", "ACADEMIC_ADMIN");

        // Enroll 2 students up to configured capacity
        domainService.addStudentToBatch(created.getId(), "STU-1001", null, MembershipType.REGULAR, "2024-09-01", "admin-1", "ACADEMIC_ADMIN");
        domainService.addStudentToBatch(created.getId(), "STU-1002", null, MembershipType.REGULAR, "2024-09-01", "admin-1", "ACADEMIC_ADMIN");

        // 3rd student attempt must be rejected with 409 ACD_BATCH_CAPACITY_EXCEEDED
        try {
            domainService.addStudentToBatch(created.getId(), "STU-1003", null, MembershipType.REGULAR, "2024-09-01", "admin-1", "ACADEMIC_ADMIN");
            fail("Expected capacity exceeded exception");
        } catch (BatchCapacityExceededException ex) {
            assertEquals("ACD_BATCH_CAPACITY_EXCEEDED", ex.getErrorCode());
        }

        // Grant Capacity Override to 3
        BatchCapacityOverride ovr = new BatchCapacityOverride();
        ovr.setOverrideCapacity(3);
        ovr.setReason("Dean authorized extra seat");
        ovr.setEffectiveFrom("2024-09-01");
        ovr.setEffectiveTo("2030-12-31");
        BatchCapacityOverride granted = domainService.grantCapacityOverride(created.getId(), ovr, "admin-1", "ACADEMIC_ADMIN");
        assertNotNull(granted.getId());

        // Now 3rd student can be added successfully
        BatchRoster roster3 = domainService.addStudentToBatch(created.getId(), "STU-1003", null, MembershipType.REGULAR, "2024-09-01", "admin-1", "ACADEMIC_ADMIN");
        assertNotNull(roster3.getId());

        // 4th student attempt is rejected again
        try {
            domainService.addStudentToBatch(created.getId(), "STU-1004", null, MembershipType.REGULAR, "2024-09-01", "admin-1", "ACADEMIC_ADMIN");
            fail("Expected capacity exceeded beyond override");
        } catch (BatchCapacityExceededException ex) {
            assertEquals("ACD_BATCH_CAPACITY_EXCEEDED", ex.getErrorCode());
        }

        // Revoke override
        domainService.revokeCapacityOverride(created.getId(), granted.getId(), "admin-1", "ACADEMIC_ADMIN");
        assertEquals(2, domainService.getEffectiveCapacity(created.getId()));
    }

    // =========================================================================
    // 5. Roster & Student Enrollment Invariants (Stories 23-27, 73)
    // =========================================================================

    @Test
    public void testStudentEligibilityAndDuplicateEnrollmentCheck() {
        Batch b = new Batch();
        b.setBatchCode("ROSTER-INV-01");
        b.setName("Roster Invariants Batch");
        b.setCourseId("CRS-CS101");
        b.setAcademicYear("2024-2025");
        b.setSemesterNo(1);
        b.setCapacity(20);
        Batch created = domainService.createBatch(b, "admin-1", "ACADEMIC_ADMIN", null);
        domainService.openBatch(created.getId(), "admin-1", "ACADEMIC_ADMIN");

        // Ineligible student must fail (BR-05)
        try {
            domainService.addStudentToBatch(created.getId(), "STU-INELIGIBLE", null, MembershipType.REGULAR, "2024-09-01", "admin-1", "ACADEMIC_ADMIN");
            fail("Expected ineligible student to be rejected");
        } catch (BatchValidationException ex) {
            assertEquals("ACD_BATCH_STUDENT_INELIGIBLE", ex.getErrorCode());
        }

        // Add valid student STU-1001
        domainService.addStudentToBatch(created.getId(), "STU-1001", null, MembershipType.REGULAR, "2024-09-01", "admin-1", "ACADEMIC_ADMIN");

        // Duplicate active enrollment must fail with 409 (BR-06)
        try {
            domainService.addStudentToBatch(created.getId(), "STU-1001", null, MembershipType.REGULAR, "2024-09-01", "admin-1", "ACADEMIC_ADMIN");
            fail("Expected duplicate active enrollment to be rejected");
        } catch (BatchConflictException ex) {
            assertEquals("ACD_BATCH_ALREADY_ASSIGNED", ex.getErrorCode());
        }
    }

    @Test
    public void testRemoveStudentRetainsHistoricalMembership() {
        Batch b = new Batch();
        b.setBatchCode("REMOVE-STU-01");
        b.setName("Remove Student Batch");
        b.setCourseId("CRS-CS101");
        b.setAcademicYear("2024-2025");
        b.setSemesterNo(1);
        b.setCapacity(20);
        Batch created = domainService.createBatch(b, "admin-1", "ACADEMIC_ADMIN", null);
        domainService.openBatch(created.getId(), "admin-1", "ACADEMIC_ADMIN");

        domainService.addStudentToBatch(created.getId(), "STU-1001", null, MembershipType.REGULAR, "2024-09-01", "admin-1", "ACADEMIC_ADMIN");
        assertEquals(1, domainService.getBatch(created.getId(), "ACADEMIC_ADMIN", "admin-1").getRosterCount());

        // Remove student
        BatchRoster ended = domainService.removeStudentFromBatch(created.getId(), "STU-1001", "Transferred to other branch", "admin-1", "ACADEMIC_ADMIN");
        assertEquals(MembershipStatus.ENDED, ended.getStatus());
        assertNotNull(ended.getEffectiveTo());
        assertEquals(0, domainService.getBatch(created.getId(), "ACADEMIC_ADMIN", "admin-1").getRosterCount());

        // Active roster is now empty
        List<BatchRoster> activeList = domainService.getRoster(created.getId(), "ACADEMIC_ADMIN", "admin-1");
        assertTrue(activeList.isEmpty());

        // Historical roster still contains the student record (BR-09, Story 26)
        List<BatchRoster> histList = domainService.getRosterHistory(created.getId(), null, "ACADEMIC_ADMIN");
        assertEquals(1, histList.size());
        assertEquals("STU-1001", histList.get(0).getStudentId());
    }

    @Test
    public void testClosedBatchRejectsNewRosterEnrollment() {
        Batch b = new Batch();
        b.setBatchCode("CLOSED-ROSTER-01");
        b.setName("Closed Batch Enrollment Block");
        b.setCourseId("CRS-CS101");
        b.setAcademicYear("2024-2025");
        b.setSemesterNo(1);
        b.setCapacity(20);
        Batch created = domainService.createBatch(b, "admin-1", "ACADEMIC_ADMIN", null);
        domainService.openBatch(created.getId(), "admin-1", "ACADEMIC_ADMIN");

        // Close the batch
        domainService.closeBatch(created.getId(), "admin-1", "ACADEMIC_ADMIN");

        // Attempting to enroll student into CLOSED batch must fail with 409 ACD_BATCH_CLOSED (BR-07, Story 27, 73)
        try {
            domainService.addStudentToBatch(created.getId(), "STU-1001", null, MembershipType.REGULAR, "2024-09-01", "admin-1", "ACADEMIC_ADMIN");
            fail("Expected enrollment to fail on closed batch");
        } catch (BatchConflictException ex) {
            assertEquals("ACD_BATCH_CLOSED", ex.getErrorCode());
        }
    }

    // =========================================================================
    // 6. Batch Lifecycle Governance Tests (Stories 31-35)
    // =========================================================================

    @Test
    public void testFullLifecycleTransitions() {
        Batch b = new Batch();
        b.setBatchCode("LC-01");
        b.setName("Lifecycle Test Batch");
        b.setCourseId("CRS-CS101");
        b.setAcademicYear("2024-2025");
        b.setSemesterNo(1);
        b.setCapacity(50);
        Batch created = domainService.createBatch(b, "admin-1", "ACADEMIC_ADMIN", null);
        assertEquals(BatchStatus.DRAFT, created.getStatus());

        // DRAFT -> ACTIVE
        Batch opened = domainService.openBatch(created.getId(), "admin-1", "ACADEMIC_ADMIN");
        assertEquals(BatchStatus.ACTIVE, opened.getStatus());

        // ACTIVE -> CLOSED
        Batch closed = domainService.closeBatch(created.getId(), "admin-1", "ACADEMIC_ADMIN");
        assertEquals(BatchStatus.CLOSED, closed.getStatus());

        // CLOSED -> ACTIVE (Reopen)
        Batch reopened = domainService.reopenBatch(created.getId(), "admin-1", "ACADEMIC_ADMIN");
        assertEquals(BatchStatus.ACTIVE, reopened.getStatus());

        // ACTIVE -> CLOSED again
        domainService.closeBatch(created.getId(), "admin-1", "ACADEMIC_ADMIN");

        // CLOSED -> ARCHIVED
        Batch archived = domainService.archiveBatch(created.getId(), "admin-1", "REGISTRAR");
        assertEquals(BatchStatus.ARCHIVED, archived.getStatus());

        // Illegal transition: ARCHIVED -> ACTIVE must fail (Story 35)
        try {
            domainService.reopenBatch(created.getId(), "admin-1", "REGISTRAR");
            fail("Expected invalid state transition from ARCHIVED");
        } catch (BatchConflictException ex) {
            assertEquals("ACD_BATCH_INVALID_STATE_TRANSITION", ex.getErrorCode());
        }
    }

    // =========================================================================
    // 7. Batch Split & Merge Cross-Module Integration with ADM-02 (Stories 37-39, 75-77)
    // =========================================================================

    @Test
    public void testBatchSplitWorkflowWithADM02Decision() {
        Batch b = new Batch();
        b.setBatchCode("SPLIT-SRC-01");
        b.setName("Large Cohort To Split");
        b.setCourseId("CRS-CS101");
        b.setDepartmentId("DEP-CS");
        b.setAcademicYear("2024-2025");
        b.setSemesterNo(1);
        b.setCapacity(60);
        Batch created = domainService.createBatch(b, "admin-1", "ACADEMIC_ADMIN", null);
        domainService.openBatch(created.getId(), "admin-1", "ACADEMIC_ADMIN");

        // Enroll 2 students
        domainService.addStudentToBatch(created.getId(), "STU-1001", null, MembershipType.REGULAR, "2024-09-01", "admin-1", "ACADEMIC_ADMIN");
        domainService.addStudentToBatch(created.getId(), "STU-1002", null, MembershipType.REGULAR, "2024-09-01", "admin-1", "ACADEMIC_ADMIN");

        // Request Split into 2 target sections
        BatchSplitRequest req = new BatchSplitRequest();
        req.setReason("Cohort exceeds lab capacity");
        List<ProposedSectionSplit> proposed = new ArrayList<>();
        proposed.add(new ProposedSectionSplit("A1", "Section A1", 30, Collections.singletonList("STU-1001")));
        proposed.add(new ProposedSectionSplit("A2", "Section A2", 30, Collections.singletonList("STU-1002")));
        req.setProposedSections(proposed);

        BatchSplitRequest requested = domainService.requestSplit(created.getId(), req, "admin-1", "ACADEMIC_ADMIN");
        assertNotNull(requested.getRequestId());
        assertEquals("PENDING", requested.getStatus());

        // Batch enters PENDING_SPLIT_APPROVAL
        Batch pendingBatch = domainService.getBatch(created.getId(), "ACADEMIC_ADMIN", "admin-1");
        assertEquals(BatchStatus.PENDING_SPLIT_APPROVAL, pendingBatch.getStatus());

        // ADM-02 Registrar approves the split decision
        domainService.consumeApprovalDecision(requested.getRequestId(), SplitMergeDecision.APPROVED, "registrar-1", System.currentTimeMillis(), "Approved by registrar");

        // Source batch is closed
        Batch closedSource = domainService.getBatch(created.getId(), "ACADEMIC_ADMIN", "admin-1");
        assertEquals(BatchStatus.CLOSED, closedSource.getStatus());

        // Resulting batches created
        Map<String, String> f = new HashMap<>();
        f.put("courseId", "CRS-CS101");
        List<Batch> results = domainService.searchBatches(f, 1, 10, "ACADEMIC_ADMIN", "admin-1");
        // Must contain SPLIT-SRC-01-A1 and SPLIT-SRC-01-A2
        boolean hasA1 = false, hasA2 = false;
        for (Batch r : results) {
            if ("SPLIT-SRC-01-A1".equals(r.getBatchCode())) hasA1 = true;
            if ("SPLIT-SRC-01-A2".equals(r.getBatchCode())) hasA2 = true;
        }
        assertTrue("Resulting batch A1 must exist", hasA1);
        assertTrue("Resulting batch A2 must exist", hasA2);
    }

    @Test
    public void testBatchMergeWorkflowWithADM02Rejection() {
        Batch b1 = new Batch();
        b1.setBatchCode("MRG-01");
        b1.setName("Merge Cohort 1");
        b1.setCourseId("CRS-CS101");
        b1.setDepartmentId("DEP-CS");
        b1.setAcademicYear("2024-2025");
        b1.setSemesterNo(1);
        b1.setCapacity(30);
        Batch created1 = domainService.createBatch(b1, "admin-1", "ACADEMIC_ADMIN", null);
        domainService.openBatch(created1.getId(), "admin-1", "ACADEMIC_ADMIN");

        Batch b2 = new Batch();
        b2.setBatchCode("MRG-02");
        b2.setName("Merge Cohort 2");
        b2.setCourseId("CRS-CS101");
        b2.setDepartmentId("DEP-CS");
        b2.setAcademicYear("2024-2025");
        b2.setSemesterNo(1);
        b2.setCapacity(30);
        Batch created2 = domainService.createBatch(b2, "admin-1", "ACADEMIC_ADMIN", null);
        domainService.openBatch(created2.getId(), "admin-1", "ACADEMIC_ADMIN");

        // Request Merge
        BatchMergeRequest mergeReq = new BatchMergeRequest();
        mergeReq.setReason("Under-enrolled batches consolidation");
        mergeReq.setSourceBatchIds(Arrays.asList(created1.getId(), created2.getId()));

        BatchMergeRequest requested = domainService.requestMerge(mergeReq, "admin-1", "ACADEMIC_ADMIN");
        assertEquals(BatchStatus.PENDING_MERGE_APPROVAL, domainService.getBatch(created1.getId(), "ACADEMIC_ADMIN", "admin-1").getStatus());
        assertEquals(BatchStatus.PENDING_MERGE_APPROVAL, domainService.getBatch(created2.getId(), "ACADEMIC_ADMIN", "admin-1").getStatus());

        // ADM-02 Registrar rejects merge
        domainService.consumeApprovalDecision(requested.getRequestId(), SplitMergeDecision.REJECTED, "registrar-1", System.currentTimeMillis(), "Not permitted mid-semester");

        // Source batches revert to ACTIVE
        assertEquals(BatchStatus.ACTIVE, domainService.getBatch(created1.getId(), "ACADEMIC_ADMIN", "admin-1").getStatus());
        assertEquals(BatchStatus.ACTIVE, domainService.getBatch(created2.getId(), "ACADEMIC_ADMIN", "admin-1").getStatus());
    }

    // =========================================================================
    // 8. Event Ingestion, Outbox, Reconciliation & Rate Limiter (Stories 41-46, 61, 62)
    // =========================================================================

    @Test
    public void testEventDeduplicationAndUpstreamIngestion() {
        String eventId = "EVT-STU-STATUS-999";
        domainService.consumeStudentStatusChanged(eventId, "STU-1005", "SUSPENDED");
        assertTrue(domainService.isEventProcessed(eventId));

        // Replaying same event should be skipped idempotently (Story 45)
        domainService.consumeStudentStatusChanged(eventId, "STU-1005", "SUSPENDED");

        // Course deactivated flags referencing batches (Story 43)
        Batch b = new Batch();
        b.setBatchCode("COURSE-DEACT-01");
        b.setName("Course Deactivation Batch");
        b.setCourseId("CRS-MATH201");
        b.setAcademicYear("2024-2025");
        b.setSemesterNo(2);
        b.setCapacity(40);
        Batch created = domainService.createBatch(b, "admin-1", "ACADEMIC_ADMIN", null);
        assertTrue(created.isCourseActive());

        domainService.consumeCourseDeactivated("EVT-CRS-DEACT-1", "CRS-MATH201");
        assertFalse(domainService.getBatch(created.getId(), "ACADEMIC_ADMIN", "admin-1").isCourseActive());
    }

    @Test
    public void testRosterReconciliationCheck() {
        // Zero mismatches initially
        assertEquals(0, domainService.checkRosterReconciliationMismatches());
    }

    @Test
    public void testRateLimiterExhaustionAndReset() {
        String clientKey = "test-client";
        for (int i = 0; i < 5; i++) {
            RateLimiter.RateLimitResult res = rateLimiter.tryAcquire(clientKey);
            assertTrue("Request " + i + " should be allowed", res.isAllowed());
        }

        // 6th request should be blocked (HTTP 429)
        RateLimiter.RateLimitResult blocked = rateLimiter.tryAcquire(clientKey);
        assertFalse("Request 6 must be blocked", blocked.isAllowed());
        assertTrue(blocked.getRetryAfterSeconds() > 0);

        rateLimiter.reset();
        assertTrue(rateLimiter.tryAcquire(clientKey).isAllowed());
    }

    @Test
    public void testTransactionContextAtomicCommitAndRollback() {
        TransactionContext tx = new TransactionContext("TX-TEST-01");
        tx.begin();
        assertTrue(tx.isActive());

        final boolean[] compensated = {false};
        tx.addRollback(() -> compensated[0] = true);

        tx.rollback();
        assertTrue("Compensation hook must run on rollback", compensated[0]);
        assertFalse(tx.isActive());
    }

    @Test
    public void testCurriculumLifecycleEventsAndValidation() {
        // 1. Creating batch with invalid/unknown curriculumId throws BatchValidationException
        Batch b1 = new Batch();
        b1.setBatchCode("CURR-VAL-01");
        b1.setName("Curriculum Validation Batch");
        b1.setCourseId("CRS-CS101");
        b1.setCurriculumId("CURR-UNKNOWN-999");
        b1.setAcademicYear("2024-2025");
        b1.setSemesterNo(1);
        b1.setCapacity(50);
        try {
            domainService.createBatch(b1, "admin-1", "ACADEMIC_ADMIN", null);
            fail("Expected BatchValidationException for invalid curriculumId");
        } catch (BatchValidationException e) {
            assertTrue(e.getMessage().contains("CURR-UNKNOWN-999"));
        }

        // 2. Consume CurriculumPublished event
        domainService.consumeCurriculumPublished("EVT-CURR-PUB-01", "CURR-NEP-2026");

        // 3. Batch creation with newly published curriculum succeeds
        b1.setCurriculumId("CURR-NEP-2026");
        Batch created = domainService.createBatch(b1, "admin-1", "ACADEMIC_ADMIN", null);
        assertNotNull(created);
        assertEquals("CURR-NEP-2026", created.getCurriculumId());

        // 4. Consume CurriculumRetired event
        domainService.consumeCurriculumRetired("EVT-CURR-RET-01", "CURR-NEP-2026");

        // 5. Creating another batch with the retired curriculum throws BatchValidationException
        Batch b2 = new Batch();
        b2.setBatchCode("CURR-VAL-02");
        b2.setName("Curriculum Validation Batch 2");
        b2.setCourseId("CRS-CS101");
        b2.setCurriculumId("CURR-NEP-2026");
        b2.setAcademicYear("2024-2025");
        b2.setSemesterNo(1);
        b2.setCapacity(50);
        try {
            domainService.createBatch(b2, "admin-1", "ACADEMIC_ADMIN", null);
            fail("Expected BatchValidationException for retired curriculumId");
        } catch (BatchValidationException e) {
            assertTrue(e.getMessage().contains("retired or inactive"));
        }
    }
}
