'use client';

import { useEffect, useState } from 'react';

type Row = Record<string, unknown>;

const FINDING_FLOW: Record<string, string> = {
  ASSIGNED: 'REMEDIATION_IN_PROGRESS',
  REMEDIATION_IN_PROGRESS: 'PENDING_VERIFICATION',
};

/**
 * IADM-07 Institution Security desk (R03).
 */
export default function InstitutionSecurityPage() {
  const [posture, setPosture] = useState<Row | null>(null);
  const [findings, setFindings] = useState<Row[]>([]);
  const [alerts, setAlerts] = useState<Row[]>([]);
  const [exceptions, setExceptions] = useState<Row[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [actionMsg, setActionMsg] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [evidenceRef, setEvidenceRef] = useState('ref://demo/institution-evidence');

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [p, f, a, e] = await Promise.all([
        fetch('/api/institution/security?view=posture'),
        fetch('/api/institution/security?view=findings'),
        fetch('/api/institution/security?view=alerts'),
        fetch('/api/institution/security?view=exceptions'),
      ]);
      const pBody = await p.json();
      const fBody = await f.json();
      if (p.status === 403) {
        setError(pBody.detail || 'You do not have access to this view');
        return;
      }
      if (!p.ok) throw new Error(pBody.detail || 'Unavailable');
      setPosture(pBody);
      setFindings(Array.isArray(fBody.items) ? fBody.items : []);
      const aBody = await a.json();
      setAlerts(Array.isArray(aBody.items) ? aBody.items : []);
      const eBody = await e.json();
      setExceptions(Array.isArray(eBody.items) ? eBody.items : []);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const post = async (view: string, payload: Row, id?: string) => {
    setActionMsg(null);
    const qs = new URLSearchParams({ view });
    if (id) qs.set('id', id);
    const res = await fetch(`/api/institution/security?${qs}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    const body = await res.json();
    if (!res.ok) {
      setActionMsg(body.detail || 'Refused');
      return;
    }
    setActionMsg(`Updated: ${String(body.status ?? body.code ?? 'ok')}`);
    await load();
  };

  const advanceFinding = async (row: Row) => {
    const from = String(row.status);
    const to = FINDING_FLOW[from];
    if (!to) {
      setActionMsg(`No institution transition from ${from}`);
      return;
    }
    await post('finding-transition', { toStatus: to }, String(row.id));
  };

  const attachEvidence = async (row: Row) => {
    await post('evidence', {
      linkedType: 'FINDING',
      linkedId: row.id,
      referenceAddress: evidenceRef,
      source: 'MANUAL',
    });
  };

  const ackAlert = async (row: Row) => {
    await post('alert-transition', { toStatus: 'ACKNOWLEDGED' }, String(row.id));
  };

  const requestException = async () => {
    const f = findings.find((x) => x.code === 'SEC-FIND-001') || findings[0];
    await post('exception', {
      code: 'SEC-EXC-R03',
      rationale: 'Institute requested compensating control',
      linkedFindingId: f?.id,
      validFrom: new Date().toISOString().slice(0, 10),
      validTo: new Date(Date.now() + 14 * 864e5).toISOString().slice(0, 10),
    });
  };

  if (loading) {
    return (
      <main data-testid="iadm07-institution-desk" className="mx-auto max-w-4xl p-6">
        Loading institute security…
      </main>
    );
  }

  if (error) {
    return (
      <main data-testid="iadm07-institution-desk" className="mx-auto max-w-4xl p-6">
        <p data-testid="iadm07-access-denied">{error}</p>
      </main>
    );
  }

  return (
    <main data-testid="iadm07-institution-desk" className="mx-auto max-w-4xl space-y-6 p-6">
      <h1 className="text-2xl font-semibold">Institute Security Posture</h1>
      <p className="text-slate-600">Own institute registers only. No standard edits or resolve.</p>
      <p data-testid="iadm07-scope-tenant" className="text-sm">
        Scope: {String(posture?.scopeTenantId ?? '')}
      </p>
      <section data-testid="iadm07-institute-posture">
        <pre className="overflow-auto rounded bg-slate-50 p-3 text-xs">{JSON.stringify(posture?.indicators ?? posture, null, 2)}</pre>
      </section>

      <section data-testid="iadm07-institute-findings" className="space-y-2">
        <h2 className="text-lg font-medium">Findings</h2>
        <ul className="space-y-2 text-sm">
          {findings.map((f) => (
            <li key={String(f.id)} data-testid={`iadm07-institute-finding-${f.code}`} className="flex flex-wrap gap-2 border-b pb-2">
              <span>
                {String(f.code)} — {String(f.title)} · {String(f.status)}
              </span>
              {FINDING_FLOW[String(f.status)] ? (
                <button
                  type="button"
                  data-testid={`iadm07-advance-finding-${f.code}`}
                  className="rounded bg-slate-800 px-2 py-0.5 text-xs text-white"
                  onClick={() => void advanceFinding(f)}
                >
                  → {FINDING_FLOW[String(f.status)]}
                </button>
              ) : null}
              <button
                type="button"
                data-testid={`iadm07-attach-evidence-${f.code}`}
                className="rounded bg-slate-100 px-2 py-0.5 text-xs"
                onClick={() => void attachEvidence(f)}
              >
                Attach evidence ref
              </button>
            </li>
          ))}
        </ul>
        <input
          data-testid="iadm07-evidence-ref-input"
          className="rounded border px-2 py-1 text-sm"
          value={evidenceRef}
          onChange={(e) => setEvidenceRef(e.target.value)}
        />
      </section>

      <section data-testid="iadm07-institute-alerts" className="space-y-2">
        <h2 className="text-lg font-medium">Alerts</h2>
        {alerts.map((a) => (
          <div key={String(a.id)} data-testid={`iadm07-institute-alert-${a.code}`} className="flex gap-2 text-sm">
            <span>
              {String(a.code)} — {String(a.status)}
            </span>
            {a.status === 'OPEN' ? (
              <button
                type="button"
                data-testid={`iadm07-institute-ack-${a.code}`}
                className="rounded bg-slate-100 px-2 py-0.5 text-xs"
                onClick={() => void ackAlert(a)}
              >
                Acknowledge
              </button>
            ) : null}
          </div>
        ))}
      </section>

      <section data-testid="iadm07-institute-exceptions" className="space-y-2">
        <h2 className="text-lg font-medium">Exceptions</h2>
        <ul className="text-sm">
          {exceptions.map((e) => (
            <li key={String(e.id)} data-testid={`iadm07-institute-exception-${e.code}`}>
              {String(e.code)} · {String(e.status)}
            </li>
          ))}
        </ul>
        <button
          type="button"
          data-testid="iadm07-institute-request-exception"
          className="rounded bg-slate-800 px-3 py-2 text-sm text-white"
          onClick={() => void requestException()}
        >
          Request exception
        </button>
      </section>

      {actionMsg ? <p data-testid="iadm07-action-msg">{actionMsg}</p> : null}
    </main>
  );
}