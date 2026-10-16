'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import {
  Clock,
  CalendarCheck2,
  CheckCircle2,
  AlertCircle,
  ArrowRight,
  BookOpen,
  Calendar,
  History,
  AlertTriangle,
  Play,
  Sliders,
} from 'lucide-react';
import { StatCard } from '@/components/ui/stat-card';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { PreferenceModal } from '@/components/preferences/preference-modal';
import { formatDate } from '@/lib/utils';
import { UserPreferences, PRODUCT_DEFAULTS, ROLE_DEFAULT_WIDGETS } from '@/lib/preferences/preference-model';

export interface FacultyDashboardProps {
  displayName: string;
  userId: string;
  tenantId: string;
  todayDateStr: string;
  dayName: string;
  todaySlots: any[];
  allAssignedSlots: any[];
  upcomingSlots: any[];
  missedSessions: any[];
  openCount: number;
  submittedCount: number;
  initialPreferences?: UserPreferences;
}

export function FacultyDashboardView({
  displayName,
  userId,
  tenantId,
  todayDateStr,
  dayName,
  todaySlots,
  allAssignedSlots,
  upcomingSlots,
  missedSessions,
  openCount,
  submittedCount,
  initialPreferences,
}: FacultyDashboardProps) {
  const [preferences, setPreferences] = useState<UserPreferences>(
    initialPreferences || PRODUCT_DEFAULTS
  );
  const [isCustomizeOpen, setIsCustomizeOpen] = useState(false);

  useEffect(() => {
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

  const roleConfig =
    preferences.roles?.faculty ||
    ROLE_DEFAULT_WIDGETS.faculty;

  const widgetOrder = roleConfig.order || ROLE_DEFAULT_WIDGETS.faculty.order;
  const hiddenWidgets = roleConfig.hidden || [];
  const isWidgetVisible = (id: string) => !hiddenWidgets.includes(id);

  const renderWidget = (widgetId: string) => {
    if (!isWidgetVisible(widgetId)) return null;

    switch (widgetId) {
      case 'today':
        return (
          <div key="today" className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-base font-bold text-[#0B1F3A] flex items-center gap-2">
                  <Calendar className="h-4.5 w-4.5 text-[#1F6FEB]" />
                  Today&apos;s Timetable Slots ({todaySlots.length})
                </h2>
                <p className="text-xs text-[#475569]">
                  Select any scheduled session to begin roster attendance capture or review submitted status.
                </p>
              </div>
            </div>

            {todaySlots.length === 0 ? (
              <Card className="p-8 text-center bg-[#F6F8FB]/50 border-dashed">
                <BookOpen className="mx-auto h-8 w-8 text-[#6B7688]/60" />
                <p className="text-sm font-semibold text-[#0B1F3A] mt-2">No Scheduled Classes Today</p>
                <p className="text-xs text-[#6B7688] mt-1 max-w-sm mx-auto">
                  You have no teaching periods scheduled for {dayName}. Check upcoming slots below or visit My Sessions.
                </p>
              </Card>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {todaySlots.map((slot) => {
                  const isSubmitted = slot.attendanceState === 'SUBMITTED';
                  const isOpen = slot.attendanceState === 'OPEN';

                  return (
                    <Card
                      key={slot.id}
                      className={`overflow-hidden transition-all hover:shadow-md border ${
                        isSubmitted
                          ? 'border-[#B2E6CE] bg-[#EAF8F1]/10'
                          : isOpen
                          ? 'border-[#B8D3FC] bg-[#E8F0FE]/10'
                          : 'border-[#E3E8EF] hover:border-[#1F6FEB]'
                      }`}
                    >
                      <CardHeader className="py-3 px-4 border-b border-[#E3E8EF] bg-[#F6F8FB]/50">
                        <div className="flex items-center justify-between">
                          <span className="text-xs font-mono font-bold text-[#1D4ED8]">
                            {slot.start_time.slice(0, 5)} - {slot.end_time.slice(0, 5)}
                          </span>
                          <Badge
                            status={isSubmitted ? 'SUBMITTED' : isOpen ? 'OPEN' : 'DRAFT'}
                            label={
                              isSubmitted
                                ? 'Submitted & Locked'
                                : isOpen
                                ? 'In Progress'
                                : 'Not Started'
                            }
                          />
                        </div>
                      </CardHeader>
                      <CardContent className="p-4 space-y-3">
                        <div>
                          <h3 className="text-sm font-bold text-[#0B1F3A]">{slot.subject_name}</h3>
                          <div className="flex items-center gap-2 mt-1 text-xs text-[#475569] font-mono">
                            <span className="px-1.5 py-0.5 rounded bg-[#F0F2F5] text-[#1B2433]">
                              {slot.subject_code}
                            </span>
                            <span>•</span>
                            <span className="text-[#1D4ED8] font-semibold">
                              {slot.section_name || 'All Sections'}
                            </span>
                          </div>
                        </div>

                        <div className="pt-2 border-t border-[#E3E8EF] flex items-center justify-between">
                          <span className="text-[11px] text-[#475569]">ACD-06 Compliance</span>
                          <Link href={`/faculty/attendance?slotId=${slot.id}`}>
                            <Button
                              variant={isSubmitted ? 'outline' : 'primary'}
                              size="sm"
                              className="text-xs h-7"
                            >
                              {isSubmitted ? (
                                <>
                                  <CheckCircle2 className="mr-1 h-3 w-3 text-[#1E9E6A]" />
                                  View Sheet
                                </>
                              ) : isOpen ? (
                                <>
                                  <Clock className="mr-1 h-3 w-3 text-[#1F6FEB]" />
                                  Resume
                                </>
                              ) : (
                                <>
                                  <Play className="mr-1 h-3 w-3" />
                                  Start Attendance
                                </>
                              )}
                            </Button>
                          </Link>
                        </div>
                      </CardContent>
                    </Card>
                  );
                })}
              </div>
            )}
          </div>
        );

      case 'missed':
        if (missedSessions.length === 0) return null;
        return (
          <Card key="missed" className="border-[#FCE2B3] bg-[#FEF7EA]/30">
            <CardHeader className="py-3 px-4 border-b border-[#FCE2B3]">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <AlertTriangle className="h-4.5 w-4.5 text-[#E39B16]" />
                  <CardTitle className="text-sm font-bold text-[#0B1F3A]">
                    Action Required: Unsubmitted Attendance Sessions ({missedSessions.length})
                  </CardTitle>
                </div>
                <Badge status="ONBOARDING" label="Pending Resolution" />
              </div>
              <CardDescription className="text-xs text-[#475569] mt-0.5">
                The following sessions were opened but have not been formally submitted. Please finalize attendance records to ensure institutional reporting integrity.
              </CardDescription>
            </CardHeader>
            <CardContent className="p-4">
              <div className="divide-y divide-[#E3E8EF]">
                {missedSessions.map((ms) => (
                  <div key={ms.id} className="py-2.5 flex items-center justify-between">
                    <div>
                      <p className="text-xs font-semibold text-[#0B1F3A]">
                        {ms.subject_name} ({ms.subject_code}) — Section {ms.section_name}
                      </p>
                      <p className="text-[11px] text-[#475569] font-mono mt-0.5">
                        Session Date: {formatDate(ms.session_date)} ({ms.start_time.slice(0, 5)} - {ms.end_time.slice(0, 5)})
                      </p>
                    </div>
                    <Link href={`/faculty/attendance?sessionId=${ms.id}&slotId=${ms.timetable_entry_id}`}>
                      <Button variant="primary" size="sm" className="text-xs h-7 bg-[#E39B16] hover:bg-[#C88510]">
                        Catch-up & Submit
                      </Button>
                    </Link>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        );

      case 'upcoming':
        return (
          <Card key="upcoming">
            <CardHeader className="py-3.5 border-b border-[#E3E8EF] bg-[#F6F8FB]/50">
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="text-sm font-semibold text-[#0B1F3A]">
                    Upcoming Curriculum Schedule Slots
                  </CardTitle>
                  <CardDescription className="text-xs">
                    Your institutional teaching slots across the published academic calendar.
                  </CardDescription>
                </div>
                <Link href="/faculty/sessions" className="text-xs text-[#1F6FEB] hover:underline flex items-center gap-1 font-semibold">
                  My Sessions <ArrowRight className="h-3 w-3" />
                </Link>
              </div>
            </CardHeader>
            <CardContent className="pt-3">
              {upcomingSlots.length === 0 ? (
                <p className="text-xs text-[#6B7688] py-4 text-center">No other upcoming slots recorded for this week.</p>
              ) : (
                <div className="divide-y divide-[#E3E8EF]">
                  {upcomingSlots.slice(0, 5).map((slot) => (
                    <div key={slot.id} className="py-2.5 flex items-center justify-between">
                      <div>
                        <p className="text-xs font-semibold text-[#0B1F3A]">
                          {slot.subject_name} ({slot.subject_code})
                        </p>
                        <p className="text-[11px] text-[#6B7688] font-mono mt-0.5">
                          Section: {slot.section_name} · Slot: {slot.start_time.slice(0, 5)} - {slot.end_time.slice(0, 5)}
                        </p>
                      </div>
                      <Link href={`/faculty/attendance?slotId=${slot.id}`}>
                        <Button variant="ghost" size="sm" className="text-xs text-[#1F6FEB]">
                          Start Slot <ArrowRight className="ml-1 h-3 w-3" />
                        </Button>
                      </Link>
                    </div>
                  ))}
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
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
            Faculty Teaching Workspace
          </h1>
          <p className="text-xs text-[#475569] mt-1">
            Welcome, {displayName}. Manage your timetable slots, record student attendance, and monitor class compliance.
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

          <Link href="/faculty/sessions">
            <Button variant="outline" size="sm" className="text-xs">
              <History className="mr-1.5 h-4 w-4" />
              My Sessions
            </Button>
          </Link>
          <Link href="/faculty/attendance">
            <Button variant="primary" size="sm" className="text-xs">
              <CalendarCheck2 className="mr-1.5 h-4 w-4" />
              Start Attendance
            </Button>
          </Link>
        </div>
      </div>

      {/* KPI Row */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-4">
        <StatCard
          title="Today's Scheduled Slots"
          value={`${todaySlots.length} Slots`}
          icon={Calendar}
          description={`${dayName}, ${formatDate(todayDateStr)}`}
        />
        <StatCard
          title="Total Assigned Slots"
          value={`${allAssignedSlots.length} Slots`}
          icon={Clock}
          description="Institutional curriculum entries"
        />
        <StatCard
          title="Open / In-Progress"
          value={openCount.toString()}
          icon={AlertCircle}
          description="Awaiting faculty finalization"
        />
        <StatCard
          title="Submitted & Locked"
          value={submittedCount.toString()}
          icon={CheckCircle2}
          description="Archived attendance sheets"
        />
      </div>

      {/* Dynamic Widget Order */}
      <div className="space-y-6">{widgetOrder.map((widgetId) => renderWidget(widgetId))}</div>

      {/* Preference Customization Modal */}
      <PreferenceModal
        isOpen={isCustomizeOpen}
        onClose={() => setIsCustomizeOpen(false)}
        userId={userId}
        role="faculty"
        tenantId={tenantId}
        onPreferencesSaved={(newPrefs) => setPreferences(newPrefs)}
      />
    </div>
  );
}
