'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';

type Term = { id: string; yearCode: string; termCode: string; name: string };
type Enrolment = { id: string; studentId: string; status: string; programId?: string; termId?: string };

export default function CollegeEnrollmentDeskPage() {
  const [terms, setTerms] = useState<Term[]>([]);
  const [enrolments, setEnrolments] = useState<Enrolment[]>([]);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [termId, setTermId] = useState('');
  const [programId, setProgramId] = useState('');
  const [courseId, setCourseId] = useState('');
  const searchParams = useSearchParams();
  const [form, setForm] = useState({
    yearCode: '2026',
    termCode: 'ODD',
    name: 'Odd semester',
    opensAt: '2026-11-01T00:00:00Z',
    closesAt: '2026-12-31T23:59:00Z',
    minCredits: '12',
    maxCredits: '24',
    code: 'CS101',
    credits: '3',
    core: true,
    capacity: '40',
    facultyUserId: '',
    applicationId: '',
    studentId: '',
  });

  async function load() {
    setError('');
    const [termsRes, enrolRes] = await Promise.all([
      fetch('/api/college/enrollment?view=terms', { cache: 'no-store' }),
      fetch('/api/college/enrollment?view=enrolments', { cache: 'no-store' }),
    ]);
    if (termsRes.ok) {
      const json = await termsRes.json();
      setTerms(json.terms || []);
    }
    if (enrolRes.ok) {
      const json = await enrolRes.json();
      setEnrolments(json.enrolments || []);
    }
  }

  useEffect(() => {
    const studentId = searchParams.get('studentId');
    const applicationId = searchParams.get('applicationId');
    if (studentId || applicationId) {
      setForm((current) => ({
        ...current,
        studentId: studentId || current.studentId,
        applicationId: applicationId || current.applicationId,
      }));
    }
    void load();
  }, [searchParams]);

  function update(field: string, value: string | boolean) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  async function post(action: string, extra: Record<string, unknown>) {
    setError('');
    setMessage('');
    const res = await fetch('/api/college/enrollment', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action, ...extra }),
    });
    const text = await res.text();
    if (!res.ok) {
      setError(text);
      return null;
    }
    setMessage(`${action} saved.`);
    return JSON.parse(text);
  }

  return (
    <main className="mx-auto max-w-3xl space-y-6 p-6" data-testid="college-enrollment-desk">
      <div>
        <h1 className="text-xl font-semibold text-[#0B1F3A]" data-testid="college-enrollment-heading">
          Course registration desk
        </h1>
        <p className="text-sm text-[#475569]">
          Open the term, publish courses and sections, then hand the student an enrolment they can register against.
        </p>
      </div>

      <section className="space-y-2 rounded border p-4" data-testid="college-enrollment-term-section">
        <h2 className="text-sm font-semibold">1. Term and window</h2>
        <div className="grid gap-2 sm:grid-cols-3">
          <input className="rounded border px-2 py-1" value={form.yearCode} onChange={(e) => update('yearCode', e.target.value)} placeholder="Year" />
          <input className="rounded border px-2 py-1" value={form.termCode} onChange={(e) => update('termCode', e.target.value)} placeholder="Term code" />
          <input className="rounded border px-2 py-1" value={form.name} onChange={(e) => update('name', e.target.value)} placeholder="Name" />
        </div>
        <button
          type="button"
          data-testid="college-enrollment-create-term"
          className="rounded bg-[#0B1F3A] px-3 py-2 text-sm text-white"
          onClick={async () => {
            const created = await post('term', form);
            if (created?.id) {
              setTermId(created.id);
              await load();
            }
          }}
        >
          Create term
        </button>
        <div className="grid gap-2 sm:grid-cols-2">
          <input
            className="rounded border px-2 py-1"
            data-testid="college-enrollment-term-id"
            value={termId}
            onChange={(e) => setTermId(e.target.value)}
            placeholder="Term id"
          />
          <input className="rounded border px-2 py-1" value={form.opensAt} onChange={(e) => update('opensAt', e.target.value)} />
          <input className="rounded border px-2 py-1" value={form.closesAt} onChange={(e) => update('closesAt', e.target.value)} />
          <input className="rounded border px-2 py-1" value={form.minCredits} onChange={(e) => update('minCredits', e.target.value)} placeholder="Min credits" />
          <input className="rounded border px-2 py-1" value={form.maxCredits} onChange={(e) => update('maxCredits', e.target.value)} placeholder="Max credits" />
        </div>
        <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => void post('window', { ...form, termId })}>
          Open registration window
        </button>
        {terms.length > 0 ? (
          <ul className="text-xs text-[#475569]" data-testid="college-enrollment-term-list">
            {terms.map((term) => (
              <li key={term.id}>
                <button
                  type="button"
                  className="underline"
                  data-testid={`college-enrollment-select-term-${term.id}`}
                  onClick={() => {
                    setTermId(term.id);
                    setMessage(`term selected: ${term.id}`);
                  }}
                >
                  {term.yearCode} {term.termCode} · {term.id}
                </button>
              </li>
            ))}
          </ul>
        ) : null}
      </section>

      <section className="space-y-2 rounded border p-4" data-testid="college-enrollment-catalogue-section">
        <h2 className="text-sm font-semibold">2. Catalogue</h2>
        <input
          className="w-full rounded border px-2 py-1"
          data-testid="college-enrollment-program-id"
          value={programId}
          onChange={(e) => setProgramId(e.target.value)}
          placeholder="Programme id"
        />
        <div className="grid gap-2 sm:grid-cols-3">
          <input className="rounded border px-2 py-1" value={form.code} onChange={(e) => update('code', e.target.value)} placeholder="Course code" />
          <input className="rounded border px-2 py-1" value={form.credits} onChange={(e) => update('credits', e.target.value)} placeholder="Credits" />
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={form.core} onChange={(e) => update('core', e.target.checked)} />
            Required
          </label>
        </div>
        <button
          type="button"
          className="rounded border px-3 py-2 text-sm"
          onClick={async () => {
            const created = await post('course', { ...form, programId, termId });
            if (created?.id) {
              setCourseId(created.id);
            }
          }}
        >
          Add course
        </button>
        <input className="w-full rounded border px-2 py-1" value={courseId} onChange={(e) => setCourseId(e.target.value)} placeholder="Course id" />
        <div className="grid gap-2 sm:grid-cols-2">
          <input className="rounded border px-2 py-1" value={form.capacity} onChange={(e) => update('capacity', e.target.value)} placeholder="Capacity" />
          <input className="rounded border px-2 py-1" value={form.facultyUserId} onChange={(e) => update('facultyUserId', e.target.value)} placeholder="Faculty user id" />
        </div>
        <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => void post('section', { ...form, courseId })}>
          Open section
        </button>
      </section>

      <section className="space-y-2 rounded border p-4" data-testid="college-enrollment-handover-section">
        <h2 className="text-sm font-semibold">3. Open a student enrolment</h2>
        <input
          className="w-full rounded border px-2 py-1"
          data-testid="college-enrollment-application-id"
          value={form.applicationId}
          onChange={(e) => update('applicationId', e.target.value)}
          placeholder="Application id"
        />
        <input
          className="w-full rounded border px-2 py-1"
          data-testid="college-enrollment-student-id"
          value={form.studentId}
          onChange={(e) => update('studentId', e.target.value)}
          placeholder="Student record id"
        />
        <button
          type="button"
          data-testid="college-enrollment-open-handover"
          className="rounded bg-[#15803D] px-3 py-2 text-sm text-white"
          onClick={async () => {
            const created = await post('handover', { ...form, programId, termId });
            if (created) {
              await load();
            }
          }}
        >
          Open enrolment
        </button>
        <ul className="space-y-1 text-sm" data-testid="college-enrollment-list">
          {enrolments.map((item) => (
            <li key={item.id} className="flex flex-wrap items-center gap-2" data-testid={`college-enrollment-row-${item.id}`}>
              <span data-testid={`college-enrollment-status-${item.id}`}>
                {item.status} · student {item.studentId.slice(0, 8)}…
              </span>
              <button type="button" className="text-xs underline" onClick={() => void post('status', { enrolmentId: item.id, status: 'ELIGIBLE' })}>
                Mark eligible
              </button>
            </li>
          ))}
        </ul>
      </section>

      {message ? (
        <p className="text-sm text-[#166534]" data-testid="college-enrollment-success">
          {message}
        </p>
      ) : null}
      {error ? (
        <pre className="overflow-auto rounded bg-[#FEF2F2] p-3 text-xs text-[#991B1B]" data-testid="college-enrollment-error">
          {error}
        </pre>
      ) : null}
    </main>
  );
}
