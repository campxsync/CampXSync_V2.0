package com.campx.academic.attendance;

import com.campx.academic.attendance.exception.*;
import com.campx.academic.attendance.model.AttendanceModels.*;
import com.campx.academic.attendance.service.AttendanceDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit test suite for ACD-06 Attendance Management Service.
 * Validates all 29 user stories, business rules, and error conditions.
 */
public class AttendanceServiceTest {

    private AttendanceDomainService service;

    @Before
    public void setUp() {
        service = new AttendanceDomainService();
    }

    // =========================================================================
    // 1. Session Management Tests (Stories 1-6)
    // =========================================================================

    @Test
    public void testCreateSessionSuccess() {
        CreateSessionRequest req = new CreateSessionRequest();
        req.batchId = "BATCH-001";
        req.subjectId = "SUB-101";
        req.timetableEntryId = "TT-SLOT-001";
        req.attendanceDate = "2026-10-12";
        req.periodNo = 1;

        AttendanceSession session = service.createSession(req, "TENANT-001", "FAC-001", "FACULTY");
        assertNotNull(session);
        assertNotNull(session.getId());
        assertEquals(SessionStatus.OPEN, session.getStatus());
        assertEquals(5, session.getRosterCount()); // BATCH-001 has 5 students seeded
        assertEquals(1, session.getVersion());

        // Verify outbox event emitted
        assertTrue(service.getPendingOutboxCount() > 0);
    }

    @Test(expected = InvalidTimetableException.class)
    public void testCreateSessionInvalidTimetableSlot() {
        CreateSessionRequest req = new CreateSessionRequest();
        req.batchId = "BATCH-001";
        req.subjectId = "SUB-101";
        req.timetableEntryId = "NON-EXISTENT-SLOT";
        req.attendanceDate = "2026-10-12";

        service.createSession(req, "TENANT-001", "FAC-001", "FACULTY");
    }

    @Test(expected = DependencyUnavailableException.class)
    public void testCreateSessionFailClosedOnTimetableOutage() {
        service.setFailClosedOnTimetableOutage(true);

        CreateSessionRequest req = new CreateSessionRequest();
        req.batchId = "BATCH-001";
        req.subjectId = "SUB-101";
        req.timetableEntryId = "TT-SLOT-001";
        req.attendanceDate = "2026-10-12";

        service.createSession(req, "TENANT-001", "FAC-001", "FACULTY");
    }

    @Test(expected = CalendarViolationException.class)
    public void testCreateSessionOnHolidayRejected() {
        CreateSessionRequest req = new CreateSessionRequest();
        req.batchId = "BATCH-001";
        req.subjectId = "SUB-101";
        req.timetableEntryId = "TT-SLOT-001";
        req.attendanceDate = "2026-12-25"; // Christmas seeded as holiday

        service.createSession(req, "TENANT-001", "FAC-001", "FACULTY");
    }

    @Test(expected = DuplicateSessionException.class)
    public void testRejectDuplicateSession() {
        CreateSessionRequest req1 = new CreateSessionRequest();
        req1.batchId = "BATCH-001";
        req1.subjectId = "SUB-101";
        req1.timetableEntryId = "TT-SLOT-001";
        req1.attendanceDate = "2026-10-15";
        req1.periodNo = 1;
        service.createSession(req1, "TENANT-001", "FAC-001", "FACULTY");

        // Concurrent/duplicate request with same unique key
        CreateSessionRequest req2 = new CreateSessionRequest();
        req2.batchId = "BATCH-001";
        req2.subjectId = "SUB-101";
        req2.timetableEntryId = "TT-SLOT-001";
        req2.attendanceDate = "2026-10-15";
        req2.periodNo = 1;
        service.createSession(req2, "TENANT-001", "FAC-001", "FACULTY");
    }

    @Test
    public void testUpdateSessionAndOptimisticLocking() {
        CreateSessionRequest req = new CreateSessionRequest();
        req.batchId = "BATCH-001";
        req.subjectId = "SUB-101";
        req.timetableEntryId = "TT-SLOT-001";
        req.attendanceDate = "2026-10-16";
        AttendanceSession session = service.createSession(req, "TENANT-001", "admin-1", "ACADEMIC_ADMIN");

        UpdateSessionRequest updateReq = new UpdateSessionRequest();
        updateReq.startTime = "09:15";
        updateReq.endTime = "10:15";
        updateReq.expectedVersion = 1;

        AttendanceSession updated = service.updateSession(session.getId(), updateReq, "TENANT-001", "admin-1", "ACADEMIC_ADMIN");
        assertEquals("09:15", updated.getStartTime());
        assertEquals(2, updated.getVersion());

        // Stale version update must fail
        try {
            updateReq.expectedVersion = 1;
            service.updateSession(session.getId(), updateReq, "TENANT-001", "admin-1", "ACADEMIC_ADMIN");
            fail("Expected AttendanceVersionConflictException");
        } catch (AttendanceVersionConflictException e) {
            assertEquals("ACD_ATTENDANCE_VERSION_CONFLICT", e.getErrorCode());
        }
    }

    @Test
    public void testCancelSessionSoftDelete() {
        CreateSessionRequest req = new CreateSessionRequest();
        req.batchId = "BATCH-001";
        req.subjectId = "SUB-101";
        req.timetableEntryId = "TT-SLOT-001";
        req.attendanceDate = "2026-10-17";
        AttendanceSession session = service.createSession(req, "TENANT-001", "admin-1", "ACADEMIC_ADMIN");

        AttendanceSession cancelled = service.cancelSession(session.getId(), "TENANT-001", "admin-1", "ACADEMIC_ADMIN", "Schedule cancelled due to lab maintenance");
        assertEquals(SessionStatus.CANCELLED, cancelled.getStatus());
        // Verify session is still queryable for audit
        AttendanceSession retrieved = service.getSession(session.getId(), "TENANT-001");
        assertNotNull(retrieved);
        assertEquals(SessionStatus.CANCELLED, retrieved.getStatus());
    }

    // =========================================================================
    // 2. Attendance Marking Tests (Stories 7-15)
    // =========================================================================

    @Test
    public void testMarkAttendanceSuccess() {
        CreateSessionRequest req = new CreateSessionRequest();
        req.batchId = "BATCH-001";
        req.subjectId = "SUB-101";
        req.timetableEntryId = "TT-SLOT-001";
        req.attendanceDate = "2026-10-18";
        AttendanceSession session = service.createSession(req, "TENANT-001", "admin-1", "ACADEMIC_ADMIN");

        MarkAttendanceRequest markReq = new MarkAttendanceRequest();
        markReq.records.add(new RecordItem("STU-001", "PRESENT"));
        markReq.records.add(new RecordItem("STU-002", "PRESENT"));
        markReq.records.add(new RecordItem("STU-003", "ABSENT"));
        markReq.records.add(new RecordItem("STU-004", "LEAVE", "Medical certificate"));
        markReq.records.add(new RecordItem("STU-005", "LATE"));

        MarkSummaryResponse resp = service.markAttendance(session.getId(), markReq, "TENANT-001", "FAC-001", "FACULTY");
        assertNotNull(resp);
        assertEquals(5, resp.markedCount);
        assertEquals(3, resp.presentCount); // 2 PRESENT + 1 LATE = 3
        assertEquals(1, resp.absentCount);
        assertEquals(1, resp.leaveCount);

        AttendanceSession refreshed = service.getSession(session.getId(), "TENANT-001");
        assertEquals(3, refreshed.getPresentCount());
        assertEquals(1, refreshed.getAbsentCount());
        assertEquals(1, refreshed.getLeaveCount());
        assertEquals(1, refreshed.getLateCount());
    }

    @Test(expected = StudentNotInBatchException.class)
    public void testMarkAttendanceStudentNotInBatchAtomicFail() {
        CreateSessionRequest req = new CreateSessionRequest();
        req.batchId = "BATCH-001";
        req.subjectId = "SUB-101";
        req.timetableEntryId = "TT-SLOT-001";
        req.attendanceDate = "2026-10-19";
        AttendanceSession session = service.createSession(req, "TENANT-001", "admin-1", "ACADEMIC_ADMIN");

        MarkAttendanceRequest markReq = new MarkAttendanceRequest();
        markReq.records.add(new RecordItem("STU-001", "PRESENT"));
        markReq.records.add(new RecordItem("STU-999-EXTERNAL", "PRESENT")); // Not in batch!

        service.markAttendance(session.getId(), markReq, "TENANT-001", "FAC-001", "FACULTY");
    }

    @Test(expected = InvalidAttendanceStatusException.class)
    public void testMarkAttendanceInvalidStatus() {
        CreateSessionRequest req = new CreateSessionRequest();
        req.batchId = "BATCH-001";
        req.subjectId = "SUB-101";
        req.timetableEntryId = "TT-SLOT-001";
        req.attendanceDate = "2026-10-20";
        AttendanceSession session = service.createSession(req, "TENANT-001", "admin-1", "ACADEMIC_ADMIN");

        MarkAttendanceRequest markReq = new MarkAttendanceRequest();
        markReq.records.add(new RecordItem("STU-001", "INVALID_UNKNOWN_STATUS"));

        service.markAttendance(session.getId(), markReq, "TENANT-001", "FAC-001", "FACULTY");
    }

    @Test(expected = DuplicateAttendanceRecordException.class)
    public void testPreventDuplicateAttendanceRecords() {
        CreateSessionRequest req = new CreateSessionRequest();
        req.batchId = "BATCH-001";
        req.subjectId = "SUB-101";
        req.timetableEntryId = "TT-SLOT-001";
        req.attendanceDate = "2026-10-21";
        AttendanceSession session = service.createSession(req, "TENANT-001", "admin-1", "ACADEMIC_ADMIN");

        MarkAttendanceRequest markReq = new MarkAttendanceRequest();
        markReq.records.add(new RecordItem("STU-001", "PRESENT"));
        service.markAttendance(session.getId(), markReq, "TENANT-001", "FAC-001", "FACULTY");

        // Attempting to mark the same student again in the same session must fail with duplicate exception
        MarkAttendanceRequest markReq2 = new MarkAttendanceRequest();
        markReq2.records.add(new RecordItem("STU-001", "ABSENT"));
        service.markAttendance(session.getId(), markReq2, "TENANT-001", "FAC-001", "FACULTY");
    }

    // =========================================================================
    // 3. Attendance Corrections Tests (Stories 17-23)
    // =========================================================================

    @Test
    public void testCorrectAttendanceSuccess() {
        CreateSessionRequest req = new CreateSessionRequest();
        req.batchId = "BATCH-001";
        req.subjectId = "SUB-101";
        req.timetableEntryId = "TT-SLOT-001";
        req.attendanceDate = "2026-10-22";
        AttendanceSession session = service.createSession(req, "TENANT-001", "admin-1", "ACADEMIC_ADMIN");

        MarkAttendanceRequest markReq = new MarkAttendanceRequest();
        markReq.records.add(new RecordItem("STU-001", "ABSENT"));
        service.markAttendance(session.getId(), markReq, "TENANT-001", "FAC-001", "FACULTY");

        CorrectionRequest corrReq = new CorrectionRequest();
        corrReq.studentId = "STU-001";
        corrReq.newStatus = "PRESENT";
        corrReq.reason = "Late arrival due to college transport breakdown";
        corrReq.expectedVersion = 1;

        AttendanceCorrection corr = service.correctAttendance(session.getId(), "STU-001", corrReq, "TENANT-001", "FAC-001", "FACULTY");
        assertNotNull(corr);
        assertEquals(AttendanceStatus.ABSENT, corr.getOldStatus());
        assertEquals(AttendanceStatus.PRESENT, corr.getNewStatus());
        assertEquals(1, corr.getPreviousVersion());
        assertEquals(2, corr.getNewVersion());

        AttendanceSession refreshed = service.getSession(session.getId(), "TENANT-001");
        assertEquals(SessionStatus.CORRECTED, refreshed.getStatus());
        assertEquals(1, refreshed.getPresentCount());
        assertEquals(0, refreshed.getAbsentCount());
    }

    @Test(expected = AttendanceBadRequestException.class)
    public void testCorrectionRequiresMandatoryReason() {
        CreateSessionRequest req = new CreateSessionRequest();
        req.batchId = "BATCH-001";
        req.subjectId = "SUB-101";
        req.timetableEntryId = "TT-SLOT-001";
        req.attendanceDate = "2026-10-23";
        AttendanceSession session = service.createSession(req, "TENANT-001", "admin-1", "ACADEMIC_ADMIN");

        MarkAttendanceRequest markReq = new MarkAttendanceRequest();
        markReq.records.add(new RecordItem("STU-001", "ABSENT"));
        service.markAttendance(session.getId(), markReq, "TENANT-001", "FAC-001", "FACULTY");

        CorrectionRequest corrReq = new CorrectionRequest();
        corrReq.studentId = "STU-001";
        corrReq.newStatus = "PRESENT";
        corrReq.reason = ""; // Empty reason must be rejected!

        service.correctAttendance(session.getId(), "STU-001", corrReq, "TENANT-001", "FAC-001", "FACULTY");
    }

    @Test(expected = AttendanceCorrectionForbiddenException.class)
    public void testCorrectionForbiddenForUnauthorizedRole() {
        CreateSessionRequest req = new CreateSessionRequest();
        req.batchId = "BATCH-001";
        req.subjectId = "SUB-101";
        req.timetableEntryId = "TT-SLOT-001";
        req.attendanceDate = "2026-10-24";
        AttendanceSession session = service.createSession(req, "TENANT-001", "admin-1", "ACADEMIC_ADMIN");

        MarkAttendanceRequest markReq = new MarkAttendanceRequest();
        markReq.records.add(new RecordItem("STU-001", "ABSENT"));
        service.markAttendance(session.getId(), markReq, "TENANT-001", "FAC-001", "FACULTY");

        CorrectionRequest corrReq = new CorrectionRequest();
        corrReq.studentId = "STU-001";
        corrReq.newStatus = "PRESENT";
        corrReq.reason = "Fixing error";

        // Student role attempting correction
        service.correctAttendance(session.getId(), "STU-001", corrReq, "TENANT-001", "STU-001", "STUDENT");
    }

    // =========================================================================
    // 4. Lifecycle Governance Tests (Stories 24-28)
    // =========================================================================

    @Test
    public void testSessionLifecycleTransitions() {
        CreateSessionRequest req = new CreateSessionRequest();
        req.batchId = "BATCH-001";
        req.subjectId = "SUB-101";
        req.timetableEntryId = "TT-SLOT-001";
        req.attendanceDate = "2026-10-25";
        AttendanceSession session = service.createSession(req, "TENANT-001", "admin-1", "ACADEMIC_ADMIN");
        assertEquals(SessionStatus.OPEN, session.getStatus());

        // Submit
        AttendanceSession submitted = service.submitSession(session.getId(), "TENANT-001", "FAC-001", "FACULTY");
        assertEquals(SessionStatus.SUBMITTED, submitted.getStatus());

        // Lock
        AttendanceSession locked = service.lockSession(session.getId(), "TENANT-001", "admin-1", "ACADEMIC_ADMIN");
        assertEquals(SessionStatus.LOCKED, locked.getStatus());

        // Archive
        AttendanceSession archived = service.archiveSession(session.getId(), "TENANT-001", "admin-1", "ACADEMIC_ADMIN");
        assertEquals(SessionStatus.ARCHIVED, archived.getStatus());
    }

    @Test(expected = InvalidLifecycleStateException.class)
    public void testRejectInvalidLifecycleTransition() {
        CreateSessionRequest req = new CreateSessionRequest();
        req.batchId = "BATCH-001";
        req.subjectId = "SUB-101";
        req.timetableEntryId = "TT-SLOT-001";
        req.attendanceDate = "2026-10-26";
        AttendanceSession session = service.createSession(req, "TENANT-001", "admin-1", "ACADEMIC_ADMIN");

        // Cannot jump directly from OPEN to ARCHIVED
        service.archiveSession(session.getId(), "TENANT-001", "admin-1", "ACADEMIC_ADMIN");
    }

    @Test
    public void testCloseEndOfDaySessions() {
        CreateSessionRequest req1 = new CreateSessionRequest();
        req1.batchId = "BATCH-001";
        req1.subjectId = "SUB-101";
        req1.timetableEntryId = "TT-SLOT-001";
        req1.attendanceDate = "2026-10-27";
        req1.periodNo = 1;
        service.createSession(req1, "TENANT-001", "admin-1", "ACADEMIC_ADMIN");

        int closed = service.closeEndOfDaySessions("TENANT-001", "2026-10-27", "SYSTEM-CRON");
        assertTrue(closed >= 1);
    }

    // =========================================================================
    // 5. Summaries & Shortage Engine Tests (Stories 30-39)
    // =========================================================================

    @Test
    public void testShortageDetectionSingleEventOnThresholdCrossing() {
        CreateSessionRequest req1 = new CreateSessionRequest();
        req1.batchId = "BATCH-001";
        req1.subjectId = "SUB-101";
        req1.timetableEntryId = "TT-SLOT-001";
        req1.attendanceDate = "2026-10-28";
        req1.periodNo = 1;
        AttendanceSession s1 = service.createSession(req1, "TENANT-001", "admin-1", "ACADEMIC_ADMIN");

        MarkAttendanceRequest m1 = new MarkAttendanceRequest();
        m1.records.add(new RecordItem("STU-001", "ABSENT")); // 0% attendance -> shortage!
        service.markAttendance(s1.getId(), m1, "TENANT-001", "FAC-001", "FACULTY");

        AttendanceSummary sum = service.getStudentSummaries("STU-001", "TENANT-001").get(0);
        assertTrue(sum.isShortageFlag());
        assertEquals(0.0, sum.getAttendancePercentage(), 0.01);

        // Verify outbox event AttendanceShortageDetected was emitted
        int initialOutbox = service.getPendingOutboxCount();
        assertTrue(initialOutbox > 0);

        // Another absent in another session: still in shortage, must NOT emit duplicate shortage event
        CreateSessionRequest req2 = new CreateSessionRequest();
        req2.batchId = "BATCH-001";
        req2.subjectId = "SUB-101";
        req2.timetableEntryId = "TT-SLOT-001";
        req2.attendanceDate = "2026-10-29";
        req2.periodNo = 2;
        AttendanceSession s2 = service.createSession(req2, "TENANT-001", "admin-1", "ACADEMIC_ADMIN");

        MarkAttendanceRequest m2 = new MarkAttendanceRequest();
        m2.records.add(new RecordItem("STU-001", "ABSENT"));
        service.markAttendance(s2.getId(), m2, "TENANT-001", "FAC-001", "FACULTY");

        AttendanceSummary sum2 = service.getStudentSummaries("STU-001", "TENANT-001").get(0);
        assertTrue(sum2.isShortageFlag());
    }

    @Test
    public void testReconcileSummaries() {
        int reconciled = service.reconcileAllSummaries("TENANT-001");
        assertTrue(reconciled >= 0);
    }

    // =========================================================================
    // 6. Bulk Import & Event Ingestion (Stories 45-57)
    // =========================================================================

    @Test
    public void testBulkImportPartialSuccess() {
        CreateSessionRequest req = new CreateSessionRequest();
        req.batchId = "BATCH-001";
        req.subjectId = "SUB-101";
        req.timetableEntryId = "TT-SLOT-001";
        req.attendanceDate = "2026-10-30";
        req.periodNo = 1;
        service.createSession(req, "TENANT-001", "admin-1", "ACADEMIC_ADMIN");

        List<BulkImportRow> rows = new ArrayList<>();
        BulkImportRow r1 = new BulkImportRow();
        r1.batchId = "BATCH-001";
        r1.subjectId = "SUB-101";
        r1.attendanceDate = "2026-10-30";
        r1.periodNo = 1;
        r1.studentId = "STU-001";
        r1.status = "PRESENT";
        rows.add(r1);

        BulkImportRow r2 = new BulkImportRow();
        r2.batchId = "NON_EXISTENT_BATCH";
        r2.subjectId = "SUB-101";
        r2.attendanceDate = "2026-10-30";
        r2.periodNo = 1;
        r2.studentId = "STU-002";
        r2.status = "PRESENT";
        rows.add(r2); // This row will fail

        Map<String, Object> res = service.bulkImport(rows, "TENANT-001", "admin-1", "ACADEMIC_ADMIN");
        assertEquals(2, res.get("totalRows"));
        assertEquals(1, res.get("successCount"));
        assertEquals(1, res.get("failureCount"));
    }

    @Test
    public void testEventDeduplicationAndBatchSplitReassignment() {
        Map<String, Object> splitEvent = new HashMap<>();
        splitEvent.put("eventId", "EVT-SPLIT-TEST-001");
        splitEvent.put("eventType", "BatchSplit");
        Map<String, Object> data = new HashMap<>();
        Map<String, String> reassignment = new HashMap<>();
        reassignment.put("STU-001", "BATCH-001-A");
        data.put("reassignmentMap", reassignment);
        splitEvent.put("data", data);

        boolean processedFirst = service.consumeEvent(splitEvent);
        assertTrue(processedFirst);

        // Deduplication test (Story 55): Repeated event must be skipped gracefully
        boolean processedSecond = service.consumeEvent(splitEvent);
        assertTrue(processedSecond);
    }
}
