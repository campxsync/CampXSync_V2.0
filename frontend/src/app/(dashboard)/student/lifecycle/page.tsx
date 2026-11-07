'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type StatusView = { status?: string; effectiveFrom?: string; studentId?: string };
type HistoryRow = { status: string; action: string; at: string };

export default function StudentLifecyclePage() {
  const [studentId, setStudentId] = useState('');
  const [status, setStatus] = useState<StatusView | null>(null);
  const [history, setHistory] = useState<HistoryRow[]>([]);
  const [requestType, setRequestType] = useState('LEAVE');
  const [reason, setReason] = useState('');
  const [effectiveDate, setEffectiveDate] = useState('2026-12-01');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  async function resolveStudent() {
    const me = await fetch('/api/student/me', { cache: 'no-store' });
    if (!me.ok) {
      return '';
    }
    const json = await me.json();
    return String(json?.id || json?.studentId || json?.student?.id || '');
  }

  async function load(id: string) {
    if (!id) {
      setError('Your student record could not be resolved.');
      return;
    }
    setError('');
    const statusRes = await fetch(`/api/student/lifecycle?studentId=${encodeURIComponent(id)}`, { cache: 'no-store' });
    const statusText = await statusRes.text();
    if (!statusRes.ok) {
      setError(statusText);
      return;
    }
    setStatus(JSON.parse(statusText));
    const historyRes = await fetch(`/api/student/lifecycle?studentId=${encodeURIComponent(id)}&view=history`, {
      cache: 'no-store',
    });
    if (historyRes.ok) {
      const json = await historyRes.json();
      setHistory(json.history || []);
    }
  }

  useEffect(() => {
    void (async () => {
      const id = await resolveStudent();
      setStudentId(id);
      if (id) {
        await load(id);
      }
    })();
  }, []);

  async function submitRequest() {
    setError('');
    setMessage('');
    const res = await fetch('/api/student/lifecycle', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        studentId,
        requestType,
        reason,
        effectiveDate: requestType === 'WITHDRAWAL' ? effectiveDate : undefined,
      }),
    });
    const text = await res.text();
    if (!res.ok) {
      setError(text);
      return;
    }
    setMessage('Request submitted. The registrar will approve or reject it.');
    setReason('');
    await load(studentId);
  }

  return (
    <main className="mx-auto max-w-3xl space-y-6 p-6">
      <div>
        <h1 className="text-xl font-semibold text-[#0B1F3A]">Student lifecycle</h1>
        <p className="text-sm text-[#475569]">
          Your current status is shown here. Submit leave, reinstatement, transfer, or withdrawal for registrar approval.
        </p>
        <Link href="/student/enrollment" className="text-sm text-[#1F6FEB]">
          Back to course registration
        </Link>
      </div>
      <section className="rounded border p-4 text-sm">
        <p className="font-medium text-[#0B1F3A]">Current status</p>
        <p className="mt-1 text-[#475569]">{status?.status || 'Loading…'}</p>
      </section>
      {history.length > 0 ? (
        <ul className="space-y-1 text-sm text-[#475569]">
          {history.map((row, index) => (
            <li key={`${row.at}-${index}`}>
              {row.status} · {row.action} · {row.at}
            </li>
          ))}
        </ul>
      ) : null}
      <section className="space-y-2 rounded border p-4">
        <h2 className="text-sm font-semibold">Request a change</h2>
        <select className="rounded border px-3 py-2" value={requestType} onChange={(e) => setRequestType(e.target.value)}>
          <option value="LEAVE">Leave</option>
          <option value="REINSTATE">Reinstate</option>
          <option value="TRANSFER">Transfer</option>
          <option value="WITHDRAWAL">Withdrawal</option>
        </select>
        {requestType === 'WITHDRAWAL' ? (
          <label className="block text-sm text-[#475569]">
            Effective date
            <input
              className="mt-1 block rounded border px-3 py-2"
              type="date"
              value={effectiveDate}
              onChange={(e) => setEffectiveDate(e.target.value)}
            />
          </label>
        ) : null}
        <textarea
          className="block w-full rounded border px-3 py-2"
          placeholder="Why are you requesting this?"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
        <button type="button" className="rounded bg-[#0B1F3A] px-4 py-2 text-sm text-white" onClick={() => void submitRequest()}>
          Submit request
        </button>
      </section>
      {message ? <p className="text-sm text-[#166534]">{message}</p> : null}
      {error ? <pre className="overflow-auto rounded bg-[#FEF2F2] p-3 text-xs text-[#991B1B]">{error}</pre> : null}
    </main>
  );
}
