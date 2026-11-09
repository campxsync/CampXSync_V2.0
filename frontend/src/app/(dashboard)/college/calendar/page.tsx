import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import {
  AcademicCalendar,
  CalendarManager,
  CollegeOption,
  YearOption,
} from './calendar-manager';

export const dynamic = 'force-dynamic';

export default async function CollegeCalendarPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'college_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  const tenantId = resolution.session.tenantId;
  const assignedCollegeId = resolution.session.assignedCollegeId;
  if (!tenantId) {
    redirect('/access-denied?reason=forbidden');
  }

  let colleges: CollegeOption[] = [];
  let years: YearOption[] = [];
  let calendars: AcademicCalendar[] = [];

  try {
    const collegeRes = await query(
      `SELECT id, name, code
       FROM core.colleges
       WHERE tenant_id = $1 AND deleted_at IS NULL
         AND ($2::uuid IS NULL OR id = $2)
       ORDER BY name`,
      [tenantId, assignedCollegeId]
    );
    colleges = collegeRes.rows;

    const yearRes = await query(
      `SELECT id, code, start_date::text AS start_date, end_date::text AS end_date
       FROM core.academic_years
       WHERE tenant_id = $1 AND deleted_at IS NULL
       ORDER BY start_date DESC`,
      [tenantId]
    );
    years = yearRes.rows.map((row) => ({
      id: row.id,
      code: row.code,
      startDate: row.start_date,
      endDate: row.end_date,
    }));

    const calendarRes = await query(
      `SELECT
         c.id,
         c.code,
         c.name,
         c.calendar_type,
         c.status,
         y.code AS year_code,
         y.start_date::text AS year_start,
         y.end_date::text AS year_end,
         col.name AS college_name
       FROM core.calendars c
       LEFT JOIN core.academic_years y
         ON y.id = c.academic_year_id AND y.tenant_id = c.tenant_id
       LEFT JOIN core.colleges col
         ON col.id = c.college_id AND col.tenant_id = c.tenant_id
       WHERE c.tenant_id = $1 AND c.deleted_at IS NULL
         AND ($2::uuid IS NULL OR c.college_id = $2 OR c.college_id IS NULL)
       ORDER BY c.status, c.code`,
      [tenantId, assignedCollegeId]
    );

    const calendarIds = calendarRes.rows.map((row) => row.id);
    const termsByCalendar = new Map<string, AcademicCalendar['terms']>();
    const eventsByCalendar = new Map<string, AcademicCalendar['events']>();
    const daysByCalendar = new Map<string, AcademicCalendar['workingDays']>();

    if (calendarIds.length > 0) {
      const termRes = await query(
        `SELECT calendar_id, term_no, name, term_type, start_date::text AS start_date, end_date::text AS end_date, status
         FROM acd.calendar_terms
         WHERE tenant_id = $1 AND deleted_at IS NULL AND calendar_id = ANY($2::uuid[])
         ORDER BY term_no`,
        [tenantId, calendarIds]
      );
      for (const row of termRes.rows) {
        const list = termsByCalendar.get(row.calendar_id) || [];
        list.push({
          termNo: row.term_no,
          name: row.name,
          termType: row.term_type,
          startDate: row.start_date,
          endDate: row.end_date,
          status: row.status,
        });
        termsByCalendar.set(row.calendar_id, list);
      }

      const eventRes = await query(
        `SELECT calendar_id, event_type, title, start_date::text AS start_date, end_date::text AS end_date, is_holiday
         FROM core.calendar_events
         WHERE tenant_id = $1 AND deleted_at IS NULL AND calendar_id = ANY($2::uuid[])
         ORDER BY start_date, title`,
        [tenantId, calendarIds]
      );
      for (const row of eventRes.rows) {
        const list = eventsByCalendar.get(row.calendar_id) || [];
        list.push({
          eventType: row.event_type,
          title: row.title,
          startDate: row.start_date,
          endDate: row.end_date,
          isHoliday: row.is_holiday,
        });
        eventsByCalendar.set(row.calendar_id, list);
      }

      const dayRes = await query(
        `SELECT calendar_id, day_of_week, is_working, is_half_day
         FROM core.calendar_working_days
         WHERE tenant_id = $1 AND deleted_at IS NULL AND calendar_id = ANY($2::uuid[])
         ORDER BY day_of_week`,
        [tenantId, calendarIds]
      );
      for (const row of dayRes.rows) {
        const list = daysByCalendar.get(row.calendar_id) || [];
        list.push({
          dayOfWeek: row.day_of_week,
          isWorking: row.is_working,
          isHalfDay: row.is_half_day,
        });
        daysByCalendar.set(row.calendar_id, list);
      }
    }

    calendars = calendarRes.rows.map((row) => ({
      id: row.id,
      code: row.code,
      name: row.name,
      calendarType: row.calendar_type,
      status: row.status,
      yearCode: row.year_code,
      yearStart: row.year_start,
      yearEnd: row.year_end,
      collegeName: row.college_name,
      terms: termsByCalendar.get(row.id) || [],
      events: eventsByCalendar.get(row.id) || [],
      workingDays: daysByCalendar.get(row.id) || [],
    }));
  } catch (err) {
    console.error('Error loading academic calendars:', err);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">Academic Calendar</h1>
        <p className="mt-1 text-xs text-[#6B7688]">
          Set the academic terms, the working week, and the holidays for this college. Publishing fixes the terms.
        </p>
      </div>
      <CalendarManager colleges={colleges} years={years} calendars={calendars} />
    </div>
  );
}
