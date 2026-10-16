import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { StudentRoster } from './student-roster';
import { query } from '@/lib/db';

export const dynamic = 'force-dynamic';

export default async function CollegeStudentsPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  // 1. Enforce College Admin role
  if (!resolution.success || resolution.session.role !== 'college_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  const tenantId = resolution.session.tenantId || '11111111-1111-1111-1111-111111111111';
  let studentsList: any[] = [];
  let programs: any[] = [];
  let batches: any[] = [];

  try {
    const progRes = await query(`
      SELECT id, name, code
      FROM acd.programs
      WHERE tenant_id = $1 AND deleted_at IS NULL
      ORDER BY name
    `, [tenantId]);
    programs = progRes.rows;

    const batchRes = await query(`
      SELECT id, name, code, program_id
      FROM acd.batches
      WHERE tenant_id = $1 AND deleted_at IS NULL
      ORDER BY name
    `, [tenantId]);
    batches = batchRes.rows;

    const stuRes = await query(`
      SELECT 
        s.id,
        s.enrollment_no,
        s.status,
        s.program_id,
        s.created_at,
        p.display_name,
        p.primary_email,
        pr.name as program_name,
        b.id as batch_id,
        b.name as batch_name,
        b.code as batch_code,
        bs.roll_no
      FROM stu.students s
      LEFT JOIN core.persons p ON p.id = s.person_id
      LEFT JOIN acd.programs pr ON pr.id = s.program_id
      LEFT JOIN acd.batch_students bs ON bs.student_id = s.id AND bs.status = 'ACTIVE' AND bs.deleted_at IS NULL
      LEFT JOIN acd.batches b ON b.id = bs.batch_id
      WHERE s.tenant_id = $1 AND s.deleted_at IS NULL
      ORDER BY s.enrollment_no ASC
    `, [tenantId]);

    studentsList = stuRes.rows.map((s: any) => ({
      id: s.id,
      enrollment_no: s.enrollment_no,
      status: s.status,
      program_id: s.program_id,
      program_name: s.program_name,
      batch_id: s.batch_id,
      batch_name: s.batch_name,
      batch_code: s.batch_code,
      roll_no: s.roll_no,
      display_name: s.display_name || 'Unnamed Student',
      primary_email: s.primary_email || 'No Email',
      created_at: s.created_at,
    }));
  } catch (err) {
    console.error('Error fetching student roster:', err);
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
