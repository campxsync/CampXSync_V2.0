'use client';

import { useEffect, useState } from 'react';

type Tab = 'overview' | 'audit' | 'alerts' | 'sla';
type Row = Record<string, unknown>;

function daysAgoIso(days: number): string {
  return new Date(Date.now() - days * 86400000).toISOString();
}

/**
 * IADM-08 Platform Operations desk (R01 / R02; R28 read-only via BFF).
 * Plain language: search shared audit, read on-request health, view alerts; no export/ack.
 */
export default function PlatformOperationsPage() {
  const [tab, setTab] = useState<Tab>('overview');
  const [health, setHealth] = useState<Row[]>([]);
  const [audit, setAudit] = useState<Row[]>([]);
  const [alerts, setAlerts] = useState<Row | null>(null);
  const [sla, setSla] = useState<Row | null>(null);
  const [detail, setDetail] = useState<Row | null>(null);
  const [limits, setLimits] = useState<Row | null>(null);
  const [from, setFrom] = useState(daysAgoIso(7));
  const [to, setTo] = useState(new Date().toISOString());
  const [action, setAction] = useState('');
  const [denialOnly, setDenialOnly] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionMsg, setActionMsg] = useState<string | null>(null);

  const itemsOf = (body: Row | null): Row[] => {
    if (!body) return [];
    return Array.isArray(body.items) ? (body.items as Row[]) : [];
  };

  const loadOverview = async () => {
    setLoading(true);
    setError(null);
    try {
      const [h, lim] = await Promise.all([
        fetch('/api/platform/operations?view=health-overview'),
        fetch('/api/platform/operations?view=limits'),
      ]);
      const hBody = await h.json();
      const limBody = await lim.json();
      if (h.status === 403) {
        setError(hBody.detail || 'You do not have access to this view');
        return;
      }
      if (!h.ok) throw new Error(hBody.detail || 'Health unavailable');
      setHealth(itemsOf(hBody));
      setLimits(limBody);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load operations desk');
    } finally {
      setLoading(false);
    }
  };

  const loadAudit = async () => {
    setLoading(true);
    setError(null);
    setDetail(null);
    try {
      const q = new URLSearchParams({
        view: 'audit-search',
        from,
        to,
      });
      if (action.trim()) q.set('action', action.trim());
      if (denialOnly) q.set('denialOnly', 'true');
      const res = await fetch(`/api/platform/operations?${q.toString()}`);
      const body = await res.json();
      if (!res.ok) throw new Error(body.detail || body.errorCode || 'Audit search failed');
      setAudit(itemsOf(body));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Audit search failed');
    } finally {
      setLoading(false);
    }
  };

  const openDetail = async (eventId: string) => {
    const res = await fetch(`/api/platform/operations?view=audit-detail&id=${encodeURIComponent(eventId)}`);
    const body = await res.json();
    if (!res.ok) {
      setActionMsg(body.detail || 'Detail unavailable');
      return;
    }
    setDetail(body);
  };

  const loadAlerts = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch('/api/platform/operations?view=alerts');
      const body = await res.json();
      if (!res.ok) throw new Error(body.detail || 'Alerts unavailable');
      setAlerts(body);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Alerts failed');
    } finally {
      setLoading(false);
    }
  };

  const loadSla = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch('/api/platform/operations?view=sla');
      const body = await res.json();
      if (!res.ok) throw new Error(body.detail || 'SLA unavailable');
      setSla(body);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'SLA failed');
    } finally {
      setLoading(false);
    }
  };

  const tryExport = async () => {
    const res = await fetch('/api/platform/operations?view=export', { method: 'POST' });
    const body = await res.json();
    setActionMsg(`Export attempt: ${res.status} ${body.detail || body.errorCode}`);
  };

  useEffect(() => {
    void loadOverview();
  }, []);

  const switchTab = (next: Tab) => {
    setTab(next);
    setActionMsg(null);
    if (next === 'overview') void loadOverview();
    if (next === 'audit') void loadAudit();
    if (next === 'alerts') void loadAlerts();
    if (next === 'sla') void loadSla();
  };

  return (
    <main className="mx-auto max-w-6xl p-6" data-testid="iadm08-platform-desk">
      <h1 className="text-2xl font-semibold tracking-tight" data-testid="iadm08-title">
        Platform Operations
      </h1>
      <p className="mt-1 text-sm text-neutral-600">
        Audit investigation and on-request service health (IADM-08). Read-only; shared audit is authoritative.
      </p>

      <nav className="mt-4 flex flex-wrap gap-2" data-testid="iadm08-tabs">
        {(['overview', 'audit', 'alerts', 'sla'] as Tab[]).map((t) => (
          <button
            key={t}
            type="button"
            data-testid={`iadm08-tab-${t}`}
            className={`rounded px-3 py-1.5 text-sm ${tab === t ? 'bg-neutral-900 text-white' : 'bg-neutral-100'}`}
            onClick={() => switchTab(t)}
          >
            {t === 'overview' ? 'Health overview' : t === 'audit' ? 'Audit investigation' : t === 'alerts' ? 'Alerts' : 'SLA'}
          </button>
        ))}
      </nav>

      {limits && (
        <p className="mt-2 text-xs text-neutral-500" data-testid="iadm08-limits">
          DEMO ONLY limits: max window {String(limits.maxWindowDays)} days · page size {String(limits.pageSize)}
        </p>
      )}

      {loading && <p className="mt-4" data-testid="iadm08-loading">Loading…</p>}
      {error && (
        <p className="mt-4 text-red-700" data-testid="iadm08-error">
          {error}
        </p>
      )}
      {actionMsg && (
        <p className="mt-2 text-sm" data-testid="iadm08-action-msg">
          {actionMsg}
        </p>
      )}

      {!loading && tab === 'overview' && (
        <section className="mt-4" data-testid="iadm08-health-overview">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b">
                <th className="py-2">Service</th>
                <th>Status</th>
                <th>HTTP</th>
                <th>Latency</th>
              </tr>
            </thead>
            <tbody>
              {health.map((row) => (
                <tr key={String(row.id)} className="border-b border-neutral-100" data-testid={`iadm08-health-${row.id}`}>
                  <td className="py-2">{String(row.name)}</td>
                  <td data-testid={`iadm08-health-status-${row.id}`}>{String(row.status)}</td>
                  <td>{String(row.httpStatus ?? '')}</td>
                  <td>{row.latencyMs != null ? `${row.latencyMs} ms` : ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      {!loading && tab === 'audit' && (
        <section className="mt-4" data-testid="iadm08-audit-panel">
          <div className="flex flex-wrap items-end gap-2">
            <label className="text-xs">
              From
              <input
                data-testid="iadm08-audit-from"
                className="ml-1 rounded border px-2 py-1 text-sm"
                value={from}
                onChange={(e) => setFrom(e.target.value)}
              />
            </label>
            <label className="text-xs">
              To
              <input
                data-testid="iadm08-audit-to"
                className="ml-1 rounded border px-2 py-1 text-sm"
                value={to}
                onChange={(e) => setTo(e.target.value)}
              />
            </label>
            <label className="text-xs">
              Action contains
              <input
                data-testid="iadm08-audit-action"
                className="ml-1 rounded border px-2 py-1 text-sm"
                value={action}
                onChange={(e) => setAction(e.target.value)}
              />
            </label>
            <label className="text-xs flex items-center gap-1">
              <input
                type="checkbox"
                data-testid="iadm08-denial-only"
                checked={denialOnly}
                onChange={(e) => setDenialOnly(e.target.checked)}
              />
              Denial events only
            </label>
            <button
              type="button"
              data-testid="iadm08-audit-search"
              className="rounded bg-neutral-900 px-3 py-1.5 text-sm text-white"
              onClick={() => void loadAudit()}
            >
              Search
            </button>
            <button
              type="button"
              data-testid="iadm08-export-attempt"
              className="rounded border px-3 py-1.5 text-sm"
              onClick={() => void tryExport()}
            >
              Export (should fail)
            </button>
          </div>
          <table className="mt-3 w-full text-left text-sm">
            <thead>
              <tr className="border-b">
                <th className="py-2">Time</th>
                <th>Action</th>
                <th>Actor</th>
                <th>Outcome</th>
                <th>Institute</th>
              </tr>
            </thead>
            <tbody>
              {audit.map((row) => (
                <tr
                  key={String(row.eventId)}
                  className="border-b border-neutral-100 cursor-pointer"
                  data-testid={`iadm08-audit-row-${row.eventId}`}
                  onClick={() => void openDetail(String(row.eventId))}
                >
                  <td className="py-2">{String(row.eventTime || '')}</td>
                  <td>{String(row.action || '')}</td>
                  <td>{String(row.actor || '')}</td>
                  <td>{String(row.outcome || '')}</td>
                  <td>{String(row.institute || '')}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {detail && (
            <pre className="mt-3 overflow-auto rounded bg-neutral-50 p-3 text-xs" data-testid="iadm08-audit-detail">
              {JSON.stringify(detail, null, 2)}
            </pre>
          )}
        </section>
      )}

      {!loading && tab === 'alerts' && alerts && (
        <section className="mt-4" data-testid="iadm08-alerts-panel">
          <p className="text-sm" data-testid="iadm08-alerts-note">
            {String(alerts.operationalNote || '')} Acknowledge supported:{' '}
            {String(alerts.acknowledgeSupported)} · IADM-07 included:{' '}
            {String(alerts.iadm07AlertsIncluded)}
          </p>
          <pre className="mt-3 overflow-auto rounded bg-neutral-50 p-3 text-xs" data-testid="iadm08-alerts-body">
            {JSON.stringify(alerts, null, 2)}
          </pre>
        </section>
      )}

      {!loading && tab === 'sla' && sla && (
        <section className="mt-4" data-testid="iadm08-sla-panel">
          <p className="text-lg" data-testid="iadm08-sla-value">
            SLA: {String(sla.sla)}
          </p>
          <p className="text-sm text-neutral-600">{String(sla.message)}</p>
        </section>
      )}
    </main>
  );
}
