'use client';

import { useEffect, useState } from 'react';

/**
 * IADM-05 Auditor (R28) evidence desk (US-012) — read-only governance evidence.
 */
export default function GovernanceAuditPage() {
  const [policies, setPolicies] = useState<unknown[]>([]);
  const [requests, setRequests] = useState<unknown[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void (async () => {
      try {
        const [pRes, rRes] = await Promise.all([
          fetch('/api/platform/governance?view=policies&status=PUBLISHED'),
          fetch('/api/platform/governance?view=requests'),
        ]);
        const pBody = await pRes.json();
        const rBody = await rRes.json();
        if (!pRes.ok) throw new Error(pBody.detail || 'Forbidden');
        setPolicies(Array.isArray(pBody.items) ? pBody.items : []);
        setRequests(Array.isArray(rBody.items) ? rBody.items : []);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed');
      }
    })();
  }, []);

  return (
    <main data-testid="iadm05-auditor-desk" className="mx-auto max-w-4xl space-y-6 p-6">
      <header>
        <h1 className="text-2xl font-semibold">Governance evidence</h1>
        <p className="text-sm text-neutral-600">
          Auditor (R28) read-only view of policy versions and request/receipt references. No write or
          audit-administration controls.
        </p>
      </header>
      {error && (
        <p data-testid="iadm05-auditor-error" className="text-sm text-red-700">
          {error}
        </p>
      )}
      <section data-testid="iadm05-auditor-policies">
        <h2 className="font-medium">Published policies</h2>
        <pre className="mt-2 overflow-auto border p-3 text-xs">{JSON.stringify(policies, null, 2)}</pre>
      </section>
      <section data-testid="iadm05-auditor-requests">
        <h2 className="font-medium">Governance request references</h2>
        <pre className="mt-2 overflow-auto border p-3 text-xs">{JSON.stringify(requests, null, 2)}</pre>
      </section>
    </main>
  );
}
