'use client';

import { useEffect, useState } from 'react';

type Row = Record<string, unknown>;

/**
 * IADM-07 College Security view (R04): summary + published standards only.
 */
export default function CollegeSecurityPage() {
  const [summary, setSummary] = useState<Row | null>(null);
  const [standards, setStandards] = useState<Row[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    void (async () => {
      setLoading(true);
      setError(null);
      const ac = new AbortController();
      const timer = setTimeout(() => ac.abort(), 20000);
      try {
        const [s, st] = await Promise.all([
          fetch('/api/college/security?view=summary', { signal: ac.signal }),
          fetch('/api/college/security?view=standards', { signal: ac.signal }),
        ]);
        const sBody = await s.json();
        const stBody = await st.json();
        if (s.status === 403) {
          setError(sBody.detail || 'You do not have access to this view');
          return;
        }
        if (!s.ok) throw new Error(sBody.detail || 'Summary unavailable');
        setSummary(sBody);
        setStandards(Array.isArray(stBody.items) ? stBody.items : []);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed to load');
      } finally {
        clearTimeout(timer);
        setLoading(false);
      }
    })();
  }, []);

  if (loading) {
    return (
      <main data-testid="iadm07-college-desk" className="mx-auto max-w-4xl p-6">
        Loading…
      </main>
    );
  }

  if (error) {
    return (
      <main data-testid="iadm07-college-desk" className="mx-auto max-w-4xl p-6">
        <p data-testid="iadm07-access-denied">{error}</p>
      </main>
    );
  }

  return (
    <main data-testid="iadm07-college-desk" className="mx-auto max-w-4xl space-y-6 p-6">
      <h1 className="text-2xl font-semibold">College Security Summary</h1>
      <p className="text-slate-600">Published standards and institute posture counts only — no finding detail.</p>
      <section data-testid="iadm07-college-summary" className="space-y-2">
        <p data-testid="iadm07-scope-tenant" className="text-sm">
          Scope: {String(summary?.scopeTenantId ?? '')}
        </p>
        <p data-testid="iadm07-published-count" className="text-sm">
          Published standards: {String(summary?.publishedStandardsCount ?? '—')}
        </p>
        <pre className="rounded bg-slate-50 p-3 text-xs">{JSON.stringify(summary?.indicators ?? summary, null, 2)}</pre>
      </section>
      <section data-testid="iadm07-college-standards">
        <h2 className="mb-2 text-lg font-medium">Published standards</h2>
        <ul className="space-y-2 text-sm">
          {standards.map((row) => (
            <li key={String(row.id)} data-testid={`iadm07-college-standard-${row.code}`}>
              {String(row.code)} — {String(row.title)} v{String(row.versionNo ?? 1)} · {String(row.status)}
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}