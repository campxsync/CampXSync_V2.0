'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type EvidenceRow = {
  id?: string;
  action?: string;
  principalId?: string;
  resourceType?: string;
  resourceId?: string;
  status?: string;
  createdAt?: string;
  detail?: string;
};

type ProfileRow = {
  id: string;
  email?: string | null;
  fullName?: string | null;
  status?: string;
  roleCode?: string;
};

/**
 * IADM-03-US-033 — Auditor (R28) read-only access evidence desk.
 * No mutation controls on this page; API mutations are denied separately.
 */
export default function IdentityEvidencePage() {
  const [items, setItems] = useState<EvidenceRow[]>([]);
  const [profiles, setProfiles] = useState<ProfileRow[]>([]);
  const [rolesCount, setRolesCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [denyMsg, setDenyMsg] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const [evRes, profRes, roleRes] = await Promise.all([
          fetch('/api/platform/identity/evidence'),
          fetch('/api/platform/identity?view=profiles&status=ALL'),
          fetch('/api/platform/identity?view=roles'),
        ]);
        const ev = await evRes.json();
        const prof = await profRes.json();
        const roles = await roleRes.json();
        if (!evRes.ok) {
          throw new Error(ev.detail || ev.errorCode || 'Evidence unavailable');
        }
        if (!cancelled) {
          setItems(Array.isArray(ev.items) ? ev.items : []);
          setProfiles(Array.isArray(prof.items) ? prof.items : []);
          setRolesCount(Array.isArray(roles.items) ? roles.items.length : 0);
          if (!profRes.ok) {
            setError(prof.detail || 'Profiles partially unavailable');
          }
        }
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : 'Failed to load evidence');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  async function attemptDeniedMutation() {
    setDenyMsg('Probing…');
    try {
      const res = await fetch('/api/platform/identity/grants/assign', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          subjectUserId: '00000000-0000-0000-0000-000000000001',
          targetRole: 'FACULTY',
          reason: 'US-033 auditor mutation probe',
          tenantId: '72ecbfe3-2077-41b5-8a20-b6d3e045311b',
        }),
      });
      const body = await res.json().catch(() => ({}));
      setDenyMsg(
        `${res.status}: ${body.detail || body.errorCode || 'denied'} (errorCode=${body.errorCode || 'n/a'})`
      );
    } catch (e) {
      setDenyMsg(e instanceof Error ? e.message : 'Probe failed');
    }
  }

  return (
    <div className="mx-auto max-w-6xl space-y-8 p-6" data-testid="iadm03-evidence-desk">
      <header className="space-y-2">
        <p className="text-sm uppercase tracking-wide text-slate-500">IADM-03 · Auditor (R28)</p>
        <h1 className="text-3xl font-semibold text-slate-900">Access evidence</h1>
        <p className="max-w-2xl text-slate-600">
          Read-only view of role catalogue counts, administrator profiles and IADM-03 audit evidence.
          There are no create, update, approve, grant, revoke or delegate controls on this desk.
        </p>
        <div className="flex flex-wrap gap-3 pt-2">
          <Link
            href="/platform/identity"
            className="rounded border border-slate-300 px-4 py-2 text-sm"
            data-testid="iadm03-evidence-back"
          >
            Identity overview
          </Link>
          <button
            type="button"
            className="rounded border border-amber-400 bg-amber-50 px-4 py-2 text-sm text-amber-900"
            data-testid="iadm03-evidence-probe-mutation"
            onClick={() => void attemptDeniedMutation()}
          >
            Probe mutation (must be denied)
          </button>
        </div>
      </header>

      {loading && (
        <p data-testid="iadm03-evidence-loading" className="text-slate-500">
          Loading…
        </p>
      )}
      {error && (
        <p data-testid="iadm03-evidence-error" className="text-red-700">
          {error}
        </p>
      )}
      {denyMsg && (
        <p data-testid="iadm03-evidence-deny" className="rounded border border-red-200 bg-red-50 p-3 text-red-800">
          {denyMsg}
        </p>
      )}

      <section className="grid gap-4 sm:grid-cols-3" data-testid="iadm03-evidence-stats">
        <div className="rounded border border-slate-200 bg-white p-4">
          <p className="text-xs uppercase text-slate-500">Roles in catalogue</p>
          <p className="text-2xl font-semibold" data-testid="iadm03-evidence-roles-count">
            {rolesCount}
          </p>
        </div>
        <div className="rounded border border-slate-200 bg-white p-4">
          <p className="text-xs uppercase text-slate-500">Profiles visible</p>
          <p className="text-2xl font-semibold" data-testid="iadm03-evidence-profiles-count">
            {profiles.length}
          </p>
        </div>
        <div className="rounded border border-slate-200 bg-white p-4">
          <p className="text-xs uppercase text-slate-500">Audit evidence rows</p>
          <p className="text-2xl font-semibold" data-testid="iadm03-evidence-audit-count">
            {items.length}
          </p>
        </div>
      </section>

      <section className="space-y-3" data-testid="iadm03-evidence-audit-section">
        <h2 className="text-xl font-semibold text-slate-900">IADM-03 audit evidence</h2>
        {!loading && items.length === 0 && (
          <p data-testid="iadm03-evidence-empty" className="text-slate-500">
            No IADM-03 audit rows in scope yet.
          </p>
        )}
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
          <table className="min-w-full text-left text-sm" data-testid="iadm03-evidence-table">
            <thead className="border-b bg-slate-50 text-slate-600">
              <tr>
                <th className="px-4 py-3">When</th>
                <th className="px-4 py-3">Action</th>
                <th className="px-4 py-3">Resource</th>
                <th className="px-4 py-3">Status</th>
              </tr>
            </thead>
            <tbody>
              {items.map((row) => (
                <tr key={row.id || `${row.action}-${row.createdAt}`} className="border-b last:border-0">
                  <td className="px-4 py-3 font-mono text-xs">{row.createdAt || '—'}</td>
                  <td className="px-4 py-3">{row.action || '—'}</td>
                  <td className="px-4 py-3">
                    {row.resourceType || '—'}
                    {row.resourceId ? ` · ${row.resourceId}` : ''}
                  </td>
                  <td className="px-4 py-3">{row.status || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <p className="text-sm text-slate-500" data-testid="iadm03-evidence-readonly-banner">
        Read-only desk — mutation controls are absent by design.
      </p>
    </div>
  );
}
