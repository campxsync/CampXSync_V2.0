'use client';

import { useEffect, useState } from 'react';

/**
 * IADM-05 College Admin desk (US-011) — view applicable policy + informational domain entry points.
 * No trigger, edit, or override (G-02).
 */
export default function CollegeGovernancePage() {
  const [data, setData] = useState<Record<string, unknown> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void (async () => {
      try {
        const res = await fetch('/api/college/governance');
        const body = await res.json();
        if (!res.ok) throw new Error(body.detail || 'Unavailable');
        setData(body);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed');
      }
    })();
  }, []);

  const policies = Array.isArray(data?.applicablePolicies) ? (data!.applicablePolicies as unknown[]) : [];
  const entries = Array.isArray(data?.domainWorkflowEntryPoints)
    ? (data!.domainWorkflowEntryPoints as unknown[])
    : [];

  return (
    <main data-testid="iadm05-college-desk" className="mx-auto max-w-4xl space-y-6 p-6">
      <header>
        <h1 className="text-2xl font-semibold">College policy view</h1>
        <p className="text-sm text-neutral-600">
          Applicable published policy and informational domain-workflow entry points. IADM-05 does
          not trigger domain execution from this screen.
        </p>
      </header>
      {error && (
        <p data-testid="iadm05-college-error" className="text-sm text-red-700">
          {error}
        </p>
      )}
      <section data-testid="iadm05-college-policies">
        <h2 className="font-medium">Applicable policy</h2>
        <ul className="mt-2 divide-y border">
          {policies.map((p, i) => {
            const row = p as Record<string, unknown>;
            return (
              <li key={String(row.policyId || i)} className="p-3 text-sm">
                {String(row.code)} · {String(row.policyType)} · v{String(row.versionNo)}
              </li>
            );
          })}
        </ul>
      </section>
      <section data-testid="iadm05-college-entry-points">
        <h2 className="font-medium">Domain workflow entry points (informational)</h2>
        <ul className="mt-2 divide-y border">
          {entries.map((e, i) => {
            const row = e as Record<string, unknown>;
            return (
              <li key={i} className="p-3 text-sm">
                <div className="font-medium">{String(row.label)}</div>
                <div className="text-neutral-600">{String(row.path)}</div>
                <div className="text-xs text-neutral-500">{String(row.note)}</div>
              </li>
            );
          })}
        </ul>
      </section>
      <p data-testid="iadm05-college-readonly" className="text-xs text-neutral-500">
        canEditPolicy=false · canTriggerDomain=false
      </p>
    </main>
  );
}
