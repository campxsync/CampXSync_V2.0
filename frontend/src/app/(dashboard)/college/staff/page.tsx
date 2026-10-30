import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import { StaffManager } from './staff-manager';

export const dynamic = 'force-dynamic';

export default async function CollegeStaffPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'college_admin') redirect('/access-denied?reason=forbidden');
  const tenantId = resolution.session.tenantId;
  const collegeId = resolution.session.assignedCollegeId;
  if (!tenantId) redirect('/access-denied?reason=forbidden');

  const colleges = await query(`SELECT id, code, name FROM core.colleges WHERE tenant_id = $1 AND deleted_at IS NULL AND ($2::uuid IS NULL OR id = $2) ORDER BY code`, [tenantId, collegeId]);
  const faculty = await query(
    `SELECT e.id, e.employee_no, p.display_name FROM hrm.employees e LEFT JOIN core.persons p ON p.id = e.person_id
     WHERE e.tenant_id = $1 AND e.deleted_at IS NULL AND ($2::uuid IS NULL OR e.college_id = $2) ORDER BY e.employee_no`,
    [tenantId, collegeId]
  );
  const leaves = await query(
    `SELECT a.id, a.status, a.from_date::text, a.to_date::text, a.total_days, e.employee_no
     FROM hrm.leave_applications a JOIN hrm.employees e ON e.id = a.employee_id
     WHERE a.tenant_id = $1 AND a.deleted_at IS NULL ORDER BY a.created_at DESC`,
    [tenantId]
  );
  const attendance = await query(
    `SELECT d.id, d.status, d.attendance_date::text, e.employee_no
     FROM hrm.attendance_days d JOIN hrm.employees e ON e.id = d.employee_id
     WHERE d.tenant_id = $1 AND d.deleted_at IS NULL ORDER BY d.attendance_date DESC`,
    [tenantId]
  );
  const cycles = await query(
    `SELECT id, name, status, start_date::text, end_date::text FROM hrm.performance_cycles WHERE tenant_id = $1 AND deleted_at IS NULL ORDER BY start_date DESC`,
    [tenantId]
  );
  const structures = await query(
    `SELECT id, code, name, status FROM pay.salary_structures WHERE tenant_id = $1 AND deleted_at IS NULL ORDER BY code`,
    [tenantId]
  );
  const postings = await query(
    `SELECT id, posting_no, title, status FROM hrm.job_postings WHERE tenant_id = $1 AND deleted_at IS NULL ORDER BY posting_no`,
    [tenantId]
  );
  const onboardings = await query(
    `SELECT o.id, o.status, o.expected_join_date::text, e.employee_no
     FROM hrm.onboardings o JOIN hrm.employees e ON e.id = o.employee_id
     WHERE o.tenant_id = $1 AND o.deleted_at IS NULL`,
    [tenantId]
  );
  const training = await query(
    `SELECT en.id, en.status, c.code, e.employee_no
     FROM hrm.training_enrollments en
     JOIN hrm.training_courses c ON c.id = en.course_id
     JOIN hrm.employees e ON e.id = en.employee_id
     WHERE en.tenant_id = $1 AND en.deleted_at IS NULL`,
    [tenantId]
  );
  const headcount = await query(
    `SELECT count(*)::int AS employees FROM hrm.employees WHERE tenant_id = $1 AND deleted_at IS NULL AND ($2::uuid IS NULL OR college_id = $2)`,
    [tenantId, collegeId]
  );

  return (
    <StaffManager
      colleges={colleges.rows}
      faculty={faculty.rows.map((row) => ({ id: row.id, label: `${row.employee_no} · ${row.display_name || 'Faculty'}` }))}
      leaves={leaves.rows.map((row) => ({ ...row, total_days: Number(row.total_days) }))}
      attendance={attendance.rows}
      cycles={cycles.rows}
      structures={structures.rows}
      postings={postings.rows}
      onboardings={onboardings.rows}
      training={training.rows}
      employeeCount={headcount.rows[0].employees}
    />
  );
}
