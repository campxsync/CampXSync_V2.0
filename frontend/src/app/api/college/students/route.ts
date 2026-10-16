import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';

const CreateStudentSchema = z.object({
  firstName: z.string().min(1, 'First name is required').max(50),
  lastName: z.string().min(1, 'Last name is required').max(50),
  email: z.string().email('Valid email address is required'),
  enrollmentNo: z.string().min(3, 'Enrollment number is required').max(30),
  programId: z.string().uuid('Valid degree program ID is required'),
});

export async function POST(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success || resolution.session.role !== 'college_admin') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Only college administrators can enroll students.',
        },
        { status: 403 }
      );
    }

    const tenantId = resolution.session.tenantId;
    if (!tenantId) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/bad-request',
          title: 'Missing Tenant Context',
          status: 400,
          detail: 'No active tenant linked to user session.',
        },
        { status: 400 }
      );
    }

    const body = await req.json();
    const parseResult = CreateStudentSchema.safeParse(body);
    if (!parseResult.success) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/validation-failed',
          title: 'Validation Failed',
          status: 400,
          detail: parseResult.error.errors.map((e) => `${e.path.join('.')}: ${e.message}`).join(', '),
        },
        { status: 400 }
      );
    }

    const { firstName, lastName, email, enrollmentNo, programId } = parseResult.data;

    // 1. Verify program belongs to tenant
    const progRes = await query(`
      SELECT id, college_id FROM acd.programs
      WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL
      LIMIT 1
    `, [programId, tenantId]);

    if (progRes.rows.length === 0) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/not-found',
          title: 'Program Not Found',
          status: 404,
          detail: 'The selected academic program does not exist in your tenant workspace.',
        },
        { status: 404 }
      );
    }
    const program = progRes.rows[0];

    // 2. Check duplicate enrollment number in tenant
    const existingStuRes = await query(`
      SELECT id FROM stu.students
      WHERE tenant_id = $1 AND enrollment_no = $2 AND deleted_at IS NULL
      LIMIT 1
    `, [tenantId, enrollmentNo]);

    if (existingStuRes.rows.length > 0) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/conflict',
          title: 'Duplicate Enrollment Number',
          status: 409,
          detail: `Student with enrollment number '${enrollmentNo}' already exists in your institution.`,
        },
        { status: 409 }
      );
    }

    // 3. Create person in core.persons
    const displayName = `${firstName} ${lastName}`.trim();
    const personRes = await query(`
      INSERT INTO core.persons (tenant_id, first_name, last_name, display_name, primary_email, status)
      VALUES ($1, $2, $3, $4, $5, 'ACTIVE')
      RETURNING id
    `, [tenantId, firstName, lastName, displayName, email]);

    const person = personRes.rows[0];

    // 4. Create student in stu.students
    const stuRes = await query(`
      INSERT INTO stu.students (tenant_id, person_id, college_id, program_id, enrollment_no, status)
      VALUES ($1, $2, $3, $4, $5, 'ACTIVE')
      RETURNING id, enrollment_no, status, created_at
    `, [tenantId, person.id, program.college_id, programId, enrollmentNo]);

    const student = stuRes.rows[0];

    return NextResponse.json(
      {
        success: true,
        message: `Student '${displayName}' (${enrollmentNo}) enrolled successfully.`,
        student: {
          ...student,
          display_name: displayName,
          email,
        },
      },
      { status: 201 }
    );
  } catch (err: any) {
    return NextResponse.json(
      {
        type: 'https://campxsync.edu/errors/internal-server-error',
        title: 'Internal Server Error',
        status: 500,
        detail: err.message || 'An unexpected error occurred.',
      },
      { status: 500 }
    );
  }
}
