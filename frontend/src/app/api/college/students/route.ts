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
  batchId: z.string().uuid('Valid batch ID required').optional(),
});

const DeleteStudentSchema = z.object({
  id: z.string().uuid('Valid student ID is required'),
});

export async function GET(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success || resolution.session.role !== 'college_admin') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Only college administrators can inspect the student roster.',
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

    const res = await query(`
      SELECT 
        s.id,
        s.enrollment_no,
        s.status,
        s.program_id,
        s.created_at,
        p.display_name,
        p.primary_email,
        pr.name as program_name,
        pr.code as program_code,
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

    return NextResponse.json({
      success: true,
      students: res.rows.map((s) => ({
        id: s.id,
        enrollment_no: s.enrollment_no,
        status: s.status,
        program_id: s.program_id,
        program_name: s.program_name,
        program_code: s.program_code,
        batch_id: s.batch_id,
        batch_name: s.batch_name,
        batch_code: s.batch_code,
        roll_no: s.roll_no,
        display_name: s.display_name || 'Unnamed Student',
        primary_email: s.primary_email || 'No Email',
        created_at: s.created_at,
      })),
    });
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

    const { firstName, lastName, email, enrollmentNo, programId, batchId } = parseResult.data;

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

    // 2. If batchId provided, verify it belongs to tenant and matches program
    if (batchId) {
      const batchRes = await query(`
        SELECT id FROM acd.batches
        WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL
        LIMIT 1
      `, [batchId, tenantId]);

      if (batchRes.rows.length === 0) {
        return NextResponse.json(
          {
            type: 'https://campxsync.edu/errors/not-found',
            title: 'Batch Not Found',
            status: 404,
            detail: 'The specified batch does not exist in your tenant workspace.',
          },
          { status: 404 }
        );
      }
    }

    // 3. Check duplicate enrollment number in tenant
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

    // 4. Check duplicate email in tenant
    const existingEmailRes = await query(`
      SELECT id FROM core.persons
      WHERE tenant_id = $1 AND primary_email = $2 AND deleted_at IS NULL
      LIMIT 1
    `, [tenantId, email]);

    if (existingEmailRes.rows.length > 0) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/conflict',
          title: 'Duplicate Email Address',
          status: 409,
          detail: `A person with email '${email}' is already registered in your institution.`,
        },
        { status: 409 }
      );
    }

    // 5. Create person in core.persons
    const displayName = `${firstName} ${lastName}`.trim();
    const personRes = await query(`
      INSERT INTO core.persons (tenant_id, first_name, last_name, display_name, primary_email, status)
      VALUES ($1, $2, $3, $4, $5, 'ACTIVE')
      RETURNING id
    `, [tenantId, firstName, lastName, displayName, email]);

    const person = personRes.rows[0];

    // 6. Create student in stu.students
    const stuRes = await query(`
      INSERT INTO stu.students (tenant_id, person_id, college_id, program_id, enrollment_no, status)
      VALUES ($1, $2, $3, $4, $5, 'ACTIVE')
      RETURNING id, enrollment_no, status, created_at
    `, [tenantId, person.id, program.college_id, programId, enrollmentNo]);

    const student = stuRes.rows[0];

    // 7. If batchId provided, assign student to batch in acd.batch_students
    let batchAssigned = false;
    if (batchId) {
      await query(`
        INSERT INTO acd.batch_students (
          tenant_id, batch_id, student_id, roll_no, joined_on, status, row_version
        )
        VALUES ($1, $2, $3, $4, CURRENT_DATE, 'ACTIVE', 1)
        ON CONFLICT (tenant_id, batch_id, student_id) WHERE deleted_at IS NULL DO NOTHING
      `, [tenantId, batchId, student.id, enrollmentNo]);
      batchAssigned = true;
    }

    // 8. Record Consequential Event in audit.events
    await query(`
      INSERT INTO audit.events (
        tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail
      )
      VALUES ($1, $2, now(), 'STUDENT_ENROLLED', 'COLLEGE_ADMIN', $3, 'STUDENT', $4, 'SUCCESS', 'INFO', $5)
    `, [
      tenantId,
      program.college_id,
      resolution.session.userId || null,
      student.id,
      JSON.stringify({ enrollmentNo, displayName, email, programId, batchId, batchAssigned }),
    ]);

    return NextResponse.json(
      {
        success: true,
        message: `Student '${displayName}' (${enrollmentNo}) enrolled successfully${batchAssigned ? ' and assigned to batch' : ''}.`,
        student: {
          ...student,
          display_name: displayName,
          email,
          batch_assigned: batchAssigned,
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

export async function DELETE(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success || resolution.session.role !== 'college_admin') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Only college administrators can deactivate students.',
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
    const parseResult = DeleteStudentSchema.safeParse(body);
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

    const { id } = parseResult.data;

    // 1. Verify student belongs to tenant
    const stuRes = await query(`
      SELECT s.id, s.person_id, s.college_id, s.enrollment_no, p.display_name
      FROM stu.students s
      LEFT JOIN core.persons p ON p.id = s.person_id
      WHERE s.id = $1 AND s.tenant_id = $2 AND s.deleted_at IS NULL
      LIMIT 1
    `, [id, tenantId]);

    if (stuRes.rows.length === 0) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/not-found',
          title: 'Student Not Found',
          status: 404,
          detail: 'Student does not exist in your tenant workspace or is already deactivated.',
        },
        { status: 404 }
      );
    }
    const stu = stuRes.rows[0];

    // 2. Perform audited soft delete
    const now = new Date().toISOString();
    await query(`
      UPDATE stu.students
      SET deleted_at = $1, status = 'WITHDRAWN', updated_at = $1
      WHERE id = $2 AND tenant_id = $3
    `, [now, id, tenantId]);

    await query(`
      UPDATE acd.batch_students
      SET deleted_at = $1, status = 'DROPPED', updated_at = $1
      WHERE student_id = $2 AND tenant_id = $3
    `, [now, id, tenantId]);

    if (stu.person_id) {
      await query(`
        UPDATE core.persons
        SET status = 'INACTIVE', updated_at = $1
        WHERE id = $2 AND tenant_id = $3
      `, [now, stu.person_id, tenantId]);
    }

    // 3. Record Audit Event
    await query(`
      INSERT INTO audit.events (
        tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail
      )
      VALUES ($1, $2, now(), 'STUDENT_DELETED', 'COLLEGE_ADMIN', $3, 'STUDENT', $4, 'SUCCESS', 'WARNING', $5)
    `, [
      tenantId,
      stu.college_id,
      resolution.session.userId || null,
      stu.id,
      JSON.stringify({ enrollmentNo: stu.enrollment_no, displayName: stu.display_name }),
    ]);

    return NextResponse.json({
      success: true,
      message: `Student '${stu.display_name || stu.enrollment_no}' has been deactivated successfully.`,
    });
  } catch (err: any) {
    console.error('DELETE /api/college/students error:', err);
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
