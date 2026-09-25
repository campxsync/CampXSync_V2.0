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

    /**
     * MED-01: Department deactivation propagation excludes subjects from active catalog.
     */
    @Test
    public void testDepartmentDeactivationExcludesFromCatalog() {
        Subject s = service.createSubject(createSampleSubject("DEPT-SUB-1", "Operating Systems", "DEP_MECH_01"), "2026-2027", "admin", "ACADEMIC_ADMIN");
        List<Subject> catalogBefore = service.getPublishedCatalog("TENANT-001", "INST-001");
        assertTrue(catalogBefore.stream().anyMatch(sub -> sub.getId().equals(s.getId())));

        // Inbound event: Department deactivated
        service.consumeDepartmentEvent("EVT-DEPT-DEACT-01", "DepartmentDeactivated", "DEP_MECH_01", false);

        // Catalog should now exclude this subject
        List<Subject> catalogAfter = service.getPublishedCatalog("TENANT-001", "INST-001");
        assertFalse(catalogAfter.stream().anyMatch(sub -> sub.getId().equals(s.getId())));
    }

    /**
     * HIGH-02: Subject code length and mandatory attribute validation.
     */
    @Test
    public void testSubjectCodeLengthAndMandatoryValidation() {
        // Too short (2 characters < 3)
        Subject shortCode = createSampleSubject("CS", "Computer Science", "DEPT-CA");
        try {
            service.createSubject(shortCode, "2026-2027", "admin", "ACADEMIC_ADMIN");
            fail("Expected SubjectValidationException on short code");
        } catch (SubjectValidationException e) {
            assertTrue(e.getMessage().contains("between 3 and 20 characters"));
        }

        // Missing departmentId
        Subject noDept = createSampleSubject("CS-102", "Computer Science 2", null);
        noDept.setDepartmentId(null);
        try {
            service.createSubject(noDept, "2026-2027", "admin", "ACADEMIC_ADMIN");
            fail("Expected SubjectValidationException on missing department");
        } catch (SubjectValidationException e) {
            assertTrue(e.getMessage().contains("departmentId is mandatory"));
        }
    }

    // =========================================================================
    // Tests for Candidate 10 User Stories
    // =========================================================================

    /**
     * Story 1: Course Outcomes (CO) with Bloom's Taxonomy levels (K1-K6).
     */
    @Test
    public void testCourseOutcomesDefinitionAndTaxonomy() {
        Subject s = service.createSubject(createSampleSubject("CS-CO-101", "Data Structures", "DEPT-CA"), "2026-2027", "admin1", "ACADEMIC_ADMIN");
        // Initial version 1 is published; modifying COs must be rejected (BR-06)
        try {
            service.updateCourseOutcomes(s.getId(), 1, Collections.singletonList(
                    new CourseOutcome("CO1", "Understand arrays", "K2", 70.0)), "admin1", "ACADEMIC_ADMIN");
            fail("Expected VersionImmutableException on published version");
        } catch (VersionImmutableException e) {
            assertEquals(409, e.getStatusCode());
        }

        // Create draft version 2
        SubjectVersion v2Req = new SubjectVersion();
        v2Req.setChangeSummary("Adding OBE Course Outcomes");
        SubjectVersion v2 = service.createSubjectVersion(s.getId(), v2Req, "admin1", "ACADEMIC_ADMIN");
        assertEquals(2, v2.getVersionNo());

        // Invalid Bloom level must be rejected
        try {
            service.updateCourseOutcomes(s.getId(), 2, Collections.singletonList(
                    new CourseOutcome("CO1", "Understand arrays", "INVALID_K9", 70.0)), "admin1", "ACADEMIC_ADMIN");
            fail("Expected InvalidTaxonomyException on invalid bloom level");
        } catch (InvalidTaxonomyException e) {
            assertEquals(422, e.getStatusCode());
        }

        // Valid COs update
        List<CourseOutcome> cos = Arrays.asList(
                new CourseOutcome("CO1", "Apply sorting algorithms", "K3", 75.0),
                new CourseOutcome("CO2", "Analyze tree complexity", "K4", 80.0)
        );
        List<CourseOutcome> saved = service.updateCourseOutcomes(s.getId(), 2, cos, "admin1", "ACADEMIC_ADMIN");
        assertEquals(2, saved.size());
        assertEquals("K3", saved.get(0).getBloomLevel());
        assertEquals(2, service.getCourseOutcomes(s.getId(), 2).size());
    }

    /**
     * Story 2: CO-to-PO Articulation Matrix with correlation weights (1, 2, 3).
     */
    @Test
    public void testCoPoArticulationMatrix() {
        Subject s = service.createSubject(createSampleSubject("CS-COPO-101", "Algorithms", "DEPT-CA"), "2026-2027", "admin1", "ACADEMIC_ADMIN");
        SubjectVersion v2 = service.createSubjectVersion(s.getId(), new SubjectVersion(), "admin1", "ACADEMIC_ADMIN");

        // Invalid correlation strength (> 3)
        try {
            service.updateCoPoMatrix(s.getId(), v2.getVersionNo(), Collections.singletonList(
                    new CoPoMapping("CO1", "PO1", 5)), "admin1", "ACADEMIC_ADMIN");
            fail("Expected SubjectValidationException for correlation strength > 3");
        } catch (SubjectValidationException e) {
            assertTrue(e.getMessage().contains("Correlation strength must be 1"));
        }

        // Valid CO-PO matrix
        List<CoPoMapping> matrix = Arrays.asList(
                new CoPoMapping("CO1", "PO1", 3),
                new CoPoMapping("CO1", "PO2", 2),
                new CoPoMapping("CO2", "PO3", 1)
        );
        List<CoPoMapping> saved = service.updateCoPoMatrix(s.getId(), v2.getVersionNo(), matrix, "admin1", "ACADEMIC_ADMIN");
        assertEquals(3, saved.size());
        assertEquals(3, service.getCoPoMatrix(s.getId(), v2.getVersionNo()).size());
    }

    /**
     * Story 3: Subject Equivalence & Credit Transfer Mapping.
     */
    @Test
    public void testSubjectEquivalenceMapping() {
        Subject s1 = service.createSubject(createSampleSubject("CS-EQ-1", "Subject One", "DEPT-CA"), "2026-2027", "admin1", "ACADEMIC_ADMIN");
        Subject s2 = service.createSubject(createSampleSubject("CS-EQ-2", "Subject Two", "DEPT-CA"), "2026-2027", "admin1", "ACADEMIC_ADMIN");

        SubjectEquivalence eq = new SubjectEquivalence();
        eq.setTargetSubjectId(s2.getId());
        eq.setEquivalenceType(EquivalenceType.DIRECT_SUBSTITUTION.name());
        eq.setTransferMultiplier(1.0);
        eq.setMinimumGrade("B");

        SubjectEquivalence created = service.addSubjectEquivalence(s1.getId(), eq, "admin1", "ACADEMIC_ADMIN");
        assertNotNull(created.getId());
        assertEquals("ACTIVE", created.getStatus());

        List<SubjectEquivalence> list = service.getSubjectEquivalences(s1.getId());
        assertEquals(1, list.size());

        // Revoke
        SubjectEquivalence revoked = service.revokeSubjectEquivalence(s1.getId(), created.getId(), "admin1", "ACADEMIC_ADMIN");
        assertEquals("REVOKED", revoked.getStatus());
    }

    /**
     * Story 4: National Registries (ABC / APAAR / AICTE / SWAYAM).
     */
    @Test
    public void testNationalIdentifiersMetadata() {
        Subject s = service.createSubject(createSampleSubject("CS-NAT-101", "Operating Systems", "DEPT-CA"), "2026-2027", "admin1", "ACADEMIC_ADMIN");

        SubjectMetadata meta = new SubjectMetadata();
        meta.setCategory("CORE");
        meta.setNationalIdentifiers(Arrays.asList(
                new NationalIdentifier("ABC_COURSE_ID", "ABC-2026-CS101", "2026-01-15", "VERIFIED"),
                new NationalIdentifier("AICTE_MODEL_CURRICULUM_ID", "AICTE-CS-402", "2026-02-01", "VERIFIED")
        ));

        SubjectMetadata saved = service.saveOrUpdateMetadata(s.getId(), meta, "admin1", "ACADEMIC_ADMIN");
        assertEquals(2, saved.getNationalIdentifiers().size());
        assertEquals("ABC_COURSE_ID", saved.getNationalIdentifiers().get(0).getScheme());

        SubjectMetadata retrieved = service.getMetadata(s.getId(), "STUDENT");
        assertEquals(2, retrieved.getNationalIdentifiers().size());
    }

    /**
     * Story 5: Modular Syllabus Units and Hours breakdown.
     */
    @Test
    public void testModularSyllabusUnits() {
        Subject s = service.createSubject(createSampleSubject("CS-SYL-101", "Database Systems", "DEPT-CA"), "2026-2027", "admin1", "ACADEMIC_ADMIN");
        SubjectVersion v2 = service.createSubjectVersion(s.getId(), new SubjectVersion(), "admin1", "ACADEMIC_ADMIN");

        // Invalid unit number
        try {
            service.updateSyllabusUnits(s.getId(), v2.getVersionNo(), Collections.singletonList(
                    new SyllabusUnit(0, "Invalid Unit", Collections.singletonList("Topic A"), 10.0)), "admin1", "ACADEMIC_ADMIN");
            fail("Expected SubjectValidationException for unitNumber <= 0");
        } catch (SubjectValidationException e) {
            assertTrue(e.getMessage().contains("unitNumber must be greater than 0"));
        }

        // Valid units
        List<SyllabusUnit> units = Arrays.asList(
                new SyllabusUnit(1, "Relational Algebra", Arrays.asList("Tuples", "Projections"), 12.0),
                new SyllabusUnit(2, "SQL & Normalization", Arrays.asList("3NF", "BCNF"), 14.0)
        );
        List<SyllabusUnit> saved = service.updateSyllabusUnits(s.getId(), v2.getVersionNo(), units, "admin1", "ACADEMIC_ADMIN");
        assertEquals(2, saved.size());
        assertEquals(12.0, saved.get(0).getHours(), 0.001);
    }

    /**
     * Story 6: Prescribed Textbooks & Bibliographies.
     */
    @Test
    public void testBibliographyMetadata() {
        Subject s = service.createSubject(createSampleSubject("CS-BIB-101", "Computer Networks", "DEPT-CA"), "2026-2027", "admin1", "ACADEMIC_ADMIN");

        SubjectMetadata meta = new SubjectMetadata();
        BibliographyItem textbook = new BibliographyItem("Computer Networking: A Top-Down Approach",
                Arrays.asList("Kurose", "Ross"), "978-0133594140", "7th Edition", "Pearson", "2016", true);
        BibliographyItem refBook = new BibliographyItem("TCP/IP Illustrated",
                Collections.singletonList("Stevens"), "978-0201633467", "1st Edition", "Addison-Wesley", "1994", false);

        meta.setBibliographies(Arrays.asList(textbook, refBook));
        SubjectMetadata saved = service.saveOrUpdateMetadata(s.getId(), meta, "admin1", "ACADEMIC_ADMIN");

        assertEquals(2, saved.getBibliographies().size());
        assertTrue(saved.getBibliographies().get(0).isTextbook());
        assertFalse(saved.getBibliographies().get(1).isTextbook());
    }

    /**
     * Story 7: Multi-Department Cross-Listing.
     */
    @Test
    public void testMultiDepartmentCrossListing() {
        Subject s = createSampleSubject("CS-XLIST-101", "Discrete Mathematics", "DEPT-CA");
        s.setCrossListedDepartmentIds(Arrays.asList("DEP_CSE_01", "DEPT-MATH"));

        Subject created = service.createSubject(s, "2026-2027", "admin1", "ACADEMIC_ADMIN");
        assertEquals(2, created.getCrossListedDepartmentIds().size());

        // Searching by cross-listed department "DEPT-MATH" should find this subject!
        List<Subject> found = service.searchSubjects(null, null, null, "DEPT-MATH", null, null, 0, 10);
        assertTrue(found.stream().anyMatch(sub -> sub.getId().equals(created.getId())));

        // Cross-listing primary department must fail validation
        Subject invalidCross = createSampleSubject("CS-XLIST-102", "Math 2", "DEPT-CA");
        invalidCross.setCrossListedDepartmentIds(Arrays.asList("DEPT-CA", "DEP_CSE_01"));
        try {
            service.createSubject(invalidCross, "2026-2027", "admin1", "ACADEMIC_ADMIN");
            fail("Expected SubjectValidationException for primary department in cross-listed depts");
        } catch (SubjectValidationException e) {
            assertTrue(e.getMessage().contains("cannot be included in cross-listed departments"));
        }
    }

    /**
     * Story 8: Multi-Campus Delivery Constraints.
     */
    @Test
    public void testCampusDeliveryRules() {
        Subject s = service.createSubject(createSampleSubject("CS-CAMPUS-101", "Microprocessors", "DEPT-CA"), "2026-2027", "admin1", "ACADEMIC_ADMIN");

        SubjectMetadata meta = new SubjectMetadata();
        meta.setCampusDeliveryRules(Arrays.asList(
                new CampusDeliveryRule("CAMPUS-MAIN", "OFFLINE", true, 60, "Requires hardware lab"),
                new CampusDeliveryRule("CAMPUS-CITY", "HYBRID", false, 40, "Simulator-based delivery")
        ));

        SubjectMetadata saved = service.saveOrUpdateMetadata(s.getId(), meta, "admin1", "ACADEMIC_ADMIN");
        assertEquals(2, saved.getCampusDeliveryRules().size());
        assertEquals("CAMPUS-MAIN", saved.getCampusDeliveryRules().get(0).getCampusId());
        assertTrue(saved.getCampusDeliveryRules().get(0).isLabFacilityRequired());
    }

    /**
     * Story 9: Board of Studies (BoS) Governance Resolution.
     */
    @Test
    public void testBoardOfStudiesApprovalResolution() {
        Subject s = service.createSubject(createSampleSubject("CS-BOS-101", "Cloud Computing", "DEPT-CA"), "2026-2027", "admin1", "ACADEMIC_ADMIN");
        SubjectVersion v2 = service.createSubjectVersion(s.getId(), new SubjectVersion(), "admin1", "ACADEMIC_ADMIN");

        ApprovalResolution resolution = new ApprovalResolution(
                "BOS-CSE-2026-R09", "Board of Studies - CSE", "2026-05-15",
                "https://campx.internal/bos/2026-r09.pdf", "GAZ-2026-114");

        ApprovalResolution saved = service.updateApprovalResolution(s.getId(), v2.getVersionNo(), resolution, "admin1", "ACADEMIC_ADMIN");
        assertEquals("BOS-CSE-2026-R09", saved.getResolutionNumber());
        assertEquals("GAZ-2026-114", saved.getGazetteNotificationNumber());
        assertEquals("BOS-CSE-2026-R09", service.getApprovalResolution(s.getId(), v2.getVersionNo()).getResolutionNumber());
    }

    /**
     * Story 10: Visual Side-by-Side Subject Version Diff Engine.
     */
    @Test
    public void testSubjectVersionDiff() {
        Subject s = service.createSubject(createSampleSubject("CS-DIFF-101", "Software Engineering", "DEPT-CA"), "2026-2027", "admin1", "ACADEMIC_ADMIN");

        SubjectVersion v2Req = new SubjectVersion();
        v2Req.setCredits(5.0); // changed from 4.0
        v2Req.setContactHours(75.0); // changed from 60.0
        v2Req.setChangeSummary("Upgraded credits and syllabus");
        SubjectVersion v2 = service.createSubjectVersion(s.getId(), v2Req, "admin1", "ACADEMIC_ADMIN");

        VersionDiffResult diff = service.compareVersions(s.getId(), 1, 2);
        assertNotNull(diff);
        assertEquals(1, diff.getVersion1());
        assertEquals(2, diff.getVersion2());
        assertTrue(diff.getDifferences().size() > 0);

        // Check that credits difference is flagged
        Optional<FieldDifference> creditsDiff = diff.getDifferences().stream()
                .filter(d -> d.getFieldName().equals("credits"))
                .findFirst();
        assertTrue(creditsDiff.isPresent());
        assertTrue(creditsDiff.get().isChanged());
        assertEquals("4.0", creditsDiff.get().getVersion1Value());
        assertEquals("5.0", creditsDiff.get().getVersion2Value());
    }
}
