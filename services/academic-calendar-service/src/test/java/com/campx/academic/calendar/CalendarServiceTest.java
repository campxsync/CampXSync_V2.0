package com.campx.academic.calendar;

import com.campx.academic.calendar.exception.*;
import com.campx.academic.calendar.model.CalendarModels.*;
import com.campx.academic.calendar.service.CalendarDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class CalendarServiceTest {

    private CalendarDomainService service;

    @Before
    public void setup() {
        service = new CalendarDomainService();
    }

    @Test
    public void testCreateCalendarDraftAndDuplicatePrevention() {
        CreateCalendarRequest req = new CreateCalendarRequest();
        req.calendarCode = "CAL-2026-ENG";
        req.name = "Engineering Academic Calendar 2026-2027";
        req.academicYear = "2026-2027";
        req.campusId = "CAMP-MAIN";
        req.effectiveFrom = "2026-08-01";
        req.effectiveTo = "2027-05-31";

        AcademicCalendar cal = service.createCalendar(req, "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN");
        assertNotNull(cal);
        assertNotNull(cal.getId());
        assertEquals(CalendarStatus.DRAFT, cal.getStatus());
        assertEquals("CAL-2026-ENG", cal.getCalendarCode());
        assertEquals(1, cal.getCurrentVersion());

        // Duplicate rejection test (US-002)
        try {
            service.createCalendar(req, "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN");
            fail("Expected DuplicateCalendarException");
        } catch (DuplicateCalendarException ex) {
            assertEquals("ACD_CALENDAR_DUPLICATE_CALENDAR", ex.getErrorCode());
            assertEquals(409, ex.getStatusCode());
        }
    }

    @Test
    public void testUpdateCalendarAndOptimisticVersioning() {
        CreateCalendarRequest req = new CreateCalendarRequest();
        req.calendarCode = "CAL-UPDATE-TEST";
        req.name = "Original Name";
        req.academicYear = "2026-2027";

        AcademicCalendar cal = service.createCalendar(req, "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN");

        UpdateCalendarRequest upReq = new UpdateCalendarRequest();
        upReq.name = "Updated Name";
        upReq.expectedVersion = 1;

        AcademicCalendar updated = service.updateCalendar(cal.getId(), upReq, "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN");
        assertEquals("Updated Name", updated.getName());

        // Stale version conflict (US-004, US-061)
        upReq.expectedVersion = 99;
        try {
            service.updateCalendar(cal.getId(), upReq, "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN");
            fail("Expected CalendarVersionConflictException");
        } catch (CalendarVersionConflictException ex) {
            assertEquals("ACD_CALENDAR_VERSION_CONFLICT", ex.getErrorCode());
            assertEquals(409, ex.getStatusCode());
        }
    }

    @Test
    public void testCancelCalendarDraft() {
        CreateCalendarRequest req = new CreateCalendarRequest();
        req.calendarCode = "CAL-CANCEL-TEST";
        req.name = "Cancel Draft";
        req.academicYear = "2026-2027";

        AcademicCalendar cal = service.createCalendar(req, "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN");
        AcademicCalendar cancelled = service.cancelCalendar(cal.getId(), "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN", "No longer needed");
        assertEquals(CalendarStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    public void testAddTermsAndOverlapRejection() {
        CreateCalendarRequest req = new CreateCalendarRequest();
        req.calendarCode = "CAL-TERM-TEST";
        req.name = "Term Calendar";
        req.academicYear = "2026-2027";
        req.effectiveFrom = "2026-08-01";
        req.effectiveTo = "2027-05-31";

        AcademicCalendar cal = service.createCalendar(req, "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN");

        AddTermRequest t1 = new AddTermRequest();
        t1.termCode = "SEM-1";
        t1.name = "Semester 1";
        t1.sequenceNo = 1;
        t1.startDate = "2026-08-01";
        t1.endDate = "2026-12-15";
        CalendarTerm term1 = service.addTerm(cal.getId(), t1, "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN");
        assertNotNull(term1);

        // Overlapping term rejection (US-008)
        AddTermRequest t2 = new AddTermRequest();
        t2.termCode = "SEM-2";
        t2.name = "Semester 2";
        t2.sequenceNo = 2;
        t2.startDate = "2026-12-10"; // Overlaps with SEM-1
        t2.endDate = "2027-04-30";

        try {
            service.addTerm(cal.getId(), t2, "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN");
            fail("Expected CalendarTermOverlapException");
        } catch (CalendarTermOverlapException ex) {
            assertEquals("ACD_CALENDAR_TERM_OVERLAP", ex.getErrorCode());
            assertEquals(422, ex.getStatusCode());
        }
    }

    @Test
    public void testEventsAndHolidaysManagement() {
        CreateCalendarRequest req = new CreateCalendarRequest();
        req.calendarCode = "CAL-EVENT-TEST";
        req.name = "Event Calendar";
        req.academicYear = "2026-2027";

        AcademicCalendar cal = service.createCalendar(req, "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN");

        // Add Holiday (US-012)
        AddEventRequest hol = new AddEventRequest();
        hol.eventCode = "HOL-IND-DAY";
        hol.eventType = "HOLIDAY";
        hol.title = "Independence Day";
        hol.startDate = "2026-08-15";
        hol.endDate = "2026-08-15";
        CalendarEvent holiday = service.addEvent(cal.getId(), hol, "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN");
        assertNotNull(holiday);
        assertEquals(EventType.HOLIDAY, holiday.getEventType());
        assertEquals(WorkingDayImpact.NON_WORKING, holiday.getWorkingDayImpact());

        // Add Working Day Override (US-013)
        AddEventRequest ovr = new AddEventRequest();
        ovr.eventCode = "OVR-COMP-SAT";
        ovr.eventType = "WORKING_DAY_OVERRIDE";
        ovr.title = "Compensatory Saturday";
        ovr.startDate = "2026-08-22";
        ovr.endDate = "2026-08-22";
        ovr.workingDayImpact = "WORKING";
        CalendarEvent override = service.addEvent(cal.getId(), ovr, "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN");
        assertNotNull(override);
        assertEquals(WorkingDayImpact.WORKING, override.getWorkingDayImpact());

        List<CalendarEvent> allEvents = service.getCalendarEvents(cal.getId(), "TENANT-001", null, null, null, null);
        assertEquals(2, allEvents.size());
    }

    @Test
    public void testFullPublicationPipelineAndSuperseding() {
        // 1. Create Draft Calendar 1
        CreateCalendarRequest req1 = new CreateCalendarRequest();
        req1.calendarCode = "CAL-PUB-01";
        req1.name = "V1 Calendar";
        req1.academicYear = "2026-2027";
        req1.campusId = "CAMPUS-A";
        req1.effectiveFrom = "2026-08-01";
        req1.effectiveTo = "2027-05-31";
        AcademicCalendar cal1 = service.createCalendar(req1, "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN");

        // Add term to make valid
        AddTermRequest tReq1 = new AddTermRequest();
        tReq1.termCode = "T1";
        tReq1.name = "Term 1";
        tReq1.startDate = "2026-08-01";
        tReq1.endDate = "2026-12-15";
        service.addTerm(cal1.getId(), tReq1, "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN");

        // 2. Submit -> Approve -> Publish
        service.submitCalendar(cal1.getId(), new SubmitCalendarRequest(), "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN");
        ApprovalRequest appReq = new ApprovalRequest();
        appReq.decision = "APPROVED";
        service.decideApproval(cal1.getId(), appReq, "TENANT-001", "REGISTRAR-01", "REGISTRAR");
        AcademicCalendar published1 = service.publishCalendar(cal1.getId(), new PublishCalendarRequest(), "TENANT-001", "REGISTRAR-01", "REGISTRAR");
        assertEquals(CalendarStatus.PUBLISHED, published1.getStatus());

        // Check current published
        AcademicCalendar current = service.getCurrentPublishedCalendar("CAMPUS-A", "2026-2027", "TENANT-001");
        assertNotNull(current);
        assertEquals(cal1.getId(), current.getId());

        // 3. Create Draft Calendar 2 for same campus and year
        CreateCalendarRequest req2 = new CreateCalendarRequest();
        req2.calendarCode = "CAL-PUB-02";
        req2.name = "V2 Calendar";
        req2.academicYear = "2026-2027";
        req2.campusId = "CAMPUS-A";
        req2.effectiveFrom = "2026-08-01";
        req2.effectiveTo = "2027-05-31";
        AcademicCalendar cal2 = service.createCalendar(req2, "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN");

        AddTermRequest tReq2 = new AddTermRequest();
        tReq2.termCode = "T1-V2";
        tReq2.name = "Term 1 V2";
        tReq2.startDate = "2026-08-01";
        tReq2.endDate = "2026-12-15";
        service.addTerm(cal2.getId(), tReq2, "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN");

        service.submitCalendar(cal2.getId(), new SubmitCalendarRequest(), "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN");
        service.decideApproval(cal2.getId(), appReq, "TENANT-001", "REGISTRAR-01", "REGISTRAR");

        // Publish Calendar 2 -> Calendar 1 must atomically become SUPERSEDED (US-027)
        AcademicCalendar published2 = service.publishCalendar(cal2.getId(), new PublishCalendarRequest(), "TENANT-001", "REGISTRAR-01", "REGISTRAR");
        assertEquals(CalendarStatus.PUBLISHED, published2.getStatus());

        AcademicCalendar refreshed1 = service.getCalendar(cal1.getId(), "TENANT-001");
        assertEquals(CalendarStatus.SUPERSEDED, refreshed1.getStatus());

        AcademicCalendar updatedCurrent = service.getCurrentPublishedCalendar("CAMPUS-A", "2026-2027", "TENANT-001");
        assertEquals(cal2.getId(), updatedCurrent.getId());
    }

    @Test
    public void testEffectiveDateResolution() {
        CreateCalendarRequest req = new CreateCalendarRequest();
        req.calendarCode = "CAL-RES-TEST";
        req.name = "Resolution Calendar";
        req.academicYear = "2026-2027";
        AcademicCalendar cal = service.createCalendar(req, "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN");

        AddTermRequest tReq = new AddTermRequest();
        tReq.termCode = "FALL";
        tReq.name = "Fall 2026";
        tReq.startDate = "2026-08-01";
        tReq.endDate = "2026-12-15";
        tReq.instructionalStartDate = "2026-08-15";
        tReq.instructionalEndDate = "2026-12-01";
        service.addTerm(cal.getId(), tReq, "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN");

        AddEventRequest hol = new AddEventRequest();
        hol.eventCode = "HOL-TEST";
        hol.eventType = "HOLIDAY";
        hol.title = "Gandhi Jayanti";
        hol.startDate = "2026-10-02";
        hol.endDate = "2026-10-02";
        service.addEvent(cal.getId(), hol, "TENANT-001", "ADMIN-01", "ACADEMIC_ADMIN");

        // Normal Instructional Day
        EffectiveDateResolution r1 = service.resolveEffectiveDate(cal.getId(), "2026-09-01", "TENANT-001");
        assertTrue(r1.isInstructionalDay);
        assertFalse(r1.isHoliday);
        assertEquals("WORKING", r1.workingDayStatus);

        // Holiday Resolution
        EffectiveDateResolution r2 = service.resolveEffectiveDate(cal.getId(), "2026-10-02", "TENANT-001");
        assertTrue(r2.isHoliday);
        assertEquals("NON_WORKING", r2.workingDayStatus);
        assertEquals("Gandhi Jayanti", r2.holidayTitle);
    }
}
