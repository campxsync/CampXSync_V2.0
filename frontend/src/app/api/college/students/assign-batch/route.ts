import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';

const AssignBatchSchema = z.object({
  studentId: z.string().uuid('Valid student ID is required'),
  batchId: z.string().uuid('Valid batch ID is required'),
  rollNo: z.string().max(30).optional(),
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
          detail: 'Only college administrators can assign students to academic batches.',
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
    const parseResult = AssignBatchSchema.safeParse(body);
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

    const { studentId, batchId, rollNo } = parseResult.data;

    // 1. Verify student exists in tenant
    const stuRes = await query(`
      SELECT s.id, s.college_id, s.enrollment_no, p.display_name
      FROM stu.students s
      LEFT JOIN core.persons p ON p.id = s.person_id
      WHERE s.id = $1 AND s.tenant_id = $2 AND s.deleted_at IS NULL
      LIMIT 1
    `, [studentId, tenantId]);

    if (stuRes.rows.length === 0) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/not-found',
          title: 'Student Not Found',
          status: 404,
          detail: 'Student does not exist in your tenant workspace.',
        },
        { status: 404 }
      );
    }
    const student = stuRes.rows[0];

    // 2. Verify batch exists in tenant
    const batchRes = await query(`
      SELECT id, name, code
      FROM acd.batches
      WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL
      LIMIT 1
    `, [batchId, tenantId]);

    if (batchRes.rows.length === 0) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/not-found',
          title: 'Batch Not Found',
          status: 404,
          detail: 'Batch does not exist in your tenant workspace.',
        },
        { status: 404 }
      );
    }
    const batch = batchRes.rows[0];

    // 3. Upsert into acd.batch_students
    const effectiveRollNo = rollNo || student.enrollment_no;
    await query(`
      INSERT INTO acd.batch_students (
        tenant_id, batch_id, student_id, roll_no, joined_on, status, row_version
      )
      VALUES ($1, $2, $3, $4, CURRENT_DATE, 'ACTIVE', 1)
      ON CONFLICT (tenant_id, batch_id, student_id) WHERE deleted_at IS NULL
      DO UPDATE SET 
        status = 'ACTIVE',
        roll_no = COALESCE($4, acd.batch_students.roll_no),
        updated_at = now()
    `, [tenantId, batchId, studentId, effectiveRollNo]);

    // 4. Record Audit Event
    await query(`
      INSERT INTO audit.events (
        tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail
      )
      VALUES ($1, $2, now(), 'STUDENT_BATCH_ASSIGNED', 'COLLEGE_ADMIN', $3, 'STUDENT', $4, 'SUCCESS', 'INFO', $5)
    `, [
      tenantId,
      student.college_id,
      resolution.session.userId || null,
      student.id,
      JSON.stringify({
        enrollmentNo: student.enrollment_no,
        studentName: student.display_name,
        batchId,
        batchName: batch.name,
        batchCode: batch.code,
        rollNo: effectiveRollNo,
      }),
    ]);

    return NextResponse.json({
      success: true,
      message: `Student '${student.display_name || student.enrollment_no}' successfully assigned to batch '${batch.name}'.`,
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
