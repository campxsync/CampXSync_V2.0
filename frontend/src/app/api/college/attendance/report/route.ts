import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';

export const dynamic = 'force-dynamic';

function escapeCsvField(val: any): string {
  if (val === null || val === undefined) return '""';
  let str = String(val).trim();
  // Prevent CSV Formula Injection
  if (/^[=+@-]/.test(str)) {
    str = `'${str}`;
  }
  // Escape internal double quotes
  str = str.replace(/"/g, '""');
  return `"${str}"`;
}

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

    const url = new URL(req.url);
    const studentId = url.searchParams.get('studentId');
    const sectionId = url.searchParams.get('sectionId');
    const subjectId = url.searchParams.get('subjectId');
    const startDate = url.searchParams.get('startDate');
    const endDate = url.searchParams.get('endDate');
    const shortageOnly = url.searchParams.get('shortageOnly') === 'true';
    const format = url.searchParams.get('format') || 'json';

    // Build parameterized query for sessions/records join
    const params: any[] = [tenantId];
    let sessionFilters = 's.tenant_id = $1 AND s.deleted_at IS NULL';

    if (sectionId) {
      params.push(sectionId);
      sessionFilters += ` AND s.section_id = $${params.length}`;
    }

    if (subjectId) {
      params.push(subjectId);
      sessionFilters += ` AND s.subject_id = $${params.length}`;
    }

    if (startDate) {
      params.push(startDate);
      sessionFilters += ` AND s.session_date >= $${params.length}`;
    }

    if (endDate) {
      params.push(endDate);
      sessionFilters += ` AND s.session_date <= $${params.length}`;
    }

    let studentWhereClause = 'st.tenant_id = $1 AND st.deleted_at IS NULL';
    if (studentId) {
      params.push(studentId);
      studentWhereClause += ` AND st.id = $${params.length}`;
    }

    const reportSql = `
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
        count(CASE WHEN ar.status = 'LEAVE' THEN 1 END)::int as leave_sessions,
        count(CASE WHEN ar.status = 'LATE' THEN 1 END)::int as late_sessions,
        count(CASE WHEN ar.status = 'ON_DUTY' THEN 1 END)::int as on_duty_sessions
      FROM stu.students st
      JOIN core.persons p ON p.id = st.person_id
      LEFT JOIN acd.programs prog ON prog.id = st.program_id
      LEFT JOIN (
        SELECT r.id, r.student_id, r.status
        FROM acd.attendance_records r
        JOIN acd.attendance_sessions s ON s.id = r.session_id
        WHERE ${sessionFilters} AND r.deleted_at IS NULL
      ) ar ON ar.student_id = st.id
      WHERE ${studentWhereClause}
      GROUP BY st.id, st.enrollment_no, p.display_name, p.primary_email, prog.name, prog.code
      ORDER BY st.enrollment_no ASC;
    `;

    const reportRes = await query(reportSql, params);
    const SHORTAGE_THRESHOLD = 75; // 75% statutory shortage threshold (ACD-06 / DEC-14)

    let students = reportRes.rows.map((row: any) => {
      const total = Number(row.total_sessions) || 0;
      const attended = Number(row.attended_sessions) || 0;
      const percentage = total > 0 ? Math.round((attended / total) * 1000) / 10 : 100;
      const hasShortage = total > 0 && percentage < SHORTAGE_THRESHOLD;

      return {
        studentId: row.student_id,
        enrollmentNo: row.enrollment_no,
        studentName: row.student_name,
        primaryEmail: row.primary_email || '',
        programName: row.program_name || 'General Program',
        programCode: row.program_code || 'GEN',
        totalSessions: total,
        attendedSessions: attended,
        absentSessions: Number(row.absent_sessions) || 0,
        leaveSessions: Number(row.leave_sessions) || 0,
        lateSessions: Number(row.late_sessions) || 0,
        onDutySessions: Number(row.on_duty_sessions) || 0,
        attendancePercentage: percentage,
        hasShortage,
      };
    });

    if (shortageOnly) {
      students = students.filter((s: any) => s.hasShortage);
    }

    const totalStudents = students.length;
    const shortageCount = students.filter((s: any) => s.hasShortage).length;
    const totalPercentageSum = students.reduce((acc: number, s: any) => acc + s.attendancePercentage, 0);
    const averageAttendance =
      totalStudents > 0 ? Math.round((totalPercentageSum / totalStudents) * 10) / 10 : 0;

    // Handle CSV Export
    if (format === 'csv') {
      const csvHeader = [
        'Enrollment No',
        'Student Name',
        'Email',
        'Program',
        'Total Sessions',
        'Attended Sessions',
        'Absent Sessions',
        'Leave Sessions',
        'Attendance %',
        'Shortage Status',
      ].join(',');

      const csvRows = students.map((s: any) =>
        [
          escapeCsvField(s.enrollmentNo),
          escapeCsvField(s.studentName),
          escapeCsvField(s.primaryEmail),
          escapeCsvField(`${s.programCode} - ${s.programName}`),
          escapeCsvField(s.totalSessions),
          escapeCsvField(s.attendedSessions),
          escapeCsvField(s.absentSessions),
          escapeCsvField(s.leaveSessions),
          escapeCsvField(`${s.attendancePercentage}%`),
          escapeCsvField(s.hasShortage ? 'SHORTAGE (<75%)' : 'REGULAR'),
        ].join(',')
      );

      const csvContent = [csvHeader, ...csvRows].join('\r\n');
      return new NextResponse(csvContent, {
        status: 200,
        headers: {
          'Content-Type': 'text/csv; charset=utf-8',
          'Content-Disposition': `attachment; filename="attendance-report-${new Date().toISOString().split('T')[0]}.csv"`,
          'Cache-Control': 'no-store',
        },
      });
    }

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
    console.error('GET /api/college/attendance/report error:', err);
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
