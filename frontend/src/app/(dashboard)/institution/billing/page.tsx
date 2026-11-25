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

          <InstitutionInvoices />
          <InstitutionPlanChange />
        </>
      )}
    </main>
  );
}

function InstitutionInvoices() {
  const [items, setItems] = useState<Array<{ id: string; status?: string; amountMinorUnits?: number; currencyCode?: string }>>([]);
  useEffect(() => {
    (async () => {
      const res = await fetch('/api/institution/billing/invoices', { cache: 'no-store' });
      const body = await res.json().catch(() => ({}));
      if (res.ok && Array.isArray(body.items)) setItems(body.items);
    })();
  }, []);
  return (
    <section className="space-y-2" data-testid="iadm04-own-invoices">
      <h2 className="text-lg font-medium text-[#1B2433]">Invoices</h2>
      {items.length === 0 ? (
        <p className="text-sm text-[#5B6573]">No invoices yet.</p>
      ) : (
        <ul className="space-y-1 text-sm">
          {items.map((inv) => (
            <li key={inv.id}>
              {inv.id.slice(0, 8)} — {inv.status} — {inv.amountMinorUnits} {inv.currencyCode}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function InstitutionPlanChange() {
  const [msg, setMsg] = useState<string | null>(null);
  const [plans, setPlans] = useState<Array<{ id: string; planCode?: string }>>([]);
  const [planId, setPlanId] = useState('');
  useEffect(() => {
    (async () => {
      // Catalogue for R03 request UI — platform plans view may be denied; use BASIC/STANDARD/PREMIUM ids via own subscription desk fallback list from BFF plans is R01-only.
      // Institution requests by plan UUID entered/selected from a minimal known DEMO set loaded via plan-change GET history or hard-coded fetch to platform is blocked.
      // Load plan ids from own subscription entitlements page is insufficient — call plan-changes list only.
      setPlans([]);
    })();
  }, []);
  return (
    <section className="space-y-2" data-testid="iadm04-plan-change-request">
      <h2 className="text-lg font-medium text-[#1B2433]">Request plan change</h2>
      <p className="text-sm text-[#5B6573]">Creates a request only — Super Admin must approve. Not applied directly.</p>
      <label className="block text-sm">
        Requested plan id (UUID)
        <input
          className="mt-1 w-full rounded border border-[#E3E8EF] px-2 py-1.5 text-sm"
          data-testid="iadm04-plan-change-plan-id"
          value={planId}
          onChange={(e) => setPlanId(e.target.value)}
          placeholder="uuid of ACTIVE plan"
        />
      </label>
      <button
        type="button"
        className="rounded border border-[#E3E8EF] px-3 py-1.5 text-sm"
        data-testid="iadm04-plan-change-submit"
        onClick={async () => {
          const res = await fetch('/api/institution/billing/plan-changes', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              requestedPlanId: planId.trim(),
              effectiveOn: new Date().toISOString().slice(0, 10),
              reason: 'Institution desk upgrade/downgrade request',
              idempotencyKey: `inst-pc-${planId.trim()}-${Date.now()}`,
            }),
          });
          const body = await res.json().catch(() => ({}));
          setMsg(res.ok ? `Request ${body.id} ${body.status}` : body.detail || 'failed');
        }}
      >
        Submit request
      </button>
      {msg && (
        <p className="text-sm text-[#5B6573]" data-testid="iadm04-plan-change-msg">
          {msg}
        </p>
      )}
      {plans.length > 0 && <p className="text-xs">{plans.map((p) => p.planCode).join(', ')}</p>}
    </section>
  );
}
