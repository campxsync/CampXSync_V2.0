'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

/**
 * IADM-03 US-031 — governance / ceiling read desk (not on every request path).
 */
export default function GovernanceReadPage() {
  const [data, setData] = useState<Record<string, unknown> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const res = await fetch('/api/platform/identity/governance');
        const body = await res.json();
        if (!res.ok) {
          throw new Error(body.detail || body.errorCode || 'Governance snapshot unavailable');
        }
        if (!cancelled) setData(body);
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Failed to load');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6" data-testid="iadm03-governance-desk">
      <header className="space-y-2">
        <Link href="/platform/identity" className="text-sm text-slate-500 hover:underline">
          ← Identity &amp; access
        </Link>
        <h1 className="text-3xl font-semibold text-slate-900">Governance read</h1>
        <p className="max-w-2xl text-slate-600">
          Ceiling and claims contract for gateway/consumers. Runtime authorization does not call IADM-03 on every
          request.
        </p>
      </header>

      {loading ? <p className="text-slate-500">Loading snapshot…</p> : null}
      {error ? (
        <p className="rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800" data-testid="iadm03-governance-error">
          {error}
        </p>
      ) : null}
      {data ? (
        <pre
          className="overflow-auto rounded border border-slate-200 bg-slate-50 p-4 text-xs text-slate-800"
          data-testid="iadm03-governance-snapshot"
        >
          {JSON.stringify(data, null, 2)}
        </pre>
      ) : null}
    </div>
  );
}
