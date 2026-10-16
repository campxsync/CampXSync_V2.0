import { redirect } from 'next/navigation';
import { Calendar, Clock, MapPin, BookOpen, Users, CalendarCheck2 } from 'lucide-react';
import Link from 'next/link';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import { StatCard } from '@/components/ui/stat-card';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

export const dynamic = 'force-dynamic';

export default async function FacultyTimetablePage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || (resolution.session.role !== 'faculty' && resolution.session.role !== 'college_admin')) {
    redirect('/access-denied?reason=forbidden');
  }

  const { tenantId, displayName } = resolution.session;

  let entries: any[] = [];
  try {
    const res = await query(`
      SELECT 
        tte.id,
        tte.day_of_week,
        tte.start_time,
        tte.end_time,
        sub.name as subject_name,
        sub.code as subject_code,
        sec.section_code as section_name
      FROM acd.timetable_entries tte
      JOIN acd.subjects sub ON sub.id = tte.subject_id
      LEFT JOIN acd.course_sections sec ON sec.id = tte.section_id
      WHERE tte.tenant_id = $1 AND tte.deleted_at IS NULL
      ORDER BY tte.day_of_week ASC, tte.start_time ASC
    `, [tenantId || '11111111-1111-1111-1111-111111111111']);

    entries = res.rows;
  } catch (err) {
    console.error('Failed to query faculty timetable:', err);
  }

  const days = [
    { dayNumber: 1, name: 'Monday' },
    { dayNumber: 2, name: 'Tuesday' },
    { dayNumber: 3, name: 'Wednesday' },
    { dayNumber: 4, name: 'Thursday' },
    { dayNumber: 5, name: 'Friday' },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
            My Weekly Teaching Schedule
          </h1>
          <p className="text-xs text-[#6B7688] mt-1">
            Weekly academic timetable and scheduled lecture slots for {displayName}.
          </p>
        </div>

        <Link href="/faculty/attendance">
          <Button variant="primary" size="sm">
            <CalendarCheck2 className="mr-1.5 h-4 w-4" />
            Mark Today&apos;s Attendance
          </Button>
        </Link>
      </div>

      {/* KPI Stats */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard
          title="Weekly Teaching Hours"
          value={`${entries.length * 2} Hours`}
          icon={Clock}
          description="Institutional allocated load"
        />
        <StatCard
          title="Scheduled Lectures"
          value={`${entries.length} Periods`}
          icon={Calendar}
          description="Curriculum timetable periods"
        />
        <StatCard
          title="Assigned Cohorts"
          value="2 Sections"
          icon={Users}
          description="Undergraduate semester cohorts"
        />
      </div>

      {/* Weekly Schedule Days */}
      <div className="space-y-4">
        {days.map((day) => {
          const dayEntries = entries.filter((e) => e.day_of_week === day.dayNumber);

          return (
            <Card key={day.dayNumber}>
              <CardHeader className="py-3.5 bg-[#F6F8FB]/60 border-b border-[#E3E8EF]">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Calendar className="h-4 w-4 text-[#1F6FEB]" />
                    <CardTitle className="text-sm font-bold text-[#0B1F3A]">{day.name}</CardTitle>
                  </div>
                  <span className="text-[11px] font-mono font-medium text-[#6B7688]">
                    {dayEntries.length} {dayEntries.length === 1 ? 'class' : 'classes'}
                  </span>
                </div>
              </CardHeader>
              <CardContent className="pt-4">
                {dayEntries.length === 0 ? (
                  <p className="text-xs text-[#6B7688] italic py-2">No lectures scheduled for {day.name}.</p>
                ) : (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    {dayEntries.map((e) => (
                      <div
                        key={e.id}
                        className="flex flex-col justify-between rounded-[8px] border border-[#E3E8EF] p-3.5 bg-white hover:border-[#1F6FEB] hover:shadow-xs transition-all"
                      >
                        <div className="flex items-start justify-between gap-2">
                          <div>
                            <div className="flex items-center gap-1.5">
                              <span className="font-mono text-xs font-bold text-[#1F6FEB]">{e.subject_code}</span>
                              <span className="text-xs font-semibold text-[#0B1F3A] line-clamp-1">{e.subject_name}</span>
                            </div>
                            <p className="text-[11px] text-[#6B7688] mt-1">
                              Section: <span className="font-semibold text-[#1B2433]">{e.section_name || 'General'}</span>
                            </p>
                          </div>
                          <Badge status="ACTIVE">Scheduled</Badge>
                        </div>

                        <div className="flex items-center justify-between border-t border-[#E3E8EF]/60 pt-2.5 mt-3 text-[11px] text-[#6B7688]">
                          <div className="flex items-center gap-1 font-mono">
                            <Clock className="h-3.5 w-3.5 text-[#1F6FEB]" />
                            <span>{e.start_time?.slice(0, 5)} - {e.end_time?.slice(0, 5)}</span>
                          </div>
                          <div className="flex items-center gap-1">
                            <MapPin className="h-3.5 w-3.5 text-[#6B7688]" />
                            <span>{e.room_number || 'Room 301'}</span>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
