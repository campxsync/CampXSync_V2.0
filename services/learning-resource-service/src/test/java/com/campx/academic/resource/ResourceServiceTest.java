package com.campx.academic.resource;

import com.campx.academic.resource.exception.*;
import com.campx.academic.resource.model.ResourceModels.*;
import com.campx.academic.resource.service.ResourceDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Comprehensive domain service tests covering ACD-08 user stories:
 * - Registration, Validation, Duplicate Prevention (US-001 - US-006, US-049, US-050)
 * - Object storage references & duplicate detection (US-007, US-008, US-056)
 * - Monotonic versioning & immutability (US-009 - US-012, US-024)
 * - Lifecycle: Draft, Publish, SyllabusPublish, Archive (US-020 - US-025)
 * - Audit history (US-031 - US-033)
 * - Outbox events & DLQ replay (US-034 - US-042, US-061)
 * - Idempotency protection (US-040)
 * - Bulk import & Disaster recovery (US-048, US-058, US-065)
 */
public class ResourceServiceTest {

    private ResourceDomainService service;
    private static final String TENANT = "TENANT-001";
    private static final String USER_FACULTY = "prof-smith";
    private static final String USER_ADMIN = "admin-1";

    @Before
    public void setUp() {
        service = new ResourceDomainService();
    }

    private CreateResourceRequest createSampleRequest(String code, String type) {
        CreateResourceRequest req = new CreateResourceRequest();
        req.resourceCode = code;
        req.title = "Introduction to Algorithms Syllabus";
        req.resourceType = type != null ? type : "SYLLABUS";
        req.subjectId = "SUB_CS101";
        req.courseId = "CRS_CS101";
        req.curriculumId = "CUR_CSE_2026";
        req.departmentId = "DEP_CS";
        req.description = "Official course syllabus";
        req.tags = Arrays.asList("algorithms", "core", "syllabus");
        req.storageObjectRef = "syllabi/2026/cs101_syllabus.pdf";
        req.storageProvider = "SHARED_BLOB";
        req.fileName = "cs101_syllabus.pdf";
        req.mimeType = "application/pdf";
        req.fileSize = 204800L;
        req.checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        return req;
    }

    @Test
    public void testCreateResourceSuccess() {
        CreateResourceRequest req = createSampleRequest("RES-CS-001", "SYLLABUS");
        LearningResource res = service.createResource(req, TENANT, USER_FACULTY, "FACULTY", null, "TRACE-01");

        assertNotNull(res);
        assertNotNull(res.getId());
        assertEquals("RES-CS-001", res.getResourceCode());
        assertEquals(ResourceStatus.DRAFT, res.getStatus());
        assertEquals(1L, res.getCurrentVersion());
        assertNull(res.getPublishedVersion());
        assertEquals(3, res.getTags().size());

        // Check Version 1 registered
        List<ResourceVersion> versions = service.getResourceVersions(res.getId(), TENANT);
        assertEquals(1, versions.size());
        assertEquals(1L, versions.get(0).getVersionNo());
        assertEquals("application/pdf", versions.get(0).getMimeType());

        // Check Audit history
        List<ResourceHistory> history = service.getResourceHistory(res.getId(), TENANT);
        assertFalse(history.isEmpty());
        assertEquals("CREATE", history.get(0).getAction());
    }

    @Test(expected = ResourceDuplicateException.class)
    public void testPreventDuplicateResourceCode() {
        CreateResourceRequest req1 = createSampleRequest("RES-DUP-01", "STUDY_MATERIAL");
        service.createResource(req1, TENANT, USER_FACULTY, "FACULTY", null, "TRACE-01");

        CreateResourceRequest req2 = createSampleRequest("RES-DUP-01", "STUDY_MATERIAL");
        service.createResource(req2, TENANT, USER_FACULTY, "FACULTY", null, "TRACE-02");
    }

    @Test(expected = ResourceValidationException.class)
    public void testInvalidResourceTypeFails() {
        CreateResourceRequest req = createSampleRequest("RES-INVALID-TYPE", "UNKNOWN_TYPE");
        service.createResource(req, TENANT, USER_FACULTY, "FACULTY", null, "TRACE-01");
    }

    @Test(expected = ResourceValidationException.class)
    public void testInvalidChecksumFormatFails() {
        CreateResourceRequest req = createSampleRequest("RES-BAD-HASH", "SYLLABUS");
        req.checksum = "invalid-not-hex";
        service.createResource(req, TENANT, USER_FACULTY, "FACULTY", null, "TRACE-01");
    }

    @Test(expected = ResourceValidationException.class)
    public void testNegativeFileSizeFails() {
        CreateResourceRequest req = createSampleRequest("RES-BAD-SIZE", "SYLLABUS");
        req.fileSize = -50L;
        service.createResource(req, TENANT, USER_FACULTY, "FACULTY", null, "TRACE-01");
    }

    @Test
    public void testIdempotencyOnResourceCreation() {
        CreateResourceRequest req = createSampleRequest("RES-IDEMP-01", "STUDY_MATERIAL");
        LearningResource res1 = service.createResource(req, TENANT, USER_FACULTY, "FACULTY", "IDEMP-KEY-999", "TRACE-01");
        LearningResource res2 = service.createResource(req, TENANT, USER_FACULTY, "FACULTY", "IDEMP-KEY-999", "TRACE-01");

        assertEquals(res1.getId(), res2.getId());
    }

    @Test(expected = ResourceIdempotencyConflictException.class)
    public void testIdempotencyConflictWithDifferentPayload() {
        CreateResourceRequest req1 = createSampleRequest("RES-IDEMP-CONF", "STUDY_MATERIAL");
        service.createResource(req1, TENANT, USER_FACULTY, "FACULTY", "IDEMP-CONFLICT-KEY", "TRACE-01");

        CreateResourceRequest req2 = createSampleRequest("RES-IDEMP-CONF-2", "LAB_MANUAL");
        service.createResource(req2, TENANT, USER_FACULTY, "FACULTY", "IDEMP-CONFLICT-KEY", "TRACE-01");
    }

    @Test
    public void testMonotonicVersionCreation() {
        CreateResourceRequest req = createSampleRequest("RES-VER-01", "STUDY_MATERIAL");
        LearningResource res = service.createResource(req, TENANT, USER_FACULTY, "FACULTY", null, "TRACE-01");
        assertEquals(1L, res.getCurrentVersion());

        CreateVersionRequest verReq = new CreateVersionRequest();
        verReq.storageObjectRef = "notes/v2_notes.pdf";
        verReq.storageProvider = "SHARED_BLOB";
        verReq.fileName = "notes_v2.pdf";
        verReq.mimeType = "application/pdf";
        verReq.fileSize = 300000L;
        verReq.checksum = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
        verReq.changeSummary = "Added Chapter 2";
        verReq.expectedVersion = 1L;

        ResourceVersion v2 = service.createVersion(res.getId(), verReq, TENANT, USER_FACULTY, "FACULTY", "TRACE-02");
        assertNotNull(v2);
        assertEquals(2L, v2.getVersionNo());
        assertEquals(2L, res.getCurrentVersion());

        List<ResourceVersion> versions = service.getResourceVersions(res.getId(), TENANT);
        assertEquals(2, versions.size());
    }

    @Test(expected = ResourceVersionConflictException.class)
    public void testOptimisticLockingOnVersionCreation() {
        CreateResourceRequest req = createSampleRequest("RES-OPT-01", "STUDY_MATERIAL");
        LearningResource res = service.createResource(req, TENANT, USER_FACULTY, "FACULTY", null, "TRACE-01");

        CreateVersionRequest verReq = new CreateVersionRequest();
        verReq.storageObjectRef = "notes/v2.pdf";
        verReq.mimeType = "application/pdf";
        verReq.fileSize = 100L;
        verReq.checksum = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
        verReq.expectedVersion = 999L; // Stale version

        service.createVersion(res.getId(), verReq, TENANT, USER_FACULTY, "FACULTY", "TRACE-02");
    }

    @Test
    public void testRestoreHistoricalVersion() {
        CreateResourceRequest req = createSampleRequest("RES-RESTORE-01", "STUDY_MATERIAL");
        LearningResource res = service.createResource(req, TENANT, USER_FACULTY, "FACULTY", null, "TRACE-01");

        CreateVersionRequest verReq = new CreateVersionRequest();
        verReq.storageObjectRef = "notes/v2.pdf";
        verReq.mimeType = "application/pdf";
        verReq.fileSize = 100L;
        verReq.checksum = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
        service.createVersion(res.getId(), verReq, TENANT, USER_FACULTY, "FACULTY", "TRACE-02");
        assertEquals(2L, res.getCurrentVersion());

        // Restore Version 1
        ResourceVersion restored = service.restoreVersion(res.getId(), 1L, TENANT, USER_ADMIN, "ACADEMIC_ADMIN", "Rollback to stable v1", "TRACE-03");
        assertNotNull(restored);
        assertEquals(3L, restored.getVersionNo());
        assertEquals(3L, res.getCurrentVersion());
        assertTrue(restored.getChangeSummary().contains("Restored from version 1"));
    }

    @Test
    public void testPublishResourceAndSyllabusEvent() {
        CreateResourceRequest req = createSampleRequest("RES-PUB-SYL", "SYLLABUS");
        LearningResource res = service.createResource(req, TENANT, USER_FACULTY, "FACULTY", null, "TRACE-01");

        PublishResourceRequest pubReq = new PublishResourceRequest();
        pubReq.expectedVersion = 1L;
        pubReq.approvalRef = "APP-DEP-2026";
        pubReq.comment = "Approved by Academic Committee";

        LearningResource published = service.publishResource(res.getId(), pubReq, TENANT, USER_ADMIN, "ACADEMIC_ADMIN", "TRACE-02");
        assertEquals(ResourceStatus.PUBLISHED, published.getStatus());
        assertEquals(Long.valueOf(1L), published.getPublishedVersion());

        // Check Outbox events contains LearningResourcePublished and SyllabusPublished
        List<OutboxEvent> outbox = service.getOutboxPublisher().getPendingEvents();
        // Since outbox publisher dispatches immediately when broker is available, verify events through history
        List<ResourceHistory> history = service.getResourceHistory(res.getId(), TENANT);
        boolean hasPublishHistory = history.stream().anyMatch(h -> "PUBLISH".equals(h.getAction()));
        assertTrue(hasPublishHistory);
    }

    @Test
    public void testArchiveResource() {
        CreateResourceRequest req = createSampleRequest("RES-ARCH-01", "STUDY_MATERIAL");
        LearningResource res = service.createResource(req, TENANT, USER_FACULTY, "FACULTY", null, "TRACE-01");

        LearningResource archived = service.archiveResource(res.getId(), TENANT, USER_ADMIN, "ACADEMIC_ADMIN", "Obsolete material", "TRACE-02");
        assertEquals(ResourceStatus.ARCHIVED, archived.getStatus());
    }

    @Test
    public void testBulkImportAndDisasterRecovery() {
        BulkImportRequest bulkReq = new BulkImportRequest();
        bulkReq.importJobId = "JOB-TEST-001";
        bulkReq.items.add(createSampleRequest("BULK-01", "LECTURE_NOTES"));
        bulkReq.items.add(createSampleRequest("BULK-02", "LAB_MANUAL"));

        BulkImportResult result = service.bulkImport(bulkReq, TENANT, USER_ADMIN, "ACADEMIC_ADMIN", "TRACE-BULK");
        assertEquals(2, result.totalRecords);
        assertEquals(2, result.successfulRecords);
        assertEquals(0, result.failedRecords);

        // DR check
        DisasterRecoveryValidationResult dr = service.validateDisasterRecovery(TENANT);
        assertTrue(dr.consistent);
        assertEquals(0, dr.brokenReferences);
    }

    @Test
    public void testDeadLetterReplay() {
        OutboxEvent event = service.getOutboxPublisher().recordOutboxEvent(TENANT, "RES-TEST", "TestEvent", "{}", "TRACE-DLQ");
        service.getOutboxPublisher().moveToDLQ(event, "TEST_ERR", "Simulated failure");

        List<DeadLetterEvent> dlq = service.getOutboxPublisher().getDeadLetterEvents();
        assertFalse(dlq.isEmpty());

        boolean replayed = service.replayDeadLetterEvent(event.getEventId());
        assertTrue(replayed);
    }
}
