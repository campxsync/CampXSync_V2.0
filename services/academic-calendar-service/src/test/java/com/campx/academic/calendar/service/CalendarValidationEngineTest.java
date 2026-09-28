package com.campx.academic.calendar.service;

import com.campx.academic.calendar.exception.CalendarTermOverlapException;
import com.campx.academic.calendar.model.CalendarModels.*;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class CalendarValidationEngineTest {

    private CalendarValidationEngine engine;

    @Before
    public void setup() {
        engine = new CalendarValidationEngine();
    }

    @Test
    public void testTermOverlapThrowsException() {
        CalendarTerm term1 = new CalendarTerm("T1", "TENANT-001", "CAL-1", "TERM-1", "Fall 2026", 1,
                "2026-08-01", "2026-12-15", "2026-08-15", "2026-12-01");
        CalendarTerm term2 = new CalendarTerm("T2", "TENANT-001", "CAL-1", "TERM-2", "Winter 2026", 2,
                "2026-12-01", "2027-04-30", "2026-12-10", "2027-04-15");

        try {
            engine.validateTermOverlap(term2, Collections.singletonList(term1));
            fail("Expected CalendarTermOverlapException");
        } catch (CalendarTermOverlapException e) {
            assertTrue(e.getMessage().contains("overlaps"));
            assertEquals("ACD_CALENDAR_TERM_OVERLAP", e.getErrorCode());
            assertEquals(422, e.getStatusCode());
        }
    }

    @Test
    public void testValidNonOverlappingTerms() {
        CalendarTerm term1 = new CalendarTerm("T1", "TENANT-001", "CAL-1", "TERM-1", "Fall 2026", 1,
                "2026-08-01", "2026-11-30", "2026-08-15", "2026-11-20");
        CalendarTerm term2 = new CalendarTerm("T2", "TENANT-001", "CAL-1", "TERM-2", "Spring 2027", 2,
                "2026-12-01", "2027-04-30", "2026-12-10", "2027-04-15");

        // Should not throw
        engine.validateTermOverlap(term2, Collections.singletonList(term1));
    }

    @Test
    public void testFullCalendarValidationWithIssues() {
        AcademicCalendar cal = new AcademicCalendar("CAL-1", "TENANT-001", "INST-1", "CAMP-1", "2026-2027", "ACAD-2026", "Calendar", "Asia/Kolkata");
        cal.setEffectiveFrom("2026-08-01");
        cal.setEffectiveTo("2027-05-31");

        // Empty terms should report blocking issue
        ValidationReport r1 = engine.validateCalendar(cal, Collections.emptyList(), Collections.emptyList());
        assertFalse(r1.valid);
        assertEquals(1, r1.blockingCount);

        // Add valid term
        CalendarTerm term = new CalendarTerm("T1", "TENANT-001", "CAL-1", "SEM-1", "Semester 1", 1,
                "2026-08-01", "2026-12-15", "2026-08-15", "2026-12-01");
        ValidationReport r2 = engine.validateCalendar(cal, Collections.singletonList(term), Collections.emptyList());
        assertTrue(r2.valid);
        assertEquals(0, r2.blockingCount);
    }
}
