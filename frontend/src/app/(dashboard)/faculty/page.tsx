import { redirect } from 'next/navigation';
import { Clock, CalendarCheck2, CheckCircle2, AlertCircle, ArrowRight } from 'lucide-react';
import Link from 'next/link';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
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

  const { displayName } = resolution.session;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
            Faculty Teaching Workspace
          </h1>
          <p className="text-xs text-[#6B7688] mt-1">
            Welcome, {displayName}. View today&apos;s assigned timetable slots and record student attendance.
          </p>
        </div>

        <Link href="/faculty/attendance">
          <Button variant="primary" size="sm">
            <CalendarCheck2 className="mr-1.5 h-4 w-4" />
            Mark Today&apos;s Attendance
          </Button>
        </Link>
      </div>

      {/* KPI Row */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard
          title="Today&apos;s Scheduled Slots"
          value="2 Slots"
          icon={Clock}
          description="Assigned timetable periods"
        />
        <StatCard
          title="Sessions Awaiting Attendance"
          value="1"
          icon={AlertCircle}
          description="Open for submission"
        />
        <StatCard
          title="Submitted This Week"
          value="8"
          icon={CheckCircle2}
          description="Archived and locked"
        />
      </div>

      {/* Today's Classes Card */}
      <Card>
        <CardHeader>
          <CardTitle>Today&apos;s Assigned Classes</CardTitle>
          <CardDescription>
            Valid timetable slots for attendance capture
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-3">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between rounded-[8px] border border-[#E3E8EF] p-4 hover:border-[#1F6FEB] transition-colors gap-3">
              <div className="flex items-start gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[8px] bg-[#E8F0FE] text-[#1F6FEB]">
                  <Clock className="h-5 w-5" />
                </div>
                <div>
                  <h4 className="text-sm font-semibold text-[#0B1F3A]">
                    CS301 — Operating Systems & Distributed Kernels
                  </h4>
                  <p className="text-xs text-[#6B7688] mt-0.5">
                    Batch: CSE-2024-A · Room 302 · Period 2 (10:00 AM – 11:00 AM)
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-3">
                <Badge status="OPEN">Awaiting Attendance</Badge>
                <Link href="/faculty/attendance">
                  <Button variant="primary" size="sm">
                    Mark Attendance
                  </Button>
                </Link>
              </div>
            </div>

            <div className="flex flex-col sm:flex-row sm:items-center justify-between rounded-[8px] border border-[#E3E8EF] p-4 bg-[#F6F8FB]/50 gap-3">
              <div className="flex items-start gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[8px] bg-[#EAF8F1] text-[#1E9E6A]">
                  <CheckCircle2 className="h-5 w-5" />
                </div>
                <div>
                  <h4 className="text-sm font-semibold text-[#0B1F3A]">
                    CS302 — Database Management Systems
                  </h4>
                  <p className="text-xs text-[#6B7688] mt-0.5">
                    Batch: CSE-2024-B · Room 204 · Period 1 (09:00 AM – 10:00 AM)
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-3">
                <Badge status="SUBMITTED">Submitted</Badge>
                <Button variant="outline" size="sm" disabled>
                  Submitted (Read-Only)
                </Button>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
