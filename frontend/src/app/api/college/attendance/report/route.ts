import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';

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
          detail: 'Unauthorized: only institutional staff can view attendance reports.',
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

    const reportRes = await query(`
      SELECT 
        st.id as student_id,
        st.enrollment_no,
        p.display_name as student_name,
        p.primary_email,
        prog.name as program_name,
        prog.code as program_code,
        count(ar.id)::int as total_sessions,
        count(CASE WHEN ar.status = 'PRESENT' THEN 1 END)::int as attended_sessions,
        count(CASE WHEN ar.status = 'ABSENT' THEN 1 END)::int as absent_sessions,
        count(CASE WHEN ar.status = 'LEAVE' THEN 1 END)::int as leave_sessions
      FROM stu.students st
      JOIN core.persons p ON p.id = st.person_id
      LEFT JOIN acd.programs prog ON prog.id = st.program_id
      LEFT JOIN acd.attendance_records ar ON ar.student_id = st.id AND ar.tenant_id = $1 AND ar.deleted_at IS NULL
      WHERE st.tenant_id = $1 AND st.deleted_at IS NULL
      GROUP BY st.id, st.enrollment_no, p.display_name, p.primary_email, prog.name, prog.code
      ORDER BY st.enrollment_no ASC
    `, [tenantId]);

    const SHORTAGE_THRESHOLD = 75; // 75% statutory shortage threshold (ACD-06)

    const students = reportRes.rows.map((row: any) => {
      const total = row.total_sessions || 0;
      const attended = row.attended_sessions || 0;
      const percentage = total > 0 ? Math.round((attended / total) * 100) : 100;
      const hasShortage = total > 0 && percentage < SHORTAGE_THRESHOLD;

      return {
        studentId: row.student_id,
        enrollmentNo: row.enrollment_no,
        studentName: row.student_name,
        primaryEmail: row.primary_email,
        programName: row.program_name || 'General Program',
        programCode: row.program_code || 'GEN',
        totalSessions: total,
        attendedSessions: attended,
        absentSessions: row.absent_sessions || 0,
        leaveSessions: row.leave_sessions || 0,
        attendancePercentage: percentage,
        hasShortage,
      };
    });

    const totalStudents = students.length;
    const shortageCount = students.filter((s: any) => s.hasShortage).length;
    const totalPercentageSum = students.reduce((acc: number, s: any) => acc + s.attendancePercentage, 0);
    const averageAttendance = totalStudents > 0 ? Math.round(totalPercentageSum / totalStudents) : 0;

    return NextResponse.json({
      success: true,
      threshold: SHORTAGE_THRESHOLD,
      summary: {
        totalStudents,
        shortageCount,
        averageAttendance,
        shortagePercentage: totalStudents > 0 ? Math.round((shortageCount / totalStudents) * 100) : 0,
      },
      students,
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
