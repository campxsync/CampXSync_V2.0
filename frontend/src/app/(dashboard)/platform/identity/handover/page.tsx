'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type Handover = {
  id: string;
  tenantId: string;
  tenantCode?: string;
  tenantName?: string;
  firstAdminUserRef?: string | null;
  appointedByRef?: string | null;
  handedOverAt?: string | null;
  status: string;
  cadm02AcceptedAt?: string | null;
  cadm02Interim?: boolean;
};

export default function HandoverStatusPage() {
  const [handovers, setHandovers] = useState<Handover[]>([]);
  const [source, setSource] = useState('');
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      const res = await fetch('/api/platform/identity/handover');
      const data = await res.json();
      if (!res.ok) { setErr(data.detail || data.errorCode); return; }
      setHandovers(Array.isArray(data.handovers) ? data.handovers : []);
      setSource(data.source || 'plat.college_cadm_handovers');
    })();
  }, []);

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6" data-testid="iadm03-handover-desk">
      <header className="flex items-center justify-between">
        <div>
          <p className="text-xs uppercase tracking-wide text-slate-500">IADM-03 · Read-Only Handover Status</p>
          <h1 className="text-2xl font-semibold text-slate-900">First-Admin &amp; CADM-02 Handover</h1>
          <p className="text-xs text-slate-600">Source: {source} (read-only; no emails or credentials)</p>
        </div>
        <Link href="/platform/identity" className="rounded border border-slate-300 px-3 py-1.5 text-sm">Back to IADM-03</Link>
      </header>
      {err && <div className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800" data-testid="iadm03-handover-err">{err}</div>}
      <div className="overflow-x-auto rounded border border-slate-200 bg-white">
        <table className="min-w-full divide-y divide-slate-200 text-left text-xs" data-testid="iadm03-handover-table">
          <thead className="bg-slate-50 uppercase text-slate-600">
            <tr>
              <th className="px-3 py-2">Institute</th>
              <th className="px-3 py-2">First Admin Ref</th>
              <th className="px-3 py-2">Appointed By Ref</th>
              <th className="px-3 py-2">Status</th>
              <th className="px-3 py-2">CADM-02 Accepted</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {handovers.map((h) => (
              <tr key={h.id}>
                <td className="px-3 py-2">{h.tenantCode || h.tenantId}</td>
                <td className="px-3 py-2 font-mono">{h.firstAdminUserRef || '—'}</td>
                <td className="px-3 py-2 font-mono">{h.appointedByRef || '—'}</td>
                <td className="px-3 py-2 font-semibold">{h.status}</td>
                <td className="px-3 py-2">{h.cadm02AcceptedAt || (h.cadm02Interim ? 'INTERIM' : 'PENDING')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
