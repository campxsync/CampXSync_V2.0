import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';

const AttendanceRecordSchema = z.object({
  studentId: z.string().uuid(),
  status: z.enum(['PRESENT', 'ABSENT', 'LATE', 'LEAVE', 'ON_DUTY', 'EXCUSED']),
  remarks: z.string().optional(),
});

const SubmitAttendanceSchema = z.object({
  sessionId: z.string().uuid('Valid session ID required'),
  closeSession: z.boolean().default(true),
  records: z.array(AttendanceRecordSchema).min(1, 'At least one student attendance record required'),
});

export async function GET(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (
      !resolution.success ||
      (resolution.session.role !== 'college_admin' && resolution.session.role !== 'faculty')
    ) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Unauthorized: only instructors and college administrators can view attendance records.',
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

    const { searchParams } = new URL(req.url);
    const sessionId = searchParams.get('sessionId');

    if (!sessionId) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/validation-failed',
          title: 'Missing sessionId Parameter',
          status: 400,
          detail: 'Valid sessionId query parameter is required.',
        },
        { status: 400 }
      );
    }

    // 1. Fetch Session Metadata
    const sessionRes = await query(`
      SELECT 
        s.id,
        s.session_date,
        s.start_time,
        s.end_time,
        s.status,
        s.locked_at,
        sub.name as subject_name,
        sub.code as subject_code,
        sec.section_code,
        p.display_name as faculty_name
      FROM acd.attendance_sessions s
      LEFT JOIN acd.timetable_entries tte ON tte.id = s.timetable_entry_id
      LEFT JOIN acd.subjects sub ON sub.id = tte.subject_id
      LEFT JOIN acd.course_sections sec ON sec.id = tte.section_id
      LEFT JOIN hrm.employees emp ON emp.id = s.employee_id
      LEFT JOIN core.persons p ON p.id = emp.person_id
      WHERE s.id = $1 AND s.tenant_id = $2 AND s.deleted_at IS NULL
      LIMIT 1
    `, [sessionId, tenantId]);

    const session = sessionRes.rows[0];
    if (!session) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/not-found',
          title: 'Attendance Session Not Found',
          status: 404,
          detail: 'Attendance session not found in tenant scope.',
        },
        { status: 404 }
      );
    }

    // 2. Fetch Student Attendance Records
    const recordsRes = await query(`
      SELECT 
        r.id,
        r.session_id,
        r.student_id,
        r.status,
        r.method,
        r.remarks,
        r.marked_at,
        r.row_version,
        st.enrollment_no,
        p.display_name,
        p.primary_email
      FROM acd.attendance_records r
      JOIN stu.students st ON st.id = r.student_id
      JOIN core.persons p ON p.id = st.person_id
      WHERE r.session_id = $1 AND r.tenant_id = $2 AND r.deleted_at IS NULL
      ORDER BY st.enrollment_no ASC
    `, [sessionId, tenantId]);

    const records = recordsRes.rows;

    // 3. Fetch Correction History from audit.change_log
    let correctionHistory: any[] = [];
    try {
      const historyRes = await query(`
        SELECT 
          cl.id,
          cl.record_id,
          cl.action,
          cl.changed_fields,
          cl.old_data,
          cl.new_data,
          cl.request_id as reason,
          cl.created_at,
          p.display_name as student_name,
          st.enrollment_no
        FROM audit.change_log cl
        JOIN acd.attendance_records ar ON ar.id = cl.record_id
        JOIN stu.students st ON st.id = ar.student_id
        JOIN core.persons p ON p.id = st.person_id
        WHERE cl.table_schema = 'acd'
          AND cl.table_name = 'attendance_records'
          AND cl.tenant_id = $1
          AND ar.session_id = $2
        ORDER BY cl.created_at DESC
        LIMIT 50
      `, [tenantId, sessionId]);

      correctionHistory = historyRes.rows.map((h: any) => ({
        id: h.id,
        recordId: h.record_id,
        studentName: h.student_name,
        enrollmentNo: h.enrollment_no,
        oldStatus: h.old_data?.status || 'UNKNOWN',
        newStatus: h.new_data?.status || 'UNKNOWN',
        reason: h.reason || h.new_data?.reason || 'Administrative correction',
        timestamp: h.created_at,
        oldVersion: h.old_data?.row_version,
        newVersion: h.new_data?.row_version,
      }));
    } catch (auditErr) {
      console.warn('Failed to query correction history from audit.change_log:', auditErr);
    }

    const presentCount = records.filter((r) => r.status === 'PRESENT').length;
    const absentCount = records.filter((r) => r.status === 'ABSENT').length;
    const leaveCount = records.filter((r) => r.status === 'LEAVE').length;
    const totalRecords = records.length;
    const attendancePercentage = totalRecords > 0 ? Math.round((presentCount / totalRecords) * 100) : 0;

    return NextResponse.json({
      success: true,
      session,
      records,
      correctionHistory,
      summary: {
        totalRecords,
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

    if (
      !resolution.success ||
      (resolution.session.role !== 'college_admin' && resolution.session.role !== 'faculty')
    ) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Unauthorized: only instructors and college administrators can mark attendance.',
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
    const parseResult = SubmitAttendanceSchema.safeParse(body);
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

    const { sessionId, closeSession, records } = parseResult.data;

    // Verify session belongs to tenant
    const sessionRes = await query(`
      SELECT id, status
      FROM acd.attendance_sessions
      WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL
      LIMIT 1
    `, [sessionId, tenantId]);

    const session = sessionRes.rows[0];

    if (!session) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/not-found',
          title: 'Attendance Session Not Found',
          status: 404,
          detail: 'Session does not exist in your tenant workspace.',
        },
        { status: 404 }
      );
    }

    const now = new Date().toISOString();

    for (const r of records) {
      await query(`
        INSERT INTO acd.attendance_records (tenant_id, session_id, student_id, status, method, marked_at, remarks)
        VALUES ($1, $2, $3, $4, 'MANUAL', $5, $6)
        ON CONFLICT (tenant_id, session_id, student_id)
        DO UPDATE SET status = EXCLUDED.status, marked_at = EXCLUDED.marked_at, remarks = EXCLUDED.remarks
      `, [tenantId, sessionId, r.studentId, r.status, now, r.remarks || null]);
    }

    // Update session status if closed
    if (closeSession) {
      await query(`
        UPDATE acd.attendance_sessions
        SET status = 'CLOSED', locked_at = $1, updated_at = $1
        WHERE id = $2 AND tenant_id = $3
      `, [now, sessionId, tenantId]);
    }

    const presentCount = records.filter((r) => r.status === 'PRESENT').length;
    const absentCount = records.filter((r) => r.status === 'ABSENT').length;

    return NextResponse.json(
      {
        success: true,
        message: `Attendance submitted: ${presentCount} Present, ${absentCount} Absent.`,
        summary: {
          total: records.length,
          presentCount,
          absentCount,
          status: closeSession ? 'CLOSED' : 'OPEN',
        },
      },
      { status: 200 }
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
