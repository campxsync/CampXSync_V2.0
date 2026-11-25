'use client';

import { useEffect, useState } from 'react';

/**
 * IADM-06 Institution Analytics Desk (US-003, US-007).
 * Plain language: Institution Admin sees own-institute aggregates only — no cross-institute comparison.
 */
export default function InstitutionAnalyticsPage() {
  const [overview, setOverview] = useState<Record<string, unknown> | null>(null);
  const [exports, setExports] = useState<Array<Record<string, unknown>>>([]);
  const [error, setError] = useState<string | null>(null);
  const [actionMsg, setActionMsg] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [o, e] = await Promise.all([
        fetch('/api/institution/analytics?view=overview'),
        fetch('/api/institution/analytics?view=exports'),
      ]);
      const oBody = await o.json();
      const eBody = await e.json();
      if (o.status === 403) {
        setError(oBody.detail || 'You do not have access to this view');
        return;
      }
      if (!o.ok) throw new Error(oBody.detail || 'Unavailable');
      setOverview(oBody);
      setExports(Array.isArray(eBody.items) ? eBody.items : []);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const exportOwn = async () => {
    setActionMsg(null);
    const res = await fetch('/api/institution/analytics', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ scopeType: 'INSTITUTE' }),
    });
    const body = await res.json();
    if (!res.ok) {
      setActionMsg(body.detail || 'Export refused');
      return;
    }
    setActionMsg(`Export ${body.status}: ${body.id}`);
    await load();
  };

  if (loading) {
    return (
      <main data-testid="iadm06-institution-desk" className="mx-auto max-w-4xl p-6">
        Loading…
      </main>
    );
  }

  if (error) {
    return (
      <main data-testid="iadm06-institution-desk" className="mx-auto max-w-4xl p-6">
        <p data-testid="iadm06-access-denied">{error}</p>
      </main>
    );
  }

  const metrics = (overview?.metrics || {}) as Record<string, { status?: string; groups?: Array<{ key?: string; count?: number }> }>;

  return (
    <main data-testid="iadm06-institution-desk" className="mx-auto max-w-4xl space-y-6 p-6">
      <h1 className="text-2xl font-semibold">My Institute Analytics</h1>
      <p className="text-slate-600">Own-institute aggregates only. No cross-institute comparison.</p>
      <p data-testid="iadm06-scope-tenant" className="text-sm">
        Scope: {String(overview?.scopeTenantId || '')}
      </p>
      {overview?.demoValuesLabel ? (
        <p data-testid="iadm06-demo-values-label" className="text-amber-700">
          {String(overview.demoValuesLabel)}
        </p>
      ) : null}
      <section data-testid="iadm06-institute-metrics" className="space-y-3">
        {Object.keys(metrics).map((code) => (
          <div key={code} data-testid={`iadm06-metric-${code}`}>
            <h3 className="font-medium">{code}</h3>
            <p className="text-sm">Status: {metrics[code].status}</p>
            <ul className="list-disc pl-5 text-sm">
              {(metrics[code].groups || []).map((g, i) => (
                <li key={i}>
                  {g.key}: {g.count}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </section>
      <section data-testid="iadm06-institute-export" className="space-y-2">
        <button
          type="button"
          data-testid="iadm06-export-request"
          className="rounded bg-slate-800 px-3 py-2 text-sm text-white"
          onClick={() => void exportOwn()}
        >
          Export aggregate summary
        </button>
        {actionMsg ? <p data-testid="iadm06-action-msg">{actionMsg}</p> : null}
        <ul className="text-sm">
          {exports.map((ex) => (
            <li key={String(ex.id)}>
              {String(ex.id)} · {String(ex.status)}
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
