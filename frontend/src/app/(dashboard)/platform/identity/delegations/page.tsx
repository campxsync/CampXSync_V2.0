'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type Delegation = {
  id: string;
  delegatorUserId: string;
  delegateUserId: string;
  allowedAction: string;
  targetService: string;
  scopeType: string;
  expiresAt: string;
  status: string;
  valid: boolean;
};

export default function DelegationsPage() {
  const [items, setItems] = useState<Delegation[]>([]);
  const [delegateUserId, setDelegateUserId] = useState('');
  const [allowedAction, setAllowedAction] = useState('iadm03.holders.view');
  const [targetService, setTargetService] = useState('IADM-03');
  const [expiresAt, setExpiresAt] = useState(() => new Date(Date.now() + 86400000 * 7).toISOString());
  const [reason, setReason] = useState('Scoped temporary delegation');
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  async function load() {
    const res = await fetch('/api/platform/identity/v2/delegations');
    const data = await res.json();
    if (!res.ok) { setErr(data.detail || data.errorCode); return; }
    setItems(Array.isArray(data.items) ? data.items : []);
  }

  useEffect(() => { load(); }, []);

  async function create(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null); setErr(null);
    const res = await fetch('/api/platform/identity/v2/delegations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ delegateUserId, allowedAction, targetService, expiresAt, reason }),
    });
    const data = await res.json();
    if (!res.ok) { setErr(`${data.errorCode}: ${data.detail}`); return; }
    setMsg(`Created delegation ${data.id} (${data.status}, source=${data.validitySource})`);
    await load();
  }

  async function revoke(id: string) {
    setMsg(null); setErr(null);
    const res = await fetch(`/api/platform/identity/v2/delegations/${id}/revoke`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason: 'Revoked in delegation register' }),
    });
    const data = await res.json();
    if (!res.ok) { setErr(`${data.errorCode}: ${data.detail}`); return; }
    setMsg(`Delegation ${id} -> ${data.status} (valid=${data.valid})`);
    await load();
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6" data-testid="iadm03-delegations-desk">
      <header className="flex items-center justify-between">
        <div>
          <p className="text-xs uppercase tracking-wide text-slate-500">IADM-03 · Recorded Delegations</p>
          <h1 className="text-2xl font-semibold text-slate-900">Delegation Register</h1>
        </div>
        <Link href="/platform/identity" className="rounded border border-slate-300 px-3 py-1.5 text-sm">Back to IADM-03</Link>
      </header>
      {msg && <div className="rounded border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-900" data-testid="iadm03-del-msg">{msg}</div>}
      {err && <div className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800" data-testid="iadm03-del-err">{err}</div>}

      <form onSubmit={create} className="grid grid-cols-1 gap-3 rounded border border-slate-200 bg-white p-4 md:grid-cols-3" data-testid="iadm03-del-form">
        <input type="text" value={delegateUserId} onChange={(e) => setDelegateUserId(e.target.value)} placeholder="Delegate User UUID" className="rounded border px-3 py-2 text-sm" data-testid="iadm03-del-delegate" required />
        <input type="text" value={allowedAction} onChange={(e) => setAllowedAction(e.target.value)} placeholder="Allowed Action" className="rounded border px-3 py-2 text-sm" data-testid="iadm03-del-action" required />
        <input type="text" value={targetService} onChange={(e) => setTargetService(e.target.value)} placeholder="Target Service" className="rounded border px-3 py-2 text-sm" data-testid="iadm03-del-service" required />
        <input type="text" value={expiresAt} onChange={(e) => setExpiresAt(e.target.value)} placeholder="Expires At (ISO-8601)" className="rounded border px-3 py-2 text-sm" data-testid="iadm03-del-expires" required />
        <input type="text" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Reason" className="rounded border px-3 py-2 text-sm" data-testid="iadm03-del-reason" required />
        <button type="submit" className="rounded bg-slate-900 px-4 py-2 text-sm text-white" data-testid="iadm03-del-submit">Create Delegation</button>
      </form>

      <div className="overflow-x-auto rounded border border-slate-200 bg-white">
        <table className="min-w-full divide-y divide-slate-200 text-left text-xs" data-testid="iadm03-del-table">
          <thead className="bg-slate-50 uppercase text-slate-600">
            <tr>
              <th className="px-3 py-2">Delegate</th>
              <th className="px-3 py-2">Action / Service</th>
              <th className="px-3 py-2">Expires</th>
              <th className="px-3 py-2">Status</th>
              <th className="px-3 py-2">Action</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {items.map((d) => (
              <tr key={d.id}>
                <td className="px-3 py-2 font-mono">{d.delegateUserId}</td>
                <td className="px-3 py-2">{d.allowedAction} ({d.targetService})</td>
                <td className="px-3 py-2">{d.expiresAt}</td>
                <td className="px-3 py-2 font-semibold">{d.status} (valid={String(d.valid)})</td>
                <td className="px-3 py-2">
                  <button type="button" onClick={() => revoke(d.id)} className="rounded bg-red-700 px-2 py-1 text-white" data-testid={`iadm03-del-revoke-${d.id}`}>Revoke</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
