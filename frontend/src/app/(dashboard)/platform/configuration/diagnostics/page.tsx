'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type Row = {
  principalId?: string;
  lastConfigVersionId?: string;
  lastFetchedAt?: string;
  status?: string;
  activeConfigVersionId?: string;
};

/** IADM-02-US-025 — read-only consumer fetch diagnostics. */
export default function ConfigurationDiagnosticsPage() {
  const [items, setItems] = useState<Row[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      const res = await fetch('/api/platform/configuration/consumer/diagnostics');
      const body = await res.json();
      if (!res.ok) {
        setError(body.detail || body.errorCode || 'Failed');
        return;
      }
      setItems(Array.isArray(body.items) ? body.items : []);
    })();
  }, []);

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6" data-testid="iadm02-diagnostics-desk">
      <Link href="/platform/configuration" className="text-sm underline">
        ← Catalogue
      </Link>
      <h1 className="text-2xl font-semibold">Consumer diagnostics</h1>
      <p className="text-sm text-slate-600">Read-only. Shows last successful consumer fetches vs active version.</p>
      {error && <p className="text-red-700">{error}</p>}
      <table className="min-w-full text-left text-sm" data-testid="iadm02-diagnostics-table">
        <thead className="border-b bg-slate-50">
          <tr>
            <th className="px-3 py-2">Principal</th>
            <th className="px-3 py-2">Last version</th>
            <th className="px-3 py-2">Fetched at</th>
            <th className="px-3 py-2">Status</th>
            <th className="px-3 py-2">Active version</th>
          </tr>
        </thead>
        <tbody>
          {items.map((r, i) => (
            <tr key={i} className="border-b">
              <td className="px-3 py-2 font-mono text-xs">{r.principalId}</td>
              <td className="px-3 py-2 font-mono text-xs">{r.lastConfigVersionId}</td>
              <td className="px-3 py-2">{r.lastFetchedAt}</td>
              <td className="px-3 py-2">{r.status}</td>
              <td className="px-3 py-2 font-mono text-xs">{r.activeConfigVersionId}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {items.length === 0 && !error && <p className="text-slate-500">No consumer fetches recorded yet.</p>}
    </div>
  );
}
