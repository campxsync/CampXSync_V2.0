'use client';

import { useCallback, useEffect, useState } from 'react';

type RequestRow = {
  requestId: string;
  requestType: string;
  status: string;
  reason: string;
  collegeId?: string | null;
  createdAt?: string;
  decisionNote?: string | null;
};

export function InstitutionLifecycleClient() {
  const [rows, setRows] = useState<RequestRow[]>([]);
  const [requestType, setRequestType] = useState('SUSPEND');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const res = await fetch('/api/institution/lifecycle-requests', { cache: 'no-store' });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        setError(body.detail || 'Unable to load requests');
        return;
      }
      setRows(Array.isArray(body.requests) ? body.requests : []);
    } catch {
      setError('Unable to load requests');
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setInfo(null);
    try {
      const res = await fetch('/api/institution/lifecycle-requests', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ requestType, reason }),
      });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        setError(body.detail || body.errorCode || 'Request failed');
        return;
      }
      setInfo('Request submitted. Platform Operator will decide.');
      setReason('');
      await load();
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="mx-auto max-w-3xl space-y-6 p-6" data-testid="institution-lifecycle">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold text-[#0B1F3A]">Lifecycle requests</h1>
        <p className="text-sm text-[#6B7688]">
          Request suspend, archive, decommission, or a full export. You cannot execute these actions yourself.
        </p>
      </header>
      <form className="space-y-3 rounded-[12px] border border-[#E3E8EF] bg-white p-4" onSubmit={submit} data-testid="lifecycle-request-form">
        <label className="block text-sm">
          Action
          <select
            className="mt-1 w-full rounded-[8px] border border-[#E3E8EF] px-3 py-2"
            value={requestType}
            onChange={(e) => setRequestType(e.target.value)}
            data-testid="lifecycle-request-type"
          >
            <option value="SUSPEND">Suspend institute</option>
            <option value="ARCHIVE">Archive institute</option>
            <option value="DECOMMISSION">Decommission</option>
            <option value="FULL_EXPORT">Full export</option>
          </select>
        </label>
        <label className="block text-sm">
          Reason
          <textarea
            className="mt-1 w-full rounded-[8px] border border-[#E3E8EF] px-3 py-2"
            rows={3}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            required
            data-testid="lifecycle-request-reason"
          />
        </label>
        <button
          type="submit"
          disabled={busy || reason.trim().length < 3}
          className="rounded-[8px] bg-[#1F6FEB] px-4 py-2 text-sm text-white disabled:opacity-50"
          data-testid="lifecycle-request-submit"
        >
          Submit request
        </button>
      </form>
      {error ? <p className="text-sm text-red-700">{error}</p> : null}
      {info ? <p className="text-sm text-[#1F6FEB]">{info}</p> : null}
      <section className="space-y-2">
        <h2 className="text-sm font-semibold text-[#0B1F3A]">Your requests</h2>
        <ul className="space-y-2" data-testid="lifecycle-request-list">
          {rows.map((r) => (
            <li key={r.requestId} className="rounded-[8px] border border-[#E3E8EF] bg-white px-3 py-2 text-sm">
              <div className="font-medium">
                {r.requestType} · {r.status}
              </div>
              <div className="text-[#6B7688]">{r.reason}</div>
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}