package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.model.InstituteModels.Calendar;
import com.campx.admin.institute.model.InstituteModels.CalendarEvent;
import com.campx.admin.institute.model.InstituteModels.CalendarWorkingDay;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Live integration tests for Item 23: {@link PostgresCalendarRepository} against Supabase PostgreSQL.
 * Verifies calendars, working day schedules, events lifecycle, and multi-tenant isolation under RLS.
 */
public class PostgresCalendarRepositoryTest {

    private static DatabaseConnectionManager connectionManager;
    private static PostgresCalendarRepository repository;
    private static UserSecurityContext tenantContext;
    private static UserSecurityContext otherTenantContext;

    private static UUID tenantId;
    private static UUID otherTenantId;

    private static final UUID SUPER_ADMIN_UUID = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
    private static final Set<UUID> createdCalendarIds = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    @BeforeClass
    public static void setUpClass() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require database configuration", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresCalendarRepository(connectionManager);

        try (Connection conn = connectionManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM core.tenants WHERE deleted_at IS NULL ORDER BY created_at ASC LIMIT 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        tenantId = (UUID) rs.getObject("id");
                    }
                }
            }
        }
        assumeNotNull("Requires an active tenant", tenantId);

        otherTenantId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(SUPER_ADMIN_UUID, tenantId);
        otherTenantContext = new UserSecurityContext(SUPER_ADMIN_UUID, otherTenantId);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(true);
            for (UUID cid : createdCalendarIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM core.calendars WHERE id = ?")) {
                    ps.setObject(1, cid);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testCreateAndRetrieveCalendar() throws Exception {
        String code = "CAL_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Calendar cal = new Calendar();
        cal.setCode(code);
        cal.setName("Academic Year 2026-2027 Calendar");
        cal.setCalendarType("ACADEMIC");
        cal.setStatus("DRAFT");
        cal.setWeekStartDay((short) 1);

        Calendar created = repository.createCalendar(tenantContext, cal);
        assertNotNull(created.getId());
        createdCalendarIds.add(UUID.fromString(created.getId()));

        assertEquals(tenantId.toString(), created.getTenantId());
        assertEquals(code, created.getCode());
        assertEquals("Academic Year 2026-2027 Calendar", created.getName());
        assertEquals("ACADEMIC", created.getCalendarType());
        assertEquals("DRAFT", created.getStatus());

        Optional<Calendar> byId = repository.findById(tenantContext, created.getId());
        assertTrue(byId.isPresent());
        assertEquals(created.getId(), byId.get().getId());

        Optional<Calendar> byCode = repository.findByCode(tenantContext, code);
        assertTrue(byCode.isPresent());
        assertEquals(created.getId(), byCode.get().getId());

        List<Calendar> list = repository.listCalendars(tenantContext);
        assertFalse(list.isEmpty());
    }

    @Test
    public void testUpdateAndDeleteCalendar() throws Exception {
        String code = "CAL_UPD_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Calendar cal = new Calendar();
        cal.setCode(code);
        cal.setName("Preliminary Calendar");
        cal.setCalendarType("ADMINISTRATIVE");

        Calendar created = repository.createCalendar(tenantContext, cal);
        assertNotNull(created.getId());
        createdCalendarIds.add(UUID.fromString(created.getId()));

        created.setName("Final Approved Calendar");
        created.setStatus("PUBLISHED");
        created.setPublishedAt(System.currentTimeMillis());

        Calendar updated = repository.updateCalendar(tenantContext, created);
        assertEquals("Final Approved Calendar", updated.getName());
        assertEquals("PUBLISHED", updated.getStatus());
        assertNotNull(updated.getPublishedAt());
        assertEquals(2, updated.getRowVersion());

        repository.deleteCalendar(tenantContext, created.getId());
        Optional<Calendar> deleted = repository.findById(tenantContext, created.getId());
        assertFalse(deleted.isPresent());
    }

    @Test
    public void testWorkingDaysLifecycle() throws Exception {
        String code = "CAL_WD_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Calendar cal = new Calendar();
        cal.setCode(code);
        cal.setName("Work Week Calendar");

        Calendar created = repository.createCalendar(tenantContext, cal);
        assertNotNull(created.getId());
        createdCalendarIds.add(UUID.fromString(created.getId()));

        CalendarWorkingDay mon = new CalendarWorkingDay();
        mon.setCalendarId(created.getId());
        mon.setDayOfWeek((short) 1);
        mon.setWorking(true);
        mon.setHalfDay(false);
        CalendarWorkingDay savedMon = repository.saveWorkingDay(tenantContext, mon);
        assertNotNull(savedMon.getId());
        assertEquals(1, savedMon.getDayOfWeek());
        assertTrue(savedMon.isWorking());

        CalendarWorkingDay sat = new CalendarWorkingDay();
        sat.setCalendarId(created.getId());
        sat.setDayOfWeek((short) 6);
        sat.setWorking(true);
        sat.setHalfDay(true);
        repository.saveWorkingDay(tenantContext, sat);

        List<CalendarWorkingDay> days = repository.listWorkingDays(tenantContext, created.getId());
        assertEquals(2, days.size());
        assertEquals(1, days.get(0).getDayOfWeek());
        assertEquals(6, days.get(1).getDayOfWeek());
        assertTrue(days.get(1).isHalfDay());

        repository.deleteWorkingDay(tenantContext, savedMon.getId());
        List<CalendarWorkingDay> afterDelete = repository.listWorkingDays(tenantContext, created.getId());
        assertEquals(1, afterDelete.size());
    }

    @Test
    public void testEventsLifecycle() throws Exception {
        String code = "CAL_EV_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Calendar cal = new Calendar();
        cal.setCode(code);
        cal.setName("Events Calendar");

        Calendar created = repository.createCalendar(tenantContext, cal);
        assertNotNull(created.getId());
        createdCalendarIds.add(UUID.fromString(created.getId()));

        CalendarEvent event = new CalendarEvent();
        event.setCalendarId(created.getId());
        event.setTitle("Fall Semester Induction");
        event.setEventType("TERM_START");
        long now = System.currentTimeMillis();
        event.setStartDate(now);
        event.setEndDate(now + (7L * 24 * 3600 * 1000));
        event.setHoliday(false);
        event.setAppliesTo("{\"programs\": [\"CS\", \"IT\"]}");

        CalendarEvent saved = repository.createEvent(tenantContext, event);
        assertNotNull(saved.getId());
        assertEquals("Fall Semester Induction", saved.getTitle());
        assertEquals("TERM_START", saved.getEventType());
        assertFalse(saved.isHoliday());

        Optional<CalendarEvent> byId = repository.findEventById(tenantContext, saved.getId());
        assertTrue(byId.isPresent());

        saved.setTitle("Updated Fall Semester Induction");
        CalendarEvent updated = repository.updateEvent(tenantContext, saved);
        assertEquals("Updated Fall Semester Induction", updated.getTitle());
        assertEquals(2, updated.getRowVersion());

        List<CalendarEvent> events = repository.listEvents(tenantContext, created.getId());
        assertEquals(1, events.size());

        repository.deleteEvent(tenantContext, saved.getId());
        Optional<CalendarEvent> deleted = repository.findEventById(tenantContext, saved.getId());
        assertFalse(deleted.isPresent());
    }

    @Test
    public void testTenantIsolation() throws Exception {
        String code = "CAL_ISO_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Calendar cal = new Calendar();
        cal.setCode(code);
        cal.setName("Private Tenant Calendar");

        Calendar created = repository.createCalendar(tenantContext, cal);
        assertNotNull(created.getId());
        createdCalendarIds.add(UUID.fromString(created.getId()));

        // Ensure other tenant CANNOT access this calendar under RLS
        Optional<Calendar> isolatedById = repository.findById(otherTenantContext, created.getId());
        assertFalse("Cross-tenant read by ID must be blocked", isolatedById.isPresent());

        Optional<Calendar> isolatedByCode = repository.findByCode(otherTenantContext, code);
        assertFalse("Cross-tenant read by code must be blocked", isolatedByCode.isPresent());
    }
}
