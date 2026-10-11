package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.Calendar;
import com.campx.admin.institute.model.InstituteModels.CalendarEvent;
import com.campx.admin.institute.model.InstituteModels.CalendarWorkingDay;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing calendars, events, and working days (Item 23:
 * {@code core.calendars}, {@code core.calendar_events}, {@code core.calendar_working_days}).
 */
public interface CalendarRepository {

    Calendar createCalendar(UserSecurityContext context, Calendar calendar);

    Optional<Calendar> findById(UserSecurityContext context, String id);

    Optional<Calendar> findByCode(UserSecurityContext context, String code);

    List<Calendar> listCalendars(UserSecurityContext context);

    List<Calendar> listCalendarsByCollege(UserSecurityContext context, String collegeId);

    Calendar updateCalendar(UserSecurityContext context, Calendar calendar);

    void deleteCalendar(UserSecurityContext context, String id);

    CalendarWorkingDay saveWorkingDay(UserSecurityContext context, CalendarWorkingDay workingDay);

    List<CalendarWorkingDay> listWorkingDays(UserSecurityContext context, String calendarId);

    void deleteWorkingDay(UserSecurityContext context, String id);

    CalendarEvent createEvent(UserSecurityContext context, CalendarEvent event);

    Optional<CalendarEvent> findEventById(UserSecurityContext context, String id);

    List<CalendarEvent> listEvents(UserSecurityContext context, String calendarId);

    CalendarEvent updateEvent(UserSecurityContext context, CalendarEvent event);

    void deleteEvent(UserSecurityContext context, String id);
}
