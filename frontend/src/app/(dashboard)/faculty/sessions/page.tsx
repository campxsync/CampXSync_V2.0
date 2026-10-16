import { redirect } from 'next/navigation';
import { History, CalendarCheck2, CheckCircle2, Clock, Users, ArrowRight } from 'lucide-react';
import Link from 'next/link';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import { StatCard } from '@/components/ui/stat-card';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { formatDate } from '@/lib/utils';

export const dynamic = 'force-dynamic';

export default async function FacultySessionsPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || (resolution.session.role !== 'faculty' && resolution.session.role !== 'college_admin')) {
    redirect('/access-denied?reason=forbidden');
  }

  const { tenantId, displayName } = resolution.session;

  let sessions: any[] = [];
  try {
    const res = await query(`
      SELECT 
        sess.id,
        sess.session_date,
        sess.start_time,
        sess.end_time,
        sess.status,
        sess.created_at,
        sess.locked_at,
        te.id as timetable_entry_id,
        sub.code as subject_code,
        sub.name as subject_name,
        sec.section_code as section_name,
        count(ar.id)::int as total_students_marked,
        count(CASE WHEN ar.status = 'PRESENT' THEN 1 END)::int as present_count,
        count(CASE WHEN ar.status = 'ABSENT' THEN 1 END)::int as absent_count
      FROM acd.attendance_sessions sess
      LEFT JOIN acd.timetable_entries te ON te.id = sess.timetable_entry_id
      LEFT JOIN acd.subjects sub ON sub.id = te.subject_id
      LEFT JOIN acd.course_sections sec ON sec.id = sess.section_id
      LEFT JOIN acd.attendance_records ar ON ar.session_id = sess.id
      WHERE sess.tenant_id = $1 AND sess.deleted_at IS NULL
      GROUP BY sess.id, sess.session_date, sess.start_time, sess.end_time, sess.status, sess.created_at, sess.locked_at, te.id, sub.code, sub.name, sec.section_code
      ORDER BY sess.session_date DESC, sess.start_time DESC
    `, [tenantId || '11111111-1111-1111-1111-111111111111']);

    sessions = res.rows;
  } catch (err) {
    console.error('Failed to query faculty sessions:', err);
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
