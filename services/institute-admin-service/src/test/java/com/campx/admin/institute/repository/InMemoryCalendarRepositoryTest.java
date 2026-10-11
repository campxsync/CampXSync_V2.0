package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.Calendar;
import com.campx.admin.institute.model.InstituteModels.CalendarEvent;
import com.campx.admin.institute.model.InstituteModels.CalendarWorkingDay;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class InMemoryCalendarRepositoryTest {

    private InMemoryCalendarRepository repository;
    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;
    private UUID tenantId;

    @Before
    public void setUp() {
        repository = new InMemoryCalendarRepository();
        tenantId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(UUID.randomUUID(), tenantId);
        otherTenantContext = new UserSecurityContext(UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    public void testCreateAndRetrieveCalendar() {
        Calendar cal = new Calendar();
        cal.setCode("CAL-2026-MAIN");
        cal.setName("Main Academic Calendar 2026");
        cal.setCalendarType("ACADEMIC");
        cal.setStatus("DRAFT");
        cal.setWeekStartDay((short) 1);

        Calendar created = repository.createCalendar(tenantContext, cal);
        assertNotNull(created.getId());
        assertEquals("CAL-2026-MAIN", created.getCode());
        assertEquals("Main Academic Calendar 2026", created.getName());

        Optional<Calendar> byId = repository.findById(tenantContext, created.getId());
        assertTrue(byId.isPresent());
        assertEquals(created.getId(), byId.get().getId());

        Optional<Calendar> byCode = repository.findByCode(tenantContext, "cal-2026-main");
        assertTrue(byCode.isPresent());
        assertEquals(created.getId(), byCode.get().getId());

        List<Calendar> list = repository.listCalendars(tenantContext);
        assertEquals(1, list.size());

        // Isolation
        assertFalse(repository.findById(otherTenantContext, created.getId()).isPresent());
        assertFalse(repository.findByCode(otherTenantContext, "CAL-2026-MAIN").isPresent());
    }

    @Test(expected = MalformedPayloadException.class)
    public void testDuplicateCodeRejection() {
        Calendar c1 = new Calendar();
        c1.setCode("CAL-DUP");
        c1.setName("Calendar 1");
        repository.createCalendar(tenantContext, c1);

        Calendar c2 = new Calendar();
        c2.setCode("CAL-DUP");
        c2.setName("Calendar 2");
        repository.createCalendar(tenantContext, c2);
    }

    @Test
    public void testUpdateAndDeleteCalendar() {
        Calendar cal = new Calendar();
        cal.setCode("CAL-UPD");
        cal.setName("Initial Name");
        Calendar created = repository.createCalendar(tenantContext, cal);

        created.setName("Updated Name");
        created.setStatus("PUBLISHED");
        Calendar updated = repository.updateCalendar(tenantContext, created);
        assertEquals("Updated Name", updated.getName());
        assertEquals("PUBLISHED", updated.getStatus());
        assertEquals(2, updated.getRowVersion());

        repository.deleteCalendar(tenantContext, created.getId());
        assertFalse(repository.findById(tenantContext, created.getId()).isPresent());
    }

    @Test
    public void testWorkingDaysLifecycle() {
        Calendar cal = new Calendar();
        cal.setCode("CAL-WD");
        cal.setName("Working Days Calendar");
        Calendar created = repository.createCalendar(tenantContext, cal);

        CalendarWorkingDay wd1 = new CalendarWorkingDay();
        wd1.setCalendarId(created.getId());
        wd1.setDayOfWeek((short) 1); // Monday
        wd1.setWorking(true);
        wd1.setHalfDay(false);
        repository.saveWorkingDay(tenantContext, wd1);

        CalendarWorkingDay wd2 = new CalendarWorkingDay();
        wd2.setCalendarId(created.getId());
        wd2.setDayOfWeek((short) 6); // Saturday
        wd2.setWorking(true);
        wd2.setHalfDay(true);
        repository.saveWorkingDay(tenantContext, wd2);

        List<CalendarWorkingDay> list = repository.listWorkingDays(tenantContext, created.getId());
        assertEquals(2, list.size());
        assertEquals(1, list.get(0).getDayOfWeek());
        assertEquals(6, list.get(1).getDayOfWeek());
        assertTrue(list.get(1).isHalfDay());

        repository.deleteWorkingDay(tenantContext, list.get(0).getId());
        List<CalendarWorkingDay> remaining = repository.listWorkingDays(tenantContext, created.getId());
        assertEquals(1, remaining.size());
    }

    @Test
    public void testEventsLifecycle() {
        Calendar cal = new Calendar();
        cal.setCode("CAL-EV");
        cal.setName("Events Calendar");
        Calendar created = repository.createCalendar(tenantContext, cal);

        CalendarEvent event = new CalendarEvent();
        event.setCalendarId(created.getId());
        event.setTitle("Semester Start");
        event.setEventType("TERM_START");
        event.setStartDate(System.currentTimeMillis());
        event.setEndDate(System.currentTimeMillis() + 86400000L);
        CalendarEvent saved = repository.createEvent(tenantContext, event);

        assertNotNull(saved.getId());
        assertEquals("TERM_START", saved.getEventType());

        Optional<CalendarEvent> byId = repository.findEventById(tenantContext, saved.getId());
        assertTrue(byId.isPresent());

        saved.setTitle("Term 1 Start");
        CalendarEvent updated = repository.updateEvent(tenantContext, saved);
        assertEquals("Term 1 Start", updated.getTitle());
        assertEquals(2, updated.getRowVersion());

        List<CalendarEvent> events = repository.listEvents(tenantContext, created.getId());
        assertEquals(1, events.size());

        repository.deleteEvent(tenantContext, saved.getId());
        assertFalse(repository.findEventById(tenantContext, saved.getId()).isPresent());
    }

    @Test(expected = SecurityViolationException.class)
    public void testSecurityContextRequired() {
        repository.createCalendar(null, new Calendar());
    }
}
