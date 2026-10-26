import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { FacultyRoster } from './faculty-roster';
import { query } from '@/lib/db';

export const dynamic = 'force-dynamic';

export default async function CollegeFacultyPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  // 1. Enforce College Admin role
  if (!resolution.success || resolution.session.role !== 'college_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  const tenantId = resolution.session.tenantId;
  const assignedCollegeId = resolution.session.assignedCollegeId;
  if (!tenantId) {
    redirect('/access-denied?reason=forbidden');
  }
  let facultyList: any[] = [];
  let departments: any[] = [];

  try {
    const deptRes = await query(`
      SELECT id, name, code
      FROM core.departments
      WHERE tenant_id = $1 AND deleted_at IS NULL
        AND ($2::uuid IS NULL OR college_id = $2)
      ORDER BY name
    `, [tenantId, assignedCollegeId]);
    departments = deptRes.rows;

    const empRes = await query(`
      SELECT 
        e.id,
        e.employee_no,
        e.employment_type,
        e.status,
        e.department_id,
        e.created_at,
        p.display_name,
        p.primary_email,
        d.name as department_name
      FROM hrm.employees e
      LEFT JOIN core.persons p ON p.id = e.person_id
      LEFT JOIN core.departments d ON d.id = e.department_id
      WHERE e.tenant_id = $1 AND e.deleted_at IS NULL
        AND ($2::uuid IS NULL OR e.college_id = $2)
      ORDER BY e.created_at DESC
    `, [tenantId, assignedCollegeId]);

    facultyList = empRes.rows.map((e: any) => ({
      id: e.id,
      employee_no: e.employee_no,
      employment_type: e.employment_type,
      status: e.status,
      department_id: e.department_id,
      department_name: e.department_name,
      display_name: e.display_name || 'Unnamed Faculty',
      primary_email: e.primary_email || 'No Email',
      created_at: e.created_at,
    }));
  } catch (err) {
    console.error('Error fetching faculty roster:', err);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
          Faculty & Academic Staff Roster
        </h1>
        <p className="text-xs text-[#6B7688] mt-1">
          Appointed teaching professors, lecturers, and departmental instructors.
        </p>
      </div>

      <FacultyRoster initialFaculty={facultyList} departments={departments} />
    </div>
  );
}
