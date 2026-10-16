'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import {
  Clock,
  CheckCircle2,
  AlertCircle,
  Users,
  Check,
  X,
  Calendar,
  Lock,
  History,
  AlertTriangle,
  RotateCcw,
  ShieldAlert,
  ArrowRight
} from 'lucide-react';
import Link from 'next/link';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';

interface TimetableSlot {
  id: string;
  day_of_week: number;
  start_time: string;
  end_time: string;
  subject_name: string;
  subject_code: string;
  section_id: string;
  section_code: string;
  employee_id: string;
}

interface StudentItem {
  id: string;
  enrollment_no: string;
  display_name: string;
  primary_email: string;
}

interface Props {
  slots: TimetableSlot[];
  students: StudentItem[];
  employeeId?: string;
  initialSlotId?: string;
  initialSessionId?: string;
}

export function FacultyAttendanceView({
  slots,
  students: initialTenantStudents,
  employeeId,
  initialSlotId,
  initialSessionId,
}: Props) {
  const router = useRouter();

  // Selected slot & date
  const [selectedSlotId, setSelectedSlotId] = useState<string>(
    initialSlotId && slots.some((s) => s.id === initialSlotId)
      ? initialSlotId
      : slots[0]?.id || ''
  );
  const [sessionDate, setSessionDate] = useState<string>(
    new Date().toISOString().split('T')[0]
  );

  // Active session and lock state
  const [activeSessionId, setActiveSessionId] = useState<string | null>(initialSessionId || null);
  const [isSessionLocked, setIsSessionLocked] = useState(false);

  // Active roster students for this session
  const [rosterStudents, setRosterStudents] = useState<StudentItem[]>(initialTenantStudents);

  // Student marks: studentId -> status
  const [attendanceMarks, setAttendanceMarks] = useState<
    Record<string, 'PRESENT' | 'ABSENT' | 'LEAVE' | 'LATE' | 'ON_DUTY'>
  >({});

  // Unsaved changes state
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);

  // Submission confirmation modal state
  const [showSubmitModal, setShowSubmitModal] = useState(false);

  const [isLoading, setIsLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState<{
    type: 'error' | 'success' | 'info';
    text: string;
  } | null>(null);

  const activeSlot = slots.find((s) => s.id === selectedSlotId);

  // 1. Guard against navigating away with unsaved marks
  useEffect(() => {
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      if (hasUnsavedChanges && !isSessionLocked) {
        e.preventDefault();
        e.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [hasUnsavedChanges, isSessionLocked]);

  // 2. Fetch session details and roster records
  const loadSessionRecords = useCallback(async (sessionId: string) => {
    setIsLoading(true);
    try {
      const res = await fetch(`/api/college/attendance/records?sessionId=${sessionId}`);
      const data = await res.json();

      if (res.ok && data.success) {
        const locked = data.session.status === 'CLOSED' || data.session.status === 'LOCKED';
        setIsSessionLocked(locked);

        // Populate roster
        if (data.records && data.records.length > 0) {
          const loadedStudents: StudentItem[] = data.records.map((r: any) => ({
            id: r.student_id,
            enrollment_no: r.enrollment_no,
            display_name: r.display_name,
            primary_email: r.primary_email || '',
          }));
          setRosterStudents(loadedStudents);

          // Populate marks
          const marks: Record<string, 'PRESENT' | 'ABSENT' | 'LEAVE' | 'LATE' | 'ON_DUTY'> = {};
          data.records.forEach((r: any) => {
            marks[r.student_id] = r.status || 'PRESENT';
          });
          setAttendanceMarks(marks);
        }
        setHasUnsavedChanges(false);
      } else {
        setStatusMessage({
          type: 'error',
          text: data.detail || 'Failed to retrieve attendance records for this session.',
        });
      }
    } catch (err: any) {
      setStatusMessage({
        type: 'error',
        text: err.message || 'Network error while loading session records.',
      });
    } finally {
      setIsLoading(false);
    }
  }, []);

  // If initialSessionId is provided, load its records on mount
  useEffect(() => {
    if (initialSessionId) {
      loadSessionRecords(initialSessionId);
    }
  }, [initialSessionId, loadSessionRecords]);

  // 3. Open session (or retrieve existing session)
  const handleOpenSession = async () => {
    if (!activeSlot) return;

    if (hasUnsavedChanges && !isSessionLocked) {
      const proceed = window.confirm(
        'You have unsaved changes on the current attendance sheet. Do you want to discard them and open this session?'
      );
      if (!proceed) return;
    }

    setIsLoading(true);
    setStatusMessage(null);

    try {
      const res = await fetch('/api/college/attendance/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          timetableEntryId: activeSlot.id,
          sectionId: activeSlot.section_id,
          employeeId: activeSlot.employee_id || employeeId,
          sessionDate,
          startTime: activeSlot.start_time,
          endTime: activeSlot.end_time,
        }),
      });

      const data = await res.json();

      if (!res.ok) {
        setStatusMessage({ type: 'error', text: data.detail || 'Failed to open session.' });
      } else {
        const session = data.session;
        setActiveSessionId(session.id);
        const locked = session.status === 'CLOSED' || session.status === 'LOCKED';
        setIsSessionLocked(locked);

        // Fetch persisted or roster records
        await loadSessionRecords(session.id);

        setStatusMessage({
          type: locked ? 'info' : 'success',
          text: locked
            ? `Retrieved existing submitted session for ${activeSlot.subject_name} (${sessionDate}). Sheet is read-only.`
            : data.isExisting
            ? `Existing open session resumed for ${activeSlot.subject_name}.`
            : `New attendance session opened for ${activeSlot.subject_name}.`,
        });
      }
    } catch (err: any) {
      setStatusMessage({ type: 'error', text: err.message || 'Network error occurred.' });
    } finally {
      setIsLoading(false);
    }
  };

  // 4. Mark student status
  const handleMark = (
    studentId: string,
    status: 'PRESENT' | 'ABSENT' | 'LEAVE' | 'LATE' | 'ON_DUTY'
  ) => {
    if (isSessionLocked) return;
    setAttendanceMarks((prev) => ({ ...prev, [studentId]: status }));
    setHasUnsavedChanges(true);
  };

  // 5. Bulk mark all
  const handleMarkAll = (status: 'PRESENT' | 'ABSENT') => {
    if (isSessionLocked) return;
    const updated: Record<string, 'PRESENT' | 'ABSENT' | 'LEAVE' | 'LATE' | 'ON_DUTY'> = {};
    rosterStudents.forEach((s) => {
      updated[s.id] = status;
    });
    setAttendanceMarks(updated);
    setHasUnsavedChanges(true);
  };

  // 6. Final submit action
  const confirmAndSubmitAttendance = async () => {
    if (!activeSessionId) return;
    setIsLoading(true);
    setStatusMessage(null);
    setShowSubmitModal(false);

    const recordPayload = rosterStudents.map((s) => ({
      studentId: s.id,
      status: attendanceMarks[s.id] || 'PRESENT',
    }));

    try {
      const res = await fetch('/api/college/attendance/records', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          sessionId: activeSessionId,
          closeSession: true,
          records: recordPayload,
        }),
      });

      const data = await res.json();

      if (!res.ok) {
        setStatusMessage({ type: 'error', text: data.detail || 'Failed to submit attendance.' });
      } else {
        setStatusMessage({
          type: 'success',
          text: `${data.message} Session has been locked into the academic ledger.`,
        });
        setIsSessionLocked(true);
        setHasUnsavedChanges(false);
        // Refresh session records to show finalized version
        await loadSessionRecords(activeSessionId);
      }
    } catch (err: any) {
      setStatusMessage({ type: 'error', text: err.message || 'Network error occurred.' });
    } finally {
      setIsLoading(false);
    }
  };

  // Running counts
  const presentCount = Object.values(attendanceMarks).filter((s) => s === 'PRESENT').length;
  const absentCount = Object.values(attendanceMarks).filter((s) => s === 'ABSENT').length;
  const leaveCount = Object.values(attendanceMarks).filter((s) => s === 'LEAVE').length;
  const lateCount = Object.values(attendanceMarks).filter((s) => s === 'LATE').length;
  const onDutyCount = Object.values(attendanceMarks).filter((s) => s === 'ON_DUTY').length;
  const totalRoster = rosterStudents.length;
  const attendedCount = presentCount + lateCount + onDutyCount;
  const runningPercentage = totalRoster > 0 ? Math.round((attendedCount / totalRoster) * 100) : 0;

  return (
    <div className="space-y-6">
      {/* Slot Selection Card */}
      <Card>
        <CardHeader>
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <div>
              <CardTitle>Select Timetable Slot & Date</CardTitle>
              <CardDescription>
                Choose from your assigned class slots. Opening an existing slot will resume that session.
              </CardDescription>
            </div>
            <Link href="/faculty/sessions">
              <Button variant="outline" size="sm">
                <History className="mr-1.5 h-4 w-4" />
                My Sessions
              </Button>
            </Link>
          </div>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                Class Slot <span className="text-[#D64545]">*</span>
              </label>
              <select
                className="w-full h-10 px-3 text-xs rounded-[8px] border border-[#E3E8EF] bg-white text-[#1B2433] focus:border-[#1F6FEB] focus:outline-none"
                value={selectedSlotId}
                onChange={(e) => {
                  if (hasUnsavedChanges && !isSessionLocked) {
                    const confirmChange = window.confirm(
                      'You have unsaved attendance marks. Are you sure you want to change class slot?'
                    );
                    if (!confirmChange) return;
                  }
                  setSelectedSlotId(e.target.value);
                  setActiveSessionId(null);
                  setIsSessionLocked(false);
                  setHasUnsavedChanges(false);
                }}
              >
                {slots.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.subject_code} — {s.subject_name} ({s.start_time?.slice(0, 5)} - {s.end_time?.slice(0, 5)}) [Sec {s.section_code}]
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-medium text-[#0B1F3A] mb-1">Session Date</label>
              <Input
                type="date"
                value={sessionDate}
                onChange={(e) => {
                  if (hasUnsavedChanges && !isSessionLocked) {
                    const confirmChange = window.confirm(
                      'You have unsaved attendance marks. Are you sure you want to change date?'
                    );
                    if (!confirmChange) return;
                  }
                  setSessionDate(e.target.value);
                  setActiveSessionId(null);
                  setIsSessionLocked(false);
                  setHasUnsavedChanges(false);
                }}
              />
            </div>

            <div className="flex items-end">
              <Button
                variant="primary"
                onClick={handleOpenSession}
                isLoading={isLoading}
                disabled={!selectedSlotId}
                className="w-full"
              >
                <Clock className="mr-1.5 h-4 w-4" />
                {activeSessionId ? 'Reload Attendance Sheet' : 'Open Attendance Session'}
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Unsaved Changes Warning Banner */}
      {hasUnsavedChanges && !isSessionLocked && (
        <div className="p-3 rounded-[8px] border border-[#F2994A] bg-[#FFFBF0] text-[#7B341E] text-xs flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <AlertTriangle className="h-4 w-4 text-[#F2994A] shrink-0" />
            <span>
              <strong>Unsaved Changes:</strong> You have recorded marks that are not yet submitted to the institutional ledger. Please submit to lock them.
            </span>
          </div>
          <Button 
            variant="primary" 
            size="sm" 
            onClick={() => setShowSubmitModal(true)}
            className="h-7 text-xs bg-[#F2994A] hover:bg-[#D97706] text-white border-none"
          >
            Submit Now
          </Button>
        </div>
      )}

      {/* Status Alert */}
      {statusMessage && (
        <div
          className={`p-3 rounded-[8px] border text-xs flex items-center gap-2 ${
            statusMessage.type === 'error'
              ? 'border-[#D64545] bg-[#FDF3F3] text-[#D64545]'
              : statusMessage.type === 'info'
              ? 'border-[#1F6FEB] bg-[#E8F0FE] text-[#1F6FEB]'
              : 'border-[#107C41] bg-[#DFF6DD] text-[#107C41]'
          }`}
        >
          {statusMessage.type === 'error' ? (
            <AlertCircle className="h-4 w-4 shrink-0" />
          ) : statusMessage.type === 'info' ? (
            <Lock className="h-4 w-4 shrink-0" />
          ) : (
            <CheckCircle2 className="h-4 w-4 shrink-0" />
          )}
          <span>{statusMessage.text}</span>
        </div>
      )}

      {/* Attendance Roster Grid */}
      {activeSessionId && (
        <Card className={isSessionLocked ? 'border-[#E3E8EF]' : 'border-[#1F6FEB]/40'}>
          <CardHeader className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-4">
            <div>
              <div className="flex items-center gap-2">
                <CardTitle className="text-base text-[#0B1F3A]">
                  Class Roster ({rosterStudents.length} Students)
                </CardTitle>
                {isSessionLocked ? (
                  <span className="flex items-center gap-1 text-[11px] font-bold text-[#107C41] bg-[#DFF6DD] px-2.5 py-0.5 rounded-full border border-[#107C41]/30">
                    <Lock className="h-3 w-3" /> Submitted & Read-Only
                  </span>
                ) : (
                  <span className="flex items-center gap-1 text-[11px] font-bold text-[#1F6FEB] bg-[#E8F0FE] px-2.5 py-0.5 rounded-full border border-[#1F6FEB]/30">
                    <Clock className="h-3 w-3" /> In Progress (Open)
                  </span>
                )}
              </div>

              {/* Running Counts Breakdown */}
              <div className="flex flex-wrap items-center gap-2 text-xs mt-2 text-[#6B7688]">
                <span className="font-semibold text-[#0B1F3A]">Running Breakdown:</span>
                <span className="inline-flex items-center px-2 py-0.5 rounded bg-[#EAF8F1] text-[#1E9E6A] font-semibold">
                  {presentCount} Present
                </span>
                <span className="inline-flex items-center px-2 py-0.5 rounded bg-[#FDF3F3] text-[#D64545] font-semibold">
                  {absentCount} Absent
                </span>
                {leaveCount > 0 && (
                  <span className="inline-flex items-center px-2 py-0.5 rounded bg-[#FFF7ED] text-[#C2410C] font-semibold">
                    {leaveCount} Leave
                  </span>
                )}
                {lateCount > 0 && (
                  <span className="inline-flex items-center px-2 py-0.5 rounded bg-[#FEF3C7] text-[#92400E] font-semibold">
                    {lateCount} Late
                  </span>
                )}
                {onDutyCount > 0 && (
                  <span className="inline-flex items-center px-2 py-0.5 rounded bg-[#EFF6FF] text-[#1D4ED8] font-semibold">
                    {onDutyCount} On Duty
                  </span>
                )}
                <span className="font-semibold text-[#0B1F3A] ml-1">
                  Overall: {runningPercentage}%
                </span>
              </div>
            </div>

            {/* Quick Actions */}
            {!isSessionLocked ? (
              <div className="flex flex-wrap items-center gap-2">
                <Button variant="outline" size="sm" onClick={() => handleMarkAll('PRESENT')}>
                  Mark All Present
                </Button>
                <Button variant="outline" size="sm" onClick={() => handleMarkAll('ABSENT')}>
                  Mark All Absent
                </Button>
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => setShowSubmitModal(true)}
                  isLoading={isLoading}
                >
                  <CheckCircle2 className="mr-1.5 h-4 w-4" />
                  Submit Attendance
                </Button>
              </div>
            ) : (
              <div className="flex items-center gap-2">
                <Link href="/faculty/sessions">
                  <Button variant="outline" size="sm">
                    <History className="mr-1.5 h-4 w-4" />
                    View Session History
                  </Button>
                </Link>
              </div>
            )}
          </CardHeader>

          {isSessionLocked && (
            <div className="mx-6 mb-4 p-3 rounded-[6px] bg-[#F6F8FB] border border-[#E3E8EF] text-xs text-[#6B7688] flex items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <Lock className="h-4 w-4 text-[#107C41] shrink-0" />
                <span>
                  This session is locked in the institutional ledger. Faculty cannot edit closed sessions. Any post-submission changes must be made via authorized College Admin correction.
                </span>
              </div>
            </div>
          )}

          <CardContent>
            {rosterStudents.length === 0 ? (
              <p className="text-xs text-[#6B7688] text-center py-6">
                No students enrolled in this section roster. Enroll students from the College Admin Student Roster.
              </p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead>
                    <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                      <th className="pb-3 font-semibold uppercase tracking-wider w-28">Roll No.</th>
                      <th className="pb-3 font-semibold uppercase tracking-wider">Student Name</th>
                      <th className="pb-3 font-semibold uppercase tracking-wider hidden sm:table-cell">Email</th>
                      <th className="pb-3 font-semibold uppercase tracking-wider text-right">
                        {isSessionLocked ? 'Finalized Status' : 'Mark Attendance'}
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#E3E8EF]">
                    {rosterStudents.map((student) => {
                      const currentStatus = attendanceMarks[student.id] || 'PRESENT';

                      return (
                        <tr key={student.id} className="hover:bg-[#F6F8FB]">
                          <td className="py-3 font-mono font-medium text-[#1B2433]">
                            {student.enrollment_no}
                          </td>
                          <td className="py-3 font-medium text-[#0B1F3A]">
                            {student.display_name}
                          </td>
                          <td className="py-3 text-[#6B7688] hidden sm:table-cell">
                            {student.primary_email}
                          </td>
                          <td className="py-3 text-right">
                            {isSessionLocked ? (
                              <span
                                className={`inline-flex items-center px-2.5 py-1 rounded text-xs font-bold ${
                                  currentStatus === 'PRESENT'
                                    ? 'bg-[#107C41]/10 text-[#107C41]'
                                    : currentStatus === 'ABSENT'
                                    ? 'bg-[#D64545]/10 text-[#D64545]'
                                    : currentStatus === 'LEAVE'
                                    ? 'bg-[#F2994A]/10 text-[#C2410C]'
                                    : currentStatus === 'LATE'
                                    ? 'bg-[#FEF3C7] text-[#92400E]'
                                    : 'bg-[#EFF6FF] text-[#1D4ED8]'
                                }`}
                              >
                                {currentStatus}
                              </span>
                            ) : (
                              <div className="inline-flex rounded-[6px] border border-[#E3E8EF] p-0.5 bg-white">
                                <button
                                  type="button"
                                  onClick={() => handleMark(student.id, 'PRESENT')}
                                  className={`px-2.5 py-1 text-xs font-bold rounded-[4px] transition-all ${
                                    currentStatus === 'PRESENT'
                                      ? 'bg-[#107C41] text-white shadow-xs'
                                      : 'text-[#6B7688] hover:text-[#0B1F3A]'
                                  }`}
                                >
                                  Present
                                </button>
                                <button
                                  type="button"
                                  onClick={() => handleMark(student.id, 'ABSENT')}
                                  className={`px-2.5 py-1 text-xs font-bold rounded-[4px] transition-all ${
                                    currentStatus === 'ABSENT'
                                      ? 'bg-[#D64545] text-white shadow-xs'
                                      : 'text-[#6B7688] hover:text-[#0B1F3A]'
                                  }`}
                                >
                                  Absent
                                </button>
                                <button
                                  type="button"
                                  onClick={() => handleMark(student.id, 'LEAVE')}
                                  className={`px-2.5 py-1 text-xs font-bold rounded-[4px] transition-all ${
                                    currentStatus === 'LEAVE'
                                      ? 'bg-[#F2994A] text-white shadow-xs'
                                      : 'text-[#6B7688] hover:text-[#0B1F3A]'
                                  }`}
                                >
                                  Leave
                                </button>
                                <button
                                  type="button"
                                  onClick={() => handleMark(student.id, 'LATE')}
                                  className={`px-2 py-1 text-xs font-bold rounded-[4px] transition-all hidden sm:inline-block ${
                                    currentStatus === 'LATE'
                                      ? 'bg-[#D97706] text-white shadow-xs'
                                      : 'text-[#6B7688] hover:text-[#0B1F3A]'
                                  }`}
                                >
                                  Late
                                </button>
                                <button
                                  type="button"
                                  onClick={() => handleMark(student.id, 'ON_DUTY')}
                                  className={`px-2 py-1 text-xs font-bold rounded-[4px] transition-all hidden sm:inline-block ${
                                    currentStatus === 'ON_DUTY'
                                      ? 'bg-[#1D4ED8] text-white shadow-xs'
                                      : 'text-[#6B7688] hover:text-[#0B1F3A]'
                                  }`}
                                >
                                  OD
                                </button>
                              </div>
                            )}
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
      )}

      {/* Submission Confirmation Modal */}
      {showSubmitModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4">
          <div className="bg-white rounded-[12px] shadow-xl border border-[#E3E8EF] max-w-md w-full p-6 space-y-4">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#1F6FEB]/10 text-[#1F6FEB]">
                <CheckCircle2 className="h-6 w-6" />
              </div>
              <div>
                <h3 className="text-base font-bold text-[#0B1F3A]">
                  Confirm Final Attendance Submission
                </h3>
                <p className="text-xs text-[#6B7688]">
                  {activeSlot?.subject_code} — {activeSlot?.subject_name} · {sessionDate}
                </p>
              </div>
            </div>

            <div className="p-4 rounded-[8px] bg-[#F6F8FB] border border-[#E3E8EF] space-y-2">
              <div className="text-xs font-semibold text-[#0B1F3A] mb-1">
                Attendance Counts Breakdown:
              </div>
              <div className="grid grid-cols-2 gap-2 text-xs">
                <div className="flex justify-between p-2 rounded bg-white border border-[#E3E8EF]">
                  <span className="text-[#107C41] font-medium">Present:</span>
                  <span className="font-bold text-[#0B1F3A]">{presentCount}</span>
                </div>
                <div className="flex justify-between p-2 rounded bg-white border border-[#E3E8EF]">
                  <span className="text-[#D64545] font-medium">Absent:</span>
                  <span className="font-bold text-[#0B1F3A]">{absentCount}</span>
                </div>
                {leaveCount > 0 && (
                  <div className="flex justify-between p-2 rounded bg-white border border-[#E3E8EF]">
                    <span className="text-[#C2410C] font-medium">Leave:</span>
                    <span className="font-bold text-[#0B1F3A]">{leaveCount}</span>
                  </div>
                )}
                {lateCount > 0 && (
                  <div className="flex justify-between p-2 rounded bg-white border border-[#E3E8EF]">
                    <span className="text-[#92400E] font-medium">Late:</span>
                    <span className="font-bold text-[#0B1F3A]">{lateCount}</span>
                  </div>
                )}
                {onDutyCount > 0 && (
                  <div className="flex justify-between p-2 rounded bg-white border border-[#E3E8EF]">
                    <span className="text-[#1D4ED8] font-medium">On Duty:</span>
                    <span className="font-bold text-[#0B1F3A]">{onDutyCount}</span>
                  </div>
                )}
                <div className="flex justify-between p-2 rounded bg-white border border-[#E3E8EF]">
                  <span className="text-[#6B7688] font-medium">Total Roster:</span>
                  <span className="font-bold text-[#0B1F3A]">{totalRoster}</span>
                </div>
                <div className="flex justify-between p-2 rounded bg-white border border-[#E3E8EF] col-span-2">
                  <span className="text-[#1F6FEB] font-medium">Attendance Rate:</span>
                  <span className="font-bold text-[#1F6FEB]">{runningPercentage}%</span>
                </div>
              </div>
            </div>

            <div className="p-3 rounded-[6px] bg-[#FFFBF0] border border-[#F2994A]/40 text-[11px] text-[#7B341E] flex items-start gap-2">
              <ShieldAlert className="h-4 w-4 shrink-0 text-[#F2994A] mt-0.5" />
              <span>
                <strong>Important:</strong> After submission, this session is permanently locked into the institutional academic audit trail. Faculty cannot edit locked sheets. Any further changes must be requested through College Administration.
              </span>
            </div>

            <div className="flex items-center justify-end gap-3 pt-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setShowSubmitModal(false)}
                disabled={isLoading}
              >
                Cancel & Review
              </Button>
              <Button
                variant="primary"
                size="sm"
                onClick={confirmAndSubmitAttendance}
                isLoading={isLoading}
              >
                Confirm & Submit to Ledger
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
