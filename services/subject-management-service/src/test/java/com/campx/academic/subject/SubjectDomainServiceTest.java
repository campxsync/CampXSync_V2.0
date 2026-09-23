package com.campx.academic.subject;

import com.campx.academic.subject.exception.*;
import com.campx.academic.subject.model.SubjectModels.*;
import com.campx.academic.subject.service.SubjectDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit and domain tests for ACD-03 Subject Management Service.
 * Validates all key business rules (BR-01 to BR-14) and user stories.
 */
public class SubjectDomainServiceTest {

    private SubjectDomainService service;

    @Before
    public void setup() {
        service = new SubjectDomainService();
    }

    private Subject createSampleSubject(String code, String name, String deptId) {
        Subject s = new Subject();
        s.setTenantId("TENANT-001");
        s.setInstitutionId("INST-001");
        s.setSubjectCode(code);
        s.setName(name);
        s.setDepartmentId(deptId != null ? deptId : "DEPT-CA");
        s.setSubjectType("CORE");
        s.setClassification("THEORY");
        s.setCredits(4.0);
        s.setContactHours(60.0);
        s.setElective(false);
        return s;
    }

    /**
     * BR-01 & Story 3, 4: Enforce institution-scoped subjectCode uniqueness.
     */
    @Test
    public void testCreateSubjectAndDuplicateCodeRejection() {
        Subject s1 = createSampleSubject("CS-101", "Computer Systems", "DEPT-CA");
        Subject created = service.createSubject(s1, "2026-2027", "admin1", "ACADEMIC_ADMIN");
        assertNotNull(created.getId());
        assertEquals("CS-101", created.getSubjectCode());
        assertEquals(SubjectStatus.ACTIVE, created.getStatus());
        assertEquals(1, created.getCurrentVersion());

        // Duplicate code in same tenant + institution must throw DuplicateSubjectCodeException (409)
        Subject s2 = createSampleSubject("CS-101", "Another Name", "DEPT-CA");
        try {
            service.createSubject(s2, "2026-2027", "admin1", "ACADEMIC_ADMIN");
            fail("Expected DuplicateSubjectCodeException");
        } catch (DuplicateSubjectCodeException e) {
            assertEquals(409, e.getStatusCode());
            assertEquals("ACD_SUBJECT_CODE_DUPLICATE", e.getErrorCode());
        }
    }

    /**
     * BR-08 & Story 5: Department validation.
     */
    @Test
    public void testDepartmentValidation() {
        Subject s = createSampleSubject("MATH-101", "Calculus", "NON_EXISTENT_DEPT");
        try {
            service.createSubject(s, "2026-2027", "admin1", "ACADEMIC_ADMIN");
            fail("Expected SubjectValidationException for non-existent department");
        } catch (SubjectValidationException e) {
            assertEquals(422, e.getStatusCode());
            assertTrue(e.getMessage().contains("does not exist"));
        }
    }

    /**
     * BR-02, BR-03 & Story 12: Credit and contact hours validation.
     */
    @Test
    public void testCreditAndContactHoursPolicy() {
        Subject invalidCredits = createSampleSubject("PHY-101", "Physics", "DEPT-CA");
        invalidCredits.setCredits(0.0);
        try {
            service.createSubject(invalidCredits, "2026-2027", "admin1", "ACADEMIC_ADMIN");
            fail("Expected CreditPolicyViolationException for 0 credits");
        } catch (CreditPolicyViolationException e) {
            assertEquals(422, e.getStatusCode());
        }

        Subject negativeHours = createSampleSubject("PHY-102", "Physics 2", "DEPT-CA");
        negativeHours.setContactHours(-10.0);
        try {
            service.createSubject(negativeHours, "2026-2027", "admin1", "ACADEMIC_ADMIN");
            fail("Expected CreditPolicyViolationException for negative contact hours");
        } catch (CreditPolicyViolationException e) {
            assertEquals(422, e.getStatusCode());
        }
    }

    /**
     * BR-04 & Story 10, 11: Taxonomy and classification validation.
     */
    @Test
    public void testTaxonomyValidation() {
        Subject invalidType = createSampleSubject("CHEM-101", "Chemistry", "DEPT-CA");
        invalidType.setSubjectType("UNKNOWN_TYPE");
        try {
            service.createSubject(invalidType, "2026-2027", "admin1", "ACADEMIC_ADMIN");
            fail("Expected InvalidTaxonomyException");
        } catch (InvalidTaxonomyException e) {
            assertEquals(422, e.getStatusCode());
        }
    }

    /**
     * BR-12 & Story 6, 26: Optimistic concurrency locking on update.
     */
    @Test
    public void testOptimisticLockingOnUpdate() {
        Subject s = service.createSubject(createSampleSubject("BIO-101", "Biology", "DEPT-CA"), "2026-2027", "admin1", "ACADEMIC_ADMIN");
        assertEquals(1L, s.getVersion());

        // Concurrent update with stale version
        Subject updateReq = new Subject();
        updateReq.setName("Advanced Biology");
        try {
            service.updateSubject(s.getId(), updateReq, 999L, "admin1", "ACADEMIC_ADMIN");
            fail("Expected VersionConflictException");
        } catch (VersionConflictException e) {
            assertEquals(409, e.getStatusCode());
            assertEquals("ACD_STALE_VERSION", e.getErrorCode());
        }

        // Correct version update
        Subject updated = service.updateSubject(s.getId(), updateReq, 1L, "admin1", "ACADEMIC_ADMIN");
        assertEquals("Advanced Biology", updated.getName());
        assertEquals(2L, updated.getVersion());
    }

    /**
     * BR-05, Story 8, 80: Hard deletion prevented when subject is referenced.
     */
    @Test
    public void testHardDeletionBlockedWhenReferenced() {
        Subject s = service.createSubject(createSampleSubject("HIST-101", "World History", "DEPT-CA"), "2026-2027", "admin1", "ACADEMIC_ADMIN");

        // Mark subject as actively referenced by curriculum
        service.addReferencedSubject(s.getId());

        try {
            service.deleteSubject(s.getId(), "admin1", "ACADEMIC_ADMIN");
            fail("Expected SubjectReferencedException");
        } catch (SubjectReferencedException e) {
            assertEquals(409, e.getStatusCode());
            assertEquals("ACD_SUBJECT_REFERENCED", e.getErrorCode());
        }

        // Once unreferenced, hard deletion succeeds
        service.removeReferencedSubject(s.getId());
        boolean deleted = service.deleteSubject(s.getId(), "admin1", "ACADEMIC_ADMIN");
        assertTrue(deleted);
    }

    /**
     * Epic 4: Subject Version Management (BR-06, BR-07, BR-10, Story 19-25).
     */
    @Test
    public void testVersionLifecycleAndImmutability() {
        Subject s = service.createSubject(createSampleSubject("ENG-101", "English Literature", "DEPT-CA"), "2026-2027", "admin1", "ACADEMIC_ADMIN");

        // 1. Initial version is 1 and published
        List<SubjectVersion> versions = service.getSubjectVersions(s.getId());
        assertEquals(1, versions.size());
        assertEquals(VersionStatus.PUBLISHED, versions.get(0).getStatus());

        // 2. Published versions are immutable (BR-06, Story 22)
        SubjectVersion updateReq = new SubjectVersion();
        updateReq.setCredits(5.0);
        try {
            service.updateDraftSubjectVersion(s.getId(), 1, updateReq, "admin1", "ACADEMIC_ADMIN");
            fail("Expected VersionImmutableException");
        } catch (VersionImmutableException e) {
            assertEquals(409, e.getStatusCode());
            assertEquals("ACD_VERSION_IMMUTABLE", e.getErrorCode());
        }

        // 3. Create draft version 2 (Story 19, 23)
        SubjectVersion v2Data = new SubjectVersion();
        v2Data.setCredits(5.0);
        v2Data.setChangeSummary("Updated credit weight");
        SubjectVersion v2 = service.createSubjectVersion(s.getId(), v2Data, "admin1", "ACADEMIC_ADMIN");
        assertEquals(2, v2.getVersionNo());
        assertEquals(VersionStatus.DRAFT, v2.getStatus());

        // 4. Update draft version 2 in-place (Story 20)
        v2Data.setContactHours(75.0);
        SubjectVersion v2Updated = service.updateDraftSubjectVersion(s.getId(), 2, v2Data, "admin1", "ACADEMIC_ADMIN");
        assertEquals(75.0, v2Updated.getContactHours(), 0.001);

        // 5. Publish version 2 (Story 21)
        SubjectVersion publishedV2 = service.publishSubjectVersion(s.getId(), 2, "approver1", "REGISTRAR");
        assertEquals(VersionStatus.PUBLISHED, publishedV2.getStatus());

        // Verify previous version is SUPERSEDED
        SubjectVersion v1 = service.getSubjectVersion(s.getId(), 1);
        assertEquals(VersionStatus.SUPERSEDED, v1.getStatus());

        // Verify subject master currentVersion is updated
        Subject updatedSubject = service.getSubject(s.getId());
        assertEquals(2, updatedSubject.getCurrentVersion());
        assertEquals(5.0, updatedSubject.getCredits(), 0.001);
    }

    /**
     * Epic 6: Prerequisite DAG Cycle Detection and Dependency Graph (Story 32, 33, 34, 35, 37).
     */
    @Test
    public void testPrerequisiteCycleDetectionAndGraph() {
        Subject a = service.createSubject(createSampleSubject("CS-A", "Subject A", "DEPT-CA"), "2026-2027", "admin", "ACADEMIC_ADMIN");
        Subject b = service.createSubject(createSampleSubject("CS-B", "Subject B", "DEPT-CA"), "2026-2027", "admin", "ACADEMIC_ADMIN");
        Subject c = service.createSubject(createSampleSubject("CS-C", "Subject C", "DEPT-CA"), "2026-2027", "admin", "ACADEMIC_ADMIN");

        // A -> B (A requires B)
        SubjectPrerequisite req1 = new SubjectPrerequisite();
        req1.setPrerequisiteSubjectId(b.getId());
        req1.setRelationshipType(RelationshipType.PREREQUISITE);
        service.addPrerequisite(a.getId(), req1, "admin", "ACADEMIC_ADMIN");

        // B -> C (B requires C)
        SubjectPrerequisite req2 = new SubjectPrerequisite();
        req2.setPrerequisiteSubjectId(c.getId());
        req2.setRelationshipType(RelationshipType.PREREQUISITE);
        service.addPrerequisite(b.getId(), req2, "admin", "ACADEMIC_ADMIN");

        // Duplicate active prerequisite check (Story 34)
        try {
            service.addPrerequisite(a.getId(), req1, "admin", "ACADEMIC_ADMIN");
            fail("Expected DuplicatePrerequisiteException");
        } catch (DuplicatePrerequisiteException e) {
            assertEquals(409, e.getStatusCode());
        }

        // C -> A would create a cycle (C -> A -> B -> C): Must be rejected (Story 33)
        SubjectPrerequisite cycleReq = new SubjectPrerequisite();
        cycleReq.setPrerequisiteSubjectId(a.getId());
        try {
            service.addPrerequisite(c.getId(), cycleReq, "admin", "ACADEMIC_ADMIN");
            fail("Expected PrerequisiteCycleException for circular dependency");
        } catch (PrerequisiteCycleException e) {
            assertEquals(409, e.getStatusCode());
            assertEquals("ACD_PREREQUISITE_CYCLE", e.getErrorCode());
        }

        // Verify Graph traversal (Story 37)
        PrerequisiteGraphView bGraph = service.getPrerequisiteGraph(b.getId());
        assertEquals(1, bGraph.getPrerequisites().size()); // requires C
        assertEquals(c.getId(), bGraph.getPrerequisites().get(0).getSubjectId());
        assertEquals(1, bGraph.getDependents().size());    // required by A
        assertEquals(a.getId(), bGraph.getDependents().get(0).getSubjectId());
    }

    /**
     * Story 35: Block deactivated subjects from being newly introduced as prerequisites.
     */
    @Test
    public void testBlockDeactivatedSubjectAsPrerequisite() {
        Subject activeSubject = service.createSubject(createSampleSubject("AI-101", "AI Intro", "DEPT-CA"), "2026-2027", "admin", "ACADEMIC_ADMIN");
        Subject targetSubject = service.createSubject(createSampleSubject("AI-100", "Old AI", "DEPT-CA"), "2026-2027", "admin", "ACADEMIC_ADMIN");

        // Deactivate target
        service.deactivateSubject(targetSubject.getId(), "Phasing out", "admin", "ACADEMIC_ADMIN");

        SubjectPrerequisite req = new SubjectPrerequisite();
        req.setPrerequisiteSubjectId(targetSubject.getId());

        try {
            service.addPrerequisite(activeSubject.getId(), req, "admin", "ACADEMIC_ADMIN");
            fail("Expected SubjectValidationException when adding deactivated subject as prerequisite");
        } catch (SubjectValidationException e) {
            assertEquals(422, e.getStatusCode());
            assertTrue(e.getMessage().contains("deactivated"));
        }
    }

    /**
     * Epic 7: Subject Lifecycle Governance (Story 39-44).
     */
    @Test
    public void testLifecycleTransitions() {
        Subject s = service.createSubject(createSampleSubject("GEO-101", "Geography", "DEPT-CA"), "2026-2027", "admin", "ACADEMIC_ADMIN");
        assertEquals(SubjectStatus.ACTIVE, s.getStatus());

        // Deactivate (Story 39)
        Subject deactivated = service.deactivateSubject(s.getId(), "Semester break", "admin", "ACADEMIC_ADMIN");
        assertEquals(SubjectStatus.DEACTIVATED, deactivated.getStatus());

        // Reactivate (Story 40)
        Subject reactivated = service.reactivateSubject(s.getId(), "New semester", "admin", "ACADEMIC_ADMIN");
        assertEquals(SubjectStatus.ACTIVE, reactivated.getStatus());

        // Deprecate (Story 41)
        Subject deprecated = service.deprecateSubject(s.getId(), "Phasing out for new curriculum", "admin", "ACADEMIC_ADMIN");
        assertEquals(SubjectStatus.DEPRECATED, deprecated.getStatus());

        // Retire (Story 42)
        Subject retired = service.retireSubject(s.getId(), "Permanently closed", "admin", "ACADEMIC_ADMIN");
        assertEquals(SubjectStatus.RETIRED, retired.getStatus());

        // Check history records (Story 45)
        List<SubjectHistory> history = service.getSubjectHistory(s.getId());
        assertTrue(history.size() >= 5);
    }

    /**
     * Epic 5: Subject Metadata & Sensitivity Filtering (Story 28, 29, 64).
     */
    @Test
    public void testMetadataAndSensitivityClassification() {
        Subject s = service.createSubject(createSampleSubject("STAT-101", "Statistics", "DEPT-CA"), "2026-2027", "admin", "ACADEMIC_ADMIN");

        SubjectMetadata meta = new SubjectMetadata();
        meta.setTags(Arrays.asList("data", "math", "analytics"));
        meta.setDeliveryMode("HYBRID");
        meta.setAssessmentMode("INTEGRATED");
        meta.setRegulatoryCode("AICTE-REG-2026-09"); // L3 Confidential
        meta.setPrerequisiteNotes("Strong calculus background advised"); // L3

        SubjectMetadata saved = service.saveOrUpdateMetadata(s.getId(), meta, "admin", "ACADEMIC_ADMIN");
        assertEquals("HYBRID", saved.getDeliveryMode());

        // Privileged caller receives L3 fields
        SubjectMetadata adminView = service.getMetadata(s.getId(), "ACADEMIC_ADMIN");
        assertNotNull(adminView.getRegulatoryCode());

        // Non-privileged Student/Parent caller has L3 fields masked (Story 64)
        SubjectMetadata studentView = service.getMetadata(s.getId(), "STUDENT");
        assertNull(studentView.getRegulatoryCode());
        assertNull(studentView.getPrerequisiteNotes());
        assertEquals("HYBRID", studentView.getDeliveryMode());
        assertEquals(3, studentView.getTags().size());
    }

    /**
     * Epic 8: Bulk Import & Export (Story 47, 48).
     */
    @Test
    public void testBulkImportAndExport() {
        List<Subject> rows = new ArrayList<>();
        rows.add(createSampleSubject("BULK-001", "Bulk Subject 1", "DEPT-CA"));
        rows.add(createSampleSubject("BULK-002", "Bulk Subject 2", "DEPT-CA"));

        // Row 3 has duplicate code of Row 1
        rows.add(createSampleSubject("BULK-001", "Duplicate Code Subject", "DEPT-CA"));

        BulkImportResult res = service.bulkImportSubjects(rows, "2026-2027", "admin", "ACADEMIC_ADMIN");
        assertEquals(3, res.getTotalRows());
        assertEquals(2, res.getSuccessfulRows());
        assertEquals(1, res.getFailedRows());
        assertEquals("ACD_SUBJECT_CODE_DUPLICATE", res.getErrors().get(0).getErrorCode());

        // Test Export CSV
        String csv = service.exportCatalogAsCsv("ACADEMIC_ADMIN");
        assertNotNull(csv);
        assertTrue(csv.contains("BULK-001"));
        assertTrue(csv.contains("BULK-002"));
    }

    /**
     * Epic 9 & 10: Outbox, Idempotency and Event Deduplication (Story 50, 53, 56).
     */
    @Test
    public void testOutboxAndIdempotencyAndEventDeduplication() {
        Subject s = service.createSubject(createSampleSubject("OUT-101", "Outbox Subject", "DEPT-CA"), "2026-2027", "admin", "ACADEMIC_ADMIN");

        // Verify outbox event written
        List<OutboxEvent> outbox = service.getOutboxEvents();
        assertFalse(outbox.isEmpty());
        OutboxEvent last = outbox.get(outbox.size() - 1);
        assertEquals("SubjectCreated", last.getEventType());
        assertEquals(s.getId(), last.getAggregateId());
        assertEquals("PENDING", last.getStatus());

        // Verify Inbound Event Deduplication (Story 53)
        boolean first = service.consumeDepartmentEvent("EVT-DEPT-999", "DepartmentUpdated", "DEPT-CA", true);
        assertTrue(first);
        boolean duplicate = service.consumeDepartmentEvent("EVT-DEPT-999", "DepartmentUpdated", "DEPT-CA", true);
        assertFalse(duplicate);

        // Verify Idempotency recording (Story 56)
        service.recordIdempotency("IDEMP-KEY-1", "TENANT-001", "HASH123", "CREATE", 201, "{\"success\":true}");
        IdempotencyRecord rec = service.checkIdempotency("IDEMP-KEY-1", "TENANT-001", "HASH123");
        assertNotNull(rec);
        assertEquals(201, rec.getStatusCode());

        // Hash mismatch throws conflict
        try {
            service.checkIdempotency("IDEMP-KEY-1", "TENANT-001", "DIFFERENT_HASH");
            fail("Expected SubjectValidationException on idempotency hash mismatch");
        } catch (SubjectValidationException e) {
            assertEquals("IDEMP_HASH_MISMATCH", e.getErrorCode());
        }
    }
}
