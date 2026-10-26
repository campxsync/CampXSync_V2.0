'use client';

import React, { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { AlertCircle, CalendarDays, CheckCircle2 } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/ui/empty-state';

export interface CollegeOption {
  id: string;
  name: string;
  code: string;
}

export interface YearOption {
  id: string;
  code: string;
  startDate: string;
  endDate: string;
}

export interface AcademicCalendar {
  id: string;
  code: string;
  name: string;
  calendarType: string;
  status: string;
  yearCode: string | null;
  yearStart: string | null;
  yearEnd: string | null;
  collegeName: string | null;
  terms: {
    termNo: number;
    name: string;
    termType: string;
    startDate: string;
    endDate: string;
    status: string;
  }[];
  events: {
    eventType: string;
    title: string;
    startDate: string;
    endDate: string;
    isHoliday: boolean;
  }[];
  workingDays: {
    dayOfWeek: number;
    isWorking: boolean;
    isHalfDay: boolean;
  }[];
}

const WEEK = [
  { day: 1, label: 'Mon' },
  { day: 2, label: 'Tue' },
  { day: 3, label: 'Wed' },
  { day: 4, label: 'Thu' },
  { day: 5, label: 'Fri' },
  { day: 6, label: 'Sat' },
  { day: 0, label: 'Sun' },
];

interface Props {
  colleges: CollegeOption[];
  years: YearOption[];
  calendars: AcademicCalendar[];
}

async function readDetail(res: Response): Promise<string> {
  const data = await res.json().catch(() => ({}));
  return data.detail || data.message || 'The request could not be completed.';
}

export function CalendarManager({ colleges, years, calendars }: Props) {
  const router = useRouter();
  const [message, setMessage] = useState<{ type: 'error' | 'success'; text: string } | null>(null);
  const [saving, setSaving] = useState(false);
  const [selectedId, setSelectedId] = useState(calendars[0]?.id || '');
  const [collegeId, setCollegeId] = useState(colleges[0]?.id || '');
  const [academicYearId, setAcademicYearId] = useState(years[0]?.id || '');
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [calendarType, setCalendarType] = useState('ACADEMIC');
  const [termNo, setTermNo] = useState('1');
  const [termName, setTermName] = useState('');
  const [termType, setTermType] = useState('SEMESTER');
  const [termStart, setTermStart] = useState(years[0]?.startDate || '');
  const [termEnd, setTermEnd] = useState(years[0]?.endDate || '');
  const [eventType, setEventType] = useState('HOLIDAY');
  const [eventTitle, setEventTitle] = useState('');
  const [eventStart, setEventStart] = useState(years[0]?.startDate || '');
  const [eventEnd, setEventEnd] = useState(years[0]?.startDate || '');

  useEffect(() => {
    if (!selectedId && calendars[0]) {
      setSelectedId(calendars[0].id);
    }
  }, [calendars, selectedId]);

  const selected = calendars.find((item) => item.id === selectedId) || null;
  const editable = selected && selected.status !== 'ARCHIVED';
  const draft = selected?.status === 'DRAFT';

  const createCalendar = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setMessage(null);
    try {
      const res = await fetch('/api/college/calendars', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          collegeId,
          academicYearId,
          code: code.trim().toUpperCase(),
          name: name.trim(),
          calendarType,
        }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        setMessage({ type: 'error', text: data.detail || 'The request could not be completed.' });
        return;
      }
      if (data.calendar?.id) setSelectedId(data.calendar.id);
      setCode('');
      setName('');
      setMessage({ type: 'success', text: data.message || 'Draft calendar opened.' });
      router.refresh();
    } finally {
      setSaving(false);
    }
  };

  const addTerm = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!selected) return;
    setSaving(true);
    setMessage(null);
    try {
      const res = await fetch('/api/college/calendars/terms', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          calendarId: selected.id,
          termNo: Number(termNo),
          name: termName.trim(),
          termType,
          startDate: termStart,
          endDate: termEnd,
        }),
      });
      const detail = await readDetail(res);
      setMessage({ type: res.ok ? 'success' : 'error', text: detail });
      if (res.ok) {
        setTermName('');
        router.refresh();
      }
    } finally {
      setSaving(false);
    }
  };

  const addEvent = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!selected) return;
    setSaving(true);
    setMessage(null);
    try {
      const res = await fetch('/api/college/calendars/events', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          calendarId: selected.id,
          eventType,
          title: eventTitle.trim(),
          startDate: eventStart,
          endDate: eventEnd,
        }),
      });
      const detail = await readDetail(res);
      setMessage({ type: res.ok ? 'success' : 'error', text: detail });
      if (res.ok) {
        setEventTitle('');
        router.refresh();
      }
    } finally {
      setSaving(false);
    }
  };

  const toggleDay = async (dayOfWeek: number, isWorking: boolean) => {
    if (!selected) return;
    setSaving(true);
    setMessage(null);
    try {
      const res = await fetch('/api/college/calendars/working-days', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          calendarId: selected.id,
          dayOfWeek,
          isWorking,
          isHalfDay: false,
        }),
      });
      const detail = await readDetail(res);
      if (!res.ok) {
        setMessage({ type: 'error', text: detail });
        return;
      }
      router.refresh();
    } finally {
      setSaving(false);
    }
  };

  const publish = async () => {
    if (!selected) return;
    setSaving(true);
    setMessage(null);
    try {
      const res = await fetch('/api/college/calendars/publish', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ calendarId: selected.id }),
      });
      const detail = await readDetail(res);
      setMessage({ type: res.ok ? 'success' : 'error', text: detail });
      if (res.ok) router.refresh();
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      {message && (
        <div
          className={`flex items-center gap-2 rounded-[8px] border p-3 text-xs ${
            message.type === 'error'
              ? 'border-[#B91C1C] bg-[#FDF3F3] text-[#B91C1C]'
              : 'border-[#0D6535] bg-[#DFF6DD] text-[#0D6535]'
          }`}
        >
          {message.type === 'error' ? <AlertCircle className="h-4 w-4" /> : <CheckCircle2 className="h-4 w-4" />}
          <span>{message.text}</span>
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Open a draft calendar</CardTitle>
          <CardDescription>
            A new calendar starts as a draft with Monday to Friday as working days.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={createCalendar} className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <label className="text-xs font-semibold text-[#0B1F3A]">
              College
              <select
                className="mt-1 h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 text-xs"
                value={collegeId}
                onChange={(event) => setCollegeId(event.target.value)}
                required
              >
                {colleges.map((college) => (
                  <option key={college.id} value={college.id}>
                    {college.name} ({college.code})
                  </option>
                ))}
              </select>
            </label>
            <label className="text-xs font-semibold text-[#0B1F3A]">
              Academic year
              <select
                className="mt-1 h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 text-xs"
                value={academicYearId}
                onChange={(event) => setAcademicYearId(event.target.value)}
                required
              >
                {years.map((year) => (
                  <option key={year.id} value={year.id}>
                    {year.code} ({year.startDate} to {year.endDate})
                  </option>
                ))}
              </select>
            </label>
            <label className="text-xs font-semibold text-[#0B1F3A]">
              Code
              <Input
                value={code}
                onChange={(event) => setCode(event.target.value.toUpperCase())}
                placeholder="CAL-DIT-2026"
                required
                className="mt-1 font-mono uppercase"
              />
            </label>
            <label className="text-xs font-semibold text-[#0B1F3A]">
              Name
              <Input
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder="Academic calendar 2026-27"
                required
                className="mt-1"
              />
            </label>
            <label className="text-xs font-semibold text-[#0B1F3A]">
              Type
              <select
                className="mt-1 h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 text-xs"
                value={calendarType}
                onChange={(event) => setCalendarType(event.target.value)}
              >
                <option value="ACADEMIC">Academic</option>
                <option value="HOLIDAY">Holiday</option>
                <option value="EXAM">Examination</option>
                <option value="ADMINISTRATIVE">Administrative</option>
              </select>
            </label>
            <div className="flex items-end justify-end">
              <Button type="submit" variant="primary" isLoading={saving} disabled={colleges.length === 0 || years.length === 0}>
                Create draft
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {calendars.length === 0 ? (
        <EmptyState icon={CalendarDays} title="No calendar yet" description="Open a draft, add the terms, then publish it." />
      ) : (
        <Card>
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <CardTitle>
                  <select
                    className="bg-transparent text-base font-semibold text-[#0B1F3A]"
                    value={selectedId}
                    onChange={(event) => setSelectedId(event.target.value)}
                  >
                    {calendars.map((item) => (
                      <option key={item.id} value={item.id}>
                        {item.name} ({item.code})
                      </option>
                    ))}
                  </select>
                </CardTitle>
                <CardDescription>
                  {selected?.collegeName || 'Institute'} · {selected?.calendarType}
                  {selected?.yearCode ? ` · ${selected.yearCode}` : ''}
                  {selected?.yearStart ? ` · ${selected.yearStart} to ${selected.yearEnd}` : ''}
                </CardDescription>
              </div>
              {selected && <Badge status={selected.status} />}
            </div>
          </CardHeader>
          <CardContent className="space-y-6">
            <div>
              <p className="mb-2 text-xs font-semibold text-[#0B1F3A]">Working week</p>
              <div className="flex flex-wrap gap-2">
                {WEEK.map((day) => {
                  const saved = selected?.workingDays.find((item) => item.dayOfWeek === day.day);
                  const working = saved?.isWorking === true;
                  return (
                    <button
                      key={day.day}
                      type="button"
                      disabled={!editable || saving}
                      onClick={() => toggleDay(day.day, !working)}
                      className={`rounded-[8px] border px-3 py-2 text-xs font-semibold ${
                        working
                          ? 'border-[#15803D] bg-[#EAF8F1] text-[#15803D]'
                          : 'border-[#E3E8EF] bg-white text-[#6B7688]'
                      }`}
                    >
                      {day.label}
                    </button>
                  );
                })}
              </div>
            </div>

            <div>
              <p className="mb-2 text-xs font-semibold text-[#0B1F3A]">Terms</p>
              {selected && selected.terms.length > 0 ? (
                <table className="w-full text-left text-xs">
                  <thead>
                    <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                      <th className="pb-2 font-semibold">No.</th>
                      <th className="pb-2 font-semibold">Term</th>
                      <th className="pb-2 font-semibold">Dates</th>
                      <th className="pb-2 font-semibold">Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {selected.terms.map((term) => (
                      <tr key={`${selected.id}-${term.termNo}`} className="border-b border-[#E3E8EF]">
                        <td className="py-2 font-mono">{term.termNo}</td>
                        <td className="py-2">
                          <span className="font-semibold text-[#0B1F3A]">{term.name}</span>
                          <span className="ml-2 text-[#6B7688]">{term.termType}</span>
                        </td>
                        <td className="py-2 font-mono">
                          {term.startDate} to {term.endDate}
                        </td>
                        <td className="py-2">{term.status}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              ) : (
                <p className="text-xs text-[#6B7688]">No terms on this calendar yet.</p>
              )}
            </div>

            <div>
              <p className="mb-2 text-xs font-semibold text-[#0B1F3A]">Holidays and events</p>
              {selected && selected.events.length > 0 ? (
                <table className="w-full text-left text-xs">
                  <thead>
                    <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                      <th className="pb-2 font-semibold">Date</th>
                      <th className="pb-2 font-semibold">Title</th>
                      <th className="pb-2 font-semibold">Type</th>
                    </tr>
                  </thead>
                  <tbody>
                    {selected.events.map((item) => (
                      <tr key={`${selected.id}-${item.title}-${item.startDate}`} className="border-b border-[#E3E8EF]">
                        <td className="py-2 font-mono">
                          {item.startDate}
                          {item.endDate !== item.startDate ? ` to ${item.endDate}` : ''}
                        </td>
                        <td className="py-2 font-semibold text-[#0B1F3A]">{item.title}</td>
                        <td className="py-2">{item.eventType.replaceAll('_', ' ')}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              ) : (
                <p className="text-xs text-[#6B7688]">No holidays or events recorded.</p>
              )}
            </div>
          </CardContent>
        </Card>
      )}

      {draft && selected && (
        <Card>
          <CardHeader>
            <CardTitle>Add a term to {selected.code}</CardTitle>
            <CardDescription>Term dates must sit inside the academic year and must not overlap another term.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={addTerm} className="grid grid-cols-1 gap-4 lg:grid-cols-3">
              <label className="text-xs font-semibold text-[#0B1F3A]">
                Term number
                <Input type="number" min={1} max={16} value={termNo} onChange={(event) => setTermNo(event.target.value)} required className="mt-1" />
              </label>
              <label className="text-xs font-semibold text-[#0B1F3A] lg:col-span-2">
                Name
                <Input value={termName} onChange={(event) => setTermName(event.target.value)} placeholder="Semester 1" required className="mt-1" />
              </label>
              <label className="text-xs font-semibold text-[#0B1F3A]">
                Type
                <select
                  className="mt-1 h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 text-xs"
                  value={termType}
                  onChange={(event) => setTermType(event.target.value)}
                >
                  <option value="SEMESTER">Semester</option>
                  <option value="TRIMESTER">Trimester</option>
                  <option value="QUARTER">Quarter</option>
                  <option value="SUMMER">Summer</option>
                  <option value="WINTER">Winter</option>
                </select>
              </label>
              <label className="text-xs font-semibold text-[#0B1F3A]">
                Starts
                <Input type="date" value={termStart} onChange={(event) => setTermStart(event.target.value)} required className="mt-1" />
              </label>
              <label className="text-xs font-semibold text-[#0B1F3A]">
                Ends
                <Input type="date" value={termEnd} onChange={(event) => setTermEnd(event.target.value)} required className="mt-1" />
              </label>
              <div className="flex items-end justify-end gap-2 lg:col-span-3">
                <Button type="button" variant="outline" onClick={publish} isLoading={saving} disabled={selected.terms.length === 0}>
                  Publish calendar
                </Button>
                <Button type="submit" variant="primary" isLoading={saving}>
                  Add term
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      {editable && selected && (
        <Card>
          <CardHeader>
            <CardTitle>Record a holiday or break</CardTitle>
            <CardDescription>
              A published calendar can still take a holiday or an examination window. A holiday marks the day as closed.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={addEvent} className="grid grid-cols-1 gap-4 lg:grid-cols-4">
              <label className="text-xs font-semibold text-[#0B1F3A]">
                Type
                <select
                  className="mt-1 h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 text-xs"
                  value={eventType}
                  onChange={(event) => setEventType(event.target.value)}
                >
                  <option value="HOLIDAY">Holiday</option>
                  <option value="EXAM">Examination</option>
                  <option value="TERM_START">Term start</option>
                  <option value="TERM_END">Term end</option>
                  <option value="COMMENCEMENT">Commencement</option>
                  <option value="EVENT">Event</option>
                  <option value="DEADLINE">Deadline</option>
                  <option value="OTHER">Other</option>
                </select>
              </label>
              <label className="text-xs font-semibold text-[#0B1F3A] lg:col-span-3">
                Title
                <Input value={eventTitle} onChange={(event) => setEventTitle(event.target.value)} placeholder="Gandhi Jayanti" required className="mt-1" />
              </label>
              <label className="text-xs font-semibold text-[#0B1F3A]">
                Starts
                <Input type="date" value={eventStart} onChange={(event) => setEventStart(event.target.value)} required className="mt-1" />
              </label>
              <label className="text-xs font-semibold text-[#0B1F3A]">
                Ends
                <Input type="date" value={eventEnd} onChange={(event) => setEventEnd(event.target.value)} required className="mt-1" />
              </label>
              <div className="flex items-end justify-end lg:col-span-2">
                <Button type="submit" variant="primary" isLoading={saving}>
                  Add to calendar
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
