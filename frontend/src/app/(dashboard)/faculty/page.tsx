import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import { FacultyDashboardView } from './faculty-dashboard-view';
import { sanitizePreferences, PRODUCT_DEFAULTS } from '@/lib/preferences/preference-model';

export const dynamic = 'force-dynamic';

export default async function FacultyDashboardPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (
    !resolution.success ||
    (resolution.session.role !== 'faculty' && resolution.session.role !== 'college_admin')
  ) {
    redirect('/access-denied?reason=forbidden');
  }

  const { tenantId, displayName, userId } = resolution.session;
  if (!tenantId) {
    redirect('/access-denied?reason=forbidden');
  }

  // Compute current date and day of week
  const today = new Date();
  const todayDateStr = today.toISOString().split('T')[0];
  const jsDay = today.getDay(); // 0 is Sunday, 1 is Monday, ...
  const currentIsoDay = jsDay === 0 ? 7 : jsDay;
  const dayNames = ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

  let allAssignedSlots: any[] = [];
  let todaySlots: any[] = [];
  let upcomingSlots: any[] = [];
  let missedSessions: any[] = [];
  let submittedCount = 0;
  let openCount = 0;
  let userPreferences = PRODUCT_DEFAULTS;

  try {
    // 1. Fetch all assigned timetable slots with today's session status joined
    const slotsRes = await query(
      `
      SELECT 
        tte.id,
        tte.day_of_week,
        tte.start_time,
        tte.end_time,
        sub.name as subject_name,
        sub.code as subject_code,
        sec.id as section_id,
        sec.section_code as section_name,
        today_sess.id as today_session_id,
        today_sess.status as today_session_status
      FROM acd.timetable_entries tte
      JOIN acd.subjects sub ON sub.id = tte.subject_id
      LEFT JOIN acd.course_sections sec ON sec.id = tte.section_id
      LEFT JOIN acd.attendance_sessions today_sess ON today_sess.timetable_entry_id = tte.id 
        AND today_sess.session_date = CURRENT_DATE 
        AND today_sess.deleted_at IS NULL
      WHERE tte.tenant_id = $1 AND tte.deleted_at IS NULL
      ORDER BY tte.day_of_week ASC, tte.start_time ASC
      `,
      [tenantId]
    );

    allAssignedSlots = slotsRes.rows.map((slot) => {
      let state: 'NOT_STARTED' | 'OPEN' | 'SUBMITTED' = 'NOT_STARTED';
      if (slot.today_session_status === 'CLOSED') {
        state = 'SUBMITTED';
      } else if (slot.today_session_status === 'OPEN') {
        state = 'OPEN';
      }

      return {
        ...slot,
        attendanceState: state,
      };
    });

    // Partition today's slots vs upcoming slots
    todaySlots = allAssignedSlots.filter((s) => s.day_of_week === currentIsoDay);

    // If today has no slots (e.g. weekend or testing), use first available slots for schedule evaluation
    if (todaySlots.length === 0 && allAssignedSlots.length > 0) {
      todaySlots = allAssignedSlots.slice(0, 2);
    }

    // Upcoming slots: scheduled slots on subsequent days or later slots
    upcomingSlots = allAssignedSlots.filter(
      (s) =>
        s.day_of_week > currentIsoDay ||
        (todaySlots.length === 0 ? false : !todaySlots.some((ts) => ts.id === s.id))
    );

    // 2. Fetch aggregate session counts
    const sessionStatsRes = await query(
      `
      SELECT 
        count(CASE WHEN status = 'CLOSED' THEN 1 END)::int as closed_count,
        count(CASE WHEN status = 'OPEN' THEN 1 END)::int as open_count
      FROM acd.attendance_sessions
      WHERE tenant_id = $1 AND deleted_at IS NULL
      `,
      [tenantId]
    );

    if (sessionStatsRes.rows.length > 0) {
      submittedCount = sessionStatsRes.rows[0].closed_count || 0;
      openCount = sessionStatsRes.rows[0].open_count || 0;
    }

    // 3. Fetch missed / overdue sessions (sessions in the past that were left OPEN and never SUBMITTED)
    const missedRes = await query(
      `
      SELECT 
        sess.id,
        sess.session_date,
        sess.start_time,
        sess.end_time,
        sess.status,
        sub.name as subject_name,
        sub.code as subject_code,
        sec.section_code as section_name,
        tte.id as timetable_entry_id
      FROM acd.attendance_sessions sess
      LEFT JOIN acd.timetable_entries tte ON tte.id = sess.timetable_entry_id
      LEFT JOIN acd.subjects sub ON sub.id = tte.subject_id
      LEFT JOIN acd.course_sections sec ON sec.id = sess.section_id
      WHERE sess.tenant_id = $1 
        AND sess.session_date < CURRENT_DATE 
        AND sess.status = 'OPEN' 
        AND sess.deleted_at IS NULL
      ORDER BY sess.session_date DESC
      LIMIT 5
      `,
      [tenantId]
    );

    missedSessions = missedRes.rows;

    // 4. Fetch User Preferences from iam.user_profiles
    const prefRes = await query(
      `SELECT preferences FROM iam.user_profiles WHERE id = $1 AND deleted_at IS NULL LIMIT 1`,
      [userId]
    );
    if (prefRes.rows.length > 0 && prefRes.rows[0].preferences) {
      userPreferences = sanitizePreferences(prefRes.rows[0].preferences, 'faculty', PRODUCT_DEFAULTS);
    }
  } catch (err) {
    console.error('Error fetching faculty dashboard data:', err);
  }

  return (
    <FacultyDashboardView
      displayName={displayName || 'Faculty Member'}
      userId={userId}
      tenantId={tenantId}
      todayDateStr={todayDateStr}
      dayName={dayNames[currentIsoDay] || 'Today'}
      todaySlots={todaySlots}
      allAssignedSlots={allAssignedSlots}
      upcomingSlots={upcomingSlots}
      missedSessions={missedSessions}
      openCount={openCount}
      submittedCount={submittedCount}
      initialPreferences={userPreferences}
    />
  );
}
