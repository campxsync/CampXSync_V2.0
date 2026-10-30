import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query, withTransaction } from '@/lib/db';

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
      SELECT id, session_id, student_id, status, remarks, COALESCE(row_version, 1) as row_version
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

    if (resolution.session.assignedCollegeId) {
      const collegeRes = await query(
        `
        SELECT b.college_id
        FROM acd.attendance_sessions s
        JOIN acd.course_sections cs ON cs.id = s.section_id
        JOIN acd.batches b ON b.id = cs.batch_id
        WHERE s.id = $1 AND s.tenant_id = $2 AND s.deleted_at IS NULL
        LIMIT 1
        `,
        [sessionId, tenantId]
      );
      if (collegeRes.rows[0]?.college_id !== resolution.session.assignedCollegeId) {
        return NextResponse.json(
          {
            type: 'https://campxsync.edu/errors/forbidden',
            title: 'Forbidden',
            status: 403,
            detail: 'That attendance session belongs to another college.',
          },
          { status: 403 }
        );
      }
    }

    // 2. Concurrency Check: Assert expected version matches current database version
    if (existingRecord.row_version !== expectedVersion) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/conflict',
          title: 'Optimistic Concurrency Conflict',
          status: 409,
          detail: `This record was updated by another administrator or session. Expected version was v${expectedVersion}, but current database version is v${existingRecord.row_version}. Please reload and review the latest data before retrying.`,
          currentVersion: existingRecord.row_version,
          currentStatus: existingRecord.status,
        },
        { status: 409 }
      );
    }

    const oldStatus = existingRecord.status;
    const oldVersion = existingRecord.row_version;
    const newVersion = oldVersion + 1;
    const userId = resolution.session.userId;

    // Verify if userId exists in iam.user_profiles for this tenant to satisfy FK constraint
    let validUserProfileId: string | null = null;
    if (userId) {
      const upCheck = await query(`
        SELECT id FROM iam.user_profiles WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL LIMIT 1
      `, [userId, tenantId]);
      if (upCheck.rows.length > 0) {
        validUserProfileId = userId;
      }
    }

    // 3–7. Update the record and write correction history, change log, audit, and outbox together.
    // A failure after the update rolls the version change back.
    const updatedRecord = await withTransaction(async (client) => {
      const updateRes = await client.query(`
        UPDATE acd.attendance_records
        SET 
          status = $1,
          remarks = $2,
          row_version = $3,
          updated_at = now()
        WHERE id = $4 AND tenant_id = $5 AND COALESCE(row_version, 1) = $6
        RETURNING id, session_id, student_id, status, remarks, row_version, updated_at
      `, [newStatus, reason, newVersion, recordId, tenantId, expectedVersion]);

      if (updateRes.rowCount === 0) {
        throw new Error('OPTIMISTIC_CONFLICT');
      }

      const updated = updateRes.rows[0];

      await client.query(`
        INSERT INTO acd.attendance_corrections (
          tenant_id,
          record_id,
          old_status,
          new_status,
          reason,
          requested_by,
          approved_by,
          status,
          decided_at,
          created_at,
          updated_at
        ) VALUES (
          $1, $2, $3, $4, $5, $6, $7, 'APPROVED', now(), now(), now()
        )
      `, [
        tenantId,
        recordId,
        oldStatus,
        newStatus,
        reason,
        validUserProfileId,
        validUserProfileId,
      ]);

      await client.query(`
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
          $1, 'acd', 'attendance_records', $2, 'U', ARRAY['status', 'remarks', 'row_version'],
          $3, $4, $5, now()
        )
      `, [
        tenantId,
        recordId,
        JSON.stringify({ status: oldStatus, row_version: oldVersion }),
        JSON.stringify({ status: newStatus, row_version: newVersion, reason }),
        reason,
      ]);

      await client.query(`
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

      await client.query(`
        INSERT INTO sys.outbox_events (
          tenant_id,
          service,
          aggregate_type,
          aggregate_id,
          event_type,
          payload,
          status,
          created_at,
          updated_at
        ) VALUES (
          $1,
          'attendance-service',
          'AttendanceRecord',
          $2,
          'AttendanceCorrected',
          $3,
          'PENDING',
          now(),
          now()
        )
      `, [
        tenantId,
        recordId,
        JSON.stringify({
          recordId,
          sessionId,
          studentId: existingRecord.student_id,
          oldStatus,
          newStatus,
          reason,
          rowVersion: newVersion,
          correctedBy: resolution.session.displayName || 'College Administrator',
          timestamp: new Date().toISOString(),
        }),
      ]);

      return updated;
    });

    // 8. Recalculate session summary
    const summaryRes = await query(`
      SELECT 
        count(*)::int as total,
        count(CASE WHEN status = 'PRESENT' THEN 1 END)::int as present_count,
        count(CASE WHEN status = 'ABSENT' THEN 1 END)::int as absent_count,
        count(CASE WHEN status = 'LEAVE' THEN 1 END)::int as leave_count,
        count(CASE WHEN status = 'LATE' THEN 1 END)::int as late_count,
        count(CASE WHEN status = 'ON_DUTY' THEN 1 END)::int as on_duty_count
      FROM acd.attendance_records
      WHERE session_id = $1 AND tenant_id = $2 AND deleted_at IS NULL
    `, [sessionId, tenantId]);

    const summaryRow = summaryRes.rows[0] || { 
      total: 0, 
      present_count: 0, 
      absent_count: 0, 
      leave_count: 0,
      late_count: 0,
      on_duty_count: 0,
    };
    const total = summaryRow.total || 0;
    const presentCount = summaryRow.present_count || 0;
    const absentCount = summaryRow.absent_count || 0;
    const leaveCount = summaryRow.leave_count || 0;
    const lateCount = summaryRow.late_count || 0;
    const onDutyCount = summaryRow.on_duty_count || 0;
    const attendedCount = presentCount + lateCount + onDutyCount;
    const attendancePercentage = total > 0 ? Math.round((attendedCount / total) * 100) : 0;

    return NextResponse.json({
      success: true,
      message: `Attendance corrected from ${oldStatus} to ${newStatus}. Record updated to v${newVersion}.`,
      record: updatedRecord,
      summary: {
        total,
        presentCount,
        absentCount,
        leaveCount,
        lateCount,
        onDutyCount,
        attendancePercentage,
      },
    });
  } catch (err: any) {
    if (err?.message === 'OPTIMISTIC_CONFLICT') {
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
