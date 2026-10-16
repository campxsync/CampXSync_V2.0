'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  Calendar as CalendarIcon,
  Clock,
  BookOpen,
  User,
  Plus,
  AlertCircle,
  CheckCircle2,
  Layers,
} from 'lucide-react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { EmptyState } from '@/components/ui/empty-state';

interface TimetableEntry {
  id: string;
  day_of_week: number;
  start_time: string;
  end_time: string;
  entry_type: string;
  subject_name?: string;
  subject_code?: string;
  faculty_name?: string;
  section_code?: string;
}

interface Subject {
  id: string;
  name: string;
  code: string;
}

interface Faculty {
  id: string;
  display_name: string;
  employee_no: string;
}

interface Section {
  id: string;
  section_code: string;
  batch_name?: string;
}

interface Props {
  timetableId?: string;
  timetableVersionId?: string;
  entries: TimetableEntry[];
  subjects: Subject[];
  faculty: Faculty[];
  sections: Section[];
}

const DAYS = [
  { num: 1, label: 'Monday' },
  { num: 2, label: 'Tuesday' },
  { num: 3, label: 'Wednesday' },
  { num: 4, label: 'Thursday' },
  { num: 5, label: 'Friday' },
  { num: 6, label: 'Saturday' },
];

export function TimetableGrid({
  timetableVersionId,
  entries,
  subjects,
  faculty,
  sections,
}: Props) {
  const router = useRouter();
  const [showAddModal, setShowAddModal] = useState(false);
  const [selectedDay, setSelectedDay] = useState<number | 'ALL'>('ALL');

  // Form states
  const [subjectId, setSubjectId] = useState(subjects[0]?.id || '');
  const [employeeId, setEmployeeId] = useState(faculty[0]?.id || '');
  const [sectionId, setSectionId] = useState(sections[0]?.id || '');
  const [dayOfWeek, setDayOfWeek] = useState<number>(1);
  const [startTime, setStartTime] = useState('10:00:00');
  const [endTime, setEndTime] = useState('11:00:00');
  const [entryType, setEntryType] = useState<'LECTURE' | 'LAB' | 'TUTORIAL' | 'SEMINAR'>('LECTURE');

  const [isLoading, setIsLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState<{ type: 'error' | 'success'; text: string } | null>(null);

  const handleCreateEntry = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!timetableVersionId) {
      setStatusMessage({ type: 'error', text: 'No active timetable version available.' });
      return;
    }

    setIsLoading(true);
    setStatusMessage(null);

    try {
      const res = await fetch('/api/college/timetable/entries', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          timetableVersionId,
          sectionId,
          subjectId,
          employeeId,
          dayOfWeek: Number(dayOfWeek),
          startTime,
          endTime,
          entryType,
        }),
      });

      const data = await res.json();

      if (!res.ok) {
        setStatusMessage({ type: 'error', text: data.detail || 'Failed to create slot.' });
      } else {
        setStatusMessage({ type: 'success', text: data.message });
        setShowAddModal(false);
        router.refresh();
      }
    } catch (err: any) {
      setStatusMessage({ type: 'error', text: err.message || 'Network error occurred.' });
    } finally {
      setIsLoading(false);
    }
  };

  const filteredDays = selectedDay === 'ALL' ? DAYS : DAYS.filter((d) => d.num === selectedDay);

  return (
    <div className="space-y-6">
      {/* Controls Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div className="flex items-center gap-2 overflow-x-auto pb-1 sm:pb-0">
          <button
            onClick={() => setSelectedDay('ALL')}
            className={`px-3 py-1.5 rounded-[8px] text-xs font-semibold transition-all ${
              selectedDay === 'ALL'
                ? 'bg-[#1F6FEB] text-white shadow-sm'
                : 'bg-white border border-[#E3E8EF] text-[#6B7688] hover:text-[#0B1F3A]'
            }`}
          >
            All Days
          </button>
          {DAYS.map((d) => (
            <button
              key={d.num}
              onClick={() => setSelectedDay(d.num)}
              className={`px-3 py-1.5 rounded-[8px] text-xs font-semibold transition-all ${
                selectedDay === d.num
                  ? 'bg-[#1F6FEB] text-white shadow-sm'
                  : 'bg-white border border-[#E3E8EF] text-[#6B7688] hover:text-[#0B1F3A]'
              }`}
            >
              {d.label}
            </button>
          ))}
        </div>

        <Button
          variant="primary"
          size="sm"
          disabled={!timetableVersionId || subjects.length === 0 || faculty.length === 0}
          onClick={() => setShowAddModal(!showAddModal)}
        >
          <Plus className="mr-1.5 h-4 w-4" />
          {showAddModal ? 'Close Form' : 'Schedule Class Slot'}
        </Button>
      </div>

      {/* Alert */}
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

      {/* Add Slot Drawer */}
      {showAddModal && (
        <Card className="border-[#1F6FEB]/40 bg-[#F6F8FB]/50">
          <CardHeader>
            <CardTitle>Schedule Timetable Slot</CardTitle>
            <CardDescription>
              Assigns a subject section, faculty instructor, and recurring weekly time period in acd.timetable_entries.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleCreateEntry} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                    Subject <span className="text-[#D64545]">*</span>
                  </label>
                  <select
                    className="w-full h-10 px-3 text-xs rounded-[8px] border border-[#E3E8EF] bg-white text-[#1B2433] focus:border-[#1F6FEB] focus:outline-none"
                    value={subjectId}
                    onChange={(e) => setSubjectId(e.target.value)}
                    required
                  >
                    {subjects.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.name} ({s.code})
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                    Faculty Instructor <span className="text-[#D64545]">*</span>
                  </label>
                  <select
                    className="w-full h-10 px-3 text-xs rounded-[8px] border border-[#E3E8EF] bg-white text-[#1B2433] focus:border-[#1F6FEB] focus:outline-none"
                    value={employeeId}
                    onChange={(e) => setEmployeeId(e.target.value)}
                    required
                  >
                    {faculty.map((f) => (
                      <option key={f.id} value={f.id}>
                        {f.display_name} ({f.employee_no})
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                    Section / Batch <span className="text-[#D64545]">*</span>
                  </label>
                  <select
                    className="w-full h-10 px-3 text-xs rounded-[8px] border border-[#E3E8EF] bg-white text-[#1B2433] focus:border-[#1F6FEB] focus:outline-none"
                    value={sectionId}
                    onChange={(e) => setSectionId(e.target.value)}
                    required
                  >
                    {sections.map((sec) => (
                      <option key={sec.id} value={sec.id}>
                        {sec.section_code} {sec.batch_name ? `(${sec.batch_name})` : ''}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">Day of Week</label>
                  <select
                    className="w-full h-10 px-3 text-xs rounded-[8px] border border-[#E3E8EF] bg-white text-[#1B2433] focus:border-[#1F6FEB] focus:outline-none"
                    value={dayOfWeek}
                    onChange={(e) => setDayOfWeek(Number(e.target.value))}
                  >
                    {DAYS.map((d) => (
                      <option key={d.num} value={d.num}>
                        {d.label}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">Start Time</label>
                  <Input
                    type="time"
                    value={startTime.substring(0, 5)}
                    onChange={(e) => setStartTime(`${e.target.value}:00`)}
                    required
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">End Time</label>
                  <Input
                    type="time"
                    value={endTime.substring(0, 5)}
                    onChange={(e) => setEndTime(`${e.target.value}:00`)}
                    required
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[#0B1F3A] mb-1">Entry Type</label>
                  <select
                    className="w-full h-10 px-3 text-xs rounded-[8px] border border-[#E3E8EF] bg-white text-[#1B2433] focus:border-[#1F6FEB] focus:outline-none"
                    value={entryType}
                    onChange={(e: any) => setEntryType(e.target.value)}
                  >
                    <option value="LECTURE">Lecture</option>
                    <option value="LAB">Laboratory</option>
                    <option value="TUTORIAL">Tutorial</option>
                    <option value="SEMINAR">Seminar</option>
                  </select>
                </div>
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <Button type="button" variant="ghost" onClick={() => setShowAddModal(false)}>
                  Cancel
                </Button>
                <Button type="submit" variant="primary" isLoading={isLoading}>
                  Confirm Schedule Slot
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      {/* Weekly Grid */}
      {entries.length === 0 ? (
        <EmptyState
          icon={CalendarIcon}
          title="No Timetable Slots Scheduled"
          description="Create recurring timetable slots to organize faculty teaching schedules and enable attendance tracking."
          action={
            timetableVersionId && subjects.length > 0 && faculty.length > 0
              ? {
                  label: 'Schedule First Class Slot',
                  onClick: () => setShowAddModal(true),
                }
              : undefined
          }
        />
      ) : (
        <div className="space-y-4">
          {filteredDays.map((day) => {
            const dayEntries = entries
              .filter((e) => e.day_of_week === day.num)
              .sort((a, b) => a.start_time.localeCompare(b.start_time));

            return (
              <Card key={day.num}>
                <CardHeader className="py-3 bg-[#F6F8FB]/60 border-b border-[#E3E8EF]">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold uppercase tracking-wider text-[#0B1F3A]">
                      {day.label}
                    </span>
                    <span className="text-[11px] font-medium text-[#6B7688]">
                      {dayEntries.length} {dayEntries.length === 1 ? 'Period' : 'Periods'}
                    </span>
                  </div>
                </CardHeader>
                <CardContent className="p-3">
                  {dayEntries.length === 0 ? (
                    <p className="text-xs text-[#6B7688] italic py-2 text-center">
                      No classes scheduled for {day.label}.
                    </p>
                  ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                      {dayEntries.map((slot) => (
                        <div
                          key={slot.id}
                          className="p-3 rounded-[8px] border border-[#E3E8EF] bg-white hover:border-[#1F6FEB]/40 transition-all space-y-2 shadow-xs"
                        >
                          <div className="flex items-center justify-between">
                            <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-[#E8F0FE] text-[#1F6FEB]">
                              {slot.entry_type}
                            </span>
                            <div className="flex items-center gap-1 text-[11px] font-mono font-medium text-[#6B7688]">
                              <Clock className="h-3 w-3" />
                              <span>
                                {slot.start_time.substring(0, 5)} - {slot.end_time.substring(0, 5)}
                              </span>
                            </div>
                          </div>

                          <div>
                            <p className="text-xs font-bold text-[#0B1F3A]">
                              {slot.subject_name || slot.subject_code || 'Subject Slot'}
                            </p>
                            <p className="text-[11px] font-mono text-[#6B7688]">
                              {slot.subject_code}
                            </p>
                          </div>

                          <div className="pt-1 border-t border-[#E3E8EF]/60 flex items-center justify-between text-[11px] text-[#6B7688]">
                            <div className="flex items-center gap-1">
                              <User className="h-3 w-3 text-[#1F6FEB]" />
                              <span>{slot.faculty_name || 'Faculty Assigned'}</span>
                            </div>
                            <span className="font-semibold text-[#1B2433]">
                              {slot.section_code || 'Section A'}
                            </span>
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
      )}
    </div>
  );
}
