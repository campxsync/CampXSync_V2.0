'use client';

import { useEffect, useState } from 'react';

/**
 * IADM-05 Institution Admin adherence desk (US-010) — view-only (G-16).
 */
export default function InstitutionGovernancePage() {
  const [data, setData] = useState<Record<string, unknown> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void (async () => {
      try {
        const res = await fetch('/api/institution/governance');
        const body = await res.json();
        if (!res.ok) throw new Error(body.detail || 'Unavailable');
        setData(body);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed');
      }
    })();
  }, []);

  const policies = Array.isArray(data?.applicablePolicies) ? (data!.applicablePolicies as unknown[]) : [];
  const requests = Array.isArray(data?.ownRequests) ? (data!.ownRequests as unknown[]) : [];

  return (
    <main data-testid="iadm05-institution-desk" className="mx-auto max-w-4xl space-y-6 p-6">
      <header>
        <h1 className="text-2xl font-semibold">Institute policy adherence</h1>
        <p className="text-sm text-neutral-600">
          View-only. Shows published policies applicable to your institute and your own governance
          request status. No write controls (G-16).
        </p>
      </header>
      {error && (
        <p data-testid="iadm05-institution-error" className="text-sm text-red-700">
          {error}
        </p>
      )}
      <section data-testid="iadm05-adherence-policies">
        <h2 className="font-medium">Applicable published policies</h2>
        <ul className="mt-2 divide-y border">
          {policies.map((p, i) => {
            const row = p as Record<string, unknown>;
            return (
              <li key={String(row.policyId || i)} className="p-3 text-sm">
                {String(row.code)} · {String(row.policyType)} · v{String(row.versionNo)} ·{' '}
                {String(row.demoLabel)}
              </li>
            );
          })}
        </ul>
      </section>
      <section data-testid="iadm05-adherence-requests">
        <h2 className="font-medium">Own institute governance requests</h2>
        <ul className="mt-2 divide-y border">
          {requests.map((r, i) => {
            const row = r as Record<string, unknown>;
            return (
              <li key={String(row.requestId || i)} className="p-3 text-sm">
                {String(row.requestType)} · {String(row.status)} · {String(row.correlationId)}
              </li>
            );
          })}
          {requests.length === 0 && <li className="p-3 text-sm text-neutral-500">None yet</li>}
        </ul>
      </section>
      <p data-testid="iadm05-adherence-readonly" className="text-xs text-neutral-500">
        mode={String(data?.mode || 'VIEW_ONLY')} · writeControls=false
      </p>
    </main>
  );
}
