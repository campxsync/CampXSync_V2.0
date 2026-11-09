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

export function StaffManager(props: {
  colleges: Named[];
  faculty: { id: string; label: string }[];
  leaves: { id: string; status: string; from_date: string; to_date: string; total_days: number; employee_no: string }[];
  attendance: { id: string; status: string; attendance_date: string; employee_no: string }[];
  cycles: { id: string; name: string; status: string; start_date: string; end_date: string }[];
  structures: { id: string; code: string; name: string; status: string }[];
  postings: { id: string; posting_no: string; title: string; status: string }[];
  onboardings: { id: string; status: string; expected_join_date: string; employee_no: string }[];
  training: { id: string; status: string; code: string; employee_no: string }[];
  employeeCount: number;
}) {
  const router = useRouter();
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
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
  const colleges = props.colleges.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }));

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-[#0B1F3A]">Staff</h1>
        <p className="mt-1 max-w-3xl text-sm text-[#5C6B80]">
          Record leave, a staff day, an appraisal cycle, a draft salary structure, a vacancy, onboarding, and a training nomination.
          {` ${props.employeeCount} faculty are on the roster.`} A calculated payslip is an entered amount, not a bank payment.
        </p>
      </div>
      {message && <p className="text-sm text-[#067647]">{message}</p>}
      {error && <p className="text-sm text-[#B42318]">{error}</p>}
      <Card>
        <CardHeader><CardTitle>Leave</CardTitle><CardDescription>A draft casual-leave application. It is not approved.</CardDescription></CardHeader>
        <CardContent className="space-y-3">
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); run(() => post('/api/college/staff/leave', { employeeId: form.get('employeeId'), fromDate: form.get('fromDate'), toDate: form.get('toDate'), reason: form.get('reason') })); }}>
            <Select name="employeeId" label="Faculty" options={props.faculty} />
            <Field name="fromDate" label="From" placeholder="2026-11-03" />
            <Field name="toDate" label="To" placeholder="2026-11-03" />
            <Field name="reason" label="Reason" placeholder="Personal work" />
            <Button type="submit" variant="primary" isLoading={saving}>Save leave</Button>
          </form>
          <Rows rows={props.leaves.map((item) => ({ id: item.id, title: item.employee_no, meta: `${item.from_date} to ${item.to_date} · ${item.total_days} day`, status: item.status }))} />
        </CardContent>
      </Card>
      <Card>
        <CardHeader><CardTitle>Staff day</CardTitle><CardDescription>An office record of present. No punch or device is stored.</CardDescription></CardHeader>
        <CardContent className="space-y-3">
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); run(() => post('/api/college/staff/attendance', { employeeId: form.get('employeeId'), attendanceDate: form.get('attendanceDate') })); }}>
            <Select name="employeeId" label="Faculty" options={props.faculty} />
            <Field name="attendanceDate" label="Date" placeholder="2026-10-30" />
            <Button type="submit" variant="primary" isLoading={saving}>Record present</Button>
          </form>
          <Rows rows={props.attendance.map((item) => ({ id: item.id, title: item.employee_no, meta: item.attendance_date, status: item.status }))} />
        </CardContent>
      </Card>
      <Card>
        <CardHeader><CardTitle>Appraisal</CardTitle><CardDescription>A draft cycle. No score is entered.</CardDescription></CardHeader>
        <CardContent className="space-y-3">
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); run(() => post('/api/college/staff/appraisal', { collegeId: form.get('collegeId'), name: form.get('name'), startDate: form.get('startDate'), endDate: form.get('endDate') })); }}>
            <Select name="collegeId" label="College" options={colleges} />
            <Field name="name" label="Cycle" placeholder="AY 2026-27 appraisal" />
            <Field name="startDate" label="Start" placeholder="2026-07-01" />
            <Field name="endDate" label="End" placeholder="2027-06-30" />
            <Button type="submit" variant="primary" isLoading={saving}>Open cycle</Button>
          </form>
          <Rows rows={props.cycles.map((item) => ({ id: item.id, title: item.name, meta: `${item.start_date} to ${item.end_date}`, status: item.status }))} />
        </CardContent>
      </Card>
      <Card>
        <CardHeader><CardTitle>Salary structure</CardTitle><CardDescription>A draft monthly structure. A payslip uses the basic pay entered below and stays calculated, not paid to a bank.</CardDescription></CardHeader>
        <CardContent className="space-y-3">
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); run(() => post('/api/college/staff/pay', { collegeId: form.get('collegeId'), code: String(form.get('code')).toUpperCase(), name: form.get('name') })); }}>
            <Select name="collegeId" label="College" options={colleges} />
            <Field name="code" label="Code" placeholder="FAC-PAY-2026" />
            <Field name="name" label="Name" placeholder="Faculty monthly structure" />
            <Button type="submit" variant="primary" isLoading={saving}>Save structure</Button>
          </form>
          <Rows rows={props.structures.map((item) => ({ id: item.id, title: `${item.code} · ${item.name}`, meta: 'Draft structure', status: item.status }))} />
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); run(() => post('/api/college/staff/payslip', { employeeId: form.get('employeeId'), basicPay: Number(form.get('basicPay')) })); }}>
            <Select name="employeeId" label="Faculty" options={props.faculty} />
            <Field name="basicPay" label="Basic pay entered for this slip" placeholder="45000" />
            <Button type="submit" variant="primary" isLoading={saving}>Calculate payslip</Button>
          </form>
        </CardContent>
      </Card>
      <Card>
        <CardHeader><CardTitle>Vacancy</CardTitle><CardDescription>A draft requisition for one position and a matching posting. No applicant is recorded.</CardDescription></CardHeader>
        <CardContent className="space-y-3">
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); run(() => post('/api/college/staff/recruitment', { collegeId: form.get('collegeId'), requisitionNo: String(form.get('requisitionNo')).toUpperCase(), title: form.get('title') })); }}>
            <Select name="collegeId" label="College" options={colleges} />
            <Field name="requisitionNo" label="Number" placeholder="REQ-CSE-001" />
            <Field name="title" label="Title" placeholder="Assistant professor, CSE" />
            <Button type="submit" variant="primary" isLoading={saving}>Save vacancy</Button>
          </form>
          <Rows rows={props.postings.map((item) => ({ id: item.id, title: item.posting_no, meta: item.title, status: item.status }))} />
        </CardContent>
      </Card>
      <Card>
        <CardHeader><CardTitle>Onboarding</CardTitle><CardDescription>A pending checklist for an existing faculty member. No file is uploaded.</CardDescription></CardHeader>
        <CardContent className="space-y-3">
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); run(() => post('/api/college/staff/onboarding', { employeeId: form.get('employeeId'), joinDate: form.get('joinDate') })); }}>
            <Select name="employeeId" label="Faculty" options={props.faculty} />
            <Field name="joinDate" label="Expected join" placeholder="2026-11-16" />
            <Button type="submit" variant="primary" isLoading={saving}>Open onboarding</Button>
          </form>
          <Rows rows={props.onboardings.map((item) => ({ id: item.id, title: item.employee_no, meta: item.expected_join_date, status: item.status }))} />
        </CardContent>
      </Card>
      <Card>
        <CardHeader><CardTitle>Training</CardTitle><CardDescription>A draft faculty-development program and a nomination. No certificate is issued.</CardDescription></CardHeader>
        <CardContent className="space-y-3">
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); run(() => post('/api/college/staff/training', { employeeId: form.get('employeeId'), code: String(form.get('code')).toUpperCase(), name: form.get('name') })); }}>
            <Select name="employeeId" label="Faculty" options={props.faculty} />
            <Field name="code" label="Code" placeholder="FDP-OS-2026" />
            <Field name="name" label="Name" placeholder="Operating systems faculty workshop" />
            <Button type="submit" variant="primary" isLoading={saving}>Nominate</Button>
          </form>
          <Rows rows={props.training.map((item) => ({ id: item.id, title: item.code, meta: item.employee_no, status: item.status }))} />
        </CardContent>
      </Card>
    </div>
  );
}

function Field({ name, label, placeholder }: { name: string; label: string; placeholder: string }) {
  return <label className="text-xs font-semibold text-[#0B1F3A]">{label}<Input name={name} placeholder={placeholder} required className="mt-1" /></label>;
}
function Select({ name, label, options }: { name: string; label: string; options: { value?: string; id?: string; label: string }[] }) {
  return <label className="text-xs font-semibold text-[#0B1F3A]">{label}<select name={name} required defaultValue={options[0]?.value || options[0]?.id || ''} className="mt-1 h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 text-xs">{options.map((option) => { const value = option.value || option.id || ''; return <option key={value} value={value}>{option.label}</option>; })}</select></label>;
}
function Rows({ rows }: { rows: { id: string; title: string; meta: string; status: string }[] }) {
  if (rows.length === 0) return <p className="text-xs text-[#6B7688]">Nothing recorded yet.</p>;
  return <div className="space-y-2">{rows.map((row) => <div key={row.id} className="flex flex-wrap items-center justify-between gap-2 rounded-[8px] border border-[#E3E8EF] px-3 py-2"><div><p className="text-sm font-semibold text-[#0B1F3A]">{row.title}</p><p className="text-xs text-[#6B7688]">{row.meta}</p></div><Badge status={row.status} /></div>)}</div>;
}
