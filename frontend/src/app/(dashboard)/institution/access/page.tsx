'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type Overview = {
  profileCount?: number;
  activeProfiles?: number;
  expiringGrants?: number;
  openReviews?: number;
  pendingRequests?: number;
  pendingRequestsNote?: string;
  scope?: string;
};

type RoleItem = { code: string; name?: string; kind?: string; scopeNote?: string; status?: string };
type ProfileRow = {
  id: string;
  email?: string | null;
  fullName?: string | null;
  status?: string;
  roleCode?: string;
};

/**
 * IADM-03 institution desk (R03) — overview, assignable roles, own-institute profiles.
 */
export default function InstitutionAccessPage() {
  const [overview, setOverview] = useState<Overview | null>(null);
  const [roles, setRoles] = useState<RoleItem[]>([]);
  const [profiles, setProfiles] = useState<ProfileRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const [ovRes, roleRes, profRes] = await Promise.all([
          fetch('/api/institution/access?view=overview'),
          fetch('/api/institution/access?view=roles'),
          fetch('/api/institution/access?view=profiles&status=ALL'),
        ]);
        const ov = await ovRes.json();
        const roleBody = await roleRes.json();
        const profBody = await profRes.json();
        if (!ovRes.ok) {
          throw new Error(ov.detail || ov.errorCode || 'Overview unavailable');
        }
        if (!cancelled) {
          setOverview(ov);
          setRoles(Array.isArray(roleBody.items) ? roleBody.items : []);
          setProfiles(Array.isArray(profBody.items) ? profBody.items : []);
        }
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Failed to load');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="mx-auto max-w-6xl space-y-8 p-6" data-testid="iadm03-institution-desk">
      <header className="space-y-2">
        <p className="text-sm uppercase tracking-wide text-slate-500">IADM-03</p>
        <h1 className="text-3xl font-semibold text-slate-900">Institute access</h1>
        <p className="max-w-2xl text-slate-600">
          Overview and catalogues for your institute only. Cross-institute rows are never shown.
        </p>
        <div className="flex flex-wrap gap-3">
          <Link
            href="/institution/access/delegations"
            className="inline-block rounded bg-slate-900 px-4 py-2 text-sm text-white"
            data-testid="iadm03-open-delegations"
          >
            Delegations
          </Link>
          <Link
            href="/institution/access/requests"
            className="inline-block rounded border border-slate-300 px-4 py-2 text-sm"
            data-testid="iadm03-open-requests"
          >
            Access requests
          </Link>
          <Link
            href="/institution/access/reviews"
            className="inline-block rounded border border-slate-300 px-4 py-2 text-sm"
            data-testid="iadm03-open-reviews"
          >
            Access reviews
          </Link>
        </div>
      </header>

      {loading && <p data-testid="iadm03-inst-loading">Loading…</p>}
      {error && (
        <p data-testid="iadm03-inst-error" className="text-red-700">
          {error}
        </p>
      )}

      {overview && (
        <section className="grid gap-4 sm:grid-cols-4" data-testid="iadm03-inst-overview">
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <p className="text-xs uppercase text-slate-500">Profiles</p>
            <p className="mt-1 text-lg font-medium" data-testid="iadm03-inst-stat-profiles">
              {overview.profileCount ?? 0}
            </p>
          </div>
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <p className="text-xs uppercase text-slate-500">Active</p>
            <p className="mt-1 text-lg font-medium" data-testid="iadm03-inst-stat-active">
              {overview.activeProfiles ?? 0}
            </p>
          </div>
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <p className="text-xs uppercase text-slate-500">Expiring grants</p>
            <p className="mt-1 text-lg font-medium">{overview.expiringGrants ?? 0}</p>
          </div>
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <p className="text-xs uppercase text-slate-500">Scope</p>
            <p className="mt-1 text-lg font-medium" data-testid="iadm03-inst-stat-scope">
              {overview.scope || '—'}
            </p>
          </div>
        </section>
      )}

      <section className="space-y-3">
        <h2 className="text-xl font-semibold">Assignable roles (institute)</h2>
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
          <table className="min-w-full text-left text-sm" data-testid="iadm03-inst-roles-table">
            <thead className="border-b bg-slate-50 text-slate-600">
              <tr>
                <th className="px-4 py-3">Code</th>
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Scope note</th>
              </tr>
            </thead>
            <tbody>
              {roles.map((r) => (
                <tr key={r.code} className="border-b last:border-0" data-testid={`iadm03-inst-role-${r.code}`}>
                  <td className="px-4 py-3 font-medium">{r.code}</td>
                  <td className="px-4 py-3">{r.name || '—'}</td>
                  <td className="px-4 py-3">{r.scopeNote || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-xl font-semibold">Administrator profiles</h2>
        <ul className="space-y-2" data-testid="iadm03-inst-profiles">
          {profiles.map((p) => (
            <li key={p.id} className="rounded border border-slate-200 bg-white px-4 py-3 text-sm">
              <Link
                href={`/institution/access/profiles/${encodeURIComponent(p.id)}`}
                className="font-medium underline-offset-2 hover:underline"
                data-testid={`iadm03-inst-profile-${p.id}`}
              >
                {p.fullName || p.email || p.id}
              </Link>
              <span className="ml-2 text-slate-500">
                {p.roleCode} · {p.status}
              </span>
            </li>
          ))}
        </ul>
        {!loading && profiles.length === 0 && (
          <p data-testid="iadm03-inst-profiles-empty" className="text-slate-500">
            No administrator profiles in this institute.
          </p>
        )}
      </section>
    </div>
  );
}
