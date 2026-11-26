'use client';

import { useEffect, useState } from 'react';

type Row = Record<string, unknown>;

/**
 * IADM-07 Auditor (R28) read-only evidence list.
 */
export default function PlatformSecurityAuditPage() {
  const [items, setItems] = useState<Row[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [denyMsg, setDenyMsg] = useState<string | null>(null);

  useEffect(() => {
    void (async () => {
      try {
        const res = await fetch('/api/platform/security?view=audit&limit=50');
        const body = await res.json();
        if (!res.ok) {
          setError(body.detail || 'Unavailable');
          return;
        }
        setItems(Array.isArray(body.items) ? body.items : []);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed');
      }
    })();
  }, []);

  const tryExport = async () => {
    const res = await fetch('/api/platform/security?view=propose-standard', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: 'AUDIT-ATTEMPT', title: 'blocked' }),
    });
    const body = await res.json();
    setDenyMsg(`${res.status} ${body.detail || body.errorCode || ''}`);
  };

  return (
    <main data-testid="iadm07-auditor-desk" className="mx-auto max-w-4xl space-y-6 p-6">
      <h1 className="text-2xl font-semibold">Security audit evidence (read-only)</h1>
      <p className="text-slate-600">Auditor may inspect IADM07_% audit rows; mutations are denied.</p>
      {error ? <p data-testid="iadm07-access-denied">{error}</p> : null}
      <section data-testid="iadm07-audit-list" className="space-y-1 text-sm">
        {items.map((row) => (
          <div key={String(row.id)} data-testid={`iadm07-audit-row-${row.action}`} className="border-b py-1">
            {String(row.action)} · {String(row.eventTime ?? '')} · tenant {String(row.tenantId ?? '—')}
          </div>
        ))}
        {items.length === 0 && !error ? <p data-testid="iadm07-audit-empty">No audit rows</p> : null}
      </section>
      <button
        type="button"
        data-testid="iadm07-auditor-export-attempt"
        className="rounded bg-slate-100 px-3 py-2 text-sm"
        onClick={() => void tryExport()}
      >
        Attempt mutation (must 403)
      </button>
      {denyMsg ? <p data-testid="iadm07-auditor-export-denied">{denyMsg}</p> : null}
    </main>
  );
}