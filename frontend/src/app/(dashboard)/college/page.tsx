import { redirect } from 'next/navigation';
import {
  Users,
  GraduationCap,
  Calendar,
  CheckCircle2,
  AlertTriangle,
  ArrowRight,
  ClipboardList
} from 'lucide-react';
import Link from 'next/link';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { StatCard } from '@/components/ui/stat-card';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

export const dynamic = 'force-dynamic';

export default async function CollegeAdminDashboardPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'college_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  const { tenantId, tenantName } = resolution.session;

  // Real database counts from Supabase staging
  let studentCount = 0;
  let employeeCount = 0;
  let sessionCount = 0;

  try {
    const { count: sCount } = await supabase
      .schema('stu')
      .from('students')
      .select('id', { count: 'exact' });
    studentCount = sCount || 0;

    const { count: eCount } = await supabase
      .schema('hrm')
      .from('employees')
      .select('id', { count: 'exact' });
    employeeCount = eCount || 0;

    const { count: sesCount } = await supabase
      .schema('acd')
      .from('attendance_sessions')
      .select('id', { count: 'exact' });
    sessionCount = sesCount || 0;
  } catch (err) {
    console.error('College admin aggregate query error:', err);
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
            College Operations Dashboard
          </h1>
          <p className="text-xs text-[#6B7688] mt-1">
            Institutional overview, roster management, and timetable-linked attendance governance.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Link href="/college/attendance">
            <Button variant="primary" size="sm">
              <ClipboardList className="mr-1.5 h-4 w-4" />
              Manage Attendance
            </Button>
          </Link>
        </div>
      </div>

      {/* KPI Row */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          title="Enrolled Students"
          value={studentCount}
          icon={GraduationCap}
          description="Active batch memberships"
        />
        <StatCard
          title="Appointed Faculty"
          value={employeeCount}
          icon={Users}
          description="Department teaching faculty"
        />
        <StatCard
          title="Attendance Sessions"
          value={sessionCount}
          icon={Calendar}
          description="Timetable slot sessions"
        />
        <StatCard
          title="Shortage Alerts"
          value={0}
          icon={AlertTriangle}
          description="Students below 75% threshold"
        />
      </div>

      {/* Overview Cards */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Academic Timetable Governance</CardTitle>
            <CardDescription>
              Published class periods, room allocations, and assigned faculty
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <p className="text-xs text-[#6B7688]">
              All attendance capture requires an approved, published timetable slot. Slots enforce strict batch roster membership and unique business keys.
            </p>
            <div className="pt-2">
              <Link href="/college/timetable">
                <Button variant="outline" size="sm">
                  View Timetable Grid
                  <ArrowRight className="ml-1.5 h-3.5 w-3.5" />
                </Button>
              </Link>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Audited Attendance Corrections</CardTitle>
            <CardDescription>
              ACD-06 correction trail with mandatory reason logging
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <p className="text-xs text-[#6B7688]">
              Submitted attendance records cannot be altered by faculty. College administrators may submit corrections which append immutable audit records.
            </p>
            <div className="pt-2">
              <Link href="/college/attendance">
                <Button variant="outline" size="sm">
                  Review Attendance Records
                  <ArrowRight className="ml-1.5 h-3.5 w-3.5" />
                </Button>
              </Link>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
