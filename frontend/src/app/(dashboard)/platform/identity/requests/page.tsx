'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type RequestRow = {
  requestId?: string;
  requestedRoleCode?: string;
  tenantId?: string;
  status?: string;
  justification?: string;
  requesterUserId?: string;
};

/**
 * IADM-03 S3 — platform access-request inbox (US-025).
 */
export default function PlatformAccessRequestsPage() {
  const [items, setItems] = useState<RequestRow[]>([]);
  const [reason, setReason] = useState('');
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function load() {
    const res = await fetch('/api/platform/identity/requests?status=ALL');
    const body = await res.json();
    if (res.ok) setItems(Array.isArray(body.items) ? body.items : []);
  }

  useEffect(() => {
    void load();
  }, []);

  async function decide(id: string, decision: 'APPROVE' | 'REJECT') {
    setBusy(true);
    setMsg(null);
    try {
      const res = await fetch(`/api/platform/identity/requests/${encodeURIComponent(id)}/decide`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ decision, reason }),
      });
      const body = await res.json();
      if (!res.ok) throw new Error(body.detail || body.errorCode || 'Decide failed');
      setMsg(`Decision ${body.decision}`);
      await load();
    } catch (e) {
      setMsg(e instanceof Error ? e.message : 'Decide failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6" data-testid="iadm03-platform-requests-desk">
      <Link href="/platform/identity" className="text-sm underline">
        ← Identity desk
      </Link>
      <h1 className="text-3xl font-semibold">Access requests</h1>
      <label className="flex flex-col gap-1 text-sm">
        <span>Decision reason</span>
        <input
          data-testid="iadm03-plat-request-reason"
          className="rounded border px-3 py-2"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </label>
      {msg && <p data-testid="iadm03-plat-request-result">{msg}</p>}
      <ul className="space-y-2" data-testid="iadm03-plat-requests-list">
        {items.map((r) => (
          <li key={r.requestId} className="rounded border bg-white p-3 text-sm">
            <p className="font-medium">
              {r.requestedRoleCode} · {r.status}
            </p>
            <p className="text-slate-600">{r.justification}</p>
            {(r.status === 'SUBMITTED' || r.status === 'PENDING') && (
              <div className="mt-2 flex gap-3">
                <button
                  type="button"
                  data-testid={`iadm03-plat-approve-${r.requestId}`}
                  className="underline disabled:opacity-50"
                  disabled={busy || !reason.trim()}
                  onClick={() => void decide(String(r.requestId), 'APPROVE')}
                >
                  Approve
                </button>
                <button
                  type="button"
                  data-testid={`iadm03-plat-reject-${r.requestId}`}
                  className="underline disabled:opacity-50"
                  disabled={busy || !reason.trim()}
                  onClick={() => void decide(String(r.requestId), 'REJECT')}
                >
                  Reject
                </button>
              </div>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
