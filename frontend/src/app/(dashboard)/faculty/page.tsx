import { redirect } from 'next/navigation';
import { Clock, CalendarCheck2, CheckCircle2, AlertCircle, ArrowRight, BookOpen } from 'lucide-react';
import Link from 'next/link';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import { StatCard } from '@/components/ui/stat-card';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

export const dynamic = 'force-dynamic';

export default async function FacultyDashboardPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || (resolution.session.role !== 'faculty' && resolution.session.role !== 'college_admin')) {
    redirect('/access-denied?reason=forbidden');
  }

  const { tenantId, displayName } = resolution.session;

  let assignedSlots: any[] = [];
  let submittedCount = 0;
  let openCount = 0;

  try {
    const slotsRes = await query(`
      SELECT 
        tte.id,
        tte.day_of_week,
        tte.start_time,
        tte.end_time,
        sub.name as subject_name,
        sub.code as subject_code,
        sec.section_code as section_name,
        sess.id as session_id,
        sess.status as session_status
      FROM acd.timetable_entries tte
      JOIN acd.subjects sub ON sub.id = tte.subject_id
      LEFT JOIN acd.course_sections sec ON sec.id = tte.section_id
      LEFT JOIN acd.attendance_sessions sess ON sess.timetable_entry_id = tte.id AND sess.deleted_at IS NULL
      WHERE tte.tenant_id = $1 AND tte.deleted_at IS NULL
      ORDER BY tte.day_of_week ASC, tte.start_time ASC
    `, [tenantId || '11111111-1111-1111-1111-111111111111']);

    assignedSlots = slotsRes.rows;

    const sessionStatsRes = await query(`
      SELECT 
        count(CASE WHEN status = 'CLOSED' THEN 1 END)::int as closed_count,
        count(CASE WHEN status = 'OPEN' THEN 1 END)::int as open_count
      FROM acd.attendance_sessions
      WHERE tenant_id = $1 AND deleted_at IS NULL
    `, [tenantId || '11111111-1111-1111-1111-111111111111']);

    if (sessionStatsRes.rows.length > 0) {
      submittedCount = sessionStatsRes.rows[0].closed_count || 0;
      openCount = sessionStatsRes.rows[0].open_count || 0;
    }
  } catch (err) {
    console.error('Error fetching faculty dashboard data:', err);
  }

  const dayNames = ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
            Faculty Teaching Workspace
          </h1>
          <p className="text-xs text-[#6B7688] mt-1">
            Welcome, {displayName}. View your assigned timetable slots and record student attendance.
          </p>
        </div>

        <Link href="/faculty/attendance">
          <Button variant="primary" size="sm">
            <CalendarCheck2 className="mr-1.5 h-4 w-4" />
            Mark Attendance Sheet
          </Button>
        </Link>
      </div>

      {/* KPI Row */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard
          title="Assigned Timetable Slots"
          value={`${assignedSlots.length} Slots`}
          icon={Clock}
          description="Institutional curriculum entries"
        />
        <StatCard
          title="Sessions Awaiting Attendance"
          value={openCount.toString()}
          icon={AlertCircle}
          description="Open for faculty submission"
        />
        <StatCard
          title="Submitted Sessions"
          value={submittedCount.toString()}
          icon={CheckCircle2}
          description="Verified and locked records"
        />
      </div>

      {/* Today's Classes Card */}
      <Card>
        <CardHeader>
          <CardTitle>Assigned Teaching Schedule & Slots</CardTitle>
          <CardDescription>
            Valid timetable slots linked directly to student sections and attendance rosters
          </CardDescription>
        </CardHeader>
        <CardContent>
          {assignedSlots.length === 0 ? (
            <div className="text-center py-8">
              <BookOpen className="mx-auto h-8 w-8 text-[#6B7688]/40" />
              <p className="text-xs text-[#6B7688] mt-2">No assigned timetable slots found for this institutional semester.</p>
            </div>
          ) : (
            <div className="space-y-3">
              {assignedSlots.map((slot) => {
                const isClosed = slot.session_status === 'CLOSED';
                const isOpen = slot.session_status === 'OPEN';

                return (
                  <div 
                    key={slot.id} 
                    className="flex flex-col sm:flex-row sm:items-center justify-between rounded-[8px] border border-[#E3E8EF] p-4 hover:border-[#1F6FEB] transition-colors gap-3"
                  >
                    <div className="flex items-start gap-3">
                      <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-[8px] ${
                        isClosed ? 'bg-[#EAF8F1] text-[#1E9E6A]' : 'bg-[#E8F0FE] text-[#1F6FEB]'
                      }`}>
                        {isClosed ? <CheckCircle2 className="h-5 w-5" /> : <Clock className="h-5 w-5" />}
                      </div>
                      <div>
                        <h4 className="text-sm font-semibold text-[#0B1F3A]">
                          {slot.subject_code} — {slot.subject_name}
                        </h4>
                        <p className="text-xs text-[#6B7688] mt-0.5">
                          {dayNames[slot.day_of_week] || 'Day'} · {slot.start_time?.slice(0, 5)} - {slot.end_time?.slice(0, 5)} · Section: {slot.section_name || 'General'}
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-3">
                      <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold ${
                        isClosed 
                          ? 'bg-[#1E9E6A]/10 text-[#1E9E6A]'
                          : isOpen 
                          ? 'bg-[#1F6FEB]/10 text-[#1F6FEB]'
                          : 'bg-[#F6F8FB] text-[#6B7688] border border-[#E3E8EF]'
                      }`}>
                        {isClosed ? 'Submitted' : isOpen ? 'In Progress' : 'Pending Capture'}
                      </span>

                      <Link href="/faculty/attendance">
                        <Button variant={isClosed ? 'outline' : 'primary'} size="sm">
                          {isClosed ? 'View Session' : 'Mark Attendance'}
                        </Button>
                      </Link>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

