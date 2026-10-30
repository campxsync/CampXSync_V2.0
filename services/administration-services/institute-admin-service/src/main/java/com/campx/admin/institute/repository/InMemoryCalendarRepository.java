package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.Calendar;
import com.campx.admin.institute.model.InstituteModels.CalendarEvent;
import com.campx.admin.institute.model.InstituteModels.CalendarWorkingDay;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory thread-safe fallback repository for {@link CalendarRepository}.
 */
public class InMemoryCalendarRepository implements CalendarRepository {

    private final Map<String, Calendar> calendars = new ConcurrentHashMap<>();
    private final Map<String, CalendarWorkingDay> workingDays = new ConcurrentHashMap<>();
    private final Map<String, CalendarEvent> events = new ConcurrentHashMap<>();

    private static final Set<String> VALID_TYPES = new HashSet<>(Arrays.asList("ACADEMIC", "HOLIDAY", "EXAM", "ADMINISTRATIVE"));
    private static final Set<String> VALID_STATUSES = new HashSet<>(Arrays.asList("DRAFT", "PUBLISHED", "ARCHIVED"));
    private static final Set<String> VALID_EVENT_TYPES = new HashSet<>(Arrays.asList(
            "HOLIDAY", "TERM_START", "TERM_END", "EXAM", "COMMENCEMENT", "EVENT", "DEADLINE", "OTHER"));

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

        String tenantStr = context.getTenantId().toString();
        String code = cal.getCode().trim().toUpperCase(Locale.ROOT);

        for (Calendar c : calendars.values()) {
            if (c.getDeletedAt() == null && tenantStr.equals(c.getTenantId()) && code.equalsIgnoreCase(c.getCode())) {
                throw new MalformedPayloadException("Calendar code already exists: " + code);
            }
        }

        String id = cal.getId();
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        Calendar copy = new Calendar();
        copy.setId(id);
        copy.setTenantId(tenantStr);
        copy.setCollegeId(cal.getCollegeId());
        copy.setAcademicYearId(cal.getAcademicYearId());
        copy.setCode(code);
        copy.setName(cal.getName().trim());
        copy.setCalendarType(type);
        copy.setStatus(status);
        copy.setPublishedAt(cal.getPublishedAt());
        copy.setWeekStartDay(cal.getWeekStartDay());
        copy.setCreatedAt(System.currentTimeMillis());
        copy.setUpdatedAt(copy.getCreatedAt());
        copy.setCreatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        copy.setRowVersion(1);

        calendars.put(id, copy);
        return copy;
    }

    @Override
    public Optional<Calendar> findById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();
        String tenantStr = context.getTenantId().toString();
        Calendar c = calendars.get(id);
        if (c != null && tenantStr.equals(c.getTenantId()) && c.getDeletedAt() == null) {
            return Optional.of(c);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Calendar> findByCode(UserSecurityContext context, String code) {
        checkContext(context);
        if (code == null || code.trim().isEmpty()) return Optional.empty();
        String tenantStr = context.getTenantId().toString();
        for (Calendar c : calendars.values()) {
            if (c.getDeletedAt() == null && tenantStr.equals(c.getTenantId()) && code.equalsIgnoreCase(c.getCode())) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Calendar> listCalendars(UserSecurityContext context) {
        checkContext(context);
        String tenantStr = context.getTenantId().toString();
        return calendars.values().stream()
                .filter(c -> c.getDeletedAt() == null && tenantStr.equals(c.getTenantId()))
                .sorted(Comparator.comparing(Calendar::getName))
                .collect(Collectors.toList());
    }

    @Override
    public List<Calendar> listCalendarsByCollege(UserSecurityContext context, String collegeId) {
        checkContext(context);
        String tenantStr = context.getTenantId().toString();
        return calendars.values().stream()
                .filter(c -> c.getDeletedAt() == null && tenantStr.equals(c.getTenantId()) && Objects.equals(collegeId, c.getCollegeId()))
                .sorted(Comparator.comparing(Calendar::getName))
                .collect(Collectors.toList());
    }

    @Override
    public Calendar updateCalendar(UserSecurityContext context, Calendar cal) {
        checkContext(context);
        if (cal == null || cal.getId() == null) {
            throw new MalformedPayloadException("Calendar ID is required for update");
        }
        Calendar existing = findById(context, cal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Calendar", cal.getId()));

        if (cal.getName() != null && !cal.getName().trim().isEmpty()) {
            existing.setName(cal.getName().trim());
        }
        if (cal.getCalendarType() != null) {
            String type = cal.getCalendarType().trim().toUpperCase(Locale.ROOT);
            if (!VALID_TYPES.contains(type)) throw new MalformedPayloadException("Invalid calendar type: " + type);
            existing.setCalendarType(type);
        }
        if (cal.getStatus() != null) {
            String status = cal.getStatus().trim().toUpperCase(Locale.ROOT);
            if (!VALID_STATUSES.contains(status)) throw new MalformedPayloadException("Invalid status: " + status);
            existing.setStatus(status);
        }
        if (cal.getPublishedAt() != null) {
            existing.setPublishedAt(cal.getPublishedAt());
        }
        existing.setWeekStartDay(cal.getWeekStartDay());
        existing.setUpdatedAt(System.currentTimeMillis());
        existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        existing.setRowVersion(existing.getRowVersion() + 1);

        return existing;
    }

    @Override
    public void deleteCalendar(UserSecurityContext context, String id) {
        checkContext(context);
        Calendar existing = findById(context, id)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar", id));
        existing.setDeletedAt(System.currentTimeMillis());
        existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
    }

    @Override
    public CalendarWorkingDay saveWorkingDay(UserSecurityContext context, CalendarWorkingDay wd) {
        checkContext(context);
        if (wd == null) throw new MalformedPayloadException("WorkingDay cannot be null");
        if (wd.getCalendarId() == null || wd.getCalendarId().trim().isEmpty()) {
            throw new MalformedPayloadException("Calendar ID is required");
        }
        if (wd.getDayOfWeek() < 0 || wd.getDayOfWeek() > 6) {
            throw new MalformedPayloadException("Day of week must be 0 to 6");
        }
        String tenantStr = context.getTenantId().toString();

        String id = wd.getId();
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        // Check unique per calendar and dayOfWeek
        for (CalendarWorkingDay existing : workingDays.values()) {
            if (existing.getDeletedAt() == null && tenantStr.equals(existing.getTenantId())
                    && wd.getCalendarId().equals(existing.getCalendarId())
                    && wd.getDayOfWeek() == existing.getDayOfWeek()
                    && !id.equals(existing.getId())) {
                throw new MalformedPayloadException("Day of week already configured for calendar: " + wd.getDayOfWeek());
            }
        }

        CalendarWorkingDay copy = new CalendarWorkingDay();
        copy.setId(id);
        copy.setTenantId(tenantStr);
        copy.setCalendarId(wd.getCalendarId());
        copy.setDayOfWeek(wd.getDayOfWeek());
        copy.setWorking(wd.isWorking());
        copy.setHalfDay(wd.isHalfDay());
        copy.setCreatedAt(System.currentTimeMillis());
        copy.setUpdatedAt(copy.getCreatedAt());
        copy.setCreatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        copy.setRowVersion(1);

        workingDays.put(id, copy);
        return copy;
    }

    @Override
    public List<CalendarWorkingDay> listWorkingDays(UserSecurityContext context, String calendarId) {
        checkContext(context);
        String tenantStr = context.getTenantId().toString();
        return workingDays.values().stream()
                .filter(w -> w.getDeletedAt() == null && tenantStr.equals(w.getTenantId()) && Objects.equals(calendarId, w.getCalendarId()))
                .sorted(Comparator.comparingInt(CalendarWorkingDay::getDayOfWeek))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteWorkingDay(UserSecurityContext context, String id) {
        checkContext(context);
        CalendarWorkingDay wd = workingDays.get(id);
        if (wd != null && context.getTenantId().toString().equals(wd.getTenantId())) {
            wd.setDeletedAt(System.currentTimeMillis());
            wd.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        }
    }

    @Override
    public CalendarEvent createEvent(UserSecurityContext context, CalendarEvent event) {
        checkContext(context);
        if (event == null) throw new MalformedPayloadException("Event cannot be null");
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

        String tenantStr = context.getTenantId().toString();
        String id = event.getId();
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        CalendarEvent copy = new CalendarEvent();
        copy.setId(id);
        copy.setTenantId(tenantStr);
        copy.setCalendarId(event.getCalendarId());
        copy.setEventType(type);
        copy.setTitle(event.getTitle().trim());
        copy.setDescription(event.getDescription());
        copy.setStartDate(event.getStartDate());
        copy.setEndDate(event.getEndDate());
        copy.setHoliday(event.isHoliday());
        copy.setAppliesTo(event.getAppliesTo() != null ? event.getAppliesTo() : "{}");
        copy.setCreatedAt(System.currentTimeMillis());
        copy.setUpdatedAt(copy.getCreatedAt());
        copy.setCreatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        copy.setRowVersion(1);

        events.put(id, copy);
        return copy;
    }

    @Override
    public Optional<CalendarEvent> findEventById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();
        String tenantStr = context.getTenantId().toString();
        CalendarEvent e = events.get(id);
        if (e != null && tenantStr.equals(e.getTenantId()) && e.getDeletedAt() == null) {
            return Optional.of(e);
        }
        return Optional.empty();
    }

    @Override
    public List<CalendarEvent> listEvents(UserSecurityContext context, String calendarId) {
        checkContext(context);
        String tenantStr = context.getTenantId().toString();
        return events.values().stream()
                .filter(e -> e.getDeletedAt() == null && tenantStr.equals(e.getTenantId()) && Objects.equals(calendarId, e.getCalendarId()))
                .sorted(Comparator.comparingLong(CalendarEvent::getStartDate))
                .collect(Collectors.toList());
    }

    @Override
    public CalendarEvent updateEvent(UserSecurityContext context, CalendarEvent event) {
        checkContext(context);
        if (event == null || event.getId() == null) throw new MalformedPayloadException("Event ID is required");
        CalendarEvent existing = findEventById(context, event.getId())
                .orElseThrow(() -> new ResourceNotFoundException("CalendarEvent", event.getId()));

        if (event.getTitle() != null && !event.getTitle().trim().isEmpty()) {
            existing.setTitle(event.getTitle().trim());
        }
        if (event.getEventType() != null) {
            String type = event.getEventType().trim().toUpperCase(Locale.ROOT);
            if (!VALID_EVENT_TYPES.contains(type)) throw new MalformedPayloadException("Invalid event type: " + type);
            existing.setEventType(type);
        }
        if (event.getDescription() != null) {
            existing.setDescription(event.getDescription());
        }
        if (event.getStartDate() > 0 && event.getEndDate() > 0) {
            if (event.getEndDate() < event.getStartDate()) throw new MalformedPayloadException("end_date must be >= start_date");
            existing.setStartDate(event.getStartDate());
            existing.setEndDate(event.getEndDate());
        }
        existing.setHoliday(event.isHoliday());
        if (event.getAppliesTo() != null) {
            existing.setAppliesTo(event.getAppliesTo());
        }
        existing.setUpdatedAt(System.currentTimeMillis());
        existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        existing.setRowVersion(existing.getRowVersion() + 1);

        return existing;
    }

    @Override
    public void deleteEvent(UserSecurityContext context, String id) {
        checkContext(context);
        CalendarEvent existing = findEventById(context, id)
                .orElseThrow(() -> new ResourceNotFoundException("CalendarEvent", id));
        existing.setDeletedAt(System.currentTimeMillis());
        existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
    }
}
