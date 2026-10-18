import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { AttendanceManager } from './attendance-manager';
import { query } from '@/lib/db';

export const dynamic = 'force-dynamic';

export default async function CollegeAttendancePage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'college_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  const tenantId = resolution.session.tenantId;
  if (!tenantId) {
    redirect('/access-denied?reason=forbidden');
  }
  let sessions: any[] = [];

  try {
    const res = await query(`
      SELECT 
        s.id,
        s.session_date,
        s.start_time,
        s.end_time,
        s.status,
        s.locked_at,
        sub.name as subject_name,
        sub.code as subject_code,
        sec.section_code,
        p.display_name as faculty_name,
        (SELECT count(*) FROM acd.attendance_records r WHERE r.session_id = s.id AND r.deleted_at IS NULL) as total_records,
        (SELECT count(*) FROM acd.attendance_records r WHERE r.session_id = s.id AND r.status = 'PRESENT' AND r.deleted_at IS NULL) as present_count
      FROM acd.attendance_sessions s
      LEFT JOIN acd.timetable_entries tte ON tte.id = s.timetable_entry_id
      LEFT JOIN acd.subjects sub ON sub.id = tte.subject_id
      LEFT JOIN acd.course_sections sec ON sec.id = tte.section_id
      LEFT JOIN hrm.employees emp ON emp.id = s.employee_id
      LEFT JOIN core.persons p ON p.id = emp.person_id
      WHERE s.tenant_id = $1 AND s.deleted_at IS NULL
      ORDER BY s.session_date DESC
    `, [tenantId]);

    sessions = res.rows.map((s: any) => ({
      id: s.id,
      session_date: s.session_date,
      start_time: s.start_time,
      end_time: s.end_time,
      status: s.status,
      locked_at: s.locked_at,
      subject_name: s.subject_name,
      subject_code: s.subject_code,
      section_code: s.section_code,
      faculty_name: s.faculty_name,
      total_records: Number(s.total_records) || 0,
      present_count: Number(s.present_count) || 0,
    }));
  } catch (err) {
    console.error('Error querying attendance sessions:', err);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
          Institutional Attendance Oversight
        </h1>
        <p className="text-xs text-[#6B7688] mt-1">
          Monitor class attendance submissions, track shortage metrics, and inspect audited attendance records.
        </p>
      </div>

      <AttendanceManager sessions={sessions} />
    </div>
  );
}
