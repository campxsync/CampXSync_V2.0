package com.campx.academic.analytics;

import com.campx.academic.analytics.exception.AnalyticsExceptions.*;
import com.campx.academic.analytics.model.AnalyticsModels.*;
import com.campx.academic.analytics.service.AnalyticsDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Comprehensive Unit Test Suite for ACD-10: Reporting & Analytics Service.
 * Validates domain rules, event ingestion, fact normalization, materialized projections,
 * deterministic risk evaluation, outbox atomic commits, idempotency, DLQ quarantine/replay,
 * projection rebuilds, RBAC/ABAC enforcement, tenant isolation, and audited exports.
 */
public class AnalyticsDomainServiceTest {

    private AnalyticsDomainService service;

    @Before
    public void setUp() {
        service = new AnalyticsDomainService();
    }

    // =========================================================================
    // 1. Event Ingestion & Fact Normalization (US-021, US-022, US-028)
    // =========================================================================

    @Test
    public void testConsumeAttendanceMarkedEvent() {
        EventEnvelope envelope = new EventEnvelope();
        envelope.setEventId("EVT-ACD06-001");
        envelope.setEventType("AttendanceMarked");
        envelope.setSource("ACD-06");
        envelope.setOccurredAt("2026-09-14T09:00:00Z");
        envelope.setTenantId("INST-001");
        envelope.setCorrelationId("CORR-001");

        Map<String, Object> data = new HashMap<>();
        data.put("batchId", "BAT-CSE-3A");
        data.put("subjectId", "SUB-101");
        data.put("facultyId", "FAC-001");
        data.put("presentCount", 40);
        data.put("absentCount", 5);
        data.put("leaveCount", 2);
        envelope.setData(data);

        boolean success = service.consumeEvent(envelope);
        assertTrue(success);

        // Verify outbox event emitted
        assertTrue(service.getPendingOutboxCount() > 0);
    }

    @Test
    public void testConsumeTimetablePublishedEvent() {
        EventEnvelope envelope = new EventEnvelope();
        envelope.setEventId("EVT-ACD05-001");
        envelope.setEventType("TimetablePublished");
        envelope.setSource("ACD-05");
        envelope.setOccurredAt("2026-09-14T08:00:00Z");
        envelope.setTenantId("INST-001");
        envelope.setCorrelationId("CORR-002");

        Map<String, Object> data = new HashMap<>();
        data.put("batchId", "BAT-CSE-3A");
        data.put("roomId", "R-101");
        data.put("facultyId", "FAC-001");
        data.put("slotType", "LECTURE");
        data.put("scheduledSlots", 10);
        data.put("executedSlots", 9);
        envelope.setData(data);

        boolean success = service.consumeEvent(envelope);
        assertTrue(success);
    }

    @Test
    public void testConsumeProgressionEvents() {
        // ACD-02 CurriculumPublished
        EventEnvelope currEnv = new EventEnvelope();
        currEnv.setEventId("EVT-ACD02-001");
        currEnv.setEventType("CurriculumPublished");
        currEnv.setSource("ACD-02");
        currEnv.setOccurredAt("2026-09-14T07:00:00Z");
        currEnv.setTenantId("INST-001");
        currEnv.setCorrelationId("CORR-003");

        Map<String, Object> currData = new HashMap<>();
        currData.put("batchId", "BAT-CSE-3A");
        currData.put("courseId", "CRS-101");
        currData.put("totalUnits", 12);
        currEnv.setData(currData);

        assertTrue(service.consumeEvent(currEnv));

        // ACD-09 AssessmentMappingPublished
        EventEnvelope assessEnv = new EventEnvelope();
        assessEnv.setEventId("EVT-ACD09-001");
        assessEnv.setEventType("AssessmentMappingPublished");
        assessEnv.setSource("ACD-09");
        assessEnv.setOccurredAt("2026-09-14T07:30:00Z");
        assessEnv.setTenantId("INST-001");
        assessEnv.setCorrelationId("CORR-004");

        Map<String, Object> assessData = new HashMap<>();
        assessData.put("batchId", "BAT-CSE-3A");
        assessData.put("courseId", "CRS-101");
        assessData.put("coveragePercentage", 80.0);
        assessEnv.setData(assessData);

        assertTrue(service.consumeEvent(assessEnv));
    }

    // =========================================================================
    // 2. Idempotency & Deduplication (US-023, US-050)
    // =========================================================================

    @Test
    public void testEventIdempotencyDuplicateIgnored() {
        EventEnvelope envelope = new EventEnvelope();
        envelope.setEventId("EVT-DUP-001");
        envelope.setEventType("AttendanceMarked");
        envelope.setSource("ACD-06");
        envelope.setOccurredAt("2026-09-14T09:00:00Z");
        envelope.setTenantId("INST-001");
        envelope.setCorrelationId("CORR-DUP");

        Map<String, Object> data = new HashMap<>();
        data.put("batchId", "BAT-CSE-3A");
        data.put("presentCount", 10);
        envelope.setData(data);

        int initialFacts = service.getFactsCount();
        assertTrue(service.consumeEvent(envelope));
        assertEquals(initialFacts + 1, service.getFactsCount());

        // Re-consume identical event -> returns true idempotently without adding fact
        assertTrue(service.consumeEvent(envelope));
        assertEquals(initialFacts + 1, service.getFactsCount());
    }

    // =========================================================================
    // 3. DLQ Quarantine & Replay (US-025, US-026, US-051)
    // =========================================================================

    @Test
    public void testMalformedEventQuarantinedToDlq() {
        int initialDlq = service.getDlqCount();

        // Missing required tenantId
        EventEnvelope badEnvelope = new EventEnvelope();
        badEnvelope.setEventId("EVT-BAD-001");
        badEnvelope.setEventType("AttendanceMarked");
        badEnvelope.setSource("ACD-06");
        badEnvelope.setOccurredAt("2026-09-14T09:00:00Z");
        badEnvelope.setCorrelationId("CORR-BAD");
        // tenantId is null

        assertFalse(service.consumeEvent(badEnvelope));
        assertEquals(initialDlq + 1, service.getDlqCount());
    }

    @Test
    public void testReplayDlqEventSuccess() {
        // Ingest malformed event
        EventEnvelope bad = new EventEnvelope();
        bad.setEventId("EVT-REPLAY-001");
        bad.setEventType("TimetablePublished");
        bad.setSource("ACD-05");
        bad.setOccurredAt("2026-09-14T09:00:00Z");
        // tenantId null -> quarantined
        service.consumeEvent(bad);

        // Operator replays with proper role
        boolean replayed = service.replayDlqEvent("UNKNOWN", "EVT-REPLAY-001", "OPERATOR", "CORR-REPLAY");
        assertTrue(replayed);
    }

    @Test(expected = AnalyticsForbiddenException.class)
    public void testReplayDlqUnauthorizedForbidden() {
        service.replayDlqEvent("INST-001", "EVT-001", "STUDENT", "CORR-001");
    }

    // =========================================================================
    // 4. Projection Rebuild Workflow (US-032, US-054)
    // =========================================================================

    @Test
    public void testRebuildProjections() {
        // Add a fact first
        EventEnvelope env = new EventEnvelope();
        env.setEventId("EVT-REBUILD-1");
        env.setEventType("AttendanceMarked");
        env.setSource("ACD-06");
        env.setOccurredAt("2026-09-14T09:00:00Z");
        env.setTenantId("INST-REBUILD");
        env.setCorrelationId("CORR-R");
        Map<String, Object> data = new HashMap<>();
        data.put("batchId", "BAT-REBUILD");
        data.put("presentCount", 50);
        env.setData(data);
        service.consumeEvent(env);

        int count = service.rebuildProjections("INST-REBUILD", "OPERATOR", "CORR-R");
        assertTrue(count >= 1);
    }

    // =========================================================================
    // 5. Deterministic Rules & Risk/Insight Signals (US-014, US-037, US-038)
    // =========================================================================

    @Test
    public void testAttendanceShortageAlertEmitted() {
        // Attendance with low percentage (< 75%)
        EventEnvelope env = new EventEnvelope();
        env.setEventId("EVT-SHORTAGE-001");
        env.setEventType("AttendanceMarked");
        env.setSource("ACD-06");
        env.setOccurredAt("2026-09-14T09:00:00Z");
        env.setTenantId("INST-001");
        env.setCorrelationId("CORR-SHORT");

        Map<String, Object> data = new HashMap<>();
        data.put("batchId", "BAT-SHORTAGE-1");
        data.put("subjectId", "SUB-999");
        data.put("facultyId", "FAC-999");
        data.put("presentCount", 50);
        data.put("absentCount", 50); // 50% (< 75%)
        data.put("leaveCount", 0);
        env.setData(data);

        service.consumeEvent(env);

        // Verify that attendance query flags shortage risk signal
        Map<String, Object> result = service.getAttendanceAnalytics("BAT-SHORTAGE-1", null, null, "SUB-999",
                "INST-001", "ACADEMIC_ADMIN", "admin", "DEP-CSE");
        assertNotNull(result);

        @SuppressWarnings("unchecked")
        List<String> riskSignals = (List<String>) result.get("riskSignals");
        assertTrue(riskSignals.contains("ATTENDANCE_SHORTAGE_PATTERN"));
    }

    // =========================================================================
    // 6. Outbox Relay (US-048, US-049)
    // =========================================================================

    @Test
    public void testOutboxRelayPublishing() {
        EventEnvelope env = new EventEnvelope();
        env.setEventId("EVT-OUTBOX-1");
        env.setEventType("AttendanceMarked");
        env.setSource("ACD-06");
        env.setOccurredAt("2026-09-14T09:00:00Z");
        env.setTenantId("INST-001");
        env.setCorrelationId("CORR-OUT");
        Map<String, Object> data = new HashMap<>();
        data.put("batchId", "BAT-CSE-3A");
        data.put("presentCount", 20);
        env.setData(data);
        service.consumeEvent(env);

        int pendingBefore = service.getPendingOutboxCount();
        assertTrue(pendingBefore > 0);

        int published = service.relayPendingOutboxEvents();
        assertTrue(published > 0);
        assertEquals(0, service.getPendingOutboxCount());
    }

    // =========================================================================
    // 7. RBAC / ABAC Matrix & Tenant Isolation (US-003, US-004, US-005, US-006)
    // =========================================================================

    @Test
    public void testAdminCanAccessInstitutionDashboard() {
        Map<String, Object> db = service.getDashboard("2026-27", "institution", null,
                "INST-001", "ACADEMIC_ADMIN", "admin-1", null);
        assertNotNull(db);
        assertTrue(db.containsKey("metrics"));
        assertTrue(db.containsKey("freshness"));
    }

    @Test(expected = AnalyticsForbiddenException.class)
    public void testDepartmentHeadCannotAccessInstitutionDashboard() {
        service.getDashboard("2026-27", "institution", null,
                "INST-001", "DEPARTMENT_HEAD", "hod-1", "DEP-CSE");
    }

    @Test(expected = AnalyticsForbiddenException.class)
    public void testDepartmentHeadCannotAccessOtherDepartment() {
        service.getDashboard("2026-27", "department", "DEP-ECE",
                "INST-001", "DEPARTMENT_HEAD", "hod-1", "DEP-CSE");
    }

    @Test(expected = AnalyticsForbiddenException.class)
    public void testFacultyCannotAccessUnassignedBatch() {
        service.getAttendanceAnalytics("BAT-ECE-2A", null, null, null,
                "INST-001", "FACULTY", "FAC-001", "DEP-CSE");
    }

    @Test
    public void testFacultyCanAccessAssignedBatch() {
        Map<String, Object> att = service.getAttendanceAnalytics("BAT-CSE-3A", null, null, null,
                "INST-001", "FACULTY", "FAC-001", "DEP-CSE");
        assertNotNull(att);
        assertTrue(att.containsKey("subjects"));
    }

    // =========================================================================
    // 8. Query Validation (US-009)
    // =========================================================================

    @Test(expected = AnalyticsValidationException.class)
    public void testReversedDateRangeRejected() {
        service.getAttendanceAnalytics("BAT-CSE-3A", "2026-10-01", "2026-09-01", null,
                "INST-001", "ACADEMIC_ADMIN", "admin-1", "DEP-CSE");
    }

    // =========================================================================
    // 9. Export Lifecycle & Auditing (US-040..047)
    // =========================================================================

    @Test
    public void testCreateAndDownloadExport() throws InterruptedException {
        ReportJob job = service.createExport(ReportType.ATTENDANCE_SUMMARY, ReportFormat.CSV,
                Collections.emptyMap(), "INST-001", "admin-1", "ACADEMIC_ADMIN", "CORR-EXP");
        assertNotNull(job);
        assertNotNull(job.getJobId());

        // Wait brief moment for async worker to complete
        Thread.sleep(150);

        ReportJob completed = service.getExportJob(job.getJobId(), "INST-001", "admin-1", "ACADEMIC_ADMIN");
        assertEquals(JobStatus.COMPLETED, completed.getStatus());
        assertNotNull(completed.getOutputRef());
        assertTrue(completed.getFileSize() > 0);

        // Download artifact
        String content = service.downloadExport(job.getJobId(), "INST-001", "admin-1", "ACADEMIC_ADMIN", "CORR-EXP");
        assertNotNull(content);
        assertTrue(content.contains("present"));

        // Verify audit log exists
        List<AuditLogEntry> logs = service.getExportService().getAuditLogs("INST-001");
        assertFalse(logs.isEmpty());
    }

    @Test(expected = AnalyticsForbiddenException.class)
    public void testStudentExportRestricted() {
        service.createExport(ReportType.ATTENDANCE_SUMMARY, ReportFormat.CSV,
                Collections.emptyMap(), "INST-001", "stu-1", "STUDENT", "CORR-EXP");
    }
}
