import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { DepartmentsManager } from './departments-manager';
import { query } from '@/lib/db';

export const dynamic = 'force-dynamic';

export default async function CollegeDepartmentsPage() {
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
  let departments: any[] = [];
  let programs: any[] = [];

  try {
    const deptRes = await query(`
      SELECT id, name, code, status, created_at
      FROM core.departments
      WHERE tenant_id = $1 AND deleted_at IS NULL
        AND ($2::uuid IS NULL OR college_id = $2)
      ORDER BY name
    `, [tenantId, assignedCollegeId]);
    departments = deptRes.rows;

    const progRes = await query(`
      SELECT id, department_id, name, code, level, duration_years, total_terms, status
      FROM acd.programs
      WHERE tenant_id = $1 AND deleted_at IS NULL
        AND ($2::uuid IS NULL OR college_id = $2)
      ORDER BY name
    `, [tenantId, assignedCollegeId]);
    programs = progRes.rows;
  } catch (err) {
    console.error('Error querying departments or programs:', err);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
          Academic Departments & Programs
        </h1>
        <p className="text-xs text-[#6B7688] mt-1">
          Organize academic faculty departments, degree curricula, and program tracks for your institution.
        </p>
      </div>

      <DepartmentsManager initialDepartments={departments} initialPrograms={programs} />
    </div>
  );
}
