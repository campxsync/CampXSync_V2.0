package com.campx.academic.timetable;

import com.campx.academic.timetable.exception.*;
import com.campx.academic.timetable.model.TimetableModels.*;
import com.campx.academic.timetable.service.TimetableDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Comprehensive Unit Test Suite for ACD-05 Timetable Management Service.
 * Tests domain rules, state machines, conflict detection, versioning, outbox, and event consumption.
 */
public class TimetableServiceTest {

    private TimetableDomainService service;
    private static final String TENANT_ID = "TENANT-001";
    private static final String USER_ID = "acad-admin-1";

    @Before
    public void setUp() {
        service = new TimetableDomainService();
    }

    // =========================================================================
    // 1. Timetable Draft & Metadata Lifecycle Tests
    // =========================================================================

    @Test
    public void testCreateDraftSuccess() {
        CreateTimetableRequest req = new CreateTimetableRequest();
        req.timetableCode = "TT_CSE_2026_S1";
        req.name = "B.Tech CSE 2026 Semester 1 Timetable";
        req.academicYear = "2026-2027";
        req.semester = "1";
        req.departmentId = "DEP-CSE";
        req.programId = "PROG-BTECH-CSE";
        req.batchId = "BATCH-001";
        req.effectiveFrom = "2026-08-15";
        req.effectiveTo = "2026-12-15";

        Timetable tt = service.createDraft(req, TENANT_ID, USER_ID);

        assertNotNull(tt);
        assertNotNull(tt.getId());
        assertEquals("TT_CSE_2026_S1", tt.getTimetableCode());
        assertEquals(TimetableStatus.DRAFT, tt.getStatus());
        assertEquals(1, tt.getCurrentVersionNo());
        assertEquals(1L, tt.getVersion());
        assertFalse(tt.isDeleted());

        // Verify outbox event written
        List<OutboxEvent> outbox = service.getOutboxEvents();
        assertTrue(outbox.stream().anyMatch(e -> "TimetableCreated".equals(e.getEventType()) && tt.getId().equals(e.getEntityId())));
    }

    @Test(expected = TimetableConflictException.class)
    public void testCreateDraftDuplicateCode() {
        CreateTimetableRequest req = new CreateTimetableRequest();
        req.timetableCode = "TT_DUPLICATE";
        req.name = "First Draft";
        req.academicYear = "2026-2027";
        req.departmentId = "DEP-CSE";
        service.createDraft(req, TENANT_ID, USER_ID);

        // Attempting same code in same tenant throws conflict
        service.createDraft(req, TENANT_ID, USER_ID);
    }

    @Test
    public void testUpdateDraftMetadataSuccess() {
        CreateTimetableRequest req = new CreateTimetableRequest();
        req.timetableCode = "TT_UPDATE_TEST";
        req.name = "Original Name";
        req.academicYear = "2026-2027";
        req.departmentId = "DEP-CSE";
        Timetable tt = service.createDraft(req, TENANT_ID, USER_ID);

        UpdateTimetableRequest updateReq = new UpdateTimetableRequest();
        updateReq.name = "Updated Timetable Name";
        updateReq.version = 1L; // matching version

        Timetable updated = service.updateDraftMetadata(tt.getId(), updateReq, TENANT_ID, USER_ID);
        assertEquals("Updated Timetable Name", updated.getName());
        assertEquals(2L, updated.getVersion());
    }

    @Test(expected = TimetableStaleVersionException.class)
    public void testUpdateDraftMetadataStaleVersionThrows() {
        CreateTimetableRequest req = new CreateTimetableRequest();
        req.timetableCode = "TT_STALE_TEST";
        req.name = "Draft Name";
        req.academicYear = "2026-2027";
        req.departmentId = "DEP-CSE";
        Timetable tt = service.createDraft(req, TENANT_ID, USER_ID);

        UpdateTimetableRequest updateReq = new UpdateTimetableRequest();
        updateReq.name = "Stale update attempt";
        updateReq.version = 999L; // Mismatched optimistic concurrency version

        service.updateDraftMetadata(tt.getId(), updateReq, TENANT_ID, USER_ID);
    }

    @Test
    public void testDeleteDraftSuccess() {
        CreateTimetableRequest req = new CreateTimetableRequest();
        req.timetableCode = "TT_DELETE_TEST";
        req.name = "Draft to Delete";
        req.academicYear = "2026-2027";
        req.departmentId = "DEP-CSE";
        Timetable tt = service.createDraft(req, TENANT_ID, USER_ID);

        service.deleteDraft(tt.getId(), TENANT_ID, USER_ID);

        try {
            service.getTimetable(tt.getId(), TENANT_ID);
            fail("Expected TimetableNotFoundException after soft deletion");
        } catch (TimetableNotFoundException ex) {
            assertTrue(ex.getMessage().contains("not found"));
        }
    }

    // =========================================================================
    // 2. Timetable Entries & Reference Validation Tests
    // =========================================================================

    @Test
    public void testAddEntrySuccess() {
        Timetable tt = createSampleTimetable("TT_ENTRY_SUCCESS");

        CreateEntryRequest entryReq = new CreateEntryRequest();
        entryReq.batchId = "BATCH-001";
        entryReq.subjectId = "SUB-201";
        entryReq.facultyId = "FAC-100";
        entryReq.roomId = "ROOM-12";
        entryReq.dayOfWeek = "MONDAY";
        entryReq.period = 2;
        entryReq.entryType = "TH";

        TimetableEntry entry = service.addEntry(tt.getId(), entryReq, TENANT_ID, USER_ID);

        assertNotNull(entry);
        assertNotNull(entry.getId());
        assertEquals(DayOfWeek.MONDAY, entry.getDayOfWeek());
        assertEquals(2, entry.getPeriod());
        assertEquals("P2", entry.getPeriodId());
        assertEquals(EntryType.TH, entry.getEntryType());
        assertEquals(EntryStatus.ACTIVE, entry.getStatus());

        List<OutboxEvent> outbox = service.getOutboxEvents();
        assertTrue(outbox.stream().anyMatch(e -> "TimetableEntryAdded".equals(e.getEventType()) && entry.getId().equals(e.getEntityId())));
    }

    @Test(expected = InvalidReferenceException.class)
    public void testAddEntryInvalidSubjectReferenceThrows() {
        Timetable tt = createSampleTimetable("TT_INV_SUB");

        CreateEntryRequest entryReq = new CreateEntryRequest();
        entryReq.batchId = "BATCH-001";
        entryReq.subjectId = "SUB-NONEXISTENT";
        entryReq.facultyId = "FAC-100";
        entryReq.dayOfWeek = "MONDAY";
        entryReq.period = 1;

        service.addEntry(tt.getId(), entryReq, TENANT_ID, USER_ID);
    }

    @Test(expected = CrossTenantReferenceException.class)
    public void testAddEntryCrossTenantForbidden() {
        Timetable tt = createSampleTimetable("TT_CROSS_TENANT");

        CreateEntryRequest entryReq = new CreateEntryRequest();
        entryReq.batchId = "BATCH-DIFF-TENANT"; // Foreign tenant
        entryReq.subjectId = "SUB-201";
        entryReq.facultyId = "FAC-100";
        entryReq.dayOfWeek = "MONDAY";
        entryReq.period = 1;

        service.addEntry(tt.getId(), entryReq, TENANT_ID, USER_ID);
    }

    @Test(expected = LabRightsMissingException.class)
    public void testAddLabEntryWithoutLabRightsThrows() {
        Timetable tt = createSampleTimetable("TT_LAB_FAIL");

        CreateEntryRequest entryReq = new CreateEntryRequest();
        entryReq.batchId = "BATCH-001";
        entryReq.subjectId = "SUB-201";
        entryReq.facultyId = "FAC-200"; // FAC-200 does not have lab rights
        entryReq.roomId = "LAB-01";
        entryReq.dayOfWeek = "TUESDAY";
        entryReq.period = 3;
        entryReq.entryType = "LAB";

        service.addEntry(tt.getId(), entryReq, TENANT_ID, USER_ID);
    }

    @Test
    public void testAddLabEntryWithLabRightsSucceeds() {
        Timetable tt = createSampleTimetable("TT_LAB_SUCCESS");

        CreateEntryRequest entryReq = new CreateEntryRequest();
        entryReq.batchId = "BATCH-001";
        entryReq.subjectId = "SUB-201";
        entryReq.facultyId = "FAC-100"; // FAC-100 has lab rights
        entryReq.roomId = "LAB-01";
        entryReq.dayOfWeek = "TUESDAY";
        entryReq.period = 3;
        entryReq.entryType = "LAB";

        TimetableEntry entry = service.addEntry(tt.getId(), entryReq, TENANT_ID, USER_ID);
        assertEquals(EntryType.LAB, entry.getEntryType());
    }

    @Test
    public void testUpdateAndRemoveEntry() {
        Timetable tt = createSampleTimetable("TT_UPDATE_ENTRY");

        CreateEntryRequest entryReq = new CreateEntryRequest();
        entryReq.batchId = "BATCH-001";
        entryReq.subjectId = "SUB-201";
        entryReq.facultyId = "FAC-100";
        entryReq.dayOfWeek = "WEDNESDAY";
        entryReq.period = 1;
        TimetableEntry entry = service.addEntry(tt.getId(), entryReq, TENANT_ID, USER_ID);

        UpdateEntryRequest updateReq = new UpdateEntryRequest();
        updateReq.period = 4;
        updateReq.periodId = "P4";
        TimetableEntry updated = service.updateEntry(tt.getId(), entry.getId(), updateReq, TENANT_ID, USER_ID);
        assertEquals(4, updated.getPeriod());

        service.removeEntry(tt.getId(), entry.getId(), TENANT_ID, USER_ID);
        List<TimetableEntry> entries = service.getEntriesForTimetable(tt.getId(), tt.getCurrentVersionNo());
        assertTrue(entries.isEmpty());
    }

    @Test
    public void testBulkAddEntriesWithRowErrors() {
        Timetable tt = createSampleTimetable("TT_BULK");

        BulkEntriesRequest bulkReq = new BulkEntriesRequest();

        // Row 0: Valid
        CreateEntryRequest e1 = new CreateEntryRequest();
        e1.batchId = "BATCH-001";
        e1.subjectId = "SUB-201";
        e1.facultyId = "FAC-100";
        e1.dayOfWeek = "MONDAY";
        e1.period = 1;
        bulkReq.entries.add(e1);

        // Row 1: Invalid subject
        CreateEntryRequest e2 = new CreateEntryRequest();
        e2.batchId = "BATCH-001";
        e2.subjectId = "NON_EXISTING";
        e2.facultyId = "FAC-100";
        e2.dayOfWeek = "MONDAY";
        e2.period = 2;
        bulkReq.entries.add(e2);

        BulkEntriesResult result = service.bulkAddEntries(tt.getId(), bulkReq, TENANT_ID, USER_ID);
        assertEquals(1, result.successfulEntries.size());
        assertEquals(1, result.errors.size());
        assertEquals(1, result.errors.get(0).rowIndex);
        assertEquals("ACD_TIMETABLE_INVALID_REFERENCE", result.errors.get(0).errorCode);
    }

    // =========================================================================
    // 3. Conflict Detection Engine Tests (BR-01, BR-02, BR-03, BR-07, BR-11)
    // =========================================================================

    @Test
    public void testFacultyDoubleBookingConflict() {
        Timetable tt = createSampleTimetable("TT_CONF_FACULTY");

        // Slot 1: Faculty FAC-100 in Batch 1 on MONDAY P2
        CreateEntryRequest e1 = new CreateEntryRequest();
        e1.batchId = "BATCH-001";
        e1.subjectId = "SUB-201";
        e1.facultyId = "FAC-100";
        e1.roomId = "ROOM-12";
        e1.dayOfWeek = "MONDAY";
        e1.period = 2;
        service.addEntry(tt.getId(), e1, TENANT_ID, USER_ID);

        // Slot 2: Faculty FAC-100 in Batch 2 also on MONDAY P2 (Double-Booking!)
        CreateEntryRequest e2 = new CreateEntryRequest();
        e2.batchId = "BATCH-002";
        e2.subjectId = "SUB-202";
        e2.facultyId = "FAC-100";
        e2.roomId = "ROOM-101";
        e2.dayOfWeek = "MONDAY";
        e2.period = 2;
        service.addEntry(tt.getId(), e2, TENANT_ID, USER_ID);

        List<ConflictResult> conflicts = service.validateTimetable(tt.getId(), TENANT_ID, USER_ID);

        assertFalse(conflicts.isEmpty());
        assertTrue(conflicts.stream().anyMatch(c -> c.getConflictType() == ConflictType.FACULTY
                && c.getSeverity() == ConflictSeverity.BLOCKING
                && c.getMessage().contains("Faculty FAC-100 is double-booked in MONDAY P2")));

        Timetable refreshed = service.getTimetable(tt.getId(), TENANT_ID);
        assertEquals(TimetableStatus.INVALID, refreshed.getStatus());
    }

    @Test
    public void testRoomDoubleBookingConflict() {
        Timetable tt = createSampleTimetable("TT_CONF_ROOM");

        // Two entries sharing same ROOM-12 on TUESDAY P1
        CreateEntryRequest e1 = new CreateEntryRequest();
        e1.batchId = "BATCH-001";
        e1.subjectId = "SUB-201";
        e1.facultyId = "FAC-100";
        e1.roomId = "ROOM-12";
        e1.dayOfWeek = "TUESDAY";
        e1.period = 1;
        service.addEntry(tt.getId(), e1, TENANT_ID, USER_ID);

        CreateEntryRequest e2 = new CreateEntryRequest();
        e2.batchId = "BATCH-002";
        e2.subjectId = "SUB-202";
        e2.facultyId = "FAC-300";
        e2.roomId = "ROOM-12";
        e2.dayOfWeek = "TUESDAY";
        e2.period = 1;
        service.addEntry(tt.getId(), e2, TENANT_ID, USER_ID);

        List<ConflictResult> conflicts = service.validateTimetable(tt.getId(), TENANT_ID, USER_ID);
        assertTrue(conflicts.stream().anyMatch(c -> c.getConflictType() == ConflictType.ROOM
                && c.getSeverity() == ConflictSeverity.BLOCKING
                && c.getMessage().contains("Room ROOM-12 is double-booked in TUESDAY P1")));
    }

    @Test
    public void testBatchDoubleBookingConflict() {
        Timetable tt = createSampleTimetable("TT_CONF_BATCH");

        // Batch-001 assigned to two different classes at same slot
        CreateEntryRequest e1 = new CreateEntryRequest();
        e1.batchId = "BATCH-001";
        e1.subjectId = "SUB-201";
        e1.facultyId = "FAC-100";
        e1.roomId = "ROOM-12";
        e1.dayOfWeek = "WEDNESDAY";
        e1.period = 3;
        service.addEntry(tt.getId(), e1, TENANT_ID, USER_ID);

        CreateEntryRequest e2 = new CreateEntryRequest();
        e2.batchId = "BATCH-001";
        e2.subjectId = "SUB-202";
        e2.facultyId = "FAC-300";
        e2.roomId = "ROOM-101";
        e2.dayOfWeek = "WEDNESDAY";
        e2.period = 3;
        service.addEntry(tt.getId(), e2, TENANT_ID, USER_ID);

        List<ConflictResult> conflicts = service.validateTimetable(tt.getId(), TENANT_ID, USER_ID);
        assertTrue(conflicts.stream().anyMatch(c -> c.getConflictType() == ConflictType.BATCH
                && c.getSeverity() == ConflictSeverity.BLOCKING
                && c.getMessage().contains("Batch BATCH-001 is double-booked in WEDNESDAY P3")));
    }

    // =========================================================================
    // 4. Timetable Publication & Supersession Tests (BR-05, BR-06, BR-08, BR-12)
    // =========================================================================

    @Test(expected = TimetableValidationException.class)
    public void testPublishUnvalidatedTimetableThrows() {
        Timetable tt = createSampleTimetable("TT_PUB_UNVALIDATED");
        service.publishTimetable(tt.getId(), new PublishRequest(), TENANT_ID, USER_ID);
    }

    @Test(expected = TimetableValidationException.class)
    public void testPublishConflictingTimetableThrows() {
        Timetable tt = createSampleTimetable("TT_PUB_CONFLICT");

        // Introduce double booking
        CreateEntryRequest e1 = new CreateEntryRequest();
        e1.batchId = "BATCH-001";
        e1.subjectId = "SUB-201";
        e1.facultyId = "FAC-100";
        e1.dayOfWeek = "MONDAY";
        e1.period = 1;
        service.addEntry(tt.getId(), e1, TENANT_ID, USER_ID);

        CreateEntryRequest e2 = new CreateEntryRequest();
        e2.batchId = "BATCH-002";
        e2.subjectId = "SUB-202";
        e2.facultyId = "FAC-100";
        e2.dayOfWeek = "MONDAY";
        e2.period = 1;
        service.addEntry(tt.getId(), e2, TENANT_ID, USER_ID);

        service.validateTimetable(tt.getId(), TENANT_ID, USER_ID);
        service.publishTimetable(tt.getId(), new PublishRequest(), TENANT_ID, USER_ID);
    }

    @Test
    public void testCleanTimetableValidationAndPublishSucceeds() {
        Timetable tt = createSampleTimetable("TT_PUB_CLEAN");

        CreateEntryRequest e1 = new CreateEntryRequest();
        e1.batchId = "BATCH-001";
        e1.subjectId = "SUB-201";
        e1.facultyId = "FAC-100";
        e1.roomId = "ROOM-12";
        e1.dayOfWeek = "MONDAY";
        e1.period = 1;
        service.addEntry(tt.getId(), e1, TENANT_ID, USER_ID);

        CreateEntryRequest e2 = new CreateEntryRequest();
        e2.batchId = "BATCH-002";
        e2.subjectId = "SUB-202";
        e2.facultyId = "FAC-300";
        e2.roomId = "ROOM-101";
        e2.dayOfWeek = "MONDAY";
        e2.period = 2;
        service.addEntry(tt.getId(), e2, TENANT_ID, USER_ID);

        List<ConflictResult> conflicts = service.validateTimetable(tt.getId(), TENANT_ID, USER_ID);
        assertTrue(conflicts.isEmpty());
        assertEquals(TimetableStatus.VALIDATED, service.getTimetable(tt.getId(), TENANT_ID).getStatus());

        PublishRequest pubReq = new PublishRequest();
        pubReq.effectiveFrom = "2026-08-15";
        TimetableVersion pubVer = service.publishTimetable(tt.getId(), pubReq, TENANT_ID, USER_ID);

        assertNotNull(pubVer);
        assertEquals(TimetableStatus.PUBLISHED, pubVer.getStatus());
        assertEquals(2, pubVer.getEntriesSnapshot().size());
        assertEquals(TimetableStatus.PUBLISHED, service.getTimetable(tt.getId(), TENANT_ID).getStatus());

        // Verify outbox events TimetablePublished and TimetableChanged
        List<OutboxEvent> outbox = service.getOutboxEvents();
        assertTrue(outbox.stream().anyMatch(e -> "TimetablePublished".equals(e.getEventType())));
        assertTrue(outbox.stream().anyMatch(e -> "TimetableChanged".equals(e.getEventType())));
    }

    @Test
    public void testCloneEffectiveVersionAndSupersedeWorkflow() {
        Timetable tt = createSampleTimetable("TT_CLONE_FLOW");

        CreateEntryRequest e1 = new CreateEntryRequest();
        e1.batchId = "BATCH-001";
        e1.subjectId = "SUB-201";
        e1.facultyId = "FAC-100";
        e1.roomId = "ROOM-12";
        e1.dayOfWeek = "FRIDAY";
        e1.period = 1;
        service.addEntry(tt.getId(), e1, TENANT_ID, USER_ID);

        service.validateTimetable(tt.getId(), TENANT_ID, USER_ID);
        service.publishTimetable(tt.getId(), new PublishRequest(), TENANT_ID, USER_ID);

        // Mid-term change: clone effective version (Story 39, 40)
        CloneRequest cloneReq = new CloneRequest();
        cloneReq.reason = "Mid-term room reassignment";
        Timetable cloned = service.cloneEffectiveVersion(tt.getId(), cloneReq, TENANT_ID, USER_ID);

        assertEquals(2, cloned.getCurrentVersionNo());
        assertEquals(TimetableStatus.DRAFT, cloned.getStatus());

        // Cloned entries exist in v2
        List<TimetableEntry> v2Entries = service.getEntriesForTimetable(tt.getId(), 2);
        assertEquals(1, v2Entries.size());

        // Revalidate and republish v2
        service.validateTimetable(tt.getId(), TENANT_ID, USER_ID);
        TimetableVersion pubV2 = service.publishTimetable(tt.getId(), new PublishRequest(), TENANT_ID, USER_ID);

        assertEquals(2, pubV2.getVersionNo());
        assertEquals(TimetableStatus.PUBLISHED, pubV2.getStatus());

        // Prior version v1 must now be marked SUPERSEDED (BR-08, BR-12)
        List<TimetableVersion> history = service.getVersionHistory(tt.getId(), TENANT_ID);
        assertEquals(2, history.size());
        assertEquals(TimetableStatus.SUPERSEDED, history.get(0).getStatus());
        assertEquals(TimetableStatus.PUBLISHED, history.get(1).getStatus());

        // Verify TimetableSuperseded event was recorded
        List<OutboxEvent> outbox = service.getOutboxEvents();
        assertTrue(outbox.stream().anyMatch(e -> "TimetableSuperseded".equals(e.getEventType())));
    }

    // =========================================================================
    // 5. Inbound Event Ingestion & Deduplication Tests
    // =========================================================================

    @Test
    public void testInboundBatchUpdatedEventConsumptionAndDeduplication() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", "EVT-BATCH-TEST-001");
        event.put("eventType", "BatchUpdated");
        event.put("tenantId", TENANT_ID);
        event.put("entityId", "BATCH-001");
        Map<String, Object> data = new HashMap<>();
        data.put("batchId", "BATCH-001");
        event.put("data", data);

        boolean first = service.consumeEvent(event);
        assertTrue(first);

        // Redelivered event is recognized as duplicate and processed idempotently
        boolean duplicate = service.consumeEvent(event);
        assertTrue(duplicate);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private Timetable createSampleTimetable(String code) {
        CreateTimetableRequest req = new CreateTimetableRequest();
        req.timetableCode = code;
        req.name = "Timetable " + code;
        req.academicYear = "2026-2027";
        req.semester = "1";
        req.departmentId = "DEP-CSE";
        req.programId = "PROG-BTECH-CSE";
        req.effectiveFrom = "2026-08-15";
        req.effectiveTo = "2026-12-15";
        return service.createDraft(req, TENANT_ID, USER_ID);
    }
}
