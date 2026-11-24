'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type Overview = {
  profileCount?: number;
  activeProfiles?: number;
  inactiveProfiles?: number;
  systemAdminCount?: number;
  institutionAdminCount?: number;
  pendingRequests?: number;
  pendingRequestsNote?: string;
  expiringGrants?: number;
  openReviews?: number;
  roleDistribution?: Array<{ roleCode?: string; count?: number }>;
  scope?: string;
};

type RoleItem = {
  code: string;
  name?: string;
  catalogueId?: string;
  kind?: string;
  scopeNote?: string;
  status?: string;
};

type PermItem = {
  code: string;
  module?: string;
  action?: string;
  description?: string;
  mappedRoles?: string[];
};

/**
 * IADM-03 platform desk — overview + role/permission catalogues (US-008…010).
 */
export default function PlatformIdentityPage() {
  const [overview, setOverview] = useState<Overview | null>(null);
  const [roles, setRoles] = useState<RoleItem[]>([]);
  const [perms, setPerms] = useState<PermItem[]>([]);
  const [q, setQ] = useState('iam.');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const [ovRes, roleRes, permRes] = await Promise.all([
          fetch('/api/platform/identity?view=overview'),
          fetch('/api/platform/identity?view=roles'),
          fetch(`/api/platform/identity?view=permissions&q=${encodeURIComponent(q || 'iam.')}`),
        ]);
        const ov = await ovRes.json();
        const roleBody = await roleRes.json();
        const permBody = await permRes.json();
        if (!ovRes.ok) {
          throw new Error(ov.detail || ov.errorCode || 'Overview unavailable');
        }
        if (!cancelled) {
          setOverview(ov);
          setRoles(Array.isArray(roleBody.items) ? roleBody.items : []);
          setPerms(Array.isArray(permBody.items) ? permBody.items : []);
          if (!roleRes.ok) {
            setError(roleBody.detail || 'Role catalogue partially unavailable');
          }
        }
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : 'Failed to load identity desk');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [q]);

  return (
    <div className="mx-auto max-w-6xl space-y-8 p-6" data-testid="iadm03-platform-desk">
      <header className="space-y-2">
        <p className="text-sm uppercase tracking-wide text-slate-500">IADM-03</p>
        <h1 className="text-3xl font-semibold text-slate-900">Identity &amp; access</h1>
        <p className="max-w-2xl text-slate-600">
          Role and permission catalogues, access overview, and institute administrator profiles.
          RBAC matrix §21 remains provisional for this DEV build.
        </p>
        <div className="flex flex-wrap gap-3 pt-2">
          <Link
            href="/platform/identity/profiles"
            className="rounded bg-slate-900 px-4 py-2 text-sm text-white"
            data-testid="iadm03-open-profiles"
          >
            Administrator profiles
          </Link>
          <Link
            href="/platform/identity/requests"
            className="rounded border border-slate-300 px-4 py-2 text-sm"
            data-testid="iadm03-open-requests"
          >
            Access requests
          </Link>
          <button
            type="button"
            className="rounded border border-slate-300 px-4 py-2 text-sm"
            data-testid="iadm03-claims-contract"
            onClick={() => {
              void (async () => {
                const res = await fetch('/api/platform/identity/claims/contract');
                const body = await res.json();
                setError(
                  res.ok
                    ? `Claims: auth=${body.authoritative}; cache≤${body.gatewayIdentityCacheTtlSeconds}s; ${body.note || ''}`
                    : body.detail || 'Claims contract unavailable'
                );
              })();
            }}
          >
            Claims contract (US-021)
          </button>
        </div>
      </header>

      {loading && (
        <p data-testid="iadm03-loading" className="text-slate-500">
          Loading…
        </p>
      )}
      {error && (
        <p data-testid="iadm03-error" className="text-red-700">
          {error}
        </p>
      )}

      {overview && (
        <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4" data-testid="iadm03-overview">
          <Stat label="Profiles in scope" value={overview.profileCount} testId="iadm03-stat-profiles" />
          <Stat label="Active (R03)" value={overview.activeProfiles} testId="iadm03-stat-active" />
          <Stat label="Expiring grants (30d)" value={overview.expiringGrants} testId="iadm03-stat-expiring" />
          <Stat label="Open reviews" value={overview.openReviews} testId="iadm03-stat-reviews" />
          <Stat
            label="Pending requests"
            value={overview.pendingRequests}
            note={overview.pendingRequestsNote}
            testId="iadm03-stat-requests"
          />
          <Stat label="System admins (R02)" value={overview.systemAdminCount} testId="iadm03-stat-r02" />
          <Stat
            label="Institution admins (R03)"
            value={overview.institutionAdminCount}
            testId="iadm03-stat-r03"
          />
          <Stat label="Scope" value={overview.scope} testId="iadm03-stat-scope" />
        </section>
      )}

      <section className="space-y-3" data-testid="iadm03-roles-section">
        <h2 className="text-xl font-semibold text-slate-900">Role catalogue</h2>
        {!loading && roles.length === 0 && (
          <p data-testid="iadm03-roles-empty" className="text-slate-500">
            No roles visible in your scope.
          </p>
        )}
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
          <table className="min-w-full text-left text-sm" data-testid="iadm03-roles-table">
            <thead className="border-b bg-slate-50 text-slate-600">
              <tr>
                <th className="px-4 py-3">Code</th>
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Kind</th>
                <th className="px-4 py-3">Scope note</th>
                <th className="px-4 py-3">Status</th>
              </tr>
            </thead>
            <tbody>
              {roles.map((r) => (
                <tr key={r.code} className="border-b last:border-0" data-testid={`iadm03-role-${r.code}`}>
                  <td className="px-4 py-3 font-medium">{r.code}</td>
                  <td className="px-4 py-3">{r.name || '—'}</td>
                  <td className="px-4 py-3">{r.kind || '—'}</td>
                  <td className="px-4 py-3 text-slate-600">{r.scopeNote || '—'}</td>
                  <td className="px-4 py-3">{r.status || 'ACTIVE'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="space-y-3" data-testid="iadm03-permissions-section">
        <div className="flex flex-wrap items-end gap-3">
          <h2 className="text-xl font-semibold text-slate-900">Permission catalogue</h2>
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-slate-600">Search</span>
            <input
              data-testid="iadm03-perm-search"
              className="rounded border border-slate-300 px-3 py-2"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="key, module, or action"
            />
          </label>
        </div>
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
          <table className="min-w-full text-left text-sm" data-testid="iadm03-permissions-table">
            <thead className="border-b bg-slate-50 text-slate-600">
              <tr>
                <th className="px-4 py-3">Key</th>
                <th className="px-4 py-3">Module</th>
                <th className="px-4 py-3">Mapped roles</th>
                <th className="px-4 py-3">Description</th>
              </tr>
            </thead>
            <tbody>
              {perms.map((p) => (
                <tr key={p.code} className="border-b last:border-0" data-testid={`iadm03-perm-${p.code}`}>
                  <td className="px-4 py-3 font-medium">{p.code}</td>
                  <td className="px-4 py-3">{p.module || '—'}</td>
                  <td className="px-4 py-3">{(p.mappedRoles || []).join(', ') || '—'}</td>
                  <td className="px-4 py-3 text-slate-600">{p.description || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}

function Stat({
  label,
  value,
  note,
  testId,
}: {
  label: string;
  value: string | number | undefined | null;
  note?: string;
  testId: string;
}) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <p className="text-xs uppercase text-slate-500">{label}</p>
      <p className="mt-1 text-lg font-medium" data-testid={testId}>
        {value ?? '—'}
      </p>
      {note ? <p className="mt-1 text-xs text-slate-500">{note}</p> : null}
    </div>
  );
}
