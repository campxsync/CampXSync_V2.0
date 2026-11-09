'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';

const sections = ['Fees', 'Collection', 'Expenses', 'Tax', 'Scholarships', 'Payroll', 'Budget'] as const;
type Named = { id: string; code: string; name: string };

async function post(url: string, body: unknown) {
  const res = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.detail || 'The record could not be saved.');
  return data;
}

export function FinanceManager(props: {
  colleges: Named[];
  years: { id: string; code: string }[];
  students: { id: string; label: string }[];
  structures: { id: string; code: string; name: string; status: string; total_amount: number }[];
  receipts: { receipt_no: string; total_amount: number; status: string; enrollment_no: string | null }[];
  expenses: { id: string; expense_no: string; description: string | null; total_amount: number; tax_amount: number; status: string }[];
  taxes: { code: string; name: string; rate_pct: number | null }[];
  scholarships: { id: string; code: string; name: string; total_budget: number; status: string }[];
  employeeCount: number;
  budgets: { id: string; code: string; name: string; total_amount: number; status: string }[];
  audit: { action: string; event_time: string }[];
  collected: number;
  spent: number;
}) {
  const router = useRouter();
  const [section, setSection] = useState<(typeof sections)[number]>('Fees');
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

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-[#0B1F3A]">Finance</h1>
        <p className="mt-1 max-w-3xl text-sm text-[#5C6B80]">
          Fee structures, office collections, receipts, expenses, tax codes, scholarships, and budgets.
          Payroll stays on the faculty roster. No payment gateway is called. The finance trail is also in the <Link className="text-[#1F6FEB]" href="/college/audit">audit log</Link>.
        </p>
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <Summary label="Collected" value={`INR ${props.collected.toFixed(2)}`} />
        <Summary label="Expenses recorded" value={`INR ${props.spent.toFixed(2)}`} />
        <Summary label="Faculty on roster" value={String(props.employeeCount)} />
      </div>
      <div className="flex flex-wrap gap-2">
        {sections.map((item) => (
          <button key={item} type="button" onClick={() => setSection(item)} className={`rounded-full px-3 py-1 text-xs font-semibold ${section === item ? 'bg-[#0B1F3A] text-white' : 'bg-white text-[#0B1F3A] ring-1 ring-[#E3E8EF]'}`}>{item}</button>
        ))}
      </div>
      {message && <p className="text-sm text-[#067647]">{message}</p>}
      {error && <p className="text-sm text-[#B42318]">{error}</p>}

      {section === 'Fees' && (
        <Block title="Fee structure" text="One tuition component in INR. Activating the draft makes it the live structure.">
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); run(() => post('/api/college/finance/structures', { collegeId: form.get('collegeId'), academicYearId: form.get('academicYearId'), code: String(form.get('code')).toUpperCase(), name: String(form.get('name')), componentName: String(form.get('componentName')), amount: Number(form.get('amount')), taxable: false })); }}>
            <Select name="collegeId" label="College" options={props.colleges.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }))} />
            <Select name="academicYearId" label="Academic year" options={props.years.map((item) => ({ value: item.id, label: item.code }))} />
            <Field name="code" label="Code" placeholder="CSE-FEE-2026" />
            <Field name="name" label="Name" placeholder="B.Tech CSE tuition 2026-27" />
            <Field name="componentName" label="Component" placeholder="Tuition" />
            <Field name="amount" label="Amount" type="number" defaultValue="50000" />
            <Button type="submit" variant="primary" isLoading={saving}>Open draft</Button>
          </form>
          <List rows={props.structures.map((item) => ({ id: item.id, title: `${item.code} · ${item.name}`, meta: `INR ${item.total_amount}`, status: item.status, action: item.status === 'DRAFT' ? () => run(() => post('/api/college/finance/structures/activate', { structureId: item.id })) : undefined, actionLabel: 'Activate' }))} />
        </Block>
      )}
      {section === 'Collection' && (
        <Block title="Office collection" text="Records a successful office payment and issues a receipt. A gateway callback is not accepted here.">
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); run(() => post('/api/college/finance/collections', { studentId: form.get('studentId'), structureId: form.get('structureId'), amount: Number(form.get('amount')), referenceNo: String(form.get('referenceNo')) })); }}>
            <Select name="studentId" label="Student" options={props.students.map((item) => ({ value: item.id, label: item.label }))} />
            <Select name="structureId" label="Active fee structure" options={props.structures.filter((item) => item.status === 'ACTIVE').map((item) => ({ value: item.id, label: `${item.code} · INR ${item.total_amount}` }))} />
            <Field name="amount" label="Amount" type="number" />
            <Field name="referenceNo" label="Office reference" placeholder="CASH-2026-001" />
            <Button type="submit" variant="primary" isLoading={saving}>Record and issue receipt</Button>
          </form>
          <Button type="button" variant="outline" isLoading={saving} onClick={() => run(() => post('/api/college/finance/reconcile', {}))}>Match receipt to office payment</Button>
          <List rows={props.receipts.map((item) => ({ id: item.receipt_no, title: item.receipt_no, meta: `${item.enrollment_no || ''} · INR ${item.total_amount}`, status: item.status }))} />
        </Block>
      )}
      {section === 'Expenses' && (
        <Block title="Expense" text="A draft purchase. Tax is an amount on the expense, not a filed return.">
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); run(() => post('/api/college/finance/expenses', { collegeId: form.get('collegeId'), expenseNo: String(form.get('expenseNo')).toUpperCase(), expenseDate: form.get('expenseDate'), description: form.get('description'), totalAmount: Number(form.get('totalAmount')), taxAmount: Number(form.get('taxAmount')) })); }}>
            <Select name="collegeId" label="College" options={props.colleges.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }))} />
            <Field name="expenseNo" label="Number" placeholder="EXP-LAB-001" />
            <Field name="expenseDate" label="Date" type="date" />
            <Field name="description" label="Description" placeholder="Lab consumables" />
            <Field name="totalAmount" label="Total" type="number" defaultValue="1180" />
            <Field name="taxAmount" label="Tax included" type="number" defaultValue="180" />
            <Button type="submit" variant="primary" isLoading={saving}>Record draft</Button>
          </form>
          <List rows={props.expenses.map((item) => ({ id: item.id, title: item.expense_no, meta: `${item.description || ''} · INR ${item.total_amount} · tax ${item.tax_amount}`, status: item.status }))} />
        </Block>
      )}
      {section === 'Tax' && (
        <Block title="Tax code" text="A GST, TDS, or other code with one total rate. No tax return is filed from this screen.">
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); run(() => post('/api/college/finance/tax', { code: String(form.get('code')).toUpperCase(), name: form.get('name'), category: form.get('category'), ratePct: Number(form.get('ratePct')) })); }}>
            <Field name="code" label="Code" placeholder="GST18" />
            <Field name="name" label="Name" placeholder="GST 18 percent" />
            <Select name="category" label="Category" options={['GST', 'TDS', 'TCS', 'OTHER'].map((value) => ({ value, label: value }))} />
            <Field name="ratePct" label="Rate percent" type="number" defaultValue="18" />
            <Button type="submit" variant="primary" isLoading={saving}>Record code</Button>
          </form>
          <List rows={props.taxes.map((item) => ({ id: item.code, title: item.code, meta: `${item.name} · ${item.rate_pct ?? '—'}%`, status: 'ACTIVE' }))} />
        </Block>
      )}
      {section === 'Scholarships' && (
        <Block title="Scholarship" text="An institutional scheme and its budget. An award is not paid from this screen.">
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); run(() => post('/api/college/finance/scholarships', { collegeId: form.get('collegeId'), code: String(form.get('code')).toUpperCase(), name: form.get('name'), totalBudget: Number(form.get('totalBudget')) })); }}>
            <Select name="collegeId" label="College" options={props.colleges.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }))} />
            <Field name="code" label="Code" placeholder="MERIT-2026" />
            <Field name="name" label="Name" placeholder="Merit scholarship 2026-27" />
            <Field name="totalBudget" label="Budget" type="number" defaultValue="100000" />
            <Button type="submit" variant="primary" isLoading={saving}>Open draft</Button>
          </form>
          <List rows={props.scholarships.map((item) => ({ id: item.id, title: `${item.code} · ${item.name}`, meta: `INR ${item.total_budget}`, status: item.status }))} />
        </Block>
      )}
      {section === 'Payroll' && (
        <Block title="Payroll" text="Salary is not posted in finance. The faculty roster is the employee source.">
          <p className="text-sm text-[#0B1F3A]">{props.employeeCount} faculty records are on the roster. Open the faculty roster to maintain them.</p>
        </Block>
      )}
      {section === 'Budget' && (
        <Block title="Operating budget" text="A draft budget for the academic year, with one planned line.">
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); run(() => post('/api/college/finance/budgets', { collegeId: form.get('collegeId'), academicYearId: form.get('academicYearId'), code: String(form.get('code')).toUpperCase(), name: form.get('name'), amount: Number(form.get('amount')) })); }}>
            <Select name="collegeId" label="College" options={props.colleges.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }))} />
            <Select name="academicYearId" label="Academic year" options={props.years.map((item) => ({ value: item.id, label: item.code }))} />
            <Field name="code" label="Code" placeholder="OP-2026" />
            <Field name="name" label="Name" placeholder="Operating budget 2026-27" />
            <Field name="amount" label="Amount" type="number" defaultValue="250000" />
            <Button type="submit" variant="primary" isLoading={saving}>Open draft</Button>
          </form>
          <List rows={props.budgets.map((item) => ({ id: item.id, title: `${item.code} · ${item.name}`, meta: `INR ${item.total_amount}`, status: item.status }))} />
        </Block>
      )}
      <Block title="Finance audit" text="The latest finance actions recorded for this institute.">
        {props.audit.length === 0 ? <p className="text-xs text-[#6B7688]">No finance actions yet.</p> : props.audit.map((item) => (
          <p key={`${item.action}-${item.event_time}`} className="text-xs text-[#0B1F3A]">{item.action}</p>
        ))}
      </Block>
    </div>
  );
}

function Summary({ label, value }: { label: string; value: string }) {
  return <div className="rounded-[8px] border border-[#E3E8EF] bg-white px-4 py-3"><p className="text-xs text-[#6B7688]">{label}</p><p className="text-lg font-semibold text-[#0B1F3A]">{value}</p></div>;
}
function Block({ title, text, children }: { title: string; text: string; children: React.ReactNode }) {
  return <Card><CardHeader><CardTitle>{title}</CardTitle><CardDescription>{text}</CardDescription></CardHeader><CardContent className="space-y-3">{children}</CardContent></Card>;
}
function Field({ name, label, type = 'text', placeholder, defaultValue }: { name: string; label: string; type?: string; placeholder?: string; defaultValue?: string }) {
  return <label className="text-xs font-semibold text-[#0B1F3A]">{label}<Input name={name} type={type} placeholder={placeholder} defaultValue={defaultValue} required className="mt-1" /></label>;
}
function Select({ name, label, options }: { name: string; label: string; options: { value: string; label: string }[] }) {
  return <label className="text-xs font-semibold text-[#0B1F3A]">{label}<select name={name} required defaultValue={options[0]?.value || ''} className="mt-1 h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 text-xs">{options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>;
}
function List({ rows }: { rows: { id: string; title: string; meta: string; status: string; action?: () => void; actionLabel?: string }[] }) {
  if (rows.length === 0) return <p className="text-xs text-[#6B7688]">Nothing recorded yet.</p>;
  return <div className="space-y-2">{rows.map((row) => <div key={row.id} className="flex flex-wrap items-center justify-between gap-2 rounded-[8px] border border-[#E3E8EF] px-3 py-2"><div><p className="text-sm font-semibold text-[#0B1F3A]">{row.title}</p><p className="text-xs text-[#6B7688]">{row.meta}</p></div><div className="flex items-center gap-2"><Badge status={row.status} />{row.action && <Button type="button" variant="outline" onClick={row.action}>{row.actionLabel}</Button>}</div></div>)}</div>;
}
