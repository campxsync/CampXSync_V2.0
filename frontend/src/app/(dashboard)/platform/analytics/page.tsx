'use client';

import { useEffect, useState } from 'react';

type MetricBlock = {
  metricCode?: string;
  status?: string;
  message?: string;
  demoValuesLabel?: string;
  groups?: Array<{ key?: string; count?: number }>;
  withheldGroups?: Array<{ key?: string; reason?: string }>;
  suppressed?: boolean;
};

/**
 * IADM-06 Platform Analytics Desk (US-001, 002, 004, 005, 006, 010, 013).
 * Plain language: Super Admin / System Admin see platform aggregates — never raw rows.
 */
export default function PlatformAnalyticsPage() {
  const [tab, setTab] = useState<'overview' | 'catalogue' | 'comparison' | 'entitlements' | 'trends' | 'export'>('overview');
  const [overview, setOverview] = useState<Record<string, unknown> | null>(null);
  const [catalogue, setCatalogue] = useState<Array<Record<string, unknown>>>([]);
  const [comparison, setComparison] = useState<Record<string, unknown> | null>(null);
  const [entitlements, setEntitlements] = useState<Record<string, unknown> | null>(null);
  const [trends, setTrends] = useState<Record<string, unknown> | null>(null);
  const [exports, setExports] = useState<Array<Record<string, unknown>>>([]);
  const [suppression, setSuppression] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionMsg, setActionMsg] = useState<string | null>(null);
  const [windowGrain, setWindowGrain] = useState('month');

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [o, c, s] = await Promise.all([
        fetch(`/api/platform/analytics?view=overview&window=${encodeURIComponent(windowGrain)}`),
        fetch('/api/platform/analytics?view=catalogue'),
        fetch('/api/platform/analytics?view=suppression'),
      ]);
      const oBody = await o.json();
      const cBody = await c.json();
      const sBody = await s.json();
      if (o.status === 403) {
        setError(oBody.detail || 'You do not have access to this view');
        return;
      }
      if (!o.ok && oBody.emptyState) {
        setOverview(oBody);
      } else if (!o.ok) {
        throw new Error(oBody.detail || 'Overview unavailable');
      } else {
        setOverview(oBody);
      }
      setCatalogue(Array.isArray(cBody.items) ? cBody.items : []);
      setSuppression(sBody);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load analytics');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [windowGrain]);

  const loadTab = async (next: typeof tab) => {
    setTab(next);
    setActionMsg(null);
    if (next === 'comparison') {
      const res = await fetch(`/api/platform/analytics?view=comparison&window=${encodeURIComponent(windowGrain)}`);
      setComparison(await res.json());
    } else if (next === 'entitlements') {
      const res = await fetch('/api/platform/analytics?view=entitlements');
      setEntitlements(await res.json());
    } else if (next === 'trends') {
      const res = await fetch(`/api/platform/analytics?view=trends&window=${encodeURIComponent(windowGrain)}`);
      setTrends(await res.json());
    } else if (next === 'export') {
      const res = await fetch('/api/platform/analytics?view=exports');
      const body = await res.json();
      setExports(Array.isArray(body.items) ? body.items : []);
    }
  };

  const requestExport = async () => {
    setActionMsg(null);
    const res = await fetch('/api/platform/analytics?view=export', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ scopeType: 'PLATFORM', window: windowGrain }),
    });
    const body = await res.json();
    if (!res.ok) {
      setActionMsg(body.detail || 'Export refused');
      return;
    }
    setActionMsg(`Export ${body.status}: ${body.id}`);
    await loadTab('export');
  };

  const setThreshold = async (threshold: number) => {
    const res = await fetch('/api/platform/analytics?view=suppression', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ threshold }),
    });
    const body = await res.json();
    setSuppression(body);
    setActionMsg(`DEMO ONLY threshold set to ${body.threshold}`);
    await load();
  };

  const metricsOf = (payload: Record<string, unknown> | null): Record<string, MetricBlock> => {
    if (!payload || typeof payload.metrics !== 'object' || !payload.metrics) return {};
    return payload.metrics as Record<string, MetricBlock>;
  };

  const renderMetrics = (payload: Record<string, unknown> | null) => {
    const metrics = metricsOf(payload);
    const codes = Object.keys(metrics);
    if (payload?.emptyState) {
      return <p data-testid="iadm06-empty-state">{String(payload.emptyState)}</p>;
    }
    if (codes.length === 0) return <p>No metrics</p>;
    return (
      <div className="space-y-4" data-testid="iadm06-metrics">
        {payload?.demoValuesLabel ? (
          <p data-testid="iadm06-demo-values-label" className="text-amber-700">
            {String(payload.demoValuesLabel)}
          </p>
        ) : null}
        {payload?.policyResolution ? (
          <p data-testid="iadm06-policy-resolution" className="text-sm text-slate-600">
            Policy: {String((payload.policyResolution as Record<string, unknown>).code)} v
            {String((payload.policyResolution as Record<string, unknown>).versionNo)}
          </p>
        ) : null}
        <p className="text-sm text-slate-500" data-testid="iadm06-query-time">
          Refresh (query time): {String(payload?.queryTime || '')}
        </p>
        {codes.map((code) => {
          const m = metrics[code];
          return (
            <section key={code} data-testid={`iadm06-metric-${code}`} className="border-b border-slate-200 pb-3">
              <h3 className="font-medium">{code}</h3>
              <p className="text-sm">Status: {m.status}</p>
              {m.demoValuesLabel ? <p className="text-sm text-amber-700">{m.demoValuesLabel}</p> : null}
              {m.message ? <p className="text-sm">{m.message}</p> : null}
              <ul className="mt-1 list-disc pl-5 text-sm">
                {(m.groups || []).map((g, i) => (
                  <li key={`${code}-g-${i}`}>
                    {g.key}: {g.count}
                  </li>
                ))}
                {(m.withheldGroups || []).map((g, i) => (
                  <li key={`${code}-w-${i}`} data-testid={`iadm06-withheld-${code}-${i}`}>
                    {g.key}: WITHHELD ({g.reason})
                  </li>
                ))}
              </ul>
            </section>
          );
        })}
      </div>
    );
  };

  if (loading) {
    return (
      <main data-testid="iadm06-platform-desk" className="mx-auto max-w-5xl p-6">
        Loading analytics…
      </main>
    );
  }

  if (error) {
    return (
      <main data-testid="iadm06-platform-desk" className="mx-auto max-w-5xl space-y-4 p-6">
        <h1 className="text-2xl font-semibold">Platform Analytics</h1>
        <p data-testid="iadm06-access-denied" className="text-red-700">
          {error}
        </p>
      </main>
    );
  }

  return (
    <main data-testid="iadm06-platform-desk" className="mx-auto max-w-5xl space-y-6 p-6">
      <header>
        <h1 className="text-2xl font-semibold">Platform Analytics &amp; Usage Insights</h1>
        <p className="text-slate-600">Aggregates only — no raw or person-level rows.</p>
      </header>

      <div className="flex flex-wrap gap-2" data-testid="iadm06-tabs">
        {(['overview', 'catalogue', 'comparison', 'entitlements', 'trends', 'export'] as const).map((t) => (
          <button
            key={t}
            type="button"
            data-testid={`iadm06-tab-${t}`}
            className={`rounded px-3 py-1 text-sm ${tab === t ? 'bg-slate-800 text-white' : 'bg-slate-100'}`}
            onClick={() => void loadTab(t)}
          >
            {t}
          </button>
        ))}
      </div>

      <div className="flex items-center gap-3">
        <label className="text-sm">
          Time window{' '}
          <select
            data-testid="iadm06-window"
            value={windowGrain}
            onChange={(e) => setWindowGrain(e.target.value)}
            className="rounded border px-2 py-1"
          >
            <option value="day">day</option>
            <option value="month">month</option>
            <option value="quarter">quarter</option>
          </select>
        </label>
        <span data-testid="iadm06-suppression-threshold" className="text-sm text-slate-600">
          Suppression threshold (DEMO ONLY): {String(suppression?.threshold ?? '—')}
        </span>
        <button type="button" data-testid="iadm06-threshold-low" className="rounded bg-slate-100 px-2 py-1 text-sm" onClick={() => void setThreshold(1)}>
          Demo low
        </button>
        <button type="button" data-testid="iadm06-threshold-high" className="rounded bg-slate-100 px-2 py-1 text-sm" onClick={() => void setThreshold(50)}>
          Demo raise
        </button>
      </div>

      {actionMsg ? <p data-testid="iadm06-action-msg">{actionMsg}</p> : null}

      {tab === 'overview' ? (
        <section data-testid="iadm06-overview">{renderMetrics(overview)}</section>
      ) : null}

      {tab === 'catalogue' ? (
        <section data-testid="iadm06-catalogue">
          <h2 className="mb-2 text-lg font-medium">Metric catalogue (read-only)</h2>
          <ul className="space-y-2">
            {catalogue.map((row) => (
              <li key={String(row.metricCode)} data-testid={`iadm06-catalogue-${row.metricCode}`} className="border-b pb-2 text-sm">
                <strong>{String(row.metricCode)}</strong> — {String(row.metricName)}
                <br />
                {String(row.plainDefinition)} · Source {String(row.sourceOwner)} · v{String(row.catalogueVersion)}
                {row.demoLabel ? <span className="text-amber-700"> · {String(row.demoLabel)}</span> : null}
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {tab === 'comparison' ? (
        <section data-testid="iadm06-comparison">{renderMetrics(comparison)}</section>
      ) : null}

      {tab === 'entitlements' ? (
        <section data-testid="iadm06-entitlements">{renderMetrics(entitlements)}</section>
      ) : null}

      {tab === 'trends' ? (
        <section data-testid="iadm06-trends">{renderMetrics(trends)}</section>
      ) : null}

      {tab === 'export' ? (
        <section data-testid="iadm06-export" className="space-y-3">
          <p>Aggregate Export Review — CSV of currently shown figures; metadata only stored.</p>
          <button
            type="button"
            data-testid="iadm06-export-request"
            className="rounded bg-slate-800 px-3 py-2 text-sm text-white"
            onClick={() => void requestExport()}
          >
            Request CSV export
          </button>
          <ul className="space-y-1 text-sm">
            {exports.map((ex) => (
              <li key={String(ex.id)} data-testid={`iadm06-export-row-${ex.id}`}>
                {String(ex.id)} · {String(ex.status)} · {String(ex.scopeType)} · policy {String(ex.resolvedPolicyCode)}
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </main>
  );
}
