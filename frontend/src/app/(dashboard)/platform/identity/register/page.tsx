'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type RegisterItem = {
  key: string;
  owningService: string;
  module?: string;
  action?: string;
  description?: string;
  status: string;
  templateHolders?: string[];
  templateHolderCount?: number;
  liveTenantRoleCount?: number;
};

type HolderItem = {
  accountRef: string;
  roleCode: string;
  tenantId: string;
  tenantCode?: string;
  collegeId?: string | null;
};

export default function PermissionRegisterPage() {
  const [items, setItems] = useState<RegisterItem[]>([]);
  const [liveTotal, setLiveTotal] = useState<number>(0);
  const [reconciled, setReconciled] = useState<boolean>(false);
  const [lastVerifiedAt, setLastVerifiedAt] = useState<string>('');
  const [serviceFilter, setServiceFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [q, setQ] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [holders, setHolders] = useState<HolderItem[]>([]);
  const [holdersLoading, setHoldersLoading] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const params = new URLSearchParams();
        if (serviceFilter) params.set('service', serviceFilter);
        if (statusFilter) params.set('status', statusFilter);
        if (roleFilter) params.set('role', roleFilter);
        if (q) params.set('q', q);
        const res = await fetch(`/api/platform/identity/register?${params.toString()}`);
        const data = await res.json();
        if (!res.ok) throw new Error(data.detail || data.errorCode || 'Register unavailable');
        if (!cancelled) {
          setItems(Array.isArray(data.items) ? data.items : []);
          setLiveTotal(data.liveCatalogueTotal ?? 0);
          setReconciled(Boolean(data.reconciled));
          setLastVerifiedAt(data.lastVerifiedAt || '');
        }
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Failed to load register');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [serviceFilter, statusFilter, roleFilter, q]);

  async function inspectHolders(key: string) {
    setSelectedKey(key);
    setHoldersLoading(true);
    setHolders([]);
    try {
      const res = await fetch(`/api/platform/identity/register/${encodeURIComponent(key)}/holders`);
      const data = await res.json();
      if (!res.ok) throw new Error(data.detail || 'Holders unavailable');
      setHolders(Array.isArray(data.holders) ? data.holders : []);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load holders');
    } finally {
      setHoldersLoading(false);
    }
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6" data-testid="iadm03-register-desk">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-wide text-slate-500">IADM-03 · Read-only Register</p>
          <h1 className="text-2xl font-semibold text-slate-900">Permission-Key Register</h1>
          <p className="text-sm text-slate-600" data-testid="iadm03-register-summary">
            Live catalogue total: <strong>{liveTotal}</strong> · Reconciled: <strong>{reconciled ? 'YES' : 'NO'}</strong> · Verified at: {lastVerifiedAt || '—'}
          </p>
        </div>
        <Link href="/platform/identity" className="rounded border border-slate-300 px-3 py-1.5 text-sm text-slate-700">
          Back to IADM-03
        </Link>
      </header>

      {error && (
        <div className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800" data-testid="iadm03-register-error">
          {error}
        </div>
      )}

      <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
        <input
          type="text"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search key text (e.g. iadm07)"
          className="rounded border border-slate-300 px-3 py-2 text-sm"
          data-testid="iadm03-register-search"
        />
        <select
          value={serviceFilter}
          onChange={(e) => setServiceFilter(e.target.value)}
          className="rounded border border-slate-300 px-3 py-2 text-sm"
          data-testid="iadm03-register-service-filter"
        >
          <option value="">All services</option>
          <option value="IADM-01">IADM-01</option>
          <option value="IADM-02">IADM-02</option>
          <option value="IADM-03">IADM-03</option>
          <option value="IADM-04">IADM-04</option>
          <option value="IADM-05">IADM-05</option>
          <option value="IADM-06">IADM-06</option>
          <option value="IADM-07">IADM-07</option>
          <option value="IADM-08">IADM-08</option>
          <option value="Legacy">Legacy (adm*)</option>
          <option value="Unknown">Unknown</option>
        </select>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="rounded border border-slate-300 px-3 py-2 text-sm"
          data-testid="iadm03-register-status-filter"
        >
          <option value="">All statuses</option>
          <option value="granted">granted</option>
          <option value="ungranted-reserved">ungranted-reserved</option>
          <option value="legacy">legacy</option>
          <option value="dormant">dormant</option>
          <option value="unknown">unknown</option>
        </select>
        <input
          type="text"
          value={roleFilter}
          onChange={(e) => setRoleFilter(e.target.value)}
          placeholder="Filter role (e.g. AUDITOR)"
          className="rounded border border-slate-300 px-3 py-2 text-sm"
          data-testid="iadm03-register-role-filter"
        />
      </div>

      {selectedKey && (
        <section className="rounded border border-slate-200 bg-slate-50 p-4" data-testid="iadm03-holders-panel">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold text-slate-900">Holders for {selectedKey}</h2>
            <button
              type="button"
              onClick={() => setSelectedKey(null)}
              className="text-xs text-slate-600 underline"
            >
              Close
            </button>
          </div>
          {holdersLoading ? (
            <p className="mt-2 text-xs text-slate-500">Loading holders...</p>
          ) : holders.length === 0 ? (
            <p className="mt-2 text-xs text-slate-600">No active tenant role assignments hold this key.</p>
          ) : (
            <ul className="mt-2 space-y-1 text-xs text-slate-700">
              {holders.map((h, i) => (
                <li key={`${h.accountRef}-${i}`}>
                  {h.accountRef} · {h.roleCode} · {h.tenantCode || h.tenantId}
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {loading ? (
        <p className="text-sm text-slate-500" data-testid="iadm03-register-loading">Loading register...</p>
      ) : (
        <div className="overflow-x-auto rounded border border-slate-200">
          <table className="min-w-full divide-y divide-slate-200 text-left text-sm" data-testid="iadm03-register-table">
            <thead className="bg-slate-50 text-xs uppercase text-slate-600">
              <tr>
                <th className="px-3 py-2">Key</th>
                <th className="px-3 py-2">Owning Service</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2">Template Holders</th>
                <th className="px-3 py-2">Live Holders</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {items.slice(0, 200).map((item) => (
                <tr key={item.key}>
                  <td className="px-3 py-2 font-mono text-xs text-slate-900">{item.key}</td>
                  <td className="px-3 py-2 text-xs text-slate-700">{item.owningService}</td>
                  <td className="px-3 py-2 text-xs text-slate-700">{item.status}</td>
                  <td className="px-3 py-2 text-xs text-slate-600">
                    {(item.templateHolders || []).slice(0, 4).join(', ')}
                    {(item.templateHolders || []).length > 4 ? ` (+${(item.templateHolders || []).length - 4})` : ''}
                  </td>
                  <td className="px-3 py-2 text-xs">
                    <button
                      type="button"
                      onClick={() => inspectHolders(item.key)}
                      className="text-blue-700 underline"
                      data-testid={`iadm03-holders-btn-${item.key}`}
                    >
                      Inspect ({item.liveTenantRoleCount ?? 0})
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
