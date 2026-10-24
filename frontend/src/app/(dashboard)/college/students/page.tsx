import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { StudentRoster } from './student-roster';

export const dynamic = 'force-dynamic';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export default async function CollegeStudentsPage() {
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
  let studentsList: any[] = [];
  let programs: any[] = [];
  let batches: any[] = [];

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

    // 1. Fetch Students Roster via Java Student Service (STU-01)
    const stuUrl = new URL('/api/v1/students', GATEWAY_BASE_URL);
    const stuRes = await fetch(stuUrl.toString(), {
      method: 'GET',
      headers,
      cache: 'no-store',
    });

    if (stuRes.ok) {
      const data = await stuRes.json();
      const items = Array.isArray(data.items) ? data.items : [];
      studentsList = items.map((s: any) => ({
        id: s.id,
        enrollment_no: s.enrollmentNo || s.enrollment_no,
        status: s.status,
        program_id: s.programId || s.program_id,
        program_name: s.programName || s.program_name,
        batch_id: s.batchId || s.batch_id,
        batch_name: s.batchName || s.batch_name,
        batch_code: s.batchCode || s.batch_code,
        roll_no: s.rollNo || s.roll_no,
        display_name: s.displayName || s.display_name || 'Unnamed Student',
        primary_email: s.primaryEmail || s.primary_email || 'No Email',
        created_at: s.createdAt || s.created_at,
      }));
    }

    // 2. Fetch Academic Programs via College Admin Service (ADM-02)
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
    console.error('Error fetching student roster via API gateway:', err);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
          Student Body Roster
        </h1>
        <p className="text-xs text-[#6B7688] mt-1">
          Enrolled undergraduate, graduate, and research scholars across all degree tracks.
        </p>
      </div>

      <StudentRoster initialStudents={studentsList} programs={programs} batches={batches} />
    </div>
  );
}
