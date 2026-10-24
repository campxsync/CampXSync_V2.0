import { redirect } from 'next/navigation';
import { History, CalendarCheck2, CheckCircle2, Clock, Users, ArrowRight } from 'lucide-react';
import Link from 'next/link';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { StatCard } from '@/components/ui/stat-card';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { formatDate } from '@/lib/utils';

export const dynamic = 'force-dynamic';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export default async function FacultySessionsPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || (resolution.session.role !== 'faculty' && resolution.session.role !== 'college_admin')) {
    redirect('/access-denied?reason=forbidden');
  }

  const { tenantId, displayName } = resolution.session;
  if (!tenantId) {
    redirect('/access-denied?reason=forbidden');
  }

  let sessions: any[] = [];
  try {
    const gatewayUrl = new URL('/api/v1/academics/attendance/sessions', GATEWAY_BASE_URL);
    const headers: Record<string, string> = {
      'Accept': 'application/json',
      'X-User-Id': resolution.session.userId || '00000000-0000-0000-0000-000000000001',
      'X-Tenant-Id': tenantId,
      'X-User-Role': resolution.session.role || 'faculty',
    };
    if (resolution.session.collegeId) {
      headers['X-College-Id'] = resolution.session.collegeId;
    }

    const res = await fetch(gatewayUrl.toString(), {
      method: 'GET',
      headers,
      cache: 'no-store',
    });

    if (res.ok) {
      const data = await res.json();
      const rawList = Array.isArray(data.data) ? data.data : (Array.isArray(data) ? data : []);
      sessions = rawList.map((sess: any) => ({
        id: sess.id,
        session_date: sess.sessionDate || sess.attendanceDate || sess.session_date,
        start_time: sess.startTime || sess.start_time,
        end_time: sess.endTime || sess.end_time,
        status: sess.status,
        created_at: sess.createdAt || sess.created_at,
        locked_at: sess.lockedAt || sess.locked_at,
        timetable_entry_id: sess.timetableEntryId || sess.timetable_entry_id,
        subject_code: sess.subjectCode || sess.subject_code || 'GEN',
        subject_name: sess.subjectName || sess.subject_name || 'Class Session',
        section_name: sess.sectionName || sess.section_name || 'Section A',
        total_students_marked: sess.totalStudentsMarked || sess.total_students_marked || 0,
        present_count: sess.presentCount || sess.present_count || 0,
        absent_count: sess.absentCount || sess.absent_count || 0,
      }));
    }
  } catch (err) {
    console.error('Failed to query faculty sessions via API gateway:', err);
  }

  const closedSessions = sessions.filter((s) => s.status === 'CLOSED');
  const openSessions = sessions.filter((s) => s.status === 'OPEN');

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
            Teaching Sessions & Attendance Archive
          </h1>
          <p className="text-xs text-[#6B7688] mt-1">
            Historical attendance records and active class sessions conducted by {displayName}.
          </p>
        </div>

        <Link href="/faculty/attendance">
          <Button variant="primary" size="sm">
            <CalendarCheck2 className="mr-1.5 h-4 w-4" />
            Conduct New Session
          </Button>
        </Link>
      </div>

      {/* KPI Stats */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard
          title="Total Sessions Conducted"
          value={sessions.length.toString()}
          icon={History}
          description="Institutional academic ledger"
        />
        <StatCard
          title="Archived & Verified"
          value={closedSessions.length.toString()}
          icon={CheckCircle2}
          description="Locked attendance sheets"
        />
        <StatCard
          title="Awaiting Submission"
          value={openSessions.length.toString()}
          icon={Clock}
          description="In progress or draft slots"
        />
      </div>

      {/* Sessions Table */}
      <Card>
        <CardHeader>
          <CardTitle>Attendance Sessions History ({sessions.length})</CardTitle>
          <CardDescription>
            Chronological audit of conducted timetable periods and student headcounts
          </CardDescription>
        </CardHeader>
        <CardContent>
          {sessions.length === 0 ? (
            <div className="text-center py-8 text-xs text-[#6B7688]">
              No teaching sessions have been created or submitted yet.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                    <th className="pb-3 font-semibold uppercase tracking-wider">Session Date & Time</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Curriculum Subject</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Cohort Section</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider text-center">Status</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider text-center">Roster Headcount</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E3E8EF]">
                  {sessions.map((sess) => {
                    const isClosed = sess.status === 'CLOSED';
                    return (
                      <tr key={sess.id} className="hover:bg-[#F6F8FB]">
                        <td className="py-3.5 font-medium text-[#0B1F3A]">
                          <div>{formatDate(sess.session_date)}</div>
                          <div className="text-[11px] text-[#6B7688] font-mono">
                            {sess.start_time?.slice(0, 5)} - {sess.end_time?.slice(0, 5)}
                          </div>
                        </td>
                        <td className="py-3.5">
                          <span className="font-semibold text-[#0B1F3A]">{sess.subject_name || 'Academic Class'}</span>
                          <span className="ml-1.5 font-mono text-[11px] text-[#6B7688]">
                            ({sess.subject_code || 'SUBJ'})
                          </span>
                        </td>
                        <td className="py-3.5 text-[#1B2433]">
                          {sess.section_name || 'Section 1'}
                        </td>
                        <td className="py-3.5 text-center">
                          <Badge status={sess.status}>{sess.status}</Badge>
                        </td>
                        <td className="py-3.5 text-center font-mono">
                          {sess.total_students_marked > 0 ? (
                            <span className="text-xs">
                              <span className="text-[#1E9E6A] font-semibold">{sess.present_count}P</span>
                              {' / '}
                              <span className="text-[#D9383A] font-semibold">{sess.absent_count}A</span>
                              <span className="text-[#6B7688] text-[11px]"> ({sess.total_students_marked} total)</span>
                            </span>
                          ) : (
                            <span className="text-[#6B7688] text-[11px] italic">Not submitted</span>
                          )}
                        </td>
                        <td className="py-3.5 text-right">
                          <Link href={`/faculty/attendance?slotId=${sess.timetable_entry_id || ''}&sessionId=${sess.id}`}>
                            <Button variant={isClosed ? 'outline' : 'primary'} size="sm">
                              {isClosed ? 'Review Sheet' : 'Mark Roster'}
                            </Button>
                          </Link>
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
    </div>
  );
}
