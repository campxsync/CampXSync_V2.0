'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import {
  Users,
  GraduationCap,
  Calendar,
  AlertTriangle,
  ClipboardList,
  Clock,
  TrendingUp,
  BarChart3,
  Sliders,
  ExternalLink,
  ShieldAlert,
  ArrowRight,
  CheckCircle2,
  FileSpreadsheet,
  History,
} from 'lucide-react';
import {
  ResponsiveContainer,
  LineChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
} from 'recharts';
import { StatCard } from '@/components/ui/stat-card';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { PreferenceModal } from '@/components/preferences/preference-modal';
import { formatDate } from '@/lib/utils';
import { UserPreferences, PRODUCT_DEFAULTS, ROLE_DEFAULT_WIDGETS } from '@/lib/preferences/preference-model';

export interface CollegeDashboardProps {
  stats: {
    studentCount: number;
    facultyCount: number;
    sessionCount: number;
    totalRecords: number;
    presentRecords: number;
    overallAttendancePercentage: number;
    shortageCount: number;
  };
  trend: Array<{
    dateStr: string;
    displayDate: string;
    totalRecords: number;
    presentRecords: number;
    percentage: number;
  }>;
  batchBreakdown: Array<{
    sectionCode: string;
    subjectCode: string;
    subjectName: string;
    sessionsHeld: number;
    totalRecords: number;
    presentRecords: number;
    percentage: number;
  }>;
  shortageStudents: Array<{
    studentId: string;
    enrollmentNo: string;
    displayName: string;
    totalSessions: number;
    attended: number;
    percentage: number;
  }>;
  missedSessions: Array<{
    id: string;
    sessionDate: string;
    startTime: string;
    endTime: string;
    status: string;
    subjectName: string;
    subjectCode: string;
    sectionCode: string;
    facultyName: string;
  }>;
  recentSessions: Array<{
    id: string;
    sessionDate: string;
    startTime: string;
    endTime: string;
    status: string;
    subjectName: string;
    subjectCode: string;
    sectionCode: string;
    facultyName: string;
    totalRecords: number;
    presentCount: number;
  }>;
  tenantName: string;
  userId: string;
  tenantId: string;
  initialPreferences?: UserPreferences;
}

export function CollegeDashboardView({
  stats,
  trend,
  batchBreakdown,
  shortageStudents,
  missedSessions,
  recentSessions,
  tenantName,
  userId,
  tenantId,
  initialPreferences,
}: CollegeDashboardProps) {
  const [mounted, setMounted] = useState(false);
  const [preferences, setPreferences] = useState<UserPreferences>(
    initialPreferences || PRODUCT_DEFAULTS
  );
  const [isCustomizeOpen, setIsCustomizeOpen] = useState(false);

  useEffect(() => {
    setMounted(true);

    const handlePrefChange = (e: any) => {
      if (e.detail) {
        setPreferences(e.detail);
      }
    };

    window.addEventListener('campx-preferences-changed', handlePrefChange);
    return () => {
      window.removeEventListener('campx-preferences-changed', handlePrefChange);
    };
  }, []);

  // Determine active widget order and visibility for college_admin
  const roleConfig =
    preferences.roles?.college_admin ||
    ROLE_DEFAULT_WIDGETS.college_admin;

  const widgetOrder = roleConfig.order || ROLE_DEFAULT_WIDGETS.college_admin.order;
  const hiddenWidgets = roleConfig.hidden || [];

  const isWidgetVisible = (widgetId: string) => !hiddenWidgets.includes(widgetId);

  // Widget rendering dictionary
  const renderWidget = (widgetId: string) => {
    if (!isWidgetVisible(widgetId)) return null;

    switch (widgetId) {
      case 'kpis':
        return (
          <div key="kpis" className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard
              title="Enrolled Students"
              value={stats.studentCount}
              icon={GraduationCap}
              description="Active institutional roster"
            />
            <StatCard
              title="Appointed Faculty"
              value={stats.facultyCount}
              icon={Users}
              description="Department teaching appointments"
            />
            <StatCard
              title="Overall Attendance"
              value={`${stats.overallAttendancePercentage}%`}
              icon={TrendingUp}
              description={`${stats.presentRecords} of ${stats.totalRecords} attendances recorded`}
            />
            <StatCard
              title="Shortage Alerts"
              value={stats.shortageCount}
              icon={AlertTriangle}
              description="Students below 75% statutory threshold"
            />
          </div>
        );

      case 'trends':
        return (
          <Card key="trends" className="overflow-hidden">
            <CardHeader className="py-4 border-b border-[#E3E8EF] bg-[#F6F8FB]/50">
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="text-sm font-semibold text-[#0B1F3A] flex items-center gap-2">
                    <TrendingUp className="h-4 w-4 text-[#1F6FEB]" />
                    14-Day Attendance Trend Analysis
                  </CardTitle>
                  <CardDescription className="text-xs">
                    Institutional aggregate attendance percentage across timetable dates.
                  </CardDescription>
                </div>
                <Badge status="ACTIVE" label="Real-time Derived" />
              </div>
            </CardHeader>
            <CardContent className="pt-6 pb-2">
              {trend.length === 0 ? (
                <div className="text-center py-12 text-xs text-[#6B7688]">
                  No attendance session records recorded in the selected date window.
                </div>
              ) : mounted ? (
                <div className="h-[260px] w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={trend} margin={{ top: 10, right: 30, left: -20, bottom: 5 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#E3E8EF" />
                      <XAxis
                        dataKey="displayDate"
                        tick={{ fill: '#6B7688', fontSize: 11 }}
                        tickLine={false}
                      />
                      <YAxis
                        domain={[0, 100]}
                        unit="%"
                        tick={{ fill: '#6B7688', fontSize: 11 }}
                        tickLine={false}
                      />
                      <Tooltip
                        formatter={(val: any) => [`${val}%`, 'Attendance Rate']}
                        contentStyle={{
                          backgroundColor: '#FFFFFF',
                          borderColor: '#E3E8EF',
                          borderRadius: '8px',
                          fontSize: '12px',
                        }}
                      />
                      <Line
                        type="monotone"
                        dataKey="percentage"
                        stroke="#1F6FEB"
                        strokeWidth={2.5}
                        dot={{ r: 4, fill: '#1F6FEB' }}
                        activeDot={{ r: 6 }}
                      />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              ) : (
                <div className="h-[260px] flex items-center justify-center text-xs text-[#6B7688]">
                  Loading chart...
                </div>
              )}
            </CardContent>
          </Card>
        );

      case 'batches':
        return (
          <Card key="batches" className="overflow-hidden">
            <CardHeader className="py-4 border-b border-[#E3E8EF] bg-[#F6F8FB]/50">
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="text-sm font-semibold text-[#0B1F3A] flex items-center gap-2">
                    <BarChart3 className="h-4 w-4 text-[#1F6FEB]" />
                    Attendance Distribution by Batch & Subject
                  </CardTitle>
                  <CardDescription className="text-xs">
                    Cross-section percentage comparison for active course offerings.
                  </CardDescription>
                </div>
              </div>
            </CardHeader>
            <CardContent className="pt-6 pb-2">
              {batchBreakdown.length === 0 ? (
                <div className="text-center py-12 text-xs text-[#6B7688]">
                  No batch attendance data available.
                </div>
              ) : mounted ? (
                <div className="h-[260px] w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart
                      data={batchBreakdown}
                      margin={{ top: 10, right: 30, left: -20, bottom: 5 }}
                    >
                      <CartesianGrid strokeDasharray="3 3" stroke="#E3E8EF" />
                      <XAxis
                        dataKey="sectionCode"
                        tick={{ fill: '#6B7688', fontSize: 11 }}
                        tickLine={false}
                      />
                      <YAxis
                        domain={[0, 100]}
                        unit="%"
                        tick={{ fill: '#6B7688', fontSize: 11 }}
                        tickLine={false}
                      />
                      <Tooltip
                        formatter={(val: any, name: any, item: any) => [
                          `${val}% (${item.payload.subjectCode})`,
                          item.payload.subjectName,
                        ]}
                        contentStyle={{
                          backgroundColor: '#FFFFFF',
                          borderColor: '#E3E8EF',
                          borderRadius: '8px',
                          fontSize: '12px',
                        }}
                      />
                      <Bar dataKey="percentage" fill="#1F6FEB" radius={[4, 4, 0, 0]} maxBarSize={48} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              ) : (
                <div className="h-[260px] flex items-center justify-center text-xs text-[#6B7688]">
                  Loading chart...
                </div>
              )}
            </CardContent>
          </Card>
        );

      case 'shortage':
        return (
          <Card key="shortage" className="overflow-hidden border-[#F8BDBD]">
            <CardHeader className="py-3.5 border-b border-[#F8BDBD] bg-[#FDECEC]/40">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <AlertTriangle className="h-4 w-4 text-[#B91C1C]" />
                  <CardTitle className="text-sm font-semibold text-[#B91C1C]">
                    Statutory Attendance Shortage Alerts (&lt; 75%)
                  </CardTitle>
                </div>
                <Link
                  href="/college/attendance?tab=report&shortage=true"
                  className="text-xs text-[#B91C1C] font-semibold hover:underline flex items-center gap-1"
                >
                  Full Report ({shortageStudents.length}) <ArrowRight className="h-3 w-3" />
                </Link>
              </div>
            </CardHeader>
            <CardContent className="p-0">
              {shortageStudents.length === 0 ? (
                <div className="p-6 text-center text-xs text-[#1E9E6A] flex items-center justify-center gap-2">
                  <CheckCircle2 className="h-4 w-4" />
                  All students currently satisfy the statutory 75% attendance threshold.
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs">
                    <thead>
                      <tr className="border-b border-[#E3E8EF] text-[#475569] bg-[#F6F8FB]/30">
                        <th className="py-2.5 px-4 font-semibold uppercase tracking-wider">Enrollment No</th>
                        <th className="py-2.5 px-4 font-semibold uppercase tracking-wider">Student Name</th>
                        <th className="py-2.5 px-4 font-semibold uppercase tracking-wider text-right">Attended / Total</th>
                        <th className="py-2.5 px-4 font-semibold uppercase tracking-wider text-right">Rate</th>
                        <th className="py-2.5 px-4 font-semibold uppercase tracking-wider text-right">Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#E3E8EF]">
                      {shortageStudents.map((st) => (
                        <tr key={st.studentId} className="hover:bg-[#F6F8FB]">
                          <td className="py-2.5 px-4 font-mono font-medium text-[#0B1F3A]">
                            {st.enrollmentNo}
                          </td>
                          <td className="py-2.5 px-4 text-[#1B2433] font-medium">{st.displayName}</td>
                          <td className="py-2.5 px-4 text-right font-mono text-[#475569]">
                            {st.attended} / {st.totalSessions} sessions
                          </td>
                          <td className="py-2.5 px-4 text-right font-mono">
                            <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-bold bg-[#FDECEC] text-[#B91C1C] border border-[#F8BDBD]">
                              {st.percentage}%
                            </span>
                          </td>
                          <td className="py-2.5 px-4 text-right">
                            <Link
                              href={`/college/attendance?tab=report&studentId=${st.studentId}`}
                              className="text-[11px] font-semibold text-[#1F6FEB] hover:underline"
                            >
                              Inspect Records
                            </Link>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>
        );

      case 'missed':
        return (
          <Card key="missed" className="overflow-hidden border-[#FCE2B3]">
            <CardHeader className="py-3.5 border-b border-[#FCE2B3] bg-[#FEF7EA]/40">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Clock className="h-4 w-4 text-[#E39B16]" />
                  <CardTitle className="text-sm font-semibold text-[#E39B16]">
                    Pending & Missed Attendance Sessions
                  </CardTitle>
                </div>
                <Badge status="ONBOARDING" label={`${missedSessions.length} Unsubmitted`} />
              </div>
            </CardHeader>
            <CardContent className="p-0">
              {missedSessions.length === 0 ? (
                <div className="p-6 text-center text-xs text-[#1E9E6A] flex items-center justify-center gap-2">
                  <CheckCircle2 className="h-4 w-4" />
                  All scheduled timetable slots have been completed and submitted.
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs">
                    <thead>
                      <tr className="border-b border-[#E3E8EF] text-[#475569] bg-[#F6F8FB]/30">
                        <th className="py-2.5 px-4 font-semibold uppercase tracking-wider">Date & Time</th>
                        <th className="py-2.5 px-4 font-semibold uppercase tracking-wider">Course Subject</th>
                        <th className="py-2.5 px-4 font-semibold uppercase tracking-wider">Section</th>
                        <th className="py-2.5 px-4 font-semibold uppercase tracking-wider">Assigned Faculty</th>
                        <th className="py-2.5 px-4 font-semibold uppercase tracking-wider text-right">Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#E3E8EF]">
                      {missedSessions.map((ms) => (
                        <tr key={ms.id} className="hover:bg-[#F6F8FB]">
                          <td className="py-2.5 px-4 font-mono text-[#0B1F3A]">
                            {ms.sessionDate} ({ms.startTime.slice(0, 5)} - {ms.endTime.slice(0, 5)})
                          </td>
                          <td className="py-2.5 px-4">
                            <span className="font-medium text-[#0B1F3A]">{ms.subjectName}</span>
                            <span className="text-[10px] font-mono text-[#475569] ml-1.5">
                              ({ms.subjectCode})
                            </span>
                          </td>
                          <td className="py-2.5 px-4 font-mono text-[#1B2433]">{ms.sectionCode}</td>
                          <td className="py-2.5 px-4 text-[#1B2433]">{ms.facultyName}</td>
                          <td className="py-2.5 px-4 text-right">
                            <Link href="/college/attendance">
                              <Button variant="outline" size="sm" className="h-7 text-[11px]">
                                Resolve Session
                              </Button>
                            </Link>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>
        );

      case 'recent':
        return (
          <Card key="recent" className="overflow-hidden">
            <CardHeader className="py-4 border-b border-[#E3E8EF] bg-[#F6F8FB]/50">
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="text-sm font-semibold text-[#0B1F3A] flex items-center gap-2">
                    <ClipboardList className="h-4 w-4 text-[#1F6FEB]" />
                    Recent Timetable Attendance Sessions
                  </CardTitle>
                  <CardDescription className="text-xs">
                    Latest session statuses, attendance counts, and authorized administrative corrections.
                  </CardDescription>
                </div>
                <Link
                  href="/college/attendance"
                  className="text-xs text-[#1F6FEB] font-semibold hover:underline flex items-center gap-1"
                >
                  View All Sessions <ArrowRight className="h-3 w-3" />
                </Link>
              </div>
            </CardHeader>
            <CardContent className="p-0">
              {recentSessions.length === 0 ? (
                <div className="p-8 text-center text-xs text-[#475569]">
                  No attendance sessions created yet.
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs">
                    <thead>
                      <tr className="border-b border-[#E3E8EF] text-[#475569] bg-[#F6F8FB]/30">
                        <th className="py-3 px-4 font-semibold uppercase tracking-wider">Date & Time</th>
                        <th className="py-3 px-4 font-semibold uppercase tracking-wider">Subject</th>
                        <th className="py-3 px-4 font-semibold uppercase tracking-wider">Section</th>
                        <th className="py-3 px-4 font-semibold uppercase tracking-wider">Faculty</th>
                        <th className="py-3 px-4 font-semibold uppercase tracking-wider text-right">Present / Total</th>
                        <th className="py-3 px-4 font-semibold uppercase tracking-wider">Status</th>
                        <th className="py-3 px-4 font-semibold uppercase tracking-wider text-right">Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#E3E8EF]">
                      {recentSessions.map((s) => (
                        <tr key={s.id} className="hover:bg-[#F6F8FB]">
                          <td className="py-3 px-4 font-mono text-[#0B1F3A]">
                            {s.sessionDate}{' '}
                            <span className="text-[#475569] text-[11px]">
                              ({s.startTime.slice(0, 5)})
                            </span>
                          </td>
                          <td className="py-3 px-4 font-medium text-[#0B1F3A]">
                            {s.subjectName}
                            <span className="text-[10px] font-mono text-[#475569] ml-1">
                              ({s.subjectCode})
                            </span>
                          </td>
                          <td className="py-3 px-4 font-mono text-[#1B2433]">{s.sectionCode}</td>
                          <td className="py-3 px-4 text-[#1B2433]">{s.facultyName}</td>
                          <td className="py-3 px-4 text-right font-mono font-medium text-[#0B1F3A]">
                            {s.presentCount} / {s.totalRecords}
                          </td>
                          <td className="py-3 px-4">
                            <Badge
                              status={
                                s.status === 'CLOSED'
                                  ? 'SUBMITTED'
                                  : s.status === 'OPEN'
                                  ? 'OPEN'
                                  : 'DRAFT'
                              }
                            />
                          </td>
                          <td className="py-3 px-4 text-right">
                            <Link href="/college/attendance">
                              <Button variant="ghost" size="sm" className="h-7 text-[11px] text-[#1F6FEB]">
                                Manage
                              </Button>
                            </Link>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>
        );

      default:
        return null;
    }
  };

  return (
    <div className="space-y-6">
      {/* Dashboard Header & Quick Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
            College Operations Dashboard
          </h1>
          <p className="text-xs text-[#475569] mt-1">
            Real-time attendance metrics, roster governance, and timetable compliance for{' '}
            <span className="font-semibold text-[#0B1F3A]">{tenantName}</span>.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setIsCustomizeOpen(true)}
            className="text-xs"
          >
            <Sliders className="mr-1.5 h-3.5 w-3.5 text-[#475569]" />
            Customize Dashboard
          </Button>

          <Link href="/college/attendance">
            <Button variant="primary" size="sm" className="text-xs">
              <ClipboardList className="mr-1.5 h-4 w-4" />
              Manage Attendance
            </Button>
          </Link>
        </div>
      </div>

      {/* Render Dynamic Widgets in User Preferred Order */}
      <div className="space-y-6">{widgetOrder.map((widgetId) => renderWidget(widgetId))}</div>

      {/* Quick Navigation Footprint */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3 pt-2">
        <Link href="/college/attendance?tab=report">
          <Card className="hover:border-[#1F6FEB] transition-colors cursor-pointer p-4">
            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-[8px] bg-[#E8F0FE] text-[#1F6FEB]">
                <FileSpreadsheet className="h-5 w-5" />
              </div>
              <div>
                <h4 className="text-sm font-semibold text-[#0B1F3A]">Cumulative Attendance Report</h4>
                <p className="text-[11px] text-[#475569]">Filter by batch, date range, and export CSV</p>
              </div>
            </div>
          </Card>
        </Link>

        <Link href="/college/timetable">
          <Card className="hover:border-[#1F6FEB] transition-colors cursor-pointer p-4">
            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-[8px] bg-[#E8F0FE] text-[#1F6FEB]">
                <Calendar className="h-5 w-5" />
              </div>
              <div>
                <h4 className="text-sm font-semibold text-[#0B1F3A]">Institutional Timetable</h4>
                <p className="text-[11px] text-[#475569]">Inspect published slots and teaching assignments</p>
              </div>
            </div>
          </Card>
        </Link>

        <Link href="/college/audit">
          <Card className="hover:border-[#1F6FEB] transition-colors cursor-pointer p-4">
            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-[8px] bg-[#E8F0FE] text-[#1F6FEB]">
                <History className="h-5 w-5" />
              </div>
              <div>
                <h4 className="text-sm font-semibold text-[#0B1F3A]">Governance Audit Log</h4>
                <p className="text-[11px] text-[#475569]">Tamper-evident log of administrative mutations</p>
              </div>
            </div>
          </Card>
        </Link>
      </div>

      {/* Preference Customization Modal */}
      <PreferenceModal
        isOpen={isCustomizeOpen}
        onClose={() => setIsCustomizeOpen(false)}
        userId={userId}
        role="college_admin"
        tenantId={tenantId}
        onPreferencesSaved={(newPrefs) => setPreferences(newPrefs)}
      />
    </div>
  );
}
