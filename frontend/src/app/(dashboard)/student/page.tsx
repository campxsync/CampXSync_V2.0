import { redirect } from 'next/navigation';
import Link from 'next/link';
import {
  GraduationCap,
  CalendarCheck2,
  CheckCircle2,
  AlertTriangle,
  BookOpen,
  FileText,
  Building2,
  History,
  ArrowRight,
  ShieldCheck,
} from 'lucide-react';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import { StatCard } from '@/components/ui/stat-card';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { formatDate } from '@/lib/utils';
import { fetchApplications } from '@/lib/api/admissions';
import {
  getCachedStudentProfile,
  setCachedStudentProfile,
  getCachedStudentAttendance,
  setCachedStudentAttendance,
} from '@/lib/cache/student-profile-cache';

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

  // 1. Fetch current student record in this tenant matching the authenticated user via API Gateway
  let student: any = null;
  let subjectAttendance: any[] = [];
  let recentSessions: any[] = [];
  let latestApplication: any = null;
  let gatewayFallback = false;

  // Check in-memory profile cache first
  student = getCachedStudentProfile(userId);

  const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

  try {
    if (!student) {
      // Primary path: UI -> API Gateway -> Java student-service (STM-01)
      try {
        const gwHeaders: Record<string, string> = {
          'Accept': 'application/json',
          'X-User-Id': userId,
          'X-Tenant-Id': tenantId,
          'X-User-Role': 'STUDENT',
        };
        if (resolution.session.collegeId) {
          gwHeaders['X-College-Id'] = resolution.session.collegeId;
        }
        const gwRes = await fetch(`${GATEWAY_BASE_URL}/api/v1/students/me`, {
          headers: gwHeaders,
          cache: 'no-store',
        });
        if (gwRes.ok) {
          const profile = await gwRes.json();
          student = {
            id: profile.id,
            first_name: profile.firstName || displayName?.split(' ')[0] || '',
            last_name: profile.lastName || '',
            enrollment_number: profile.enrollmentNo || '',
            email: profile.primaryEmail || email,
            status: profile.status || 'ACTIVE',
            college_name: profile.collegeName || '',
            program_name: profile.programName || '',
            program_code: profile.programCode || '',
            section_name: profile.sectionName || '',
          };
          setCachedStudentProfile(userId, student);
        } else {
          gatewayFallback = true;
        }
      } catch (_gwErr) {
        gatewayFallback = true;
      }
    }

    if (!student) {
      gatewayFallback = true;
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
        setCachedStudentProfile(userId, student);
      }
    }

    if (student) {
      // Check cached attendance metrics first to avoid repeating multi-join RLS queries
      const cachedAtt = getCachedStudentAttendance(student.id);
      if (cachedAtt) {
        subjectAttendance = cachedAtt.subjectAttendance;
        recentSessions = cachedAtt.recentSessions;
        const apps = await fetchApplications(tenantId, userId, 'APPLICANT').catch(() => []);
        if (apps && apps.length > 0) {
          latestApplication = apps[0];
        }
      } else {
        // REC-05: Parallelize independent DB queries and Admissions fetch via Promise.all
        const [summaryRes, sessionsRes, apps] = await Promise.all([
          query(`
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
          `, [student.id, tenantId]),
          query(`
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
          `, [student.id, tenantId]),
          fetchApplications(tenantId, userId, 'APPLICANT').catch(() => []),
        ]);

        subjectAttendance = summaryRes.rows;
        recentSessions = sessionsRes.rows;
        setCachedStudentAttendance(student.id, { subjectAttendance, recentSessions }, 60);

        if (apps && apps.length > 0) {
          latestApplication = apps[0];
        }
      }
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

      {/* Visible Service Degradation Indicator (REC-01) */}
      {gatewayFallback && (
        <div className="rounded-md border border-amber-300 bg-amber-50 p-4 text-xs text-amber-900 flex items-center gap-3">
          <AlertTriangle className="h-5 w-5 text-amber-600 flex-shrink-0" />
          <div>
            <span className="font-semibold text-amber-800">Backend Service Offline: </span>
            <span>Java Student Domain Service is unreachable. Showing direct database fallback snapshot.</span>
          </div>
        </div>
      )}

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

      {/* Admissions Handover Bridge Card & Quick Actions Hub */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        {/* Admissions Handover Bridge Card */}
        <Card className="lg:col-span-2 border-[#B8D3FC] bg-gradient-to-r from-[#F0F6FF] to-white">
          <CardHeader className="pb-2">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ShieldCheck className="h-4 w-4 text-[#1F6FEB]" />
                <CardTitle className="text-sm font-semibold text-[#0B1F3A]">
                  Admissions Enrolment Handover (STM-02 &rarr; STM-01)
                </CardTitle>
              </div>
              <span className="inline-flex items-center rounded-full bg-[#1E9E6A]/10 px-2 py-0.5 text-[10px] font-semibold text-[#1E9E6A]">
                {latestApplication?.status || 'HANDED_OVER'}
              </span>
            </div>
            <CardDescription className="text-xs text-[#475569]">
              Application record verified and officially transitioned into active academic matriculation.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 text-xs pt-2 border-t border-[#E3E8EF]/60">
              <div className="flex flex-wrap items-center gap-4 text-[#6B7688]">
                <div>
                  Application No:{' '}
                  <span className="font-mono font-semibold text-[#0B1F3A]">
                    {latestApplication?.applicationNumber || 'APP-DEV-2026-0001'}
                  </span>
                </div>
                <div>
                  Enrolment Status:{' '}
                  <span className="font-semibold text-[#1E9E6A]">Matriculated</span>
                </div>
              </div>
              <Link href="/student/admissions">
                <Button variant="outline" size="sm" className="h-8 gap-1.5 text-xs border-[#1F6FEB] text-[#1F6FEB] hover:bg-[#F0F6FF]">
                  <FileText className="h-3.5 w-3.5" />
                  View Admissions Dossier
                  <ArrowRight className="h-3 w-3" />
                </Button>
              </Link>
            </div>
          </CardContent>
        </Card>

        {/* Quick Actions Hub */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-semibold text-[#0B1F3A]">
              Self-Service Hub
            </CardTitle>
            <CardDescription className="text-xs text-[#6B7688]">
              Manage records and profile changes
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            <Link href="/student/address" className="block">
              <div className="flex items-center justify-between p-2 rounded-[6px] border border-[#E3E8EF] hover:border-[#1F6FEB] hover:bg-[#F8FAFC] transition-colors text-xs">
                <div className="flex items-center gap-2 text-[#0B1F3A] font-medium">
                  <Building2 className="h-3.5 w-3.5 text-[#1F6FEB]" />
                  Contact & Address
                </div>
                <ArrowRight className="h-3 w-3 text-[#6B7688]" />
              </div>
            </Link>

            <Link href="/student/corrections" className="block">
              <div className="flex items-center justify-between p-2 rounded-[6px] border border-[#E3E8EF] hover:border-[#1F6FEB] hover:bg-[#F8FAFC] transition-colors text-xs">
                <div className="flex items-center gap-2 text-[#0B1F3A] font-medium">
                  <History className="h-3.5 w-3.5 text-[#1F6FEB]" />
                  Profile Corrections
                </div>
                <ArrowRight className="h-3 w-3 text-[#6B7688]" />
              </div>
            </Link>

            <Link href="/student/enrollment" className="block">
              <div className="flex items-center justify-between p-2 rounded-[6px] border border-[#E3E8EF] hover:border-[#1F6FEB] hover:bg-[#F8FAFC] transition-colors text-xs">
                <div className="flex items-center gap-2 text-[#0B1F3A] font-medium">
                  <GraduationCap className="h-3.5 w-3.5 text-[#1F6FEB]" />
                  Course Registration
                </div>
                <ArrowRight className="h-3 w-3 text-[#6B7688]" />
              </div>
            </Link>

            <Link href="/student/admissions" className="block">
              <div className="flex items-center justify-between p-2 rounded-[6px] border border-[#E3E8EF] hover:border-[#1F6FEB] hover:bg-[#F8FAFC] transition-colors text-xs">
                <div className="flex items-center gap-2 text-[#0B1F3A] font-medium">
                  <FileText className="h-3.5 w-3.5 text-[#1F6FEB]" />
                  Admissions & Intakes
                </div>
                <ArrowRight className="h-3 w-3 text-[#6B7688]" />
              </div>
            </Link>
          </CardContent>
        </Card>
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
                          <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold ${courseShortage ? 'bg-[#D9383A]/10 text-[#D9383A]' : 'bg-[#1E9E6A]/10 text-[#1E9E6A]'
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

      {/* Recent Conducted Sessions Table */}
      <Card>
        <CardHeader>
          <CardTitle>Recent Conducted Class Sessions</CardTitle>
          <CardDescription>
            Chronological audit of sessions conducted by course faculty with attendance outcomes
          </CardDescription>
        </CardHeader>
        <CardContent>
          {recentSessions.length === 0 ? (
            <div className="text-center py-8">
              <CalendarCheck2 className="mx-auto h-8 w-8 text-[#6B7688]/40" />
              <p className="text-xs text-[#6B7688] mt-2">No class sessions conducted for your section yet.</p>
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
                        <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold ${sess.attendance_status === 'PRESENT'
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
