import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { resolveEmployeeId } from '@/lib/auth/employee-link';
import { FacultyAttendanceView } from './faculty-attendance-view';
import { query } from '@/lib/db';

export const dynamic = 'force-dynamic';

interface PageProps {
  searchParams?: Promise<{
    slotId?: string;
    sessionId?: string;
  }>;
}

export default async function FacultyAttendancePage(props: PageProps) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (
    !resolution.success ||
    (resolution.session.role !== 'faculty' && resolution.session.role !== 'college_admin')
  ) {
    redirect('/access-denied?reason=forbidden');
  }

  const tenantId = resolution.session.tenantId;
  if (!tenantId) {
    redirect('/access-denied?reason=forbidden');
  }
  const { userId, email, role } = resolution.session;
  const isFaculty = role === 'faculty';
  const searchParams = props.searchParams ? await props.searchParams : {};
  const initialSlotId = searchParams.slotId;
  const initialSessionId = searchParams.sessionId;

  let slots: any[] = [];
  let students: any[] = [];
  let employeeId: string | undefined;

  try {
    if (isFaculty) {
      employeeId = (await resolveEmployeeId(tenantId, userId, email)) ?? undefined;
    }

    if (!isFaculty || employeeId) {
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
          ${isFaculty ? 'AND tte.employee_id = $2' : ''}
        ORDER BY tte.day_of_week ASC, tte.start_time ASC
      `, isFaculty ? [tenantId, employeeId] : [tenantId]);

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

      const stuRes = await query(`
        SELECT DISTINCT
          s.id,
          s.enrollment_no,
          p.display_name,
          p.primary_email,
          sec.id as section_id
        FROM stu.students s
        JOIN acd.batch_students bs
          ON bs.student_id = s.id
         AND bs.tenant_id = s.tenant_id
         AND bs.status = 'ACTIVE'
        JOIN acd.course_sections sec
          ON sec.batch_id = bs.batch_id
         AND sec.tenant_id = s.tenant_id
         AND sec.deleted_at IS NULL
        JOIN acd.timetable_entries tte
          ON tte.section_id = sec.id
         AND tte.tenant_id = s.tenant_id
         AND tte.deleted_at IS NULL
        LEFT JOIN core.persons p ON p.id = s.person_id
        WHERE s.tenant_id = $1 AND s.deleted_at IS NULL
          ${isFaculty ? 'AND tte.employee_id = $2' : ''}
        ORDER BY s.enrollment_no ASC
      `, isFaculty ? [tenantId, employeeId] : [tenantId]);

      students = stuRes.rows.map((s: any) => ({
        id: s.id,
        enrollment_no: s.enrollment_no,
        display_name: s.display_name || s.enrollment_no,
        primary_email: s.primary_email || '',
        section_id: s.section_id,
      }));
    }
  } catch (err) {
    console.error('Error fetching faculty timetable slots or students:', err);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
          Faculty Attendance Capture
        </h1>
        <p className="text-xs text-[#475569] mt-1">
          Select your class period, take student attendance, and submit finalized sessions to the institutional audit trail.
        </p>
      </div>

      <FacultyAttendanceView 
        slots={slots} 
        students={students} 
        employeeId={employeeId} 
        initialSlotId={initialSlotId}
        initialSessionId={initialSessionId}
      />
    </div>
  );
}
