'use client';

import React, { useState } from 'react';
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
} from 'lucide-react';
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
}

export function FacultyAttendanceView({ slots, students, employeeId }: Props) {
  const router = useRouter();
  const [selectedSlotId, setSelectedSlotId] = useState<string>(slots[0]?.id || '');
  const [sessionDate, setSessionDate] = useState<string>(
    new Date().toISOString().split('T')[0]
  );

  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [isSessionLocked, setIsSessionLocked] = useState(false);

  // Student marks: studentId -> status
  const [attendanceMarks, setAttendanceMarks] = useState<Record<string, 'PRESENT' | 'ABSENT' | 'LEAVE'>>({});

  const [isLoading, setIsLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState<{ type: 'error' | 'success'; text: string } | null>(null);

  const activeSlot = slots.find((s) => s.id === selectedSlotId);

  const handleOpenSession = async () => {
    if (!activeSlot) return;
    setIsLoading(true);
    setStatusMessage(null);

    try {
      const res = await fetch('/api/college/attendance/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          timetableEntryId: activeSlot.id,
          sectionId: activeSlot.section_id,
          employeeId: activeSlot.employee_id,
          sessionDate,
          startTime: activeSlot.start_time,
          endTime: activeSlot.end_time,
        }),
      });

      const data = await res.json();

      if (!res.ok) {
        setStatusMessage({ type: 'error', text: data.detail || 'Failed to open session.' });
      } else {
        setActiveSessionId(data.session.id);
        setIsSessionLocked(data.session.status === 'CLOSED');

        // Default all students to PRESENT
        const initialMarks: Record<string, 'PRESENT' | 'ABSENT' | 'LEAVE'> = {};
        students.forEach((s) => {
          initialMarks[s.id] = 'PRESENT';
        });
        setAttendanceMarks(initialMarks);
        setStatusMessage({ type: 'success', text: `Session open for ${activeSlot.subject_name}.` });
      }
    } catch (err: any) {
      setStatusMessage({ type: 'error', text: err.message || 'Network error occurred.' });
    } finally {
      setIsLoading(false);
    }
  };

  const handleMark = (studentId: string, status: 'PRESENT' | 'ABSENT' | 'LEAVE') => {
    if (isSessionLocked) return;
    setAttendanceMarks((prev) => ({ ...prev, [studentId]: status }));
  };

  const handleMarkAll = (status: 'PRESENT' | 'ABSENT') => {
    if (isSessionLocked) return;
    const updated: Record<string, 'PRESENT' | 'ABSENT' | 'LEAVE'> = {};
    students.forEach((s) => {
      updated[s.id] = status;
    });
    setAttendanceMarks(updated);
  };

  const handleSubmitAttendance = async () => {
    if (!activeSessionId) return;
    setIsLoading(true);
    setStatusMessage(null);

    const recordPayload = students.map((s) => ({
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
        setStatusMessage({ type: 'success', text: data.message });
        setIsSessionLocked(true);
      }
    } catch (err: any) {
      setStatusMessage({ type: 'error', text: err.message || 'Network error occurred.' });
    } finally {
      setIsLoading(false);
    }
  };

  const presentCount = Object.values(attendanceMarks).filter((s) => s === 'PRESENT').length;
  const absentCount = Object.values(attendanceMarks).filter((s) => s === 'ABSENT').length;

  return (
    <div className="space-y-6">
      {/* Slot Selection Card */}
      <Card>
        <CardHeader>
          <CardTitle>Select Class Period to Capture Attendance</CardTitle>
          <CardDescription>
            Choose from your scheduled timetable slots. Attendance must be recorded against a published timetable slot.
          </CardDescription>
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
                  setSelectedSlotId(e.target.value);
                  setActiveSessionId(null);
                  setIsSessionLocked(false);
                }}
              >
                {slots.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.subject_name} ({s.subject_code}) — {s.start_time.substring(0, 5)} - {s.end_time.substring(0, 5)} [{s.section_code}]
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
                  setSessionDate(e.target.value);
                  setActiveSessionId(null);
                  setIsSessionLocked(false);
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
                {activeSessionId ? 'Reload Roster' : 'Open Attendance Session'}
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Status Alert */}
      {statusMessage && (
        <div
          className={`p-3 rounded-[8px] border text-xs flex items-center gap-2 ${
            statusMessage.type === 'error'
              ? 'border-[#D64545] bg-[#FDF3F3] text-[#D64545]'
              : 'border-[#107C41] bg-[#DFF6DD] text-[#107C41]'
          }`}
        >
          {statusMessage.type === 'error' ? (
            <AlertCircle className="h-4 w-4 shrink-0" />
          ) : (
            <CheckCircle2 className="h-4 w-4 shrink-0" />
          )}
          <span>{statusMessage.text}</span>
        </div>
      )}

      {/* Attendance Roster Grid */}
      {activeSessionId && (
        <Card className="border-[#1F6FEB]/30">
          <CardHeader className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div>
              <div className="flex items-center gap-2">
                <CardTitle>Section Roster ({students.length} Students)</CardTitle>
                {isSessionLocked && (
                  <span className="flex items-center gap-1 text-[11px] font-bold text-[#107C41] bg-[#DFF6DD] px-2 py-0.5 rounded">
                    <Lock className="h-3 w-3" /> Submitted & Locked
                  </span>
                )}
              </div>
              <CardDescription>
                Summary: <span className="font-bold text-[#107C41]">{presentCount} Present</span>,{' '}
                <span className="font-bold text-[#D64545]">{absentCount} Absent</span>
              </CardDescription>
            </div>

            {!isSessionLocked && (
              <div className="flex items-center gap-2">
                <Button variant="outline" size="sm" onClick={() => handleMarkAll('PRESENT')}>
                  Mark All Present
                </Button>
                <Button variant="outline" size="sm" onClick={() => handleMarkAll('ABSENT')}>
                  Mark All Absent
                </Button>
                <Button variant="primary" size="sm" onClick={handleSubmitAttendance} isLoading={isLoading}>
                  <CheckCircle2 className="mr-1.5 h-4 w-4" />
                  Submit Attendance
                </Button>
              </div>
            )}
          </CardHeader>
          <CardContent>
            {students.length === 0 ? (
              <p className="text-xs text-[#6B7688] text-center py-6">
                No students enrolled in this section. Enroll students from the College Admin Student Roster.
              </p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead>
                    <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                      <th className="pb-3 font-semibold uppercase tracking-wider">Roll No.</th>
                      <th className="pb-3 font-semibold uppercase tracking-wider">Student Name</th>
                      <th className="pb-3 font-semibold uppercase tracking-wider">Email</th>
                      <th className="pb-3 font-semibold uppercase tracking-wider text-right">Attendance Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#E3E8EF]">
                    {students.map((student) => {
                      const currentStatus = attendanceMarks[student.id] || 'PRESENT';

                      return (
                        <tr key={student.id} className="hover:bg-[#F6F8FB]">
                          <td className="py-3 font-mono font-medium text-[#1B2433]">
                            {student.enrollment_no}
                          </td>
                          <td className="py-3 font-medium text-[#0B1F3A]">
                            {student.display_name}
                          </td>
                          <td className="py-3 text-[#6B7688]">
                            {student.primary_email}
                          </td>
                          <td className="py-3 text-right">
                            <div className="inline-flex rounded-[6px] border border-[#E3E8EF] p-0.5 bg-white">
                              <button
                                type="button"
                                disabled={isSessionLocked}
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
                                disabled={isSessionLocked}
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
                                disabled={isSessionLocked}
                                onClick={() => handleMark(student.id, 'LEAVE')}
                                className={`px-2.5 py-1 text-xs font-bold rounded-[4px] transition-all ${
                                  currentStatus === 'LEAVE'
                                    ? 'bg-[#F2994A] text-white shadow-xs'
                                    : 'text-[#6B7688] hover:text-[#0B1F3A]'
                                }`}
                              >
                                Leave
                              </button>
                            </div>
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
    </div>
  );
}
