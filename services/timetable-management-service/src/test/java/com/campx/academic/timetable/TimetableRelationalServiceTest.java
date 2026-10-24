package com.campx.academic.timetable;

import com.campx.academic.timetable.exception.TimetableConflictException;
import com.campx.academic.timetable.exception.TimetableValidationException;
import com.campx.academic.timetable.model.*;
import com.campx.academic.timetable.repository.InMemoryTimetableRepository;
import com.campx.academic.timetable.security.UserSecurityContext;
import com.campx.academic.timetable.service.TimetableDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Unit tests verifying relational persistence and deterministic conflict detection
 * in the Timetable Domain Service (Wave 5).
 */
public class TimetableRelationalServiceTest {

    private InMemoryTimetableRepository repository;
    private TimetableDomainService service;

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID otherTenantId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID collegeId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID termId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private final UUID batchId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;

    @Before
    public void setUp() {
        repository = new InMemoryTimetableRepository();
        service = new TimetableDomainService(repository);
        tenantContext = new UserSecurityContext(userId, tenantId, collegeId, null, "college_admin", false);
        otherTenantContext = new UserSecurityContext(userId, otherTenantId, collegeId, null, "college_admin", false);
    }

    @Test
    public void testCreateTimetableAndVersionRelational() {
        TimetableEntity timetable = new TimetableEntity(tenantId, collegeId, termId, batchId, "CSE Semester 5 Schedule", "DRAFT");
        TimetableEntity savedTt = service.createTimetableRelational(tenantContext, timetable);

        assertNotNull("Saved timetable must have an ID", savedTt.getId());
        assertEquals("CSE Semester 5 Schedule", savedTt.getName());
        assertEquals(tenantId, savedTt.getTenantId());

        TimetableVersionEntity version = new TimetableVersionEntity(tenantId, savedTt.getId(), 1, "DRAFT", "Initial draft version");
        TimetableVersionEntity savedVer = service.createVersionRelational(tenantContext, version);

        assertNotNull("Saved version must have an ID", savedVer.getId());
        assertEquals(1, savedVer.getVersionNo());
        assertEquals("DRAFT", savedVer.getStatus());

        Optional<TimetableEntity> found = service.findTimetableByIdRelational(tenantContext, savedTt.getId());
        assertTrue("Timetable must be retrievable", found.isPresent());
        assertEquals(savedTt.getName(), found.get().getName());
    }

    @Test
    public void testCreateSlotAndRetrieveGrid() {
        TimetableEntity timetable = service.createTimetableRelational(tenantContext,
                new TimetableEntity(tenantId, collegeId, termId, batchId, "ECE Schedule", "DRAFT"));
        TimetableVersionEntity version = service.createVersionRelational(tenantContext,
                new TimetableVersionEntity(tenantId, timetable.getId(), 1, "DRAFT", "v1"));

        UUID sectionId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID facultyId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        CreateTimetableSlotRequest req = new CreateTimetableSlotRequest(
                version.getId(), sectionId, subjectId, facultyId, roomId, 1, "09:00:00", "10:00:00", "LECTURE"
        );

        TimetableEntryEntity slot = service.createSlotRelational(tenantContext, req);
        assertNotNull("Persisted slot must have an ID", slot.getId());
        assertEquals(1, slot.getDayOfWeek());

        List<TimetableSlotResponse> slots = service.findSlotResponsesByVersionRelational(tenantContext, version.getId());
        assertEquals(1, slots.size());
        assertEquals(slot.getId(), slots.get(0).getId());
    }

    @Test(expected = TimetableConflictException.class)
    public void testFacultyDoubleBookingConflictDetected() {
        TimetableEntity timetable = service.createTimetableRelational(tenantContext,
                new TimetableEntity(tenantId, collegeId, termId, batchId, "Mechanical Schedule", "DRAFT"));
        TimetableVersionEntity version = service.createVersionRelational(tenantContext,
                new TimetableVersionEntity(tenantId, timetable.getId(), 1, "DRAFT", "v1"));

        UUID facultyId = UUID.randomUUID();
        UUID room1 = UUID.randomUUID();
        UUID room2 = UUID.randomUUID();
        UUID section1 = UUID.randomUUID();
        UUID section2 = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();

        // Slot 1: Monday 10:00 - 11:00 with Faculty A in Room 1 for Section 1
        service.createSlotRelational(tenantContext, new CreateTimetableSlotRequest(
                version.getId(), section1, subjectId, facultyId, room1, 1, "10:00:00", "11:00:00", "LECTURE"
        ));

        // Slot 2: Overlapping Monday 10:30 - 11:30 with SAME Faculty A in Room 2 for Section 2 -> CONFLICT!
        service.createSlotRelational(tenantContext, new CreateTimetableSlotRequest(
                version.getId(), section2, subjectId, facultyId, room2, 1, "10:30:00", "11:30:00", "LECTURE"
        ));
    }

    @Test(expected = TimetableConflictException.class)
    public void testSectionDoubleBookingConflictDetected() {
        TimetableEntity timetable = service.createTimetableRelational(tenantContext,
                new TimetableEntity(tenantId, collegeId, termId, batchId, "Civil Schedule", "DRAFT"));
        TimetableVersionEntity version = service.createVersionRelational(tenantContext,
                new TimetableVersionEntity(tenantId, timetable.getId(), 1, "DRAFT", "v1"));

        UUID sectionId = UUID.randomUUID();
        UUID faculty1 = UUID.randomUUID();
        UUID faculty2 = UUID.randomUUID();
        UUID room1 = UUID.randomUUID();
        UUID room2 = UUID.randomUUID();
        UUID subject1 = UUID.randomUUID();
        UUID subject2 = UUID.randomUUID();

        // Slot 1: Tuesday 14:00 - 15:00 for Section A
        service.createSlotRelational(tenantContext, new CreateTimetableSlotRequest(
                version.getId(), sectionId, subject1, faculty1, room1, 2, "14:00:00", "15:00:00", "LECTURE"
        ));

        // Slot 2: Tuesday 14:00 - 15:00 with different faculty/room for SAME Section A -> CONFLICT!
        service.createSlotRelational(tenantContext, new CreateTimetableSlotRequest(
                version.getId(), sectionId, subject2, faculty2, room2, 2, "14:00:00", "15:00:00", "LAB"
        ));
    }

    @Test(expected = TimetableConflictException.class)
    public void testRoomDoubleBookingConflictDetected() {
        TimetableEntity timetable = service.createTimetableRelational(tenantContext,
                new TimetableEntity(tenantId, collegeId, termId, batchId, "IT Schedule", "DRAFT"));
        TimetableVersionEntity version = service.createVersionRelational(tenantContext,
                new TimetableVersionEntity(tenantId, timetable.getId(), 1, "DRAFT", "v1"));

        UUID roomId = UUID.randomUUID();
        UUID faculty1 = UUID.randomUUID();
        UUID faculty2 = UUID.randomUUID();
        UUID section1 = UUID.randomUUID();
        UUID section2 = UUID.randomUUID();
        UUID subject1 = UUID.randomUUID();
        UUID subject2 = UUID.randomUUID();

        // Slot 1: Wednesday 11:00 - 12:00 in Room 101
        service.createSlotRelational(tenantContext, new CreateTimetableSlotRequest(
                version.getId(), section1, subject1, faculty1, roomId, 3, "11:00:00", "12:00:00", "LECTURE"
        ));

        // Slot 2: Wednesday 11:00 - 12:00 in SAME Room 101 -> CONFLICT!
        service.createSlotRelational(tenantContext, new CreateTimetableSlotRequest(
                version.getId(), section2, subject2, faculty2, roomId, 3, "11:00:00", "12:00:00", "SEMINAR"
        ));
    }

    @Test(expected = TimetableValidationException.class)
    public void testInvalidTimeOrderRejected() {
        TimetableEntity timetable = service.createTimetableRelational(tenantContext,
                new TimetableEntity(tenantId, collegeId, termId, batchId, "Validation Test", "DRAFT"));
        TimetableVersionEntity version = service.createVersionRelational(tenantContext,
                new TimetableVersionEntity(tenantId, timetable.getId(), 1, "DRAFT", "v1"));

        // End time (09:00) before start time (10:00)
        service.createSlotRelational(tenantContext, new CreateTimetableSlotRequest(
                version.getId(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, "10:00:00", "09:00:00", "LECTURE"
        ));
    }

    @Test
    public void testCrossTenantIsolationEnforced() {
        TimetableEntity timetable = service.createTimetableRelational(tenantContext,
                new TimetableEntity(tenantId, collegeId, termId, batchId, "Tenant 1 Schedule", "DRAFT"));

        // Attempt to find timetable using Tenant 2's context
        Optional<TimetableEntity> crossTenantLookup = service.findTimetableByIdRelational(otherTenantContext, timetable.getId());
        assertFalse("Cross-tenant lookup must return empty Optional (isolated)", crossTenantLookup.isPresent());
    }

    @Test
    public void testDeleteSlotRelational() {
        TimetableEntity timetable = service.createTimetableRelational(tenantContext,
                new TimetableEntity(tenantId, collegeId, termId, batchId, "Deletion Test", "DRAFT"));
        TimetableVersionEntity version = service.createVersionRelational(tenantContext,
                new TimetableVersionEntity(tenantId, timetable.getId(), 1, "DRAFT", "v1"));

        TimetableEntryEntity slot = service.createSlotRelational(tenantContext, new CreateTimetableSlotRequest(
                version.getId(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, "08:00:00", "09:00:00", "LECTURE"
        ));

        boolean deleted = service.deleteSlotRelational(tenantContext, slot.getId());
        assertTrue("Slot deletion must succeed", deleted);

        List<TimetableSlotResponse> slots = service.findSlotResponsesByVersionRelational(tenantContext, version.getId());
        assertEquals("Deleted slot must not appear in active version slots", 0, slots.size());
    }
}
