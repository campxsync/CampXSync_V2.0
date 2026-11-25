'use client';

import { useEffect, useState } from 'react';

/**
 * IADM-06 Auditor (R28) read-only analytics desk (US-008).
 * Plain language: view platform aggregates; no export control.
 */
export default function PlatformAnalyticsAuditPage() {
  const [overview, setOverview] = useState<Record<string, unknown> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [denyMsg, setDenyMsg] = useState<string | null>(null);

  useEffect(() => {
    void (async () => {
      try {
        const res = await fetch('/api/platform/analytics?view=overview');
        const body = await res.json();
        if (!res.ok) {
          setError(body.detail || 'Unavailable');
          return;
        }
        setOverview(body);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed');
      }
    })();
  }, []);

  const tryExport = async () => {
    const res = await fetch('/api/platform/analytics?view=export', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ scopeType: 'PLATFORM' }),
    });
    const body = await res.json();
    setDenyMsg(`${res.status} ${body.detail || body.errorCode || ''}`);
  };

  return (
    <main data-testid="iadm06-auditor-desk" className="mx-auto max-w-4xl space-y-6 p-6">
      <h1 className="text-2xl font-semibold">Analytics evidence (read-only)</h1>
      <p className="text-slate-600">Auditor may inspect aggregates; export and manage are denied.</p>
      {error ? <p data-testid="iadm06-access-denied">{error}</p> : null}
      {overview ? (
        <section data-testid="iadm06-overview">
          <p data-testid="iadm06-query-time">Query time: {String(overview.queryTime || '')}</p>
          <pre className="overflow-auto rounded bg-slate-50 p-3 text-xs">{JSON.stringify(overview.metrics, null, 2)}</pre>
        </section>
      ) : null}
      <button
        type="button"
        data-testid="iadm06-auditor-export-attempt"
        className="rounded bg-slate-100 px-3 py-2 text-sm"
        onClick={() => void tryExport()}
      >
        Attempt export (must fail)
      </button>
      {denyMsg ? <p data-testid="iadm06-auditor-export-denied">{denyMsg}</p> : null}
    </main>
  );
}
