import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { FacultyAttendanceView } from './faculty-attendance-view';
import { query } from '@/lib/db';

export const dynamic = 'force-dynamic';

export default async function FacultyAttendancePage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (
    !resolution.success ||
    (resolution.session.role !== 'faculty' && resolution.session.role !== 'college_admin')
  ) {
    redirect('/access-denied?reason=forbidden');
  }

  const tenantId = resolution.session.tenantId || '11111111-1111-1111-1111-111111111111';
  const userId = resolution.session.userId;
  let slots: any[] = [];
  let students: any[] = [];
  let employeeId: string | undefined;

  try {
    // 1. Identify employee record for this user
    const empRes = await query(`
      SELECT id, employee_no 
      FROM hrm.employees 
      WHERE tenant_id = $1 AND person_id = $2 AND deleted_at IS NULL
      LIMIT 1
    `, [tenantId, userId]);

    if (empRes.rows.length > 0) {
      employeeId = empRes.rows[0].id;
    } else {
      // Fallback to first faculty if demo mode
      const anyEmpRes = await query(`
        SELECT id, employee_no 
        FROM hrm.employees 
        WHERE tenant_id = $1 AND deleted_at IS NULL 
        LIMIT 1
      `, [tenantId]);
      if (anyEmpRes.rows.length > 0) {
        employeeId = anyEmpRes.rows[0].id;
      }
    }

    // 2. Fetch timetable slots
    const entryRes = await query(`
      SELECT 
        tte.id,
        tte.day_of_week,
        tte.start_time,
        tte.end_time,
        tte.section_id,
        tte.employee_id,
        sub.name as subject_name,
        sub.code as subject_code,
        sec.section_code
      FROM acd.timetable_entries tte
      LEFT JOIN acd.subjects sub ON sub.id = tte.subject_id
      LEFT JOIN acd.course_sections sec ON sec.id = tte.section_id
      WHERE tte.tenant_id = $1 AND tte.deleted_at IS NULL
    `, [tenantId]);

    slots = entryRes.rows.map((e: any) => ({
      id: e.id,
      day_of_week: e.day_of_week,
      start_time: e.start_time,
      end_time: e.end_time,
      subject_name: e.subject_name || 'Academic Class',
      subject_code: e.subject_code || 'SUBJ',
      section_id: e.section_id,
      section_code: e.section_code || 'A',
      employee_id: e.employee_id || employeeId,
    }));

    // 3. Query students in tenant
    const stuRes = await query(`
      SELECT 
        s.id,
        s.enrollment_no,
        p.display_name,
        p.primary_email
      FROM stu.students s
      LEFT JOIN core.persons p ON p.id = s.person_id
      WHERE s.tenant_id = $1 AND s.deleted_at IS NULL
      ORDER BY s.enrollment_no ASC
    `, [tenantId]);

    students = stuRes.rows.map((s: any) => ({
      id: s.id,
      enrollment_no: s.enrollment_no,
      display_name: s.display_name || s.enrollment_no,
      primary_email: s.primary_email || '',
    }));
  } catch (err) {
    console.error('Error fetching faculty timetable slots or students:', err);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
          Faculty Attendance Capture
        </h1>
        <p className="text-xs text-[#6B7688] mt-1">
          Select your class period, take student attendance, and submit finalized sessions to the institutional audit trail.
        </p>
      </div>

      <FacultyAttendanceView slots={slots} students={students} employeeId={employeeId} />
    </div>
  );
}
