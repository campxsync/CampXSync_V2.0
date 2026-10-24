import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { DepartmentsManager } from './departments-manager';

export const dynamic = 'force-dynamic';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export default async function CollegeDepartmentsPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  // 1. Enforce College Admin role
  if (!resolution.success || resolution.session.role !== 'college_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  const tenantId = resolution.session.tenantId;
  if (!tenantId) {
    redirect('/access-denied?reason=forbidden');
  }
  let departments: any[] = [];
  let programs: any[] = [];

  try {
    const headers: Record<string, string> = {
      'Accept': 'application/json',
      'X-User-Id': resolution.session.userId || '00000000-0000-0000-0000-000000000001',
      'X-Tenant-Id': tenantId,
      'X-User-Role': 'COLLEGE_ADMIN',
    };
    if (resolution.session.collegeId) {
      headers['X-College-Id'] = resolution.session.collegeId;
    }

    const deptUrl = new URL('/api/v1/college-admin/departments', GATEWAY_BASE_URL);
    const deptRes = await fetch(deptUrl.toString(), {
      method: 'GET',
      headers,
      cache: 'no-store',
    });
    if (deptRes.ok) {
      const dData = await deptRes.json();
      departments = Array.isArray(dData.departments) ? dData.departments : [];
    }

    const progUrl = new URL('/api/v1/college-admin/programs', GATEWAY_BASE_URL);
    const progRes = await fetch(progUrl.toString(), {
      method: 'GET',
      headers,
      cache: 'no-store',
    });
    if (progRes.ok) {
      const pData = await progRes.json();
      programs = Array.isArray(pData.programs) ? pData.programs : [];
    }
  } catch (err) {
    console.error('Error querying departments or programs via API gateway:', err);
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
