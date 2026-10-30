'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';

type Named = { id: string; code: string; name: string };

async function post(url: string, body: unknown) {
  const res = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.detail || 'The record could not be saved.');
  return data;
}

export function ExaminationsManager(props: {
  colleges: Named[];
  subjects: Named[];
  students: { id: string; label: string }[];
  faculty: { id: string; label: string }[];
  exams: { id: string; code: string; name: string; status: string; subject_code: string | null; max_marks: number; pass_marks: number | null }[];
  registrations: { id: string; registration_no: string | null; status: string; enrollment_no: string; exam_code: string; result: string | null }[];
  sessions: { id: string; session_date: string; start_time: string; end_time: string; status: string; centre_code: string | null; room_code: string | null; exam_code: string }[];
  tickets: { id: string; status: string; enrollment_no: string; seat_no: string | null }[];
  scripts: { id: string; barcode: string; status: string; enrollment_no: string }[];
  marks: { id: string; total_marks: number; status: string; barcode: string }[];
  results: { id: string; overall_status: string; status: string; enrollment_no: string; marks_obtained: number | null; max_marks: number | null }[];
}) {
  const router = useRouter();
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const openExams = props.exams.filter((item) => item.status === 'REGISTRATION_OPEN');

  const run = async (work: () => Promise<{ message?: string }>) => {
    setSaving(true); setError(null); setMessage(null);
    try {
      const data = await work();
      setMessage(data.message || 'Saved.');
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'The record could not be saved.');
    } finally { setSaving(false); }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-[#0B1F3A]">Examinations</h1>
        <p className="mt-1 max-w-3xl text-sm text-[#5C6B80]">
          Open a regular examination, register a student, publish one sitting, record a hall ticket and script barcode, then finalize marks and process a result. No PDF, scanner, or SGPA is produced.
        </p>
      </div>
      {message && <p className="text-sm text-[#067647]">{message}</p>}
      {error && <p className="text-sm text-[#B42318]">{error}</p>}
      <Card>
        <CardHeader>
          <CardTitle>Planned examination</CardTitle>
          <CardDescription>The marks come from the active assessment scheme for the subject.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); run(() => post('/api/college/examinations', { collegeId: form.get('collegeId'), code: String(form.get('code')).toUpperCase(), name: form.get('name'), subjectId: form.get('subjectId') })); }}>
            <Select name="collegeId" label="College" options={props.colleges.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }))} />
            <Select name="subjectId" label="Subject" options={props.subjects.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }))} />
            <Field name="code" label="Code" placeholder="ODD-2026" />
            <Field name="name" label="Name" placeholder="Odd semester examinations 2026" />
            <Button type="submit" variant="primary" isLoading={saving}>Open planned exam</Button>
          </form>
          <Rows rows={props.exams.map((item) => ({ id: item.id, title: `${item.code} · ${item.name}`, meta: `${item.subject_code || ''} · ${item.max_marks} / pass ${item.pass_marks ?? '—'}`, status: item.status, action: item.status === 'PLANNED' ? () => run(() => post('/api/college/examinations/open', { examinationId: item.id })) : undefined, actionLabel: 'Open registration' }))} />
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Registration</CardTitle>
          <CardDescription>A draft registration with a fee-balance eligibility snapshot.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); run(() => post('/api/college/examinations/registrations', { examinationId: form.get('examinationId'), studentId: form.get('studentId') })); }}>
            <Select name="examinationId" label="Open examination" options={openExams.map((item) => ({ value: item.id, label: item.code }))} />
            <Select name="studentId" label="Student" options={props.students.map((item) => ({ value: item.id, label: item.label }))} />
            <Button type="submit" variant="primary" isLoading={saving} disabled={openExams.length === 0}>Register</Button>
          </form>
          <Rows rows={props.registrations.map((item) => ({ id: item.id, title: item.registration_no || item.id, meta: `${item.exam_code} · ${item.enrollment_no} · ${item.result || 'no snapshot'}`, status: item.status }))} />
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Schedule</CardTitle>
          <CardDescription>One centre, room, and sitting, published as schedule version 1.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); run(() => post('/api/college/examinations/schedule', { examinationId: form.get('examinationId'), centreCode: String(form.get('centreCode')).toUpperCase(), centreName: form.get('centreName'), roomCode: String(form.get('roomCode')).toUpperCase(), capacity: Number(form.get('capacity')), sessionDate: form.get('sessionDate'), startTime: form.get('startTime'), endTime: form.get('endTime') })); }}>
            <Select name="examinationId" label="Examination" options={props.exams.map((item) => ({ value: item.id, label: item.code }))} />
            <Field name="centreCode" label="Centre code" placeholder="DIT-CENTRE" />
            <Field name="centreName" label="Centre name" placeholder="DIT examination centre" />
            <Field name="roomCode" label="Room code" placeholder="HALL-A" />
            <Field name="capacity" label="Capacity" placeholder="40" />
            <Field name="sessionDate" label="Date" placeholder="2026-12-16" />
            <Field name="startTime" label="Start" placeholder="10:00" />
            <Field name="endTime" label="End" placeholder="13:00" />
            <Button type="submit" variant="primary" isLoading={saving}>Publish schedule</Button>
          </form>
          <Rows rows={props.sessions.map((item) => ({ id: item.id, title: `${item.exam_code} · ${item.session_date}`, meta: `${item.start_time}–${item.end_time} · ${item.centre_code || ''} · ${item.room_code || ''}`, status: item.status }))} />
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Hall ticket and script</CardTitle>
          <CardDescription>A generated ticket with a seat number, and a barcode stored on the script. No file or scanner.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); run(() => post('/api/college/examinations/hall-tickets', { registrationId: form.get('registrationId'), seatNo: form.get('seatNo') })); }}>
            <Select name="registrationId" label="Registration" options={props.registrations.map((item) => ({ value: item.id, label: `${item.registration_no} · ${item.enrollment_no}` }))} />
            <Field name="seatNo" label="Seat" placeholder="A-12" />
            <Button type="submit" variant="primary" isLoading={saving}>Record hall ticket</Button>
          </form>
          <Rows rows={props.tickets.map((item) => ({ id: item.id, title: item.enrollment_no, meta: `seat ${item.seat_no || '—'}`, status: item.status }))} />
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); run(() => post('/api/college/examinations/scripts', { registrationId: form.get('registrationId'), barcode: form.get('barcode') })); }}>
            <Select name="registrationId" label="Registration" options={props.registrations.map((item) => ({ value: item.id, label: `${item.registration_no} · ${item.enrollment_no}` }))} />
            <Field name="barcode" label="Barcode" placeholder="SCR-ODD-004" />
            <Button type="submit" variant="primary" isLoading={saving}>Record script</Button>
          </form>
          <Rows rows={props.scripts.map((item) => ({ id: item.id, title: item.barcode, meta: item.enrollment_no, status: item.status }))} />
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Marks and result</CardTitle>
          <CardDescription>Finalized marks against the script, then a processed result. Pass uses the paper pass mark. Publishing does not calculate SGPA.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); run(() => post('/api/college/examinations/marks', { scriptId: form.get('scriptId'), employeeId: form.get('employeeId'), totalMarks: Number(form.get('totalMarks')) })); }}>
            <Select name="scriptId" label="Script" options={props.scripts.map((item) => ({ value: item.id, label: `${item.barcode} · ${item.enrollment_no}` }))} />
            <Select name="employeeId" label="Faculty" options={props.faculty.map((item) => ({ value: item.id, label: item.label }))} />
            <Field name="totalMarks" label="Marks" placeholder="72" />
            <Button type="submit" variant="primary" isLoading={saving}>Finalize marks</Button>
          </form>
          <Rows rows={props.marks.map((item) => ({ id: item.id, title: item.barcode, meta: `${item.total_marks} marks`, status: item.status }))} />
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); run(() => post('/api/college/examinations/results', { registrationId: form.get('registrationId') })); }}>
            <Select name="registrationId" label="Registration" options={props.registrations.map((item) => ({ value: item.id, label: `${item.registration_no} · ${item.enrollment_no}` }))} />
            <Button type="submit" variant="primary" isLoading={saving}>Process result</Button>
          </form>
          <Rows rows={props.results.map((item) => ({ id: item.id, title: `${item.enrollment_no} · ${item.overall_status}`, meta: `${item.marks_obtained ?? '—'} / ${item.max_marks ?? '—'}`, status: item.status, action: item.status === 'PROCESSED' ? () => run(() => post('/api/college/examinations/results/publish', { resultId: item.id })) : undefined, actionLabel: 'Publish result' }))} />
        </CardContent>
      </Card>
    </div>
  );
}

function Field({ name, label, placeholder }: { name: string; label: string; placeholder: string }) {
  return <label className="text-xs font-semibold text-[#0B1F3A]">{label}<Input name={name} placeholder={placeholder} required className="mt-1" /></label>;
}
function Select({ name, label, options }: { name: string; label: string; options: { value: string; label: string }[] }) {
  return <label className="text-xs font-semibold text-[#0B1F3A]">{label}<select name={name} required defaultValue={options[0]?.value || ''} className="mt-1 h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 text-xs">{options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>;
}
function Rows({ rows }: { rows: { id: string; title: string; meta: string; status: string; action?: () => void; actionLabel?: string }[] }) {
  if (rows.length === 0) return <p className="text-xs text-[#6B7688]">Nothing recorded yet.</p>;
  return <div className="space-y-2">{rows.map((row) => <div key={row.id} className="flex flex-wrap items-center justify-between gap-2 rounded-[8px] border border-[#E3E8EF] px-3 py-2"><div><p className="text-sm font-semibold text-[#0B1F3A]">{row.title}</p><p className="text-xs text-[#6B7688]">{row.meta}</p></div><div className="flex items-center gap-2"><Badge status={row.status} />{row.action && <Button type="button" variant="outline" onClick={row.action}>{row.actionLabel}</Button>}</div></div>)}</div>;
}
