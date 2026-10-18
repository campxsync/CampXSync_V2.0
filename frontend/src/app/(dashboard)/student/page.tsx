import { redirect } from 'next/navigation';
import { GraduationCap, CalendarCheck2, CheckCircle2, AlertTriangle, Clock, BookOpen } from 'lucide-react';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import { StatCard } from '@/components/ui/stat-card';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { formatDate } from '@/lib/utils';

export const dynamic = 'force-dynamic';

export default async function StudentDashboardPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'student') {
    redirect('/access-denied?reason=forbidden');
  }

  const { tenantId, displayName, userId, email } = resolution.session;

  if (!tenantId) {
    redirect('/access-denied?reason=forbidden');
  }

  // 1. Fetch current student record in this tenant matching the authenticated user
  let student: any = null;
  let subjectAttendance: any[] = [];
  let recentSessions: any[] = [];

  try {
    const studentRes = await query(`
      SELECT 
        s.id, 
        p.first_name, 
        p.last_name, 
        s.enrollment_no as enrollment_number, 
        p.primary_email as email,
        s.status,
        c.name as college_name,
        prog.name as program_name,
        prog.code as program_code,
        b.name as section_name
      FROM stu.students s
      LEFT JOIN core.persons p ON p.id = s.person_id
      LEFT JOIN core.colleges c ON c.id = s.college_id
      LEFT JOIN acd.programs prog ON prog.id = s.program_id
      LEFT JOIN acd.batch_students bs ON bs.student_id = s.id
      LEFT JOIN acd.batches b ON b.id = bs.batch_id
      WHERE s.tenant_id = $1 
        AND s.deleted_at IS NULL 
        AND (p.id = $2 OR p.primary_email = $3)
      ORDER BY s.enrollment_no ASC
      LIMIT 1
    `, [tenantId, userId, email]);

    if (studentRes.rows.length > 0) {
      student = studentRes.rows[0];

      // 2. Fetch course-wise attendance breakdown
      const summaryRes = await query(`
        SELECT 
          sub.id as subject_id,
          sub.code as subject_code,
          sub.name as subject_name,
          count(DISTINCT sess.id)::int as total_conducted,
          count(DISTINCT CASE WHEN ar.status = 'PRESENT' OR ar.status = 'ON_DUTY' THEN sess.id END)::int as attended_count,
          count(DISTINCT CASE WHEN ar.status = 'ABSENT' THEN sess.id END)::int as absent_count
        FROM acd.subjects sub
        JOIN acd.timetable_entries te ON te.subject_id = sub.id AND te.deleted_at IS NULL
        LEFT JOIN acd.attendance_sessions sess ON sess.timetable_entry_id = te.id AND sess.status = 'CLOSED' AND sess.deleted_at IS NULL
        LEFT JOIN acd.attendance_records ar ON ar.session_id = sess.id AND ar.student_id = $1
        WHERE sub.tenant_id = $2 AND sub.deleted_at IS NULL
        GROUP BY sub.id, sub.code, sub.name
        ORDER BY sub.code ASC
      `, [student.id, tenantId]);

      subjectAttendance = summaryRes.rows;

      // 3. Fetch recent session logs
      const sessionsRes = await query(`
        SELECT 
          sess.id,
          sess.session_date,
          sess.start_time,
          sess.end_time,
          sub.code as subject_code,
          sub.name as subject_name,
          ar.status as attendance_status,
          ar.remarks,
          ar.marked_at,
          p.display_name as faculty_name
        FROM acd.attendance_records ar
        JOIN acd.attendance_sessions sess ON sess.id = ar.session_id AND sess.deleted_at IS NULL
        LEFT JOIN acd.timetable_entries te ON te.id = sess.timetable_entry_id
        LEFT JOIN acd.subjects sub ON sub.id = te.subject_id
        LEFT JOIN hrm.employees e ON e.id = sess.employee_id
        LEFT JOIN core.persons p ON p.id = e.person_id
        WHERE ar.student_id = $1 AND ar.tenant_id = $2
        ORDER BY sess.session_date DESC, sess.start_time DESC
        LIMIT 10
      `, [student.id, tenantId]);

      recentSessions = sessionsRes.rows;
    }
  } catch (err) {
    console.error('Failed to query student attendance data:', err);
  }

  // Calculate totals
  const totalConducted = subjectAttendance.reduce((acc, row) => acc + (row.total_conducted || 0), 0);
  const totalAttended = subjectAttendance.reduce((acc, row) => acc + (row.attended_count || 0), 0);
  const overallPercentage = totalConducted > 0 
    ? Math.round((totalAttended / totalConducted) * 1000) / 10 
    : 100;
  const isShortage = overallPercentage < 75.0 && totalConducted > 0;

  return (
    <div className="space-y-6">
      {/* Student Welcome Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
            My Academic Portal & Attendance
          </h1>
          <p className="text-xs text-[#6B7688] mt-1">
            Welcome, {student ? `${student.first_name} ${student.last_name}` : displayName}. Student Enrollment: <span className="font-mono font-semibold text-[#1F6FEB]">{student?.enrollment_number || 'N/A'}</span> &bull; {student?.program_name || 'Academic Program'} ({student?.section_name || 'General Section'})
          </p>
        </div>

        {student && (
          <Badge status={isShortage ? 'CANCELLED' : 'ACTIVE'}>
            {isShortage ? 'Shortage Alert (<75%)' : 'Attendance Good Standing'}
          </Badge>
        )}
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard
          title="Overall Attendance"
          value={`${overallPercentage}%`}
          icon={isShortage ? AlertTriangle : CheckCircle2}
          description={isShortage ? 'Below required 75% threshold' : 'Complies with 75% institutional requirement'}
        />
        <StatCard
          title="Total Classes Attended"
          value={`${totalAttended} / ${totalConducted}`}
          icon={CalendarCheck2}
          description="Verified timetable sessions"
        />
        <StatCard
          title="Academic Standing"
          value={isShortage ? 'At Risk' : 'Satisfactory'}
          icon={GraduationCap}
          description={student?.college_name || 'Institutional Campus'}
        />
      </div>

      {/* Course-Wise Attendance Table */}
      <Card>
        <CardHeader>
          <CardTitle>Course-Wise Attendance Summary</CardTitle>
          <CardDescription>
            Live registered subject attendance queried directly from institutional academic records
          </CardDescription>
        </CardHeader>
        <CardContent>
          {subjectAttendance.length === 0 ? (
            <div className="text-center py-8">
              <BookOpen className="mx-auto h-8 w-8 text-[#6B7688]/40" />
              <p className="text-xs text-[#6B7688] mt-2">No active course registrations or conducted sessions recorded yet.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                    <th className="pb-3 font-semibold uppercase tracking-wider">Course / Subject</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider text-center">Conducted</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider text-center">Attended</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider text-center">Absent</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider text-right">Percentage</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider text-center">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E3E8EF]">
                  {subjectAttendance.map((row) => {
                    const pct = row.total_conducted > 0 
                      ? Math.round((row.attended_count / row.total_conducted) * 1000) / 10 
                      : 100;
                    const courseShortage = pct < 75.0 && row.total_conducted > 0;

                    return (
                      <tr key={row.subject_id} className="hover:bg-[#F6F8FB]">
                        <td className="py-3 font-medium text-[#0B1F3A]">
                          <span className="font-semibold">{row.subject_name}</span>
                          <span className="ml-2 font-mono text-[11px] text-[#6B7688]">({row.subject_code})</span>
                        </td>
                        <td className="py-3 font-mono text-center">{row.total_conducted}</td>
                        <td className="py-3 font-mono text-center text-[#1E9E6A] font-semibold">{row.attended_count}</td>
                        <td className="py-3 font-mono text-center text-[#D9383A]">{row.absent_count}</td>
                        <td className="py-3 font-mono font-semibold text-right tabular-nums">
                          <span className={courseShortage ? 'text-[#D9383A]' : 'text-[#1E9E6A]'}>
                            {pct}%
                          </span>
                        </td>
                        <td className="py-3 text-center">
                          <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold ${
                            courseShortage ? 'bg-[#D9383A]/10 text-[#D9383A]' : 'bg-[#1E9E6A]/10 text-[#1E9E6A]'
                          }`}>
                            {courseShortage ? 'Shortage Alert' : 'Satisfactory'}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Recent Attendance Session Logs */}
      <Card>
        <CardHeader>
          <CardTitle>Recent Attendance History</CardTitle>
          <CardDescription>
            Individual session records verified by appointed instructors
          </CardDescription>
        </CardHeader>
        <CardContent>
          {recentSessions.length === 0 ? (
            <div className="text-center py-6 text-xs text-[#6B7688]">
              No closed attendance sessions recorded for this semester yet.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                    <th className="pb-3 font-semibold uppercase tracking-wider">Date & Time</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Subject</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Faculty Instructor</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider text-center">Status</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Remarks</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E3E8EF]">
                  {recentSessions.map((sess) => (
                    <tr key={sess.id} className="hover:bg-[#F6F8FB]">
                      <td className="py-3 font-medium text-[#0B1F3A]">
                        <div>{formatDate(sess.session_date)}</div>
                        <div className="text-[11px] text-[#6B7688] font-mono">{sess.start_time?.slice(0, 5)} - {sess.end_time?.slice(0, 5)}</div>
                      </td>
                      <td className="py-3">
                        <span className="font-semibold text-[#0B1F3A]">{sess.subject_name}</span>
                        <span className="ml-1.5 font-mono text-[11px] text-[#6B7688]">({sess.subject_code})</span>
                      </td>
                      <td className="py-3 text-[#1B2433]">{sess.faculty_name || 'Faculty Member'}</td>
                      <td className="py-3 text-center">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold ${
                          sess.attendance_status === 'PRESENT'
                            ? 'bg-[#1E9E6A]/10 text-[#1E9E6A]'
                            : sess.attendance_status === 'ABSENT'
                            ? 'bg-[#D9383A]/10 text-[#D9383A]'
                            : 'bg-[#F2994A]/10 text-[#F2994A]'
                        }`}>
                          {sess.attendance_status}
                        </span>
                      </td>
                      <td className="py-3 text-[#6B7688]">{sess.remarks || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

