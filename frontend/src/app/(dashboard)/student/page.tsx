import { redirect } from 'next/navigation';
import { GraduationCap, CalendarCheck2, CheckCircle2 } from 'lucide-react';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { StatCard } from '@/components/ui/stat-card';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';

export const dynamic = 'force-dynamic';

export default async function StudentDashboardPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success) {
    redirect('/access-denied?reason=forbidden');
  }

  const { displayName } = resolution.session;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
          My Academic Attendance
        </h1>
        <p className="text-xs text-[#6B7688] mt-1">
          Welcome, {displayName}. View your personal course attendance records.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard
          title="Overall Attendance"
          value="88.5%"
          icon={CheckCircle2}
          description="Above 75% threshold"
        />
        <StatCard
          title="Total Classes Attended"
          value="46 / 52"
          icon={CalendarCheck2}
          description="Recorded timetable sessions"
        />
        <StatCard
          title="Shortage Risk"
          value="None"
          icon={GraduationCap}
          description="Status: In Good Standing"
        />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Course-Wise Attendance Summary</CardTitle>
          <CardDescription>
            Personal attendance breakdown by registered subject
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                  <th className="pb-3 font-semibold uppercase tracking-wider">Course / Subject</th>
                  <th className="pb-3 font-semibold uppercase tracking-wider">Conducted</th>
                  <th className="pb-3 font-semibold uppercase tracking-wider">Attended</th>
                  <th className="pb-3 font-semibold uppercase tracking-wider">Percentage</th>
                  <th className="pb-3 font-semibold uppercase tracking-wider">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#E3E8EF]">
                <tr className="hover:bg-[#F6F8FB]">
                  <td className="py-3 font-medium text-[#0B1F3A]">Operating Systems (CS301)</td>
                  <td className="py-3 font-mono">26</td>
                  <td className="py-3 font-mono">24</td>
                  <td className="py-3 font-semibold text-[#1E9E6A] tabular-nums">92.3%</td>
                  <td className="py-3"><span className="text-[11px] font-semibold text-[#1E9E6A]">Satisfactory</span></td>
                </tr>
                <tr className="hover:bg-[#F6F8FB]">
                  <td className="py-3 font-medium text-[#0B1F3A]">Database Management Systems (CS302)</td>
                  <td className="py-3 font-mono">26</td>
                  <td className="py-3 font-mono">22</td>
                  <td className="py-3 font-semibold text-[#1E9E6A] tabular-nums">84.6%</td>
                  <td className="py-3"><span className="text-[11px] font-semibold text-[#1E9E6A]">Satisfactory</span></td>
                </tr>
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
