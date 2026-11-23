'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type CatalogueItem = {
  key: string;
  dataType?: string;
  domain?: string;
  description?: string;
  activeValue?: string | number | null;
  activeVersionLabel?: string | null;
  defaultValue?: string | number | null;
};

type Dashboard = {
  activeVersion?: { versionLabel?: string; versionId?: string };
  pendingDrafts?: number;
  validationFailures?: number;
  recentPublications?: Array<{ versionLabel?: string; status?: string }>;
};

/**
 * IADM-02 configuration desk (US-008 catalogue + US-010 dashboard).
 * Real seeded keys from IADM-02 service via BFF — no empty mock lists.
 */
export default function PlatformConfigurationPage() {
  const [items, setItems] = useState<CatalogueItem[]>([]);
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [q, setQ] = useState('');
  const [domain, setDomain] = useState('ALL');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const [catRes, dashRes] = await Promise.all([
          fetch('/api/platform/configuration?view=catalogue'),
          fetch('/api/platform/configuration?view=dashboard'),
        ]);
        const cat = await catRes.json();
        const dash = await dashRes.json();
        if (!catRes.ok) {
          throw new Error(cat.detail || cat.errorCode || 'Catalogue unavailable');
        }
        if (!cancelled) {
          setItems(Array.isArray(cat.items) ? cat.items : []);
          setDashboard(dashRes.ok ? dash : null);
        }
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : 'Failed to load configuration');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const domains = Array.from(new Set(items.map((i) => i.domain || 'platform'))).sort();
  const filtered = items.filter((i) => {
    if (domain !== 'ALL' && (i.domain || 'platform') !== domain) return false;
    if (!q.trim()) return true;
    const hay = `${i.key} ${i.description || ''}`.toLowerCase();
    return hay.includes(q.trim().toLowerCase());
  });

  return (
    <div className="mx-auto max-w-6xl space-y-8 p-6" data-testid="iadm02-config-desk">
      <header className="space-y-2">
        <p className="text-sm uppercase tracking-wide text-slate-500">IADM-02</p>
        <h1 className="text-3xl font-semibold text-slate-900">Platform configuration</h1>
        <p className="max-w-2xl text-slate-600">
          Catalogue of platform defaults. Draft, validate, and publish through the governed workflow.
        </p>
      </header>

      {dashboard && (
        <section
          className="grid gap-4 sm:grid-cols-3"
          data-testid="iadm02-config-dashboard"
        >
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <p className="text-xs uppercase text-slate-500">Active version</p>
            <p className="mt-1 text-lg font-medium" data-testid="iadm02-active-version">
              {dashboard.activeVersion?.versionLabel || '—'}
            </p>
          </div>
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <p className="text-xs uppercase text-slate-500">Pending drafts</p>
            <p className="mt-1 text-lg font-medium" data-testid="iadm02-pending-drafts">
              {dashboard.pendingDrafts ?? 0}
            </p>
          </div>
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <p className="text-xs uppercase text-slate-500">Validation failures</p>
            <p className="mt-1 text-lg font-medium" data-testid="iadm02-validation-failures">
              {dashboard.validationFailures ?? 0}
            </p>
          </div>
        </section>
      )}

      <section className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-slate-600">Search</span>
          <input
            data-testid="iadm02-catalogue-search"
            className="rounded border border-slate-300 px-3 py-2"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Key or description"
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-slate-600">Domain</span>
          <select
            data-testid="iadm02-catalogue-domain"
            className="rounded border border-slate-300 px-3 py-2"
            value={domain}
            onChange={(e) => setDomain(e.target.value)}
          >
            <option value="ALL">All</option>
            {domains.map((d) => (
              <option key={d} value={d}>
                {d}
              </option>
            ))}
          </select>
        </label>
        <Link
          href="/platform/configuration/drafts"
          className="rounded bg-slate-900 px-4 py-2 text-sm text-white"
          data-testid="iadm02-open-drafts"
        >
          Drafts &amp; validate
        </Link>
        <Link
          href="/platform/configuration/policies"
          className="rounded border border-slate-300 px-4 py-2 text-sm"
          data-testid="iadm02-open-policies"
        >
          Policies
        </Link>
        <Link
          href="/platform/configuration/diagnostics"
          className="rounded border border-slate-300 px-4 py-2 text-sm"
          data-testid="iadm02-link-diagnostics"
        >
          Consumer diagnostics
        </Link>
        <Link
          href="/platform/configuration/versions"
          className="rounded border border-slate-300 px-4 py-2 text-sm"
          data-testid="iadm02-open-versions"
        >
          Versions
        </Link>
      </section>

      {loading && (
        <p data-testid="iadm02-catalogue-loading" className="text-slate-500">
          Loading catalogue…
        </p>
      )}
      {error && (
        <p data-testid="iadm02-catalogue-error" className="text-red-700">
          {error}
        </p>
      )}
      {!loading && !error && filtered.length === 0 && (
        <p data-testid="iadm02-catalogue-empty" className="text-slate-500">
          No keys match this filter.
        </p>
      )}

      <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
        <table className="min-w-full text-left text-sm" data-testid="iadm02-catalogue-table">
          <thead className="border-b bg-slate-50 text-slate-600">
            <tr>
              <th className="px-4 py-3">Key</th>
              <th className="px-4 py-3">Domain</th>
              <th className="px-4 py-3">Type</th>
              <th className="px-4 py-3">Active value</th>
              <th className="px-4 py-3">Version</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((row) => (
              <tr key={row.key} className="border-b last:border-0" data-testid={`iadm02-key-row-${row.key}`}>
                <td className="px-4 py-3">
                  <Link
                    href={`/platform/configuration/${encodeURIComponent(row.key)}`}
                    className="font-medium text-slate-900 underline-offset-2 hover:underline"
                    data-testid={`iadm02-key-link-${row.key}`}
                  >
                    {row.key}
                  </Link>
                  <p className="text-xs text-slate-500">{row.description}</p>
                </td>
                <td className="px-4 py-3">{row.domain}</td>
                <td className="px-4 py-3">{row.dataType}</td>
                <td className="px-4 py-3">{row.activeValue ?? row.defaultValue ?? '—'}</td>
                <td className="px-4 py-3">{row.activeVersionLabel || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
