package com.campx.academic.attendance;

import com.campx.academic.attendance.exception.AttendanceNotFoundException;
import com.campx.academic.attendance.exception.DuplicateAttendanceRecordException;
import com.campx.academic.attendance.exception.DuplicateSessionException;
import com.campx.academic.attendance.model.AttendanceCorrectionEntity;
import com.campx.academic.attendance.model.AttendanceRecordEntity;
import com.campx.academic.attendance.model.AttendanceSessionEntity;
import com.campx.academic.attendance.repository.InMemoryAttendanceRepository;
import com.campx.academic.attendance.security.UserSecurityContext;
import com.campx.academic.attendance.service.AttendanceDomainService;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests verifying relational attendance persistence, session derivation,
 * roster marking, duplicate rejection, and tenant isolation (Wave 6).
 */
public class AttendanceRelationalServiceTest {

    private InMemoryAttendanceRepository repository;
    private AttendanceDomainService service;

    private final UUID tenantA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID tenantB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID userA = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID userB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID collegeId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private UserSecurityContext contextA;
    private UserSecurityContext contextB;

    @Before
    public void setUp() {
        repository = new InMemoryAttendanceRepository();
        service = new AttendanceDomainService();
        service.setRepository(repository);

        contextA = new UserSecurityContext(userA, tenantA, collegeId, null, "faculty", false);
        contextB = new UserSecurityContext(userA, tenantB, collegeId, null, "faculty", false);
    }

    @Test
    public void testCreateAttendanceSessionAndRetrieve() {
        UUID sectionId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID facultyId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 22);

        AttendanceSessionEntity session = new AttendanceSessionEntity();
        session.setSectionId(sectionId);
        session.setTimetableEntryId(slotId);
        session.setEmployeeId(facultyId);
        session.setSessionDate(date);
        session.setStartTime(LocalTime.of(9, 0));
        session.setEndTime(LocalTime.of(10, 0));
        session.setStatus("OPEN");

        AttendanceSessionEntity created = service.createRelationalSession(contextA, session);

        assertNotNull("Created session must have ID", created.getId());
        assertEquals(tenantA, created.getTenantId());
        assertEquals("OPEN", created.getStatus());

        Optional<AttendanceSessionEntity> found = service.findRelationalSessionById(contextA, created.getId());
        assertTrue("Session must be retrievable by ID", found.isPresent());
        assertEquals(sectionId, found.get().getSectionId());

        // Negative test: Tenant B cannot retrieve Tenant A's session
        Optional<AttendanceSessionEntity> crossTenant = service.findRelationalSessionById(contextB, created.getId());
        assertFalse("Cross-tenant access must be denied", crossTenant.isPresent());
    }

    @Test
    public void testMarkAttendanceSingleAndBulk() {
        UUID sectionId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 22);

        AttendanceSessionEntity session = new AttendanceSessionEntity();
        session.setSectionId(sectionId);
        session.setSessionDate(date);
        session.setStartTime(LocalTime.of(10, 0));
        session.setEndTime(LocalTime.of(11, 0));
        session.setStatus("OPEN");
        AttendanceSessionEntity created = service.createRelationalSession(contextA, session);

        UUID stu1 = UUID.randomUUID();
        UUID stu2 = UUID.randomUUID();
        UUID stu3 = UUID.randomUUID();

        AttendanceRecordEntity r1 = new AttendanceRecordEntity();
        r1.setStudentId(stu1);
        r1.setStatus("PRESENT");
        r1.setMethod("MANUAL");

        AttendanceRecordEntity r2 = new AttendanceRecordEntity();
        r2.setStudentId(stu2);
        r2.setStatus("ABSENT");
        r2.setMethod("MANUAL");

        AttendanceRecordEntity r3 = new AttendanceRecordEntity();
        r3.setStudentId(stu3);
        r3.setStatus("LATE");
        r3.setMethod("MANUAL");

        List<AttendanceRecordEntity> saved = service.markRelationalAttendance(contextA, created.getId(), Arrays.asList(r1, r2, r3));
        assertEquals(3, saved.size());

        List<AttendanceRecordEntity> retrieved = service.findRelationalRecordsBySessionId(contextA, created.getId());
        assertEquals(3, retrieved.size());

        // Negative test: Tenant B cannot retrieve Tenant A's attendance records
        List<AttendanceRecordEntity> crossRecords = service.findRelationalRecordsBySessionId(contextB, created.getId());
        assertEquals("Tenant B must see 0 records", 0, crossRecords.size());
    }

    @Test(expected = DuplicateAttendanceRecordException.class)
    public void testRejectDuplicateAttendanceMarking() {
        UUID sectionId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 22);

        AttendanceSessionEntity session = new AttendanceSessionEntity();
        session.setSectionId(sectionId);
        session.setSessionDate(date);
        session.setStartTime(LocalTime.of(11, 0));
        session.setEndTime(LocalTime.of(12, 0));
        session.setStatus("OPEN");
        AttendanceSessionEntity created = service.createRelationalSession(contextA, session);

        UUID stu1 = UUID.randomUUID();

        AttendanceRecordEntity r1 = new AttendanceRecordEntity();
        r1.setStudentId(stu1);
        r1.setStatus("PRESENT");

        service.markRelationalAttendance(contextA, created.getId(), Collections.singletonList(r1));

        // Marking again for the same student in the same session must fail
        AttendanceRecordEntity duplicate = new AttendanceRecordEntity();
        duplicate.setStudentId(stu1);
        duplicate.setStatus("ABSENT");

        service.markRelationalAttendance(contextA, created.getId(), Collections.singletonList(duplicate));
    }

    @Test
    public void testAttendanceCorrectionLifecycle() {
        UUID sectionId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 22);

        AttendanceSessionEntity session = new AttendanceSessionEntity();
        session.setSectionId(sectionId);
        session.setSessionDate(date);
        session.setStartTime(LocalTime.of(14, 0));
        session.setEndTime(LocalTime.of(15, 0));
        session.setStatus("OPEN");
        AttendanceSessionEntity created = service.createRelationalSession(contextA, session);

        UUID stu1 = UUID.randomUUID();
        AttendanceRecordEntity r1 = new AttendanceRecordEntity();
        r1.setStudentId(stu1);
        r1.setStatus("ABSENT");
        List<AttendanceRecordEntity> saved = service.markRelationalAttendance(contextA, created.getId(), Collections.singletonList(r1));
        UUID recordId = saved.get(0).getId();

        // 1. Request correction: ABSENT -> PRESENT
        AttendanceCorrectionEntity corr = new AttendanceCorrectionEntity();
        corr.setRecordId(recordId);
        corr.setOldStatus("ABSENT");
        corr.setNewStatus("PRESENT");
        corr.setReason("Student arrived with valid on-duty slip");
        corr.setRequestedBy(userA);

        AttendanceCorrectionEntity requested = service.requestRelationalCorrection(contextA, corr);
        assertNotNull(requested.getId());
        assertEquals("PENDING", requested.getStatus());

        // 2. Approve correction
        UUID hodId = UUID.randomUUID();
        AttendanceCorrectionEntity approved = service.applyRelationalCorrection(contextA, requested.getId(), "APPROVED", hodId);
        assertEquals("APPROVED", approved.getStatus());
        assertEquals(hodId, approved.getApprovedBy());

        // 3. Verify original record was updated to PRESENT
        List<AttendanceRecordEntity> updatedRecords = service.findRelationalRecordsBySessionId(contextA, created.getId());
        assertEquals(1, updatedRecords.size());
        assertEquals("PRESENT", updatedRecords.get(0).getStatus());
    }

    @Test
    public void testAttendanceCorrectionRejectionLeavesOriginalStatus() {
        UUID sectionId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 22);

        AttendanceSessionEntity session = new AttendanceSessionEntity();
        session.setSectionId(sectionId);
        session.setSessionDate(date);
        session.setStartTime(LocalTime.of(15, 0));
        session.setEndTime(LocalTime.of(16, 0));
        session.setStatus("OPEN");
        AttendanceSessionEntity created = service.createRelationalSession(contextA, session);

        UUID stu1 = UUID.randomUUID();
        AttendanceRecordEntity r1 = new AttendanceRecordEntity();
        r1.setStudentId(stu1);
        r1.setStatus("ABSENT");
        List<AttendanceRecordEntity> saved = service.markRelationalAttendance(contextA, created.getId(), Collections.singletonList(r1));
        UUID recordId = saved.get(0).getId();

        AttendanceCorrectionEntity corr = new AttendanceCorrectionEntity();
        corr.setRecordId(recordId);
        corr.setOldStatus("ABSENT");
        corr.setNewStatus("PRESENT");
        corr.setReason("Invalid claim");
        AttendanceCorrectionEntity requested = service.requestRelationalCorrection(contextA, corr);

        // Reject correction
        UUID hodId = UUID.randomUUID();
        AttendanceCorrectionEntity rejected = service.applyRelationalCorrection(contextA, requested.getId(), "REJECTED", hodId);
        assertEquals("REJECTED", rejected.getStatus());

        // Verify status remains ABSENT
        List<AttendanceRecordEntity> updatedRecords = service.findRelationalRecordsBySessionId(contextA, created.getId());
        assertEquals("ABSENT", updatedRecords.get(0).getStatus());
    }

    @Test(expected = com.campx.academic.attendance.exception.AttendanceCorrectionForbiddenException.class)
    public void testSelfApprovalForbiddenThrowsException() {
        UUID sectionId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 22);

        AttendanceSessionEntity session = new AttendanceSessionEntity();
        session.setSectionId(sectionId);
        session.setSessionDate(date);
        session.setStartTime(LocalTime.of(16, 0));
        session.setEndTime(LocalTime.of(17, 0));
        session.setStatus("OPEN");
        AttendanceSessionEntity created = service.createRelationalSession(contextA, session);

        AttendanceRecordEntity r1 = new AttendanceRecordEntity();
        r1.setStudentId(UUID.randomUUID());
        r1.setStatus("ABSENT");
        List<AttendanceRecordEntity> saved = service.markRelationalAttendance(contextA, created.getId(), Collections.singletonList(r1));
        UUID recordId = saved.get(0).getId();

        AttendanceCorrectionEntity corr = new AttendanceCorrectionEntity();
        corr.setRecordId(recordId);
        corr.setOldStatus("ABSENT");
        corr.setNewStatus("PRESENT");
        corr.setReason("Correction attempt");
        corr.setRequestedBy(userA);
        AttendanceCorrectionEntity requested = service.requestRelationalCorrection(contextA, corr);

        // Attempt self-approval: approver is the exact same user as requester (userA) -> MUST FAIL CLOSED
        service.applyRelationalCorrection(contextA, requested.getId(), "APPROVED", userA);
    }

    @Test(expected = IllegalStateException.class)
    public void testDuplicateCorrectionDecisionThrowsException() {
        UUID sectionId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 22);

        AttendanceSessionEntity session = new AttendanceSessionEntity();
        session.setSectionId(sectionId);
        session.setSessionDate(date);
        session.setStartTime(LocalTime.of(9, 0));
        session.setEndTime(LocalTime.of(10, 0));
        session.setStatus("OPEN");
        AttendanceSessionEntity created = service.createRelationalSession(contextA, session);

        AttendanceRecordEntity r1 = new AttendanceRecordEntity();
        r1.setStudentId(UUID.randomUUID());
        r1.setStatus("ABSENT");
        List<AttendanceRecordEntity> saved = service.markRelationalAttendance(contextA, created.getId(), Collections.singletonList(r1));

        AttendanceCorrectionEntity corr = new AttendanceCorrectionEntity();
        corr.setRecordId(saved.get(0).getId());
        corr.setOldStatus("ABSENT");
        corr.setNewStatus("PRESENT");
        corr.setReason("Medical document");
        corr.setRequestedBy(userA);
        AttendanceCorrectionEntity requested = service.requestRelationalCorrection(contextA, corr);

        UUID approver = UUID.randomUUID();
        // First approval succeeds
        service.applyRelationalCorrection(contextA, requested.getId(), "APPROVED", approver);

        // Replay of apply / duplicate decision MUST throw IllegalStateException
        service.applyRelationalCorrection(contextA, requested.getId(), "APPROVED", approver);
    }

    @Test
    public void testCorrectionIncrementsRecordRowVersion() {
        UUID sectionId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 22);

        AttendanceSessionEntity session = new AttendanceSessionEntity();
        session.setSectionId(sectionId);
        session.setSessionDate(date);
        session.setStartTime(LocalTime.of(11, 0));
        session.setEndTime(LocalTime.of(12, 0));
        session.setStatus("OPEN");
        AttendanceSessionEntity created = service.createRelationalSession(contextA, session);

        AttendanceRecordEntity r1 = new AttendanceRecordEntity();
        r1.setStudentId(UUID.randomUUID());
        r1.setStatus("ABSENT");
        r1.setRowVersion(1);
        List<AttendanceRecordEntity> saved = service.markRelationalAttendance(contextA, created.getId(), Collections.singletonList(r1));
        assertEquals(1, saved.get(0).getRowVersion());

        AttendanceCorrectionEntity corr = new AttendanceCorrectionEntity();
        corr.setRecordId(saved.get(0).getId());
        corr.setOldStatus("ABSENT");
        corr.setNewStatus("PRESENT");
        corr.setReason("Dean approval");
        corr.setRequestedBy(userA);
        AttendanceCorrectionEntity requested = service.requestRelationalCorrection(contextA, corr);

        UUID approver = UUID.randomUUID();
        service.applyRelationalCorrection(contextA, requested.getId(), "APPROVED", approver);

        List<AttendanceRecordEntity> updated = service.findRelationalRecordsBySessionId(contextA, created.getId());
        assertEquals("PRESENT", updated.get(0).getStatus());
        assertEquals(2, updated.get(0).getRowVersion());
        assertEquals("Dean approval", updated.get(0).getRemarks());
    }

    @Test
    public void testCorrectionHistoryIntegrityAndReadBack() {
        UUID sectionId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 22);

        AttendanceSessionEntity session = new AttendanceSessionEntity();
        session.setSectionId(sectionId);
        session.setSessionDate(date);
        session.setStartTime(LocalTime.of(12, 0));
        session.setEndTime(LocalTime.of(13, 0));
        session.setStatus("OPEN");
        AttendanceSessionEntity created = service.createRelationalSession(contextA, session);

        AttendanceRecordEntity r1 = new AttendanceRecordEntity();
        r1.setStudentId(UUID.randomUUID());
        r1.setStatus("LATE");
        List<AttendanceRecordEntity> saved = service.markRelationalAttendance(contextA, created.getId(), Collections.singletonList(r1));

        AttendanceCorrectionEntity corr = new AttendanceCorrectionEntity();
        corr.setRecordId(saved.get(0).getId());
        corr.setOldStatus("LATE");
        corr.setNewStatus("PRESENT");
        corr.setReason("Bus delayed by administration");
        corr.setRequestedBy(userA);
        AttendanceCorrectionEntity requested = service.requestRelationalCorrection(contextA, corr);

        UUID approver = UUID.randomUUID();
        service.applyRelationalCorrection(contextA, requested.getId(), "APPROVED", approver);

        // Verify history integrity via getRelationalCorrectionsBySessionId
        List<AttendanceCorrectionEntity> history = service.getRelationalCorrectionsBySessionId(contextA, created.getId());
        assertEquals(1, history.size());
        AttendanceCorrectionEntity logEntry = history.get(0);
        assertEquals("LATE", logEntry.getOldStatus());
        assertEquals("PRESENT", logEntry.getNewStatus());
        assertEquals("APPROVED", logEntry.getStatus());
        assertEquals("Bus delayed by administration", logEntry.getReason());
        assertEquals(userA, logEntry.getRequestedBy());
        assertEquals(approver, logEntry.getApprovedBy());
        assertNotNull(logEntry.getDecidedAt());
    }

    @Test
    public void testCrossTenantCorrectionIsolation() {
        UUID sectionId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 22);

        AttendanceSessionEntity session = new AttendanceSessionEntity();
        session.setSectionId(sectionId);
        session.setSessionDate(date);
        session.setStartTime(LocalTime.of(13, 0));
        session.setEndTime(LocalTime.of(14, 0));
        session.setStatus("OPEN");
        AttendanceSessionEntity created = service.createRelationalSession(contextA, session);

        AttendanceRecordEntity r1 = new AttendanceRecordEntity();
        r1.setStudentId(UUID.randomUUID());
        r1.setStatus("ABSENT");
        List<AttendanceRecordEntity> saved = service.markRelationalAttendance(contextA, created.getId(), Collections.singletonList(r1));

        AttendanceCorrectionEntity corr = new AttendanceCorrectionEntity();
        corr.setRecordId(saved.get(0).getId());
        corr.setOldStatus("ABSENT");
        corr.setNewStatus("PRESENT");
        corr.setReason("Tenant A correction");
        corr.setRequestedBy(userA);
        AttendanceCorrectionEntity requested = service.requestRelationalCorrection(contextA, corr);

        // Tenant B attempts to read Tenant A's correction: MUST be empty
        Optional<AttendanceCorrectionEntity> tenantBRead = service.getRelationalCorrectionById(contextB, requested.getId());
        assertFalse(tenantBRead.isPresent());

        // Tenant B attempts to decide Tenant A's correction: MUST fail with AttendanceNotFoundException
        try {
            service.applyRelationalCorrection(contextB, requested.getId(), "APPROVED", userB);
            fail("Expected AttendanceNotFoundException across tenant boundary");
        } catch (AttendanceNotFoundException e) {
            // Success
        }
    }
}
