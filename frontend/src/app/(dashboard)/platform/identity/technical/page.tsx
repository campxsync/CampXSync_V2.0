'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type ProfileRow = {
  id: string;
  email?: string | null;
  fullName?: string | null;
  status?: string;
  roleCode?: string;
  store?: string;
};

/**
 * IADM-03-US-018 — System Admin (R02) assigns platform technical identities only.
 */
export default function TechnicalIdentitiesPage() {
  const [items, setItems] = useState<ProfileRow[]>([]);
  const [subjectUserId, setSubjectUserId] = useState('');
  const [targetRole, setTargetRole] = useState('SERVICE');
  const [reason, setReason] = useState('');
  const [msg, setMsg] = useState<string | null>(null);
  const [denyMsg, setDenyMsg] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  async function load() {
    setLoading(true);
    try {
      const res = await fetch('/api/platform/identity?view=profiles&status=ALL');
      const body = await res.json();
      if (!res.ok) {
        throw new Error(body.detail || body.errorCode || 'Profiles unavailable');
      }
      const rows = Array.isArray(body.items) ? body.items : [];
      setItems(rows.filter((r: ProfileRow) => r.store === 'PLATFORM_ADMIN' || r.roleCode === 'SYSTEM_ADMIN'));
    } catch (e) {
      setMsg(e instanceof Error ? e.message : 'Load failed');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function assignTechnical() {
    setBusy(true);
    setMsg(null);
    try {
      const res = await fetch('/api/platform/identity/grants/assign', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          subjectUserId,
          targetRole,
          reason: reason || 'US-018 technical identity assign',
        }),
      });
      const body = await res.json();
      if (!res.ok) {
        throw new Error(body.detail || body.errorCode || 'Assign failed');
      }
      setMsg(
        body.replay
          ? `Replay — technical identity already recorded (${body.roleCode || targetRole})`
          : `Assigned technical identity ${body.roleCode || targetRole} for ${body.profileId || subjectUserId}`
      );
      await load();
    } catch (e) {
      setMsg(e instanceof Error ? e.message : 'Assign failed');
    } finally {
      setBusy(false);
    }
  }

  async function attemptDeniedBusinessRole() {
    setDenyMsg('Probing…');
    try {
      const res = await fetch('/api/platform/identity/grants/assign', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          subjectUserId: subjectUserId || '00000000-0000-0000-0000-000000000001',
          targetRole: 'INSTITUTION_ADMIN',
          reason: 'US-018 R02 must not assign R03',
          tenantId: '72ecbfe3-2077-41b5-8a20-b6d3e045311b',
        }),
      });
      const body = await res.json().catch(() => ({}));
      setDenyMsg(`${res.status}: ${body.detail || body.errorCode || 'denied'}`);
    } catch (e) {
      setDenyMsg(e instanceof Error ? e.message : 'Probe failed');
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-8 p-6" data-testid="iadm03-technical-desk">
      <header className="space-y-2">
        <p className="text-sm uppercase tracking-wide text-slate-500">IADM-03 · System Admin (R02)</p>
        <h1 className="text-3xl font-semibold text-slate-900">Platform technical identities</h1>
        <p className="max-w-2xl text-slate-600">
          Assign platform technical service identities only. Tenant business roles, Institution Admin
          and College Admin are denied.
        </p>
        <Link href="/platform/identity" className="text-sm text-slate-700 underline">
          Back to identity overview
        </Link>
      </header>

      {loading && <p data-testid="iadm03-technical-loading">Loading…</p>}
      {msg && (
        <p data-testid="iadm03-technical-msg" className="rounded border border-slate-200 bg-slate-50 p-3 text-sm">
          {msg}
        </p>
      )}
      {denyMsg && (
        <p data-testid="iadm03-technical-deny" className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-800">
          {denyMsg}
        </p>
      )}

      <form
        className="space-y-4 rounded-lg border border-slate-200 bg-white p-4"
        data-testid="iadm03-technical-form"
        onSubmit={(e) => {
          e.preventDefault();
          void assignTechnical();
        }}
      >
        <label className="block space-y-1 text-sm">
          <span>Subject Auth user id</span>
          <input
            className="w-full rounded border border-slate-300 px-3 py-2 font-mono text-xs"
            data-testid="iadm03-technical-subject"
            value={subjectUserId}
            onChange={(e) => setSubjectUserId(e.target.value)}
            required
          />
        </label>
        <label className="block space-y-1 text-sm">
          <span>Technical identity type</span>
          <select
            className="w-full rounded border border-slate-300 px-3 py-2"
            data-testid="iadm03-technical-role"
            value={targetRole}
            onChange={(e) => setTargetRole(e.target.value)}
          >
            <option value="SERVICE">SERVICE</option>
            <option value="TECHNICAL">TECHNICAL</option>
            <option value="SVC-BATCH">SVC-BATCH</option>
          </select>
        </label>
        <label className="block space-y-1 text-sm">
          <span>Reason</span>
          <input
            className="w-full rounded border border-slate-300 px-3 py-2"
            data-testid="iadm03-technical-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            required
          />
        </label>
        <div className="flex flex-wrap gap-3">
          <button
            type="submit"
            disabled={busy}
            className="rounded bg-slate-900 px-4 py-2 text-sm text-white disabled:opacity-50"
            data-testid="iadm03-technical-submit"
          >
            Assign technical identity
          </button>
          <button
            type="button"
            className="rounded border border-amber-400 bg-amber-50 px-4 py-2 text-sm text-amber-900"
            data-testid="iadm03-technical-probe-deny"
            onClick={() => void attemptDeniedBusinessRole()}
          >
            Probe R03 assign (must be denied)
          </button>
        </div>
      </form>

      <section className="space-y-2" data-testid="iadm03-technical-list">
        <h2 className="text-lg font-semibold">Existing platform technical / System Admin rows</h2>
        {items.length === 0 && !loading && (
          <p data-testid="iadm03-technical-empty" className="text-slate-500">
            No platform technical identities listed.
          </p>
        )}
        <ul className="divide-y rounded border border-slate-200 bg-white">
          {items.map((row) => (
            <li key={row.id} className="px-4 py-3 text-sm" data-testid={`iadm03-technical-row-${row.id}`}>
              <span className="font-medium">{row.fullName || row.email || row.id}</span>
              <span className="ml-2 text-slate-500">{row.roleCode || 'SYSTEM_ADMIN'}</span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
