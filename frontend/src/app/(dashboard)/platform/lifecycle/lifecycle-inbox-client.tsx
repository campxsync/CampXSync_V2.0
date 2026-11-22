'use client';

import { useCallback, useEffect, useState } from 'react';

type RequestRow = {
  requestId: string;
  instituteId?: string;
  tenantId?: string;
  collegeId?: string | null;
  requestType: string;
  status: string;
  reason: string;
  requestedBy?: string;
  createdAt?: string;
};

export function LifecycleInboxClient() {
  const [rows, setRows] = useState<RequestRow[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [note, setNote] = useState('Reviewed by Platform Operator');

  const load = useCallback(async () => {
    setError(null);
    try {
      const res = await fetch('/api/platform/lifecycle?status=PENDING', { cache: 'no-store' });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        setError(body.detail || body.errorCode || 'Unable to load requests');
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

  async function decide(id: string, decision: 'APPROVE' | 'REJECT') {
    setBusy(id + decision);
    setError(null);
    try {
      const res = await fetch(`/api/platform/lifecycle/${id}/decide`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ decision, note }),
      });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        setError(body.detail || body.errorCode || 'Decision failed');
        return;
      }
      await load();
    } finally {
      setBusy(null);
    }
  }

  return (
    <main className="mx-auto max-w-5xl space-y-6 p-6" data-testid="platform-lifecycle-inbox">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold text-[#0B1F3A]">Lifecycle requests</h1>
        <p className="text-sm text-[#6B7688]">Approve or reject institute and college lifecycle requests.</p>
      </header>
      <label className="block text-sm text-[#1B2433]">
        Decision reason
        <input
          className="mt-1 w-full rounded-[8px] border border-[#E3E8EF] px-3 py-2 text-sm"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          data-testid="lifecycle-decision-note"
        />
      </label>
      {error ? (
        <p className="text-sm text-red-700" data-testid="lifecycle-inbox-error">
          {error}
        </p>
      ) : null}
      <div className="overflow-x-auto rounded-[12px] border border-[#E3E8EF] bg-white">
        <table className="min-w-full text-left text-sm" data-testid="lifecycle-inbox-table">
          <thead className="bg-[#F6F8FB] text-[#6B7688]">
            <tr>
              <th className="px-3 py-2 font-medium">Type</th>
              <th className="px-3 py-2 font-medium">Institute</th>
              <th className="px-3 py-2 font-medium">College</th>
              <th className="px-3 py-2 font-medium">Reason</th>
              <th className="px-3 py-2 font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td className="px-3 py-6 text-[#6B7688]" colSpan={5}>
                  No pending requests.
                </td>
              </tr>
            ) : (
              rows.map((r) => (
                <tr key={r.requestId} className="border-t border-[#E3E8EF]" data-testid={`lifecycle-row-${r.requestId}`}>
                  <td className="px-3 py-2">{r.requestType}</td>
                  <td className="px-3 py-2 font-mono text-xs">{r.instituteId || r.tenantId}</td>
                  <td className="px-3 py-2 font-mono text-xs">{r.collegeId || '—'}</td>
                  <td className="px-3 py-2">{r.reason}</td>
                  <td className="px-3 py-2 space-x-2">
                    <button
                      type="button"
                      className="rounded-[8px] bg-[#1F6FEB] px-3 py-1.5 text-xs text-white disabled:opacity-50"
                      disabled={!!busy}
                      onClick={() => void decide(r.requestId, 'APPROVE')}
                      data-testid={`lifecycle-approve-${r.requestId}`}
                    >
                      Approve
                    </button>
                    <button
                      type="button"
                      className="rounded-[8px] border border-[#E3E8EF] px-3 py-1.5 text-xs disabled:opacity-50"
                      disabled={!!busy}
                      onClick={() => void decide(r.requestId, 'REJECT')}
                      data-testid={`lifecycle-reject-${r.requestId}`}
                    >
                      Reject
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </main>
  );
}
