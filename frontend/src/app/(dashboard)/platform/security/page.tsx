'use client';

import { useEffect, useState } from 'react';

type Tab =
  | 'posture'
  | 'standards'
  | 'signals'
  | 'findings'
  | 'risks'
  | 'controls'
  | 'exceptions'
  | 'alerts'
  | 'enforcement'
  | 'reports';

type Row = Record<string, unknown>;

/**
 * IADM-07 Platform Security & Compliance desk (R01 / R02; auditor read-only via BFF).
 */
export default function PlatformSecurityPage() {
  const [tab, setTab] = useState<Tab>('posture');
  const [posture, setPosture] = useState<Row | null>(null);
  const [standards, setStandards] = useState<Row[]>([]);
  const [signals, setSignals] = useState<Row[]>([]);
  const [denials, setDenials] = useState<Row[]>([]);
  const [findings, setFindings] = useState<Row[]>([]);
  const [risks, setRisks] = useState<Row[]>([]);
  const [controlRows, setControlRows] = useState<Row[]>([]);
  const [exceptions, setExceptions] = useState<Row[]>([]);
  const [alerts, setAlerts] = useState<Row[]>([]);
  const [enforcement, setEnforcement] = useState<Row[]>([]);
  const [reports, setReports] = useState<Row | null>(null);
  const [stale, setStale] = useState<Row | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionMsg, setActionMsg] = useState<string | null>(null);
  const [proposeCode, setProposeCode] = useState('SEC-STD-NEW');
  const [proposeTitle, setProposeTitle] = useState('Demo proposed standard');

  const itemsOf = (body: Row | null): Row[] => {
    if (!body) return [];
    return Array.isArray(body.items) ? (body.items as Row[]) : [];
  };

  const loadCore = async () => {
    setLoading(true);
    setError(null);
    try {
      const [p, st, staleRes, f] = await Promise.all([
        fetch('/api/platform/security?view=posture'),
        fetch('/api/platform/security?view=standards'),
        fetch('/api/platform/security?view=stale-threshold'),
        fetch('/api/platform/security?view=findings'),
      ]);
      const pBody = await p.json();
      const stBody = await st.json();
      const staleBody = await staleRes.json();
      const fBody = await f.json();
      if (p.status === 403) {
        setError(pBody.detail || 'You do not have access to this view');
        return;
      }
      if (!p.ok) throw new Error(pBody.detail || 'Posture unavailable');
      setPosture(pBody);
      setStandards(itemsOf(stBody));
      setFindings(itemsOf(fBody));
      setStale(staleBody);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load security desk');
    } finally {
      setLoading(false);
    }
  };

  const loadTab = async (next: Tab) => {
    setTab(next);
    setActionMsg(null);
    const map: Record<Tab, string> = {
      posture: 'posture',
      standards: 'standards',
      signals: 'signals',
      findings: 'findings',
      risks: 'risks',
      controls: 'controls',
      exceptions: 'exceptions',
      alerts: 'alerts',
      enforcement: 'enforcement',
      reports: 'reports',
    };
    const res = await fetch(`/api/platform/security?view=${encodeURIComponent(map[next])}`);
    const body = await res.json();
    if (!res.ok) {
      setActionMsg(body.detail || 'Load failed');
      return;
    }
    if (next === 'posture') setPosture(body);
    else if (next === 'standards') setStandards(itemsOf(body));
    else if (next === 'signals') {
      setSignals(itemsOf(body));
      const dRes = await fetch('/api/platform/security?view=denials&limit=25');
      if (dRes.ok) {
        const dBody = await dRes.json();
        setDenials(itemsOf(dBody));
      } else {
        setDenials([]);
      }
    } else if (next === 'findings') setFindings(itemsOf(body));
    else if (next === 'risks') setRisks(itemsOf(body));
    else if (next === 'controls') setControlRows(itemsOf(body));
    else if (next === 'exceptions') setExceptions(itemsOf(body));
    else if (next === 'alerts') setAlerts(itemsOf(body));
    else if (next === 'enforcement') setEnforcement(itemsOf(body));
    else if (next === 'reports') setReports(body);
  };

  useEffect(() => {
    void loadCore();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const mutate = async (view: string, payload: Row, id?: string) => {
    setActionMsg(null);
    const qs = new URLSearchParams({ view });
    if (id) qs.set('id', id);
    const res = await fetch(`/api/platform/security?${qs}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    const body = await res.json();
    if (!res.ok) {
      setActionMsg(body.detail || body.errorCode || 'Action refused');
      return body;
    }
    setActionMsg(`OK: ${JSON.stringify(body).slice(0, 120)}`);
    await loadTab(tab);
    if (tab === 'standards' || view.includes('standard')) {
      const st = await fetch('/api/platform/security?view=standards');
      setStandards(itemsOf(await st.json()));
    }
    return body;
  };

  const transition = async (
    view: 'finding-transition' | 'signal-transition' | 'alert-transition' | 'standard-transition',
    id: string,
    toStatus: string
  ) => {
    await mutate(view, { toStatus }, id);
  };

  const proposeStandard = async () => {
    await mutate('propose-standard', { code: proposeCode, title: proposeTitle, bodyRef: 'ref://demo/proposed' });
  };

  const requestException = async () => {
    const find = findings.find((f) => f.code === 'SEC-FIND-001') || findings[0];
    await mutate('exception', {
      code: 'SEC-EXC-UI',
      rationale: 'UI demo exception request',
      linkedFindingId: find?.id,
      instituteId: find?.instituteId || find?.assignedInstituteId || 'd4000000-0000-4000-8000-000000000004',
      validFrom: new Date().toISOString().slice(0, 10),
      validTo: new Date(Date.now() + 7 * 864e5).toISOString().slice(0, 10),
    });
  };

  const decideException = async (id: string, decision: 'APPROVED' | 'REJECTED') => {
    await mutate('exception-decide', { decision, rationale: `Desk demo ${decision}` }, id);
  };

  const setStaleThreshold = async (minutes: number) => {
    await mutate('stale-threshold', { staleThresholdMinutes: minutes });
    const res = await fetch('/api/platform/security?view=stale-threshold');
    setStale(await res.json());
  };

  const setOutage = async (flags: Row) => {
    await mutate('outage', flags);
    await loadCore();
  };

  const renderRegister = (rows: Row[], prefix: string, codeKey = 'code') => (
    <ul className="space-y-2 text-sm">
      {rows.length === 0 ? <li data-testid={`iadm07-empty-${prefix}`}>No rows</li> : null}
      {rows.map((row) => {
        const code = String(row[codeKey] ?? row.correlationId ?? row.id);
        return (
          <li
            key={String(row.id)}
            data-testid={`iadm07-${prefix}-${code}`}
            className="flex flex-wrap items-center gap-2 border-b border-slate-100 pb-2"
          >
            <span>
              <strong>{code}</strong> — {String(row.title ?? row.summary ?? '')} · {String(row.status ?? '')}
              {row.severity ? ` · ${String(row.severity)}` : ''}
            </span>
          </li>
        );
      })}
    </ul>
  );

  const tabBody = () => {
    if (tab === 'posture') {
      return (
        <section data-testid="iadm07-posture" className="space-y-2">
          <p className="text-sm text-slate-500">Scope: {String(posture?.scopeTenantId ?? 'platform')}</p>
          <pre className="overflow-auto rounded bg-slate-50 p-3 text-xs">{JSON.stringify(posture?.indicators ?? posture, null, 2)}</pre>
        </section>
      );
    }
    if (tab === 'standards') {
      return (
        <section data-testid="iadm07-standards" className="space-y-3">
          {renderRegister(standards, 'standard')}
          <div className="flex flex-wrap gap-2" data-testid="iadm07-standard-actions">
            <input
              data-testid="iadm07-propose-code"
              className="rounded border px-2 py-1 text-sm"
              value={proposeCode}
              onChange={(e) => setProposeCode(e.target.value)}
            />
            <input
              data-testid="iadm07-propose-title"
              className="rounded border px-2 py-1 text-sm"
              value={proposeTitle}
              onChange={(e) => setProposeTitle(e.target.value)}
            />
            <button
              type="button"
              data-testid="iadm07-propose-standard"
              className="rounded bg-slate-800 px-3 py-1 text-sm text-white"
              onClick={() => void proposeStandard()}
            >
              Propose standard (R02)
            </button>
            {standards
              .filter((s) => s.code === 'SEC-STD-LOGGING' || s.status === 'IN_REVIEW')
              .map((s) => (
                <span key={String(s.id)} className="flex gap-2">
                  <button
                    type="button"
                    data-testid={`iadm07-approve-standard-${s.code}`}
                    className="rounded bg-slate-100 px-2 py-1 text-xs"
                    onClick={() => void transition('standard-transition', String(s.id), 'APPROVED')}
                  >
                    Approve {String(s.code)}
                  </button>
                  <button
                    type="button"
                    data-testid={`iadm07-publish-standard-${s.code}`}
                    className="rounded bg-slate-100 px-2 py-1 text-xs"
                    onClick={() => void transition('standard-transition', String(s.id), 'PUBLISHED')}
                  >
                    Publish {String(s.code)}
                  </button>
                </span>
              ))}
          </div>
        </section>
      );
    }
    if (tab === 'signals') {
      return (
        <section data-testid="iadm07-signals" className="space-y-4">
          {renderRegister(signals, 'signal', 'correlationId')}
          {signals
            .filter((s) => s.status === 'NEW')
            .slice(0, 3)
            .map((s) => (
              <button
                key={String(s.id)}
                type="button"
                data-testid={`iadm07-triage-signal-${s.correlationId ?? s.id}`}
                className="mr-2 rounded bg-slate-100 px-2 py-1 text-xs"
                onClick={() => void transition('signal-transition', String(s.id), 'INVESTIGATING')}
              >
                Triage {String(s.correlationId ?? s.id)}
              </button>
            ))}
          <div data-testid="iadm07-denial-events" className="border-t pt-3">
            <h3 className="mb-2 text-sm font-medium">Denial events (shared audit, read-only)</h3>
            {denials.length === 0 ? (
              <p data-testid="iadm07-denial-events-empty" className="text-xs text-slate-500">
                No denial events recorded
              </p>
            ) : (
              <ul className="space-y-1 text-xs">
                {denials.map((d) => (
                  <li key={String(d.id)} data-testid={`iadm07-denial-${d.action}`}>
                    <strong>{String(d.action)}</strong> · {String(d.status ?? 'DENIED')} ·{' '}
                    {String(d.eventTime ?? '')} · tenant {String(d.tenantId ?? '—')}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </section>
      );
    }
    if (tab === 'findings') {
      return (
        <section data-testid="iadm07-findings">
          {renderRegister(findings, 'finding')}
          {findings
            .filter((f) => f.status === 'OPEN')
            .slice(0, 3)
            .map((f) => (
              <button
                key={String(f.id)}
                type="button"
                data-testid={`iadm07-triage-finding-${f.code}`}
                className="mr-2 rounded bg-slate-100 px-2 py-1 text-xs"
                onClick={() => void transition('finding-transition', String(f.id), 'TRIAGED')}
              >
                Triage {String(f.code)}
              </button>
            ))}
        </section>
      );
    }
    if (tab === 'risks') return <section data-testid="iadm07-risks">{renderRegister(risks, 'risk')}</section>;
    if (tab === 'controls') return <section data-testid="iadm07-controls">{renderRegister(controlRows, 'control')}</section>;
    if (tab === 'exceptions') {
      return (
        <section data-testid="iadm07-exceptions" className="space-y-2">
          {renderRegister(exceptions, 'exception')}
          <button
            type="button"
            data-testid="iadm07-request-exception"
            className="rounded bg-slate-800 px-3 py-1 text-sm text-white"
            onClick={() => void requestException()}
          >
            Request exception (R02)
          </button>
          {exceptions
            .filter((e) => e.status === 'REQUESTED')
            .slice(0, 2)
            .map((e) => (
              <span key={String(e.id)} className="ml-2 inline-flex gap-2">
                <button
                  type="button"
                  data-testid={`iadm07-decide-exception-${e.code}`}
                  className="rounded bg-green-100 px-2 py-1 text-xs"
                  onClick={() => void decideException(String(e.id), 'APPROVED')}
                >
                  Approve {String(e.code)} (R01)
                </button>
              </span>
            ))}
        </section>
      );
    }
    if (tab === 'alerts') {
      return (
        <section data-testid="iadm07-alerts">
          {renderRegister(alerts, 'alert')}
          {alerts
            .filter((a) => a.status === 'OPEN')
            .slice(0, 3)
            .map((a) => (
              <button
                key={String(a.id)}
                type="button"
                data-testid={`iadm07-ack-alert-${a.code}`}
                className="mr-2 rounded bg-slate-100 px-2 py-1 text-xs"
                onClick={() => void transition('alert-transition', String(a.id), 'ACKNOWLEDGED')}
              >
                Acknowledge {String(a.code)}
              </button>
            ))}
        </section>
      );
    }
    if (tab === 'enforcement') return <section data-testid="iadm07-enforcement">{renderRegister(enforcement, 'enforcement')}</section>;
    if (tab === 'reports') {
      return (
        <section data-testid="iadm07-reports">
          <pre className="rounded bg-slate-50 p-3 text-xs">{JSON.stringify(reports, null, 2)}</pre>
        </section>
      );
    }
    return null;
  };

  const deskControls = (
    <>
      <div className="flex flex-wrap gap-2" data-testid="iadm07-tabs">
        {(
          [
            'posture',
            'standards',
            'signals',
            'findings',
            'risks',
            'controls',
            'exceptions',
            'alerts',
            'enforcement',
            'reports',
          ] as Tab[]
        ).map((t) => (
          <button
            key={t}
            type="button"
            data-testid={`iadm07-tab-${t}`}
            className={`rounded px-3 py-1 text-sm ${tab === t ? 'bg-slate-800 text-white' : 'bg-slate-100'}`}
            onClick={() => void loadTab(t)}
          >
            {t}
          </button>
        ))}
      </div>

      <div className="flex flex-wrap items-center gap-2 text-sm" data-testid="iadm07-settings">
        <span data-testid="iadm07-stale-threshold">
          Stale threshold (minutes): {String(stale?.staleThresholdMinutes ?? '—')}
        </span>
        <button
          type="button"
          data-testid="iadm07-stale-7d"
          className="rounded bg-slate-100 px-2 py-1 text-xs"
          onClick={() => void setStaleThreshold(10080)}
        >
          Set 7d
        </button>
        <button
          type="button"
          data-testid="iadm07-stale-1d"
          className="rounded bg-slate-100 px-2 py-1 text-xs"
          onClick={() => void setStaleThreshold(1440)}
        >
          Set 1d
        </button>
      </div>

      <div className="flex flex-wrap gap-2" data-testid="iadm07-fault-controls">
        <span className="text-sm text-slate-500">DEMO ONLY faults:</span>
        <button
          type="button"
          data-testid="iadm07-fault-audit"
          className="rounded bg-amber-100 px-2 py-1 text-sm"
          onClick={() => void setOutage({ auditDisabled: true })}
        >
          Audit off
        </button>
        <button
          type="button"
          data-testid="iadm07-fault-notifications"
          className="rounded bg-amber-100 px-2 py-1 text-sm"
          onClick={() => void setOutage({ notificationsDisabled: true })}
        >
          Notifications off
        </button>
        <button
          type="button"
          data-testid="iadm07-fault-clear"
          className="rounded bg-slate-100 px-2 py-1 text-sm"
          onClick={() => void setOutage({ auditDisabled: false, simulatorDisabled: false, notificationsDisabled: false })}
        >
          Clear faults
        </button>
      </div>

      {actionMsg ? <p data-testid="iadm07-action-msg">{actionMsg}</p> : null}
    </>
  );

  if (loading) {
    return (
      <main data-testid="iadm07-platform-desk" className="mx-auto max-w-5xl space-y-4 p-6">
        <h1 className="text-2xl font-semibold">Security &amp; Compliance Governance</h1>
        {deskControls}
        <p>Loading…</p>
      </main>
    );
  }

  if (error) {
    return (
      <main data-testid="iadm07-platform-desk" className="mx-auto max-w-5xl space-y-4 p-6">
        <h1 className="text-2xl font-semibold">Security &amp; Compliance Governance</h1>
        {deskControls}
        <p data-testid="iadm07-access-denied" className="text-red-700">
          {error}
        </p>
      </main>
    );
  }

  const seededVisible =
    findings.some((f) => f.code === 'SEC-FIND-001') ||
    standards.some((s) => s.code === 'SEC-STD-ACCESS') ||
    tab !== 'findings';

  return (
    <main data-testid="iadm07-platform-desk" className="mx-auto max-w-5xl space-y-6 p-6">
      <header>
        <h1 className="text-2xl font-semibold">Security &amp; Compliance Governance</h1>
        <p className="text-slate-600">Registers &amp; posture — governance only, not enforcement.</p>
        {seededVisible ? (
          <p data-testid="iadm07-seeded-hint" className="text-xs text-amber-700">
            DEMO ONLY seeded rows include SEC-FIND-001, SEC-ALERT-001, SEC-EXC-001, SEC-STD-ACCESS, SEC-SRC-STALE.
          </p>
        ) : null}
      </header>
      {deskControls}
      {tabBody()}
    </main>
  );
}