import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';

const CorrectAttendanceSchema = z.object({
  recordId: z.string().uuid('Valid attendance record ID required'),
  sessionId: z.string().uuid('Valid session ID required'),
  newStatus: z.enum(['PRESENT', 'ABSENT', 'LATE', 'ON_DUTY', 'LEAVE', 'EXCUSED'], {
    errorMap: () => ({ message: 'Status must be PRESENT, ABSENT, LATE, ON_DUTY, LEAVE, or EXCUSED' }),
  }),
  reason: z.string().min(5, 'Mandatory correction reason must be at least 5 characters'),
  expectedVersion: z.number().int().min(1, 'Expected record version is required for optimistic locking'),
});

export async function POST(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    // Only college_admin is authorized to perform administrative corrections per ACD-06
    if (!resolution.success || resolution.session.role !== 'college_admin') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Unauthorized: only college administrators can perform audited attendance corrections.',
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
    const parseResult = CorrectAttendanceSchema.safeParse(body);
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

    const { recordId, sessionId, newStatus, reason, expectedVersion } = parseResult.data;

    // 1. Fetch current record to check existence and optimistic lock version
    const checkRes = await query(`
      SELECT id, session_id, student_id, status, remarks, row_version
      FROM acd.attendance_records
      WHERE id = $1 AND session_id = $2 AND tenant_id = $3 AND deleted_at IS NULL
      LIMIT 1
    `, [recordId, sessionId, tenantId]);

    const existingRecord = checkRes.rows[0];

    if (!existingRecord) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/not-found',
          title: 'Attendance Record Not Found',
          status: 404,
          detail: 'Attendance record not found in tenant session.',
        },
        { status: 404 }
      );
    }

    // 2. Concurrency Check: Assert expected version matches current database version
    if (existingRecord.row_version !== expectedVersion) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/conflict',
          title: 'Optimistic Concurrency Conflict',
          status: 409,
          detail: `Record was updated by another user or session. Expected version was ${expectedVersion}, but current database version is ${existingRecord.row_version}. Please reload and review latest data.`,
          currentVersion: existingRecord.row_version,
          currentStatus: existingRecord.status,
        },
        { status: 409 }
      );
    }

    const oldStatus = existingRecord.status;
    const oldVersion = existingRecord.row_version;
    const newVersion = oldVersion + 1;
    const userId = resolution.session.userId || '70a8ffae-b5ad-4c61-a401-16f220893df4';

    // 3. Update acd.attendance_records atomically with version increment
    const updateRes = await query(`
      UPDATE acd.attendance_records
      SET 
        status = $1,
        remarks = $2,
        row_version = $3,
        updated_at = now()
      WHERE id = $4 AND tenant_id = $5 AND row_version = $6
      RETURNING id, session_id, student_id, status, remarks, row_version, updated_at
    `, [newStatus, reason, newVersion, recordId, tenantId, expectedVersion]);

    if (updateRes.rowCount === 0) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/conflict',
          title: 'Optimistic Concurrency Conflict',
          status: 409,
          detail: 'Concurrent mutation detected during record update. Please reload.',
        },
        { status: 409 }
      );
    }

    const updatedRecord = updateRes.rows[0];

    // 4. Append immutable change log in audit.change_log
    try {
      await query(`
        INSERT INTO audit.change_log (
          tenant_id,
          table_schema,
          table_name,
          record_id,
          action,
          changed_fields,
          old_data,
          new_data,
          request_id,
          created_at
        ) VALUES (
          $1, 'acd', 'attendance_records', $2, 'U', ARRAY['status', 'remarks'],
          $3, $4, $5, now()
        )
      `, [
        tenantId,
        recordId,
        JSON.stringify({ status: oldStatus, row_version: oldVersion }),
        JSON.stringify({ status: newStatus, row_version: newVersion, reason }),
        reason,
      ]);
    } catch (changeLogErr: any) {
      console.warn('Failed to insert audit.change_log entry:', changeLogErr.message);
    }

    // 5. Append immutable audit event in audit.events
    try {
      await query(`
        INSERT INTO audit.events (
          tenant_id,
          event_time,
          action,
          resource_type,
          resource_id,
          status,
          severity,
          detail,
          created_at
        ) VALUES (
          $1, now(), 'ATTENDANCE_CORRECTED', 'ATTENDANCE_RECORD', $2, 'SUCCESS', 'INFO',
          $3, now()
        )
      `, [
        tenantId,
        recordId,
        JSON.stringify({
          sessionId,
          studentId: existingRecord.student_id,
          oldStatus,
          newStatus,
          reason,
          version: newVersion,
          correctedBy: resolution.session.displayName || 'College Administrator',
        }),
      ]);
    } catch (auditErr: any) {
      console.warn('Failed to insert audit.events entry:', auditErr.message);
    }

    // 6. Recalculate session summary
    const summaryRes = await query(`
      SELECT 
        count(*)::int as total,
        count(CASE WHEN status = 'PRESENT' THEN 1 END)::int as present_count,
        count(CASE WHEN status = 'ABSENT' THEN 1 END)::int as absent_count,
        count(CASE WHEN status = 'LEAVE' THEN 1 END)::int as leave_count
      FROM acd.attendance_records
      WHERE session_id = $1 AND tenant_id = $2 AND deleted_at IS NULL
    `, [sessionId, tenantId]);

    const summaryRow = summaryRes.rows[0] || { total: 0, present_count: 0, absent_count: 0, leave_count: 0 };
    const total = summaryRow.total || 0;
    const presentCount = summaryRow.present_count || 0;
    const absentCount = summaryRow.absent_count || 0;
    const leaveCount = summaryRow.leave_count || 0;
    const attendancePercentage = total > 0 ? Math.round((presentCount / total) * 100) : 0;

    return NextResponse.json({
      success: true,
      message: `Attendance corrected from ${oldStatus} to ${newStatus}. Version incremented to ${newVersion}.`,
      record: updatedRecord,
      summary: {
        total,
        presentCount,
        absentCount,
        leaveCount,
        attendancePercentage,
      },
    });
  } catch (err: any) {
    return NextResponse.json(
      {
        type: 'https://campxsync.edu/errors/internal-server-error',
        title: 'Internal Server Error',
        status: 500,
        detail: err.message || 'An unexpected error occurred during attendance correction.',
      },
      { status: 500 }
    );
  }
}
