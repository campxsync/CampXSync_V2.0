'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type ChangeItem = {
  id: string;
  changeType?: string;
  roleCode?: string;
  permissionCode?: string;
  justification?: string;
  status?: string;
  impactPreviewRaw?: string;
  proposedBy?: string;
  proposedAt?: string;
  decisionReason?: string;
};

type ProposeResult = {
  id?: string;
  status?: string;
  impactPreview?: Record<string, unknown>;
  message?: string;
  detail?: string;
  errorCode?: string;
};

/**
 * IADM-03 US-030 — R01 propose / different R01 approve role↔permission mapping changes.
 */
export default function CatalogChangesPage() {
  const [items, setItems] = useState<ChangeItem[]>([]);
  const [changeType, setChangeType] = useState('ADD_MAPPING');
  const [roleCode, setRoleCode] = useState('AUDITOR');
  const [permissionCode, setPermissionCode] = useState('iam.governance.read');
  const [justification, setJustification] = useState('');
  const [decideReason, setDecideReason] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [flash, setFlash] = useState<string | null>(null);
  const [lastImpact, setLastImpact] = useState<Record<string, unknown> | null>(null);

  async function refresh() {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch('/api/platform/identity/catalog-changes?status=ALL');
      const body = await res.json();
      if (!res.ok) {
        throw new Error(body.detail || body.errorCode || 'Failed to load proposals');
      }
      setItems(Array.isArray(body.items) ? body.items : []);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void refresh();
  }, []);

  async function onPropose() {
    setBusy(true);
    setError(null);
    setFlash(null);
    try {
      const res = await fetch('/api/platform/identity/catalog-changes', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          changeType,
          roleCode,
          permissionCode,
          justification,
          idempotencyKey: crypto.randomUUID(),
        }),
      });
      const body = (await res.json()) as ProposeResult;
      if (!res.ok) {
        throw new Error(body.detail || body.errorCode || 'Propose failed');
      }
      setLastImpact(body.impactPreview || null);
      setFlash(body.message || `Proposed ${body.id} (${body.status})`);
      setJustification('');
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Propose failed');
    } finally {
      setBusy(false);
    }
  }

  async function onDecide(id: string, decision: 'APPROVE' | 'REJECT') {
    setBusy(true);
    setError(null);
    setFlash(null);
    try {
      const res = await fetch(`/api/platform/identity/catalog-changes/${encodeURIComponent(id)}/decide`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ decision, reason: decideReason || `${decision} via IADM-03 desk` }),
      });
      const body = await res.json();
      if (!res.ok) {
        throw new Error(body.detail || body.errorCode || 'Decide failed');
      }
      setFlash(`${decision} → ${body.status || decision}; applied=${String(body.applied)}`);
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Decide failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-8 p-6" data-testid="iadm03-catalog-changes-desk">
      <header className="space-y-2">
        <Link href="/platform/identity" className="text-sm text-slate-500 hover:underline">
          ← Identity &amp; access
        </Link>
        <h1 className="text-3xl font-semibold text-slate-900">Catalogue mapping changes</h1>
        <p className="max-w-2xl text-slate-600">
          R01 proposes a role↔permission mapping change. A different R01 must approve before apply. This desk never
          adds roles to the R01–R39 catalogue.
        </p>
      </header>

      {error ? (
        <p className="rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800" data-testid="iadm03-catalog-error">
          {error}
        </p>
      ) : null}
      {flash ? (
        <p className="rounded border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900" data-testid="iadm03-catalog-flash">
          {flash}
        </p>
      ) : null}

      <section className="space-y-3 rounded border border-slate-200 p-4" data-testid="iadm03-catalog-propose">
        <h2 className="text-lg font-semibold">Propose mapping change</h2>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="text-sm">
            Change type
            <select
              className="mt-1 w-full rounded border border-slate-300 px-2 py-2"
              value={changeType}
              onChange={(e) => setChangeType(e.target.value)}
              data-testid="iadm03-catalog-change-type"
            >
              <option value="ADD_MAPPING">ADD_MAPPING</option>
              <option value="REMOVE_MAPPING">REMOVE_MAPPING</option>
            </select>
          </label>
          <label className="text-sm">
            Role code
            <input
              className="mt-1 w-full rounded border border-slate-300 px-2 py-2"
              value={roleCode}
              onChange={(e) => setRoleCode(e.target.value)}
              data-testid="iadm03-catalog-role"
            />
          </label>
          <label className="text-sm sm:col-span-2">
            Permission code
            <input
              className="mt-1 w-full rounded border border-slate-300 px-2 py-2"
              value={permissionCode}
              onChange={(e) => setPermissionCode(e.target.value)}
              data-testid="iadm03-catalog-permission"
            />
          </label>
          <label className="text-sm sm:col-span-2">
            Justification
            <textarea
              className="mt-1 w-full rounded border border-slate-300 px-2 py-2"
              rows={3}
              value={justification}
              onChange={(e) => setJustification(e.target.value)}
              data-testid="iadm03-catalog-justification"
            />
          </label>
        </div>
        <button
          type="button"
          disabled={busy || !justification.trim()}
          onClick={() => void onPropose()}
          className="rounded bg-slate-900 px-4 py-2 text-sm text-white disabled:opacity-40"
          data-testid="iadm03-catalog-propose-btn"
        >
          Propose
        </button>
        {lastImpact ? (
          <pre
            className="overflow-auto rounded bg-slate-50 p-3 text-xs text-slate-700"
            data-testid="iadm03-catalog-impact"
          >
            {JSON.stringify(lastImpact, null, 2)}
          </pre>
        ) : null}
      </section>

      <section className="space-y-3" data-testid="iadm03-catalog-list">
        <div className="flex flex-wrap items-end gap-3">
          <h2 className="text-lg font-semibold">Open proposals</h2>
          <label className="text-sm">
            Decision reason
            <input
              className="ml-2 rounded border border-slate-300 px-2 py-1"
              value={decideReason}
              onChange={(e) => setDecideReason(e.target.value)}
              data-testid="iadm03-catalog-decide-reason"
            />
          </label>
        </div>
        {loading ? (
          <p className="text-slate-500">Loading…</p>
        ) : items.length === 0 ? (
          <p className="text-slate-500" data-testid="iadm03-catalog-empty">
            No catalogue change proposals yet.
          </p>
        ) : (
          <ul className="divide-y divide-slate-200 rounded border border-slate-200">
            {items.map((it) => (
              <li key={it.id} className="space-y-2 p-4" data-testid={`iadm03-catalog-row-${it.id}`}>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <p className="font-medium text-slate-900">
                      {it.changeType} · {it.roleCode} ↔ {it.permissionCode}
                    </p>
                    <p className="text-sm text-slate-600">
                      {it.status} · proposed {it.proposedAt || '—'}
                    </p>
                    <p className="text-sm text-slate-500">{it.justification}</p>
                  </div>
                  {it.status === 'PROPOSED' ? (
                    <div className="flex gap-2">
                      <button
                        type="button"
                        disabled={busy}
                        className="rounded border border-slate-300 px-3 py-1 text-sm"
                        data-testid={`iadm03-catalog-reject-${it.id}`}
                        onClick={() => void onDecide(it.id, 'REJECT')}
                      >
                        Reject
                      </button>
                      <button
                        type="button"
                        disabled={busy}
                        className="rounded bg-slate-900 px-3 py-1 text-sm text-white"
                        data-testid={`iadm03-catalog-approve-${it.id}`}
                        onClick={() => void onDecide(it.id, 'APPROVE')}
                      >
                        Approve &amp; apply
                      </button>
                    </div>
                  ) : null}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
