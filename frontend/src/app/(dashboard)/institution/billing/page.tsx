'use client';

import { useEffect, useState } from 'react';

type Entitlement = {
  entitlementKey?: string;
  limitValue?: number;
  limitUnit?: string;
  enabled?: boolean;
};

type OwnPayload = {
  subscription?: {
    id?: string;
    planCode?: string;
    planName?: string;
    status?: string;
    startDate?: string;
    endDate?: string;
    trialEndsOn?: string;
    statusReason?: string;
  } | null;
  entitlements?: Entitlement[];
  accessAllowed?: boolean;
  reason?: string;
};

/**
 * IADM-04 Institution Subscription Desk (US-014 / US-015).
 */
export default function InstitutionBillingPage() {
  const [data, setData] = useState<OwnPayload | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await fetch('/api/institution/billing', { cache: 'no-store' });
        const body = await res.json();
        if (!res.ok) throw new Error(body.detail || 'Subscription unavailable');
        if (!cancelled) setData(body);
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

  const sub = data?.subscription;
  const pending = sub?.status === 'PENDING';

  return (
    <main className="mx-auto max-w-3xl space-y-8 p-6" data-testid="iadm04-institution-desk">
      <header className="space-y-2">
        <h1 className="text-2xl font-semibold text-[#1B2433]">Institute subscription</h1>
        <p className="text-sm text-[#5B6573]">Your current plan, state, and included entitlements.</p>
      </header>

      {loading && <p className="text-sm text-[#5B6573]">Loading…</p>}
      {error && (
        <p className="text-sm text-red-700" data-testid="iadm04-institution-error">
          {error}
        </p>
      )}

      {!loading && !error && (
        <>
          <section className="space-y-2" data-testid="iadm04-own-subscription">
            {sub ? (
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <dt className="text-[#5B6573]">Plan</dt>
                <dd>
                  {sub.planName || sub.planCode}
                </dd>
                <dt className="text-[#5B6573]">Status</dt>
                <dd data-testid="iadm04-own-status">{sub.status}</dd>
                <dt className="text-[#5B6573]">Start</dt>
                <dd>{sub.startDate || '—'}</dd>
                <dt className="text-[#5B6573]">Trial / renewal</dt>
                <dd>{sub.trialEndsOn || sub.endDate || '—'}</dd>
              </dl>
            ) : (
              <p className="text-sm text-[#5B6573]">No current subscription.</p>
            )}
            {pending && (
              <p className="rounded border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900" data-testid="iadm04-pending-banner">
                Access is unavailable until payment is confirmed. PENDING subscriptions grant zero entitlements.
              </p>
            )}
            <p className="text-sm text-[#5B6573]" data-testid="iadm04-access-flag">
              Commercial access: {data?.accessAllowed ? 'allowed' : 'denied'}
              {data?.reason ? ` (${data.reason})` : ''}
            </p>
          </section>

          <section className="space-y-2" data-testid="iadm04-own-entitlements">
            <h2 className="text-lg font-medium text-[#1B2433]">Entitlements</h2>
            {(data?.entitlements || []).length === 0 ? (
              <p className="text-sm text-[#5B6573]">None available for the current state.</p>
            ) : (
              <ul className="space-y-1 text-sm">
                {(data?.entitlements || []).map((e) => (
                  <li key={e.entitlementKey}>
                    {e.entitlementKey} — limit {e.limitValue} {e.limitUnit || ''}
                    {e.enabled === false ? ' (disabled)' : ''}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}
    </main>
  );
}
