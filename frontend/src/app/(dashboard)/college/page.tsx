import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import { CollegeDashboardView } from './college-dashboard-view';
import { sanitizePreferences, PRODUCT_DEFAULTS } from '@/lib/preferences/preference-model';

export const dynamic = 'force-dynamic';

export default async function CollegeAdminDashboardPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'college_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  const { tenantId, tenantName, userId } = resolution.session;
  const effectiveTenantId = tenantId || '11111111-1111-1111-1111-111111111111';

  // Real database counts from Supabase PostgreSQL staging
  let studentCount = 0;
  let facultyCount = 0;
  let sessionCount = 0;
  let totalRecords = 0;
  let presentRecords = 0;
  let shortageCount = 0;
  let overallPercentage = 0;

  let trend: any[] = [];
  let batchBreakdown: any[] = [];
  let shortageStudents: any[] = [];
  let missedSessions: any[] = [];
  let recentSessions: any[] = [];
  let userPreferences = PRODUCT_DEFAULTS;

  try {
    // 1. Overall Aggregate KPIs
    const statsRes = await query(
      `
      SELECT 
        (SELECT count(*)::int FROM stu.students WHERE tenant_id = $1 AND deleted_at IS NULL) as student_count,
        (SELECT count(*)::int FROM hrm.employees WHERE tenant_id = $1 AND deleted_at IS NULL) as faculty_count,
        (SELECT count(*)::int FROM acd.attendance_sessions WHERE tenant_id = $1 AND deleted_at IS NULL) as session_count,
        (SELECT count(*)::int FROM acd.attendance_records WHERE tenant_id = $1 AND deleted_at IS NULL) as total_records,
        (SELECT count(*)::int FROM acd.attendance_records WHERE tenant_id = $1 AND status = 'PRESENT' AND deleted_at IS NULL) as present_records
      `,
      [effectiveTenantId]
    );

    if (statsRes.rows.length > 0) {
      const row = statsRes.rows[0];
      studentCount = Number(row.student_count) || 0;
      facultyCount = Number(row.faculty_count) || 0;
      sessionCount = Number(row.session_count) || 0;
      totalRecords = Number(row.total_records) || 0;
      presentRecords = Number(row.present_records) || 0;
      overallPercentage =
        totalRecords > 0 ? Math.round((presentRecords / totalRecords) * 1000) / 10 : 0;
    }

    // 2. Shortage Students (< 75% statutory threshold per ACD-06 / DEC-14)
    const shortageRes = await query(
      `
      SELECT 
        s.id as student_id,
        s.enrollment_no,
        p.display_name,
        count(r.id)::int as total_sessions,
        count(CASE WHEN r.status = 'PRESENT' THEN 1 END)::int as attended,
        ROUND((count(CASE WHEN r.status = 'PRESENT' THEN 1 END)::numeric / NULLIF(count(r.id), 0)) * 100, 1) as percentage
      FROM stu.students s
      JOIN core.persons p ON p.id = s.person_id
      JOIN acd.attendance_records r ON r.student_id = s.id AND r.deleted_at IS NULL
      WHERE s.tenant_id = $1 AND s.deleted_at IS NULL
      GROUP BY s.id, s.enrollment_no, p.display_name
      HAVING (count(CASE WHEN r.status = 'PRESENT' THEN 1 END)::numeric / NULLIF(count(r.id), 0)) < 0.75
      ORDER BY percentage ASC;
      `,
      [effectiveTenantId]
    );

    shortageStudents = shortageRes.rows.map((r: any) => ({
      studentId: r.student_id,
      enrollmentNo: r.enrollment_no,
      displayName: r.display_name,
      totalSessions: Number(r.total_sessions) || 0,
      attended: Number(r.attended) || 0,
      percentage: Number(r.percentage) || 0,
    }));
    shortageCount = shortageStudents.length;

    // 3. 14-Day Attendance Trend
    const trendRes = await query(
      `
      SELECT 
        TO_CHAR(s.session_date, 'YYYY-MM-DD') as date_str,
        TO_CHAR(s.session_date, 'DD Mon') as display_date,
        count(r.id)::int as total_records,
        count(CASE WHEN r.status = 'PRESENT' THEN 1 END)::int as present_records,
        ROUND((count(CASE WHEN r.status = 'PRESENT' THEN 1 END)::numeric / NULLIF(count(r.id), 0)) * 100, 1) as percentage
      FROM acd.attendance_sessions s
      JOIN acd.attendance_records r ON r.session_id = s.id AND r.deleted_at IS NULL
      WHERE s.tenant_id = $1 AND s.deleted_at IS NULL
      GROUP BY s.session_date
      ORDER BY s.session_date ASC
      LIMIT 14;
      `,
      [effectiveTenantId]
    );

    trend = trendRes.rows.map((r: any) => ({
      dateStr: r.date_str,
      displayDate: r.display_date,
      totalRecords: Number(r.total_records) || 0,
      presentRecords: Number(r.present_records) || 0,
      percentage: Number(r.percentage) || 0,
    }));

    // 4. Batch & Subject Distribution
    const batchRes = await query(
      `
      SELECT 
        sec.section_code,
        sub.code as subject_code,
        sub.name as subject_name,
        count(DISTINCT s.id)::int as sessions_held,
        count(r.id)::int as total_records,
        count(CASE WHEN r.status = 'PRESENT' THEN 1 END)::int as present_records,
        ROUND((count(CASE WHEN r.status = 'PRESENT' THEN 1 END)::numeric / NULLIF(count(r.id), 0)) * 100, 1) as percentage
      FROM acd.attendance_sessions s
      JOIN acd.timetable_entries tte ON tte.id = s.timetable_entry_id
      JOIN acd.subjects sub ON sub.id = tte.subject_id
      JOIN acd.course_sections sec ON sec.id = tte.section_id
      LEFT JOIN acd.attendance_records r ON r.session_id = s.id AND r.deleted_at IS NULL
      WHERE s.tenant_id = $1 AND s.deleted_at IS NULL
      GROUP BY sec.section_code, sub.code, sub.name
      ORDER BY sec.section_code, sub.code;
      `,
      [effectiveTenantId]
    );

    batchBreakdown = batchRes.rows.map((r: any) => ({
      sectionCode: r.section_code,
      subjectCode: r.subject_code,
      subjectName: r.subject_name,
      sessionsHeld: Number(r.sessions_held) || 0,
      totalRecords: Number(r.total_records) || 0,
      presentRecords: Number(r.present_records) || 0,
      percentage: Number(r.percentage) || 0,
    }));

    // 5. Missed & Pending Sessions (Sessions in OPEN status)
    const missedRes = await query(
      `
      SELECT 
        s.id,
        TO_CHAR(s.session_date, 'YYYY-MM-DD') as session_date,
        s.start_time,
        s.end_time,
        s.status,
        sub.name as subject_name,
        sub.code as subject_code,
        sec.section_code,
        p.display_name as faculty_name
      FROM acd.attendance_sessions s
      JOIN acd.timetable_entries tte ON tte.id = s.timetable_entry_id
      JOIN acd.subjects sub ON sub.id = tte.subject_id
      JOIN acd.course_sections sec ON sec.id = tte.section_id
      JOIN hrm.employees emp ON emp.id = s.employee_id
      JOIN core.persons p ON p.id = emp.person_id
      WHERE s.tenant_id = $1 AND s.deleted_at IS NULL AND s.status = 'OPEN'
      ORDER BY s.session_date ASC;
      `,
      [effectiveTenantId]
    );

    missedSessions = missedRes.rows.map((r: any) => ({
      id: r.id,
      sessionDate: r.session_date,
      startTime: String(r.start_time || '09:00:00'),
      endTime: String(r.end_time || '10:00:00'),
      status: r.status,
      subjectName: r.subject_name,
      subjectCode: r.subject_code,
      sectionCode: r.section_code,
      facultyName: r.faculty_name,
    }));

    // 6. Recent Sessions
    const recentRes = await query(
      `
      SELECT 
        s.id,
        TO_CHAR(s.session_date, 'YYYY-MM-DD') as session_date,
        s.start_time,
        s.end_time,
        s.status,
        sub.name as subject_name,
        sub.code as subject_code,
        sec.section_code,
        p.display_name as faculty_name,
        (SELECT count(*) FROM acd.attendance_records r WHERE r.session_id = s.id AND r.deleted_at IS NULL)::int as total_records,
        (SELECT count(*) FROM acd.attendance_records r WHERE r.session_id = s.id AND r.status = 'PRESENT' AND r.deleted_at IS NULL)::int as present_count
      FROM acd.attendance_sessions s
      JOIN acd.timetable_entries tte ON tte.id = s.timetable_entry_id
      JOIN acd.subjects sub ON sub.id = tte.subject_id
      JOIN acd.course_sections sec ON sec.id = tte.section_id
      JOIN hrm.employees emp ON emp.id = s.employee_id
      JOIN core.persons p ON p.id = emp.person_id
      WHERE s.tenant_id = $1 AND s.deleted_at IS NULL
      ORDER BY s.session_date DESC
      LIMIT 6;
      `,
      [effectiveTenantId]
    );

    recentSessions = recentRes.rows.map((r: any) => ({
      id: r.id,
      sessionDate: r.session_date,
      startTime: String(r.start_time || '09:00:00'),
      endTime: String(r.end_time || '10:00:00'),
      status: r.status,
      subjectName: r.subject_name,
      subjectCode: r.subject_code,
      sectionCode: r.section_code,
      facultyName: r.faculty_name,
      totalRecords: Number(r.total_records) || 0,
      presentCount: Number(r.present_count) || 0,
    }));

    // 7. Load User Preferences from iam.user_profiles
    const prefRes = await query(
      `SELECT preferences FROM iam.user_profiles WHERE id = $1 AND deleted_at IS NULL LIMIT 1`,
      [userId]
    );
    if (prefRes.rows.length > 0 && prefRes.rows[0].preferences) {
      userPreferences = sanitizePreferences(prefRes.rows[0].preferences, 'college_admin', PRODUCT_DEFAULTS);
    }
  } catch (err) {
    console.error('College admin dashboard query error:', err);
  }

  return (
    <CollegeDashboardView
      stats={{
        studentCount,
        facultyCount,
        sessionCount,
        totalRecords,
        presentRecords,
        overallAttendancePercentage: overallPercentage,
        shortageCount,
      }}
      trend={trend}
      batchBreakdown={batchBreakdown}
      shortageStudents={shortageStudents}
      missedSessions={missedSessions}
      recentSessions={recentSessions}
      tenantName={tenantName || 'Institutional Workspace'}
      userId={userId}
      tenantId={effectiveTenantId}
      initialPreferences={userPreferences}
    />
  );
}
