package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.Calendar;
import com.campx.admin.institute.model.InstituteModels.CalendarEvent;
import com.campx.admin.institute.model.InstituteModels.CalendarWorkingDay;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL implementation of {@link CalendarRepository} backed by:
 * {@code core.calendars}, {@code core.calendar_events}, and {@code core.calendar_working_days}.
 * Executes within caller transaction under PostgreSQL RLS kernel enforcement.
 */
public class PostgresCalendarRepository implements CalendarRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresCalendarRepository.class);

    private final DatabaseConnectionManager connectionManager;

    private static final Set<String> VALID_TYPES = new HashSet<>(Arrays.asList("ACADEMIC", "HOLIDAY", "EXAM", "ADMINISTRATIVE"));
    private static final Set<String> VALID_STATUSES = new HashSet<>(Arrays.asList("DRAFT", "PUBLISHED", "ARCHIVED"));
    private static final Set<String> VALID_EVENT_TYPES = new HashSet<>(Arrays.asList(
            "HOLIDAY", "TERM_START", "TERM_END", "EXAM", "COMMENCEMENT", "EVENT", "DEADLINE", "OTHER"));

    public PostgresCalendarRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresCalendarRepository(DatabaseConnectionManager connectionManager) {
        if (connectionManager == null) {
            throw new IllegalArgumentException("DatabaseConnectionManager cannot be null");
        }
        this.connectionManager = connectionManager;
    }

    private void checkContext(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
    }

    @Override
    public Calendar createCalendar(UserSecurityContext context, Calendar cal) {
        checkContext(context);
        if (cal == null) {
            throw new MalformedPayloadException("Calendar payload cannot be null");
        }
        if (cal.getCode() == null || cal.getCode().trim().isEmpty()) {
            throw new MalformedPayloadException("Calendar code is required");
        }
        if (cal.getName() == null || cal.getName().trim().isEmpty()) {
            throw new MalformedPayloadException("Calendar name is required");
        }
        String type = cal.getCalendarType() != null ? cal.getCalendarType().trim().toUpperCase(Locale.ROOT) : "ACADEMIC";
        if (!VALID_TYPES.contains(type)) {
            throw new MalformedPayloadException("Invalid calendar type: " + type);
        }
        String status = cal.getStatus() != null ? cal.getStatus().trim().toUpperCase(Locale.ROOT) : "DRAFT";
        if (!VALID_STATUSES.contains(status)) {
            throw new MalformedPayloadException("Invalid status: " + status);
        }

        UUID calId = (cal.getId() != null && !cal.getId().trim().isEmpty())
                ? UUID.fromString(cal.getId().trim())
                : UUID.randomUUID();
        String code = cal.getCode().trim().toUpperCase(Locale.ROOT);
        UUID collegeId = (cal.getCollegeId() != null && !cal.getCollegeId().trim().isEmpty())
                ? UUID.fromString(cal.getCollegeId().trim()) : null;
        UUID ayId = (cal.getAcademicYearId() != null && !cal.getAcademicYearId().trim().isEmpty())
                ? UUID.fromString(cal.getAcademicYearId().trim()) : null;

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO core.calendars " +
                    "(id, tenant_id, college_id, academic_year_id, code, name, calendar_type, status, published_at, week_start_day, created_at, updated_at, created_by, updated_by, row_version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(), ?, ?, 1) " +
                    "RETURNING id, tenant_id, college_id, academic_year_id, code, name, calendar_type, status, published_at, week_start_day, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, calId);
                ps.setObject(2, context.getTenantId());
                ps.setObject(3, collegeId);
                ps.setObject(4, ayId);
                ps.setString(5, code);
                ps.setString(6, cal.getName().trim());
                ps.setString(7, type);
                ps.setString(8, status);
                if (cal.getPublishedAt() != null) {
                    ps.setTimestamp(9, new Timestamp(cal.getPublishedAt()));
                } else {
                    ps.setNull(9, Types.TIMESTAMP);
                }
                ps.setShort(10, cal.getWeekStartDay());
                ps.setObject(11, context.getUserId());
                ps.setObject(12, context.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Calendar persisted = mapCalendarRow(rs);
                        logger.info("Persisted calendar [{}] code [{}] in tenant [{}]",
                                persisted.getId(), persisted.getCode(), persisted.getTenantId());
                        return persisted;
                    }
                }
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new MalformedPayloadException("Calendar code already exists: " + code);
                }
                throw e;
            }
            throw new MalformedPayloadException("Failed to persist calendar record");
        });
    }

    @Override
    public Optional<Calendar> findById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, college_id, academic_year_id, code, name, calendar_type, status, published_at, week_start_day, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.calendars WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, UUID.fromString(id.trim()));
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapCalendarRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<Calendar> findByCode(UserSecurityContext context, String code) {
        checkContext(context);
        if (code == null || code.trim().isEmpty()) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, college_id, academic_year_id, code, name, calendar_type, status, published_at, week_start_day, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.calendars WHERE code = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, code.trim().toUpperCase(Locale.ROOT));
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapCalendarRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<Calendar> listCalendars(UserSecurityContext context) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, college_id, academic_year_id, code, name, calendar_type, status, published_at, week_start_day, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.calendars WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY name";
            List<Calendar> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapCalendarRow(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public List<Calendar> listCalendarsByCollege(UserSecurityContext context, String collegeId) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, college_id, academic_year_id, code, name, calendar_type, status, published_at, week_start_day, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.calendars WHERE tenant_id = ? AND college_id = ? AND deleted_at IS NULL ORDER BY name";
            List<Calendar> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setObject(2, collegeId != null ? UUID.fromString(collegeId.trim()) : null);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapCalendarRow(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public Calendar updateCalendar(UserSecurityContext context, Calendar cal) {
        checkContext(context);
        if (cal == null || cal.getId() == null) {
            throw new MalformedPayloadException("Calendar ID is required for update");
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE core.calendars SET " +
                    "name = COALESCE(?, name), " +
                    "calendar_type = COALESCE(?, calendar_type), " +
                    "status = COALESCE(?, status), " +
                    "published_at = COALESCE(?, published_at), " +
                    "week_start_day = COALESCE(?, week_start_day), " +
                    "updated_at = now(), updated_by = ?, row_version = row_version + 1 " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL " +
                    "RETURNING id, tenant_id, college_id, academic_year_id, code, name, calendar_type, status, published_at, week_start_day, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, cal.getName());
                ps.setString(2, cal.getCalendarType());
                ps.setString(3, cal.getStatus());
                if (cal.getPublishedAt() != null) {
                    ps.setTimestamp(4, new Timestamp(cal.getPublishedAt()));
                } else {
                    ps.setNull(4, Types.TIMESTAMP);
                }
                ps.setShort(5, cal.getWeekStartDay());
                ps.setObject(6, context.getUserId());
                ps.setObject(7, UUID.fromString(cal.getId().trim()));
                ps.setObject(8, context.getTenantId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapCalendarRow(rs);
                    }
                }
            }
            throw new ResourceNotFoundException("Calendar", cal.getId());
        });
    }

    @Override
    public void deleteCalendar(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return;

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE core.calendars SET deleted_at = now(), updated_at = now(), updated_by = ? " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getUserId());
                ps.setObject(2, UUID.fromString(id.trim()));
                ps.setObject(3, context.getTenantId());
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new ResourceNotFoundException("Calendar", id);
                }
            }
            return null;
        });
    }

    @Override
    public CalendarWorkingDay saveWorkingDay(UserSecurityContext context, CalendarWorkingDay wd) {
        checkContext(context);
        if (wd == null) throw new MalformedPayloadException("WorkingDay cannot be null");
        if (wd.getCalendarId() == null || wd.getCalendarId().trim().isEmpty()) {
            throw new MalformedPayloadException("Calendar ID is required");
        }
        if (wd.getDayOfWeek() < 0 || wd.getDayOfWeek() > 6) {
            throw new MalformedPayloadException("Day of week must be between 0 and 6");
        }

        UUID wdId = (wd.getId() != null && !wd.getId().trim().isEmpty())
                ? UUID.fromString(wd.getId().trim()) : UUID.randomUUID();
        UUID calId = UUID.fromString(wd.getCalendarId().trim());

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO core.calendar_working_days " +
                    "(id, tenant_id, calendar_id, day_of_week, is_working, is_half_day, created_at, updated_at, created_by, updated_by, row_version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, now(), now(), ?, ?, 1) " +
                    "ON CONFLICT (tenant_id, calendar_id, day_of_week) WHERE deleted_at IS NULL " +
                    "DO UPDATE SET is_working = EXCLUDED.is_working, is_half_day = EXCLUDED.is_half_day, updated_at = now(), updated_by = EXCLUDED.updated_by, row_version = core.calendar_working_days.row_version + 1 " +
                    "RETURNING id, tenant_id, calendar_id, day_of_week, is_working, is_half_day, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, wdId);
                ps.setObject(2, context.getTenantId());
                ps.setObject(3, calId);
                ps.setShort(4, wd.getDayOfWeek());
                ps.setBoolean(5, wd.isWorking());
                ps.setBoolean(6, wd.isHalfDay());
                ps.setObject(7, context.getUserId());
                ps.setObject(8, context.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapWorkingDayRow(rs);
                    }
                }
            }
            throw new MalformedPayloadException("Failed to persist working day record");
        });
    }

    @Override
    public List<CalendarWorkingDay> listWorkingDays(UserSecurityContext context, String calendarId) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, calendar_id, day_of_week, is_working, is_half_day, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.calendar_working_days WHERE calendar_id = ? AND tenant_id = ? AND deleted_at IS NULL ORDER BY day_of_week";
            List<CalendarWorkingDay> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, UUID.fromString(calendarId.trim()));
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapWorkingDayRow(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public void deleteWorkingDay(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return;

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE core.calendar_working_days SET deleted_at = now(), updated_at = now(), updated_by = ? " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getUserId());
                ps.setObject(2, UUID.fromString(id.trim()));
                ps.setObject(3, context.getTenantId());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public CalendarEvent createEvent(UserSecurityContext context, CalendarEvent event) {
        checkContext(context);
        if (event == null) throw new MalformedPayloadException("Event payload cannot be null");
        if (event.getCalendarId() == null || event.getCalendarId().trim().isEmpty()) {
            throw new MalformedPayloadException("Calendar ID is required");
        }
        if (event.getTitle() == null || event.getTitle().trim().isEmpty()) {
            throw new MalformedPayloadException("Title is required");
        }
        if (event.getEndDate() < event.getStartDate()) {
            throw new MalformedPayloadException("end_date must be >= start_date");
        }
        String type = event.getEventType() != null ? event.getEventType().trim().toUpperCase(Locale.ROOT) : "EVENT";
        if (!VALID_EVENT_TYPES.contains(type)) {
            throw new MalformedPayloadException("Invalid event type: " + type);
        }

        UUID eventId = (event.getId() != null && !event.getId().trim().isEmpty())
                ? UUID.fromString(event.getId().trim()) : UUID.randomUUID();
        UUID calId = UUID.fromString(event.getCalendarId().trim());

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO core.calendar_events " +
                    "(id, tenant_id, calendar_id, event_type, title, description, start_date, end_date, is_holiday, applies_to, created_at, updated_at, created_by, updated_by, row_version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now(), now(), ?, ?, 1) " +
                    "RETURNING id, tenant_id, calendar_id, event_type, title, description, start_date, end_date, is_holiday, applies_to, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, eventId);
                ps.setObject(2, context.getTenantId());
                ps.setObject(3, calId);
                ps.setString(4, type);
                ps.setString(5, event.getTitle().trim());
                ps.setString(6, event.getDescription());
                ps.setDate(7, new java.sql.Date(event.getStartDate()));
                ps.setDate(8, new java.sql.Date(event.getEndDate()));
                ps.setBoolean(9, event.isHoliday());
                ps.setString(10, event.getAppliesTo() != null ? event.getAppliesTo() : "{}");
                ps.setObject(11, context.getUserId());
                ps.setObject(12, context.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapEventRow(rs);
                    }
                }
            }
            throw new MalformedPayloadException("Failed to persist calendar event record");
        });
    }

    @Override
    public Optional<CalendarEvent> findEventById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, calendar_id, event_type, title, description, start_date, end_date, is_holiday, applies_to, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.calendar_events WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, UUID.fromString(id.trim()));
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapEventRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<CalendarEvent> listEvents(UserSecurityContext context, String calendarId) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, calendar_id, event_type, title, description, start_date, end_date, is_holiday, applies_to, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.calendar_events WHERE calendar_id = ? AND tenant_id = ? AND deleted_at IS NULL ORDER BY start_date";
            List<CalendarEvent> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, UUID.fromString(calendarId.trim()));
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapEventRow(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public CalendarEvent updateEvent(UserSecurityContext context, CalendarEvent event) {
        checkContext(context);
        if (event == null || event.getId() == null) throw new MalformedPayloadException("Event ID is required");

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE core.calendar_events SET " +
                    "title = COALESCE(?, title), " +
                    "event_type = COALESCE(?, event_type), " +
                    "description = COALESCE(?, description), " +
                    "start_date = COALESCE(?, start_date), " +
                    "end_date = COALESCE(?, end_date), " +
                    "is_holiday = COALESCE(?, is_holiday), " +
                    "applies_to = COALESCE(?::jsonb, applies_to), " +
                    "updated_at = now(), updated_by = ?, row_version = row_version + 1 " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL " +
                    "RETURNING id, tenant_id, calendar_id, event_type, title, description, start_date, end_date, is_holiday, applies_to, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, event.getTitle());
                ps.setString(2, event.getEventType());
                ps.setString(3, event.getDescription());
                if (event.getStartDate() > 0) {
                    ps.setDate(4, new java.sql.Date(event.getStartDate()));
                } else {
                    ps.setNull(4, Types.DATE);
                }
                if (event.getEndDate() > 0) {
                    ps.setDate(5, new java.sql.Date(event.getEndDate()));
                } else {
                    ps.setNull(5, Types.DATE);
                }
                ps.setBoolean(6, event.isHoliday());
                ps.setString(7, event.getAppliesTo());
                ps.setObject(8, context.getUserId());
                ps.setObject(9, UUID.fromString(event.getId().trim()));
                ps.setObject(10, context.getTenantId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapEventRow(rs);
                    }
                }
            }
            throw new ResourceNotFoundException("CalendarEvent", event.getId());
        });
    }

    @Override
    public void deleteEvent(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return;

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE core.calendar_events SET deleted_at = now(), updated_at = now(), updated_by = ? " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getUserId());
                ps.setObject(2, UUID.fromString(id.trim()));
                ps.setObject(3, context.getTenantId());
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new ResourceNotFoundException("CalendarEvent", id);
                }
            }
            return null;
        });
    }

    private Calendar mapCalendarRow(ResultSet rs) throws SQLException {
        Calendar c = new Calendar();
        c.setId(rs.getString("id"));
        c.setTenantId(rs.getString("tenant_id"));
        c.setCollegeId(rs.getString("college_id"));
        c.setAcademicYearId(rs.getString("academic_year_id"));
        c.setCode(rs.getString("code"));
        c.setName(rs.getString("name"));
        c.setCalendarType(rs.getString("calendar_type"));
        c.setStatus(rs.getString("status"));
        Timestamp pubTs = rs.getTimestamp("published_at");
        c.setPublishedAt(pubTs != null ? pubTs.getTime() : null);
        c.setWeekStartDay(rs.getShort("week_start_day"));
        Timestamp createdTs = rs.getTimestamp("created_at");
        c.setCreatedAt(createdTs != null ? createdTs.getTime() : 0L);
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        c.setUpdatedAt(updatedTs != null ? updatedTs.getTime() : 0L);
        c.setCreatedBy(rs.getString("created_by"));
        c.setUpdatedBy(rs.getString("updated_by"));
        c.setRowVersion(rs.getInt("row_version"));
        Timestamp delTs = rs.getTimestamp("deleted_at");
        c.setDeletedAt(delTs != null ? delTs.getTime() : null);
        return c;
    }

    private CalendarWorkingDay mapWorkingDayRow(ResultSet rs) throws SQLException {
        CalendarWorkingDay wd = new CalendarWorkingDay();
        wd.setId(rs.getString("id"));
        wd.setTenantId(rs.getString("tenant_id"));
        wd.setCalendarId(rs.getString("calendar_id"));
        wd.setDayOfWeek(rs.getShort("day_of_week"));
        wd.setWorking(rs.getBoolean("is_working"));
        wd.setHalfDay(rs.getBoolean("is_half_day"));
        Timestamp createdTs = rs.getTimestamp("created_at");
        wd.setCreatedAt(createdTs != null ? createdTs.getTime() : 0L);
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        wd.setUpdatedAt(updatedTs != null ? updatedTs.getTime() : 0L);
        wd.setCreatedBy(rs.getString("created_by"));
        wd.setUpdatedBy(rs.getString("updated_by"));
        wd.setRowVersion(rs.getInt("row_version"));
        Timestamp delTs = rs.getTimestamp("deleted_at");
        wd.setDeletedAt(delTs != null ? delTs.getTime() : null);
        return wd;
    }

    private CalendarEvent mapEventRow(ResultSet rs) throws SQLException {
        CalendarEvent e = new CalendarEvent();
        e.setId(rs.getString("id"));
        e.setTenantId(rs.getString("tenant_id"));
        e.setCalendarId(rs.getString("calendar_id"));
        e.setEventType(rs.getString("event_type"));
        e.setTitle(rs.getString("title"));
        e.setDescription(rs.getString("description"));
        java.sql.Date sDate = rs.getDate("start_date");
        e.setStartDate(sDate != null ? sDate.getTime() : 0L);
        java.sql.Date eDate = rs.getDate("end_date");
        e.setEndDate(eDate != null ? eDate.getTime() : 0L);
        e.setHoliday(rs.getBoolean("is_holiday"));
        e.setAppliesTo(rs.getString("applies_to"));
        Timestamp createdTs = rs.getTimestamp("created_at");
        e.setCreatedAt(createdTs != null ? createdTs.getTime() : 0L);
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        e.setUpdatedAt(updatedTs != null ? updatedTs.getTime() : 0L);
        e.setCreatedBy(rs.getString("created_by"));
        e.setUpdatedBy(rs.getString("updated_by"));
        e.setRowVersion(rs.getInt("row_version"));
        Timestamp delTs = rs.getTimestamp("deleted_at");
        e.setDeletedAt(delTs != null ? delTs.getTime() : null);
        return e;
    }
}
