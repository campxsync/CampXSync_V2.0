import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';

const AttendanceRecordSchema = z.object({
  studentId: z.string().uuid('Valid student ID required'),
  status: z.enum(['PRESENT', 'ABSENT', 'LATE', 'LEAVE', 'ON_DUTY', 'EXCUSED'], {
    errorMap: () => ({ message: 'Status must be PRESENT, ABSENT, LATE, LEAVE, ON_DUTY, or EXCUSED' }),
  }),
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
        s.section_id,
        s.timetable_entry_id,
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
      LEFT JOIN acd.course_sections sec ON sec.id = s.section_id
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

    // 2. Fetch Persisted Student Attendance Records
    const recordsRes = await query(`
      SELECT 
        r.id,
        r.session_id,
        r.student_id,
        r.status,
        r.method,
        r.remarks,
        r.marked_at,
        COALESCE(r.row_version, 1) as row_version,
        st.enrollment_no,
        p.display_name,
        p.primary_email
      FROM acd.attendance_records r
      JOIN stu.students st ON st.id = r.student_id
      JOIN core.persons p ON p.id = st.person_id
      WHERE r.session_id = $1 AND r.tenant_id = $2 AND r.deleted_at IS NULL
      ORDER BY st.enrollment_no ASC
    `, [sessionId, tenantId]);

    let records = recordsRes.rows;

    // If no records have been submitted/saved yet for this session, resolve assigned batch roster
    if (records.length === 0) {
      const rosterRes = await query(`
        SELECT 
          s.id as student_id,
          s.enrollment_no,
          p.display_name,
          p.primary_email,
          'PRESENT' as status,
          'MANUAL' as method,
          null as remarks,
          1 as row_version
        FROM acd.course_sections sec
        JOIN acd.batch_students bs ON bs.batch_id = sec.batch_id AND bs.status = 'ACTIVE'
        JOIN stu.students s ON s.id = bs.student_id AND s.deleted_at IS NULL
        JOIN core.persons p ON p.id = s.person_id
        WHERE sec.id = $1 AND sec.tenant_id = $2
        ORDER BY s.enrollment_no ASC
      `, [session.section_id, tenantId]);

      if (rosterRes.rows.length > 0) {
        records = rosterRes.rows;
      } else {
        // Fallback to all tenant students if section has no specific batch assignments
        const tenantStuRes = await query(`
          SELECT 
            s.id as student_id,
            s.enrollment_no,
            p.display_name,
            p.primary_email,
            'PRESENT' as status,
            'MANUAL' as method,
            null as remarks,
            1 as row_version
          FROM stu.students s
          JOIN core.persons p ON p.id = s.person_id
          WHERE s.tenant_id = $1 AND s.deleted_at IS NULL
          ORDER BY s.enrollment_no ASC
          LIMIT 50
        `, [tenantId]);
        records = tenantStuRes.rows;
      }
    }

    // 3. Fetch Correction History from acd.attendance_corrections & audit.change_log
    let correctionHistory: any[] = [];
    try {
      const correctionsRes = await query(`
        SELECT 
          ac.id,
          ac.record_id,
          ac.old_status,
          ac.new_status,
          ac.reason,
          ac.status,
          ac.decided_at,
          ac.created_at,
          st.enrollment_no,
          p.display_name as student_name,
          admin_p.display_name as approver_name
        FROM acd.attendance_corrections ac
        JOIN acd.attendance_records ar ON ar.id = ac.record_id
        JOIN stu.students st ON st.id = ar.student_id
        JOIN core.persons p ON p.id = st.person_id
        LEFT JOIN iam.user_profiles up ON up.id = ac.approved_by
        LEFT JOIN core.persons admin_p ON admin_p.id = up.person_id
        WHERE ac.tenant_id = $1 AND ar.session_id = $2
        ORDER BY ac.created_at DESC
        LIMIT 50
      `, [tenantId, sessionId]);

      if (correctionsRes.rows.length > 0) {
        correctionHistory = correctionsRes.rows.map((c: any) => ({
          id: c.id,
          record_id: c.record_id,
          old_status: c.old_status,
          new_status: c.new_status,
          reason: c.reason,
          action: 'U',
          created_at: c.created_at || c.decided_at,
          student_name: c.student_name,
          enrollment_no: c.enrollment_no,
          approver_name: c.approver_name || 'College Administrator',
        }));
      } else {
        // Fallback to audit.change_log
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
          record_id: h.record_id,
          old_status: h.old_data?.status || 'UNKNOWN',
          new_status: h.new_data?.status || 'UNKNOWN',
          reason: h.reason || h.new_data?.reason || 'Administrative Adjustment',
          action: h.action,
          created_at: h.created_at,
          student_name: h.student_name,
          enrollment_no: h.enrollment_no,
          approver_name: 'College Administrator',
        }));
      }
    } catch (histErr: any) {
      console.warn('Failed to query correction history:', histErr.message);
    }

    // 4. Calculate Running Counts & Summary
    const totalRecords = records.length;
    const presentCount = records.filter((r: any) => r.status === 'PRESENT').length;
    const absentCount = records.filter((r: any) => r.status === 'ABSENT').length;
    const leaveCount = records.filter((r: any) => r.status === 'LEAVE').length;
    const lateCount = records.filter((r: any) => r.status === 'LATE').length;
    const onDutyCount = records.filter((r: any) => r.status === 'ON_DUTY').length;
    const attendedCount = presentCount + lateCount + onDutyCount;
    const attendancePercentage = totalRecords > 0 ? Math.round((attendedCount / totalRecords) * 100) : 0;

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
        lateCount,
        onDutyCount,
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

    // 1. Verify session exists and belongs to tenant
    const sessionRes = await query(`
      SELECT id, status, section_id, employee_id, timetable_entry_id
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

    // 2. Lifecycle Enforcement: Once closed, faculty cannot modify attendance sheet
    if (session.status === 'CLOSED' || session.status === 'LOCKED') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/conflict',
          title: 'Session Locked',
          status: 409,
          detail: 'This attendance session is submitted and locked. Faculty cannot modify finalized sessions. Please contact College Administration for authorized corrections.',
        },
        { status: 409 }
      );
    }

    // 3. Roster Membership Validation: Ensure submitted students belong to tenant student body
    const studentIds = records.map((r) => r.studentId);
    const validStudentsRes = await query(`
      SELECT id FROM stu.students
      WHERE tenant_id = $1 AND id = ANY($2::uuid[]) AND deleted_at IS NULL
    `, [tenantId, studentIds]);

    const validStudentIdSet = new Set(validStudentsRes.rows.map((r: any) => r.id));
    for (const sid of studentIds) {
      if (!validStudentIdSet.has(sid)) {
        return NextResponse.json(
          {
            type: 'https://campxsync.edu/errors/validation-failed',
            title: 'Invalid Roster Student',
            status: 400,
            detail: `Student ${sid} is not enrolled or does not belong to this institution's roster.`,
          },
          { status: 400 }
        );
      }
    }

    // 4. Idempotent Upsert of student attendance marks
    const now = new Date().toISOString();
    const markedByUserId = resolution.session.userId;

    for (const r of records) {
      await query(`
        INSERT INTO acd.attendance_records (
          tenant_id, 
          session_id, 
          student_id, 
          status, 
          method, 
          marked_at, 
          remarks, 
          row_version
        ) VALUES ($1, $2, $3, $4, 'MANUAL', $5, $6, 1)
        ON CONFLICT (tenant_id, session_id, student_id) WHERE (deleted_at IS NULL)
        DO UPDATE SET 
          status = EXCLUDED.status, 
          marked_at = EXCLUDED.marked_at, 
          remarks = EXCLUDED.remarks,
          updated_at = now()
      `, [tenantId, sessionId, r.studentId, r.status, now, r.remarks || null]);
    }

    // 5. Lifecycle Transition: If closeSession is true, lock the session
    if (closeSession) {
      await query(`
        UPDATE acd.attendance_sessions
        SET status = 'CLOSED', locked_at = $1, updated_at = $1
        WHERE id = $2 AND tenant_id = $3
      `, [now, sessionId, tenantId]);
    }

    // 6. Record Audit Event
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
          $1, now(), 'ATTENDANCE_SUBMITTED', 'ATTENDANCE_SESSION', $2, 'SUCCESS', 'INFO',
          $3, now()
        )
      `, [
        tenantId,
        sessionId,
        JSON.stringify({
          totalRecords: records.length,
          presentCount: records.filter((r) => r.status === 'PRESENT').length,
          absentCount: records.filter((r) => r.status === 'ABSENT').length,
          leaveCount: records.filter((r) => r.status === 'LEAVE').length,
          submittedBy: resolution.session.displayName || 'Faculty Member',
          role: resolution.session.role,
          locked: closeSession,
        }),
      ]);
    } catch (auditErr: any) {
      console.warn('Failed to insert attendance submitted audit event:', auditErr.message);
    }

    const presentCount = records.filter((r) => r.status === 'PRESENT').length;
    const absentCount = records.filter((r) => r.status === 'ABSENT').length;
    const leaveCount = records.filter((r) => r.status === 'LEAVE').length;
    const lateCount = records.filter((r) => r.status === 'LATE').length;
    const onDutyCount = records.filter((r) => r.status === 'ON_DUTY').length;

    return NextResponse.json(
      {
        success: true,
        message: `Attendance submitted: ${presentCount} Present, ${absentCount} Absent, ${leaveCount} Leave.`,
        summary: {
          total: records.length,
          presentCount,
          absentCount,
          leaveCount,
          lateCount,
          onDutyCount,
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
