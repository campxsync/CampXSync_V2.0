'use client';

import { useEffect, useState } from 'react';

type PlanItem = {
  id: string;
  planCode: string;
  name?: string;
  versionNo?: number;
  planStatus?: string;
  priceMinorUnits?: number;
  currencyCode?: string;
  billingCycle?: string;
  demoLabel?: string;
};

type SubItem = {
  id: string;
  tenantName?: string;
  tenantId?: string;
  planCode?: string;
  status?: string;
  startDate?: string;
  endDate?: string;
  trialEndsOn?: string;
  demoLabel?: string;
};

/**
 * IADM-04 platform subscription desk (US-006…012) — Super Admin / System Admin operational view.
 */
export default function PlatformBillingPage() {
  const [plans, setPlans] = useState<PlanItem[]>([]);
  const [subs, setSubs] = useState<SubItem[]>([]);
  const [invoices, setInvoices] = useState<
    Array<{ id: string; status?: string; amountMinorUnits?: number; currencyCode?: string; internalRef?: string }>
  >([]);
  const [planChanges, setPlanChanges] = useState<
    Array<{ id: string; status?: string; tenantId?: string; requestedPlanId?: string; effectiveOn?: string }>
  >([]);
  const [onlinePayments, setOnlinePayments] = useState<
    Array<{ id: string; status?: string; invoiceId?: string; amountMinorUnits?: number; providerRef?: string }>
  >([]);
  const [failedCallbacks, setFailedCallbacks] = useState<
    Array<{ id: string; processingStatus?: string; eventId?: string; errorDetail?: string; retryCount?: number }>
  >([]);
  const [renewals, setRenewals] = useState<
    Array<{ id: string; status?: string; bucket?: string; planCode?: string; tenantName?: string; endDate?: string }>
  >([]);
  const [report, setReport] = useState<Record<string, unknown> | null>(null);
  const [policy, setPolicy] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState('');
  const [checkResult, setCheckResult] = useState<string | null>(null);
  const [actionMsg, setActionMsg] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const qs = statusFilter ? `&status=${encodeURIComponent(statusFilter)}` : '';
      const [planRes, subRes, polRes, invRes, pcRes, onlineRes, failRes, renRes, repRes] =
        await Promise.all([
          fetch('/api/platform/billing?view=plans'),
          fetch(`/api/platform/billing?view=subscriptions${qs}`),
          fetch('/api/platform/billing?view=trial-policy'),
          fetch('/api/platform/billing?view=invoices'),
          fetch('/api/platform/billing?view=plan-changes'),
          fetch('/api/platform/billing?view=online-payments'),
          fetch('/api/platform/billing?view=payment-callback-failures'),
          fetch('/api/platform/billing?view=renewals'),
          fetch('/api/platform/billing?view=reports'),
        ]);
      const planBody = await planRes.json();
      const subBody = await subRes.json();
      const polBody = await polRes.json();
      const invBody = await invRes.json();
      const pcBody = await pcRes.json();
      const onlineBody = await onlineRes.json().catch(() => ({}));
      const failBody = await failRes.json().catch(() => ({}));
      const renBody = await renRes.json().catch(() => ({}));
      const repBody = await repRes.json().catch(() => ({}));
      if (!planRes.ok) throw new Error(planBody.detail || 'Plans unavailable');
      setPlans(Array.isArray(planBody.items) ? planBody.items : []);
      setSubs(Array.isArray(subBody.items) ? subBody.items : []);
      setInvoices(Array.isArray(invBody.items) ? invBody.items : []);
      setPlanChanges(Array.isArray(pcBody.items) ? pcBody.items : []);
      setOnlinePayments(Array.isArray(onlineBody.items) ? onlineBody.items : []);
      setFailedCallbacks(Array.isArray(failBody.items) ? failBody.items : []);
      setRenewals(Array.isArray(renBody.items) ? renBody.items : []);
      setReport(repBody.summary || null);
      setPolicy(polBody.policy || null);
      if (!subRes.ok) setError(subBody.detail || 'Subscriptions partially unavailable');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load billing desk');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter]);

  const runGateCheck = async () => {
    const first = subs.find((s) => s.tenantId);
    if (!first?.tenantId) {
      setCheckResult('No subscription row to probe.');
      return;
    }
    const res = await fetch('/api/platform/billing?view=entitlements-check', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        instituteId: first.tenantId,
        featureKey: 'billing.demo.students_portal',
      }),
    });
    // POST is on same route with view query — use dedicated path via POST body on route
    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
      // fallback: call check via POST with view param
      const res2 = await fetch('/api/platform/billing?view=entitlements-check', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          instituteId: first.tenantId,
          featureKey: 'billing.demo.students_portal',
        }),
      });
      const body2 = await res2.json().catch(() => ({}));
      setCheckResult(JSON.stringify(body2));
      return;
    }
    setCheckResult(JSON.stringify(body));
  };

  return (
    <main className="mx-auto max-w-6xl space-y-8 p-6" data-testid="iadm04-platform-desk">
      <header className="space-y-2">
        <h1 className="text-2xl font-semibold text-[#1B2433]" data-testid="iadm04-platform-title">
          Billing & subscriptions
        </h1>
        <p className="text-sm text-[#5B6573]">
          IADM-04 plan catalogue, institute subscriptions, and DEMO trial policy. Monolith billing remains
          until cutover.
        </p>
      </header>

      {loading && (
        <p className="text-sm text-[#5B6573]" data-testid="iadm04-loading">
          Loading…
        </p>
      )}
      {error && (
        <p className="text-sm text-red-700" data-testid="iadm04-error">
          {error}
        </p>
      )}

      <section className="space-y-3" data-testid="iadm04-plan-catalogue">
        <h2 className="text-lg font-medium text-[#1B2433]">Plan & package catalogue</h2>
        {plans.length === 0 && !loading ? (
          <p className="text-sm text-[#5B6573]" data-testid="iadm04-plans-empty">
            No plans yet.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-left text-sm">
              <thead>
                <tr className="border-b border-[#E3E8EF] text-[#5B6573]">
                  <th className="py-2 pr-4">Code</th>
                  <th className="py-2 pr-4">Name</th>
                  <th className="py-2 pr-4">Version</th>
                  <th className="py-2 pr-4">Status</th>
                  <th className="py-2 pr-4">Price (minor)</th>
                  <th className="py-2">Label</th>
                </tr>
              </thead>
              <tbody>
                {plans.map((p) => (
                  <tr key={p.id} className="border-b border-[#F0F3F7]" data-testid={`iadm04-plan-${p.planCode}`}>
                    <td className="py-2 pr-4 font-medium">{p.planCode}</td>
                    <td className="py-2 pr-4">{p.name}</td>
                    <td className="py-2 pr-4">{p.versionNo}</td>
                    <td className="py-2 pr-4">{p.planStatus}</td>
                    <td className="py-2 pr-4">
                      {p.priceMinorUnits} {p.currencyCode}
                    </td>
                    <td className="py-2">{p.demoLabel || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="space-y-3" data-testid="iadm04-subscription-overview">
        <div className="flex flex-wrap items-center gap-3">
          <h2 className="text-lg font-medium text-[#1B2433]">Subscription overview</h2>
          <select
            className="h-9 rounded border border-[#E3E8EF] px-2 text-sm"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            data-testid="iadm04-sub-status-filter"
          >
            <option value="">All statuses</option>
            <option value="TRIAL">TRIAL</option>
            <option value="PENDING">PENDING</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="GRACE">GRACE</option>
            <option value="EXPIRED">EXPIRED</option>
            <option value="SUSPENDED">SUSPENDED</option>
            <option value="CANCELLED">CANCELLED</option>
          </select>
          <button
            type="button"
            className="rounded border border-[#E3E8EF] px-3 py-1.5 text-sm"
            onClick={() => void runGateCheck()}
            data-testid="iadm04-entitlement-check"
          >
            Probe entitlement check
          </button>
        </div>
        {checkResult && (
          <pre className="overflow-x-auto rounded bg-[#F7F9FC] p-3 text-xs" data-testid="iadm04-check-result">
            {checkResult}
          </pre>
        )}
        {subs.length === 0 && !loading ? (
          <p className="text-sm text-[#5B6573]" data-testid="iadm04-subs-empty">
            No subscriptions match.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-left text-sm">
              <thead>
                <tr className="border-b border-[#E3E8EF] text-[#5B6573]">
                  <th className="py-2 pr-4">Institute</th>
                  <th className="py-2 pr-4">Plan</th>
                  <th className="py-2 pr-4">Status</th>
                  <th className="py-2 pr-4">Start</th>
                  <th className="py-2">Trial ends</th>
                </tr>
              </thead>
              <tbody>
                {subs.map((s) => (
                  <tr key={s.id} className="border-b border-[#F0F3F7]" data-testid={`iadm04-sub-${s.id}`}>
                    <td className="py-2 pr-4">{s.tenantName || s.tenantId}</td>
                    <td className="py-2 pr-4">{s.planCode}</td>
                    <td className="py-2 pr-4">{s.status}</td>
                    <td className="py-2 pr-4">{s.startDate || '—'}</td>
                    <td className="py-2">{s.trialEndsOn || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="space-y-3" data-testid="iadm04-trial-policy">
        <h2 className="text-lg font-medium text-[#1B2433]">Trial & renewal policy (DEMO)</h2>
        {policy ? (
          <ul className="space-y-1 text-sm text-[#1B2433]">
            {Object.entries(policy).map(([k, v]) => (
              <li key={k}>
                <span className="font-mono text-xs text-[#5B6573]">{k}</span>: {String(v)}
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-[#5B6573]">Policy not loaded.</p>
        )}
      </section>

      <section className="space-y-3" data-testid="iadm04-invoice-desk">
        <div className="flex flex-wrap items-center gap-3">
          <h2 className="text-lg font-medium text-[#1B2433]">Invoices</h2>
          <button
            type="button"
            className="rounded border border-[#E3E8EF] px-3 py-1.5 text-sm"
            data-testid="iadm04-generate-invoice"
            onClick={async () => {
              const sub = subs.find((s) => s.status === 'ACTIVE' || s.status === 'TRIAL' || s.status === 'PENDING');
              if (!sub?.id) {
                setActionMsg('No subscription available to invoice.');
                return;
              }
              const res = await fetch('/api/platform/billing?view=invoices', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                  subscriptionId: sub.id,
                  idempotencyKey: `desk-inv-${sub.id}-${Date.now()}`,
                }),
              });
              const body = await res.json().catch(() => ({}));
              setActionMsg(res.ok ? `Invoice ${body.internalRef || body.id} ${body.status}` : body.detail || 'failed');
              void load();
            }}
          >
            Generate invoice (first listed sub)
          </button>
        </div>
        {actionMsg && (
          <p className="text-sm text-[#5B6573]" data-testid="iadm04-action-msg">
            {actionMsg}
          </p>
        )}
        {invoices.length === 0 ? (
          <p className="text-sm text-[#5B6573]">No invoices yet.</p>
        ) : (
          <ul className="space-y-1 text-sm" data-testid="iadm04-invoice-list">
            {invoices.slice(0, 20).map((inv) => (
              <li key={inv.id}>
                {inv.internalRef || inv.id.slice(0, 8)} — {inv.status} — {inv.amountMinorUnits}{' '}
                {inv.currencyCode}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="space-y-3" data-testid="iadm04-plan-change-desk">
        <h2 className="text-lg font-medium text-[#1B2433]">Plan-change requests</h2>
        {planChanges.length === 0 ? (
          <p className="text-sm text-[#5B6573]">No plan-change requests.</p>
        ) : (
          <ul className="space-y-2 text-sm" data-testid="iadm04-plan-change-list">
            {planChanges.slice(0, 20).map((pc) => (
              <li key={pc.id} className="flex flex-wrap items-center gap-2">
                <span>
                  {pc.id.slice(0, 8)} — {pc.status} — effective {pc.effectiveOn}
                </span>
                {pc.status === 'PENDING' && (
                  <>
                    <button
                      type="button"
                      className="rounded border border-[#E3E8EF] px-2 py-1 text-xs"
                      data-testid={`iadm04-plan-change-approve-${pc.id}`}
                      onClick={async () => {
                        const res = await fetch(
                          `/api/platform/billing?view=plan-changes-decide&id=${encodeURIComponent(pc.id)}`,
                          {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ decision: 'APPROVED', reason: 'S2 desk approve' }),
                          }
                        );
                        const body = await res.json().catch(() => ({}));
                        setActionMsg(res.ok ? `Approved ${pc.id.slice(0, 8)}` : body.detail || 'deny');
                        void load();
                      }}
                    >
                      Approve
                    </button>
                    <button
                      type="button"
                      className="rounded border border-[#E3E8EF] px-2 py-1 text-xs"
                      onClick={async () => {
                        const res = await fetch(
                          `/api/platform/billing?view=plan-changes-decide&id=${encodeURIComponent(pc.id)}`,
                          {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ decision: 'REJECTED', reason: 'S2 desk reject' }),
                          }
                        );
                        const body = await res.json().catch(() => ({}));
                        setActionMsg(res.ok ? `Rejected ${pc.id.slice(0, 8)}` : body.detail || 'deny');
                        void load();
                      }}
                    >
                      Reject
                    </button>
                  </>
                )}
              </li>
            ))}
          </ul>
        )}
        <p className="text-xs text-[#5B6573]">Proration/credits not calculated (D9 pending).</p>
      </section>

      <section className="space-y-3" data-testid="iadm04-offline-desk">
        <h2 className="text-lg font-medium text-[#1B2433]">Offline payment & reconciliation</h2>
        <p className="text-sm text-[#5B6573]">
          Record evidence (R01-A) then reconcile with a different Super Admin (R01-B). Same actor is denied.
        </p>
        <button
          type="button"
          className="rounded border border-[#E3E8EF] px-3 py-1.5 text-sm"
          data-testid="iadm04-offline-record"
          onClick={async () => {
            const inv = invoices.find((i) => i.status === 'ISSUED' || i.status === 'DUE');
            if (!inv) {
              setActionMsg('Generate an ISSUED invoice first.');
              return;
            }
            const res = await fetch('/api/platform/billing?view=offline-payments', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                invoiceId: inv.id,
                instrumentType: 'BANK_TRANSFER',
                reference: `DEMO-UTR-${Date.now()}`,
                amountMinorUnits: inv.amountMinorUnits,
                currencyCode: inv.currencyCode || 'INR',
                notes: 'S2 desk evidence',
              }),
            });
            const body = await res.json().catch(() => ({}));
            setActionMsg(
              res.ok
                ? `Evidence ${body.id} recorded (invoice not paid yet)`
                : body.detail || 'evidence failed'
            );
          }}
        >
          Record offline evidence on first open invoice
        </button>
      </section>

      <section className="space-y-3" data-testid="iadm04-online-payment-desk">
        <h2 className="text-lg font-medium text-[#1B2433]">Online payment & reconciliation</h2>
        <p className="text-sm text-[#5B6573]">
          DEV simulator only — initiate is not settlement. Signed callback confirms payment.
        </p>
        <button
          type="button"
          className="rounded border border-[#E3E8EF] px-3 py-1.5 text-sm"
          data-testid="iadm04-online-initiate"
          onClick={async () => {
            const inv = invoices.find((i) => i.status === 'ISSUED' || i.status === 'DUE');
            if (!inv) {
              setActionMsg('Generate an ISSUED invoice first.');
              return;
            }
            const res = await fetch('/api/platform/billing?view=online-initiate', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                invoiceId: inv.id,
                idempotencyKey: `desk-online-${inv.id}-${Date.now()}`,
                simulatorOutcome: 'SUCCESS',
              }),
            });
            const body = await res.json().catch(() => ({}));
            setActionMsg(
              res.ok
                ? `Online intent ${body.id?.slice?.(0, 8) || body.id} ${body.status} (adapter=${body.adapterOutcome || 'n/a'})`
                : `${body.errorCode || 'ERROR'}: ${body.detail || body.message || 'online initiate failed'}`
            );
            void load();
          }}
        >
          Initiate online payment (DEV simulator)
        </button>
        <p className="text-xs text-[#5B6573]" data-testid="iadm04-online-initiate-feedback">
          {actionMsg}
        </p>
        {onlinePayments.length === 0 ? (
          <p className="text-sm text-[#5B6573]">No online payment intents yet.</p>
        ) : (
          <ul className="space-y-2 text-sm" data-testid="iadm04-online-payment-list">
            {onlinePayments.slice(0, 20).map((op) => (
              <li key={op.id} className="flex flex-wrap items-center gap-2">
                <span>
                  {op.id.slice(0, 8)} — {op.status} — {op.amountMinorUnits} — {op.providerRef || 'no-ref'}
                </span>
                {(op.status === 'MISMATCHED' || op.status === 'UNMATCHED' || op.status === 'FAILED') && (
                  <button
                    type="button"
                    className="rounded border border-[#E3E8EF] px-2 py-1 text-xs"
                    data-testid={`iadm04-online-reconcile-${op.id}`}
                    onClick={async () => {
                      const res = await fetch(
                        `/api/platform/billing?view=online-reconcile&id=${encodeURIComponent(op.id)}`,
                        {
                          method: 'POST',
                          headers: { 'Content-Type': 'application/json' },
                          body: JSON.stringify({
                            decision: op.status === 'FAILED' ? 'MARK_FAILED' : 'FORCE_MATCH',
                            reason: 'S3 desk reconcile',
                          }),
                        }
                      );
                      const body = await res.json().catch(() => ({}));
                      setActionMsg(res.ok ? `Reconciled ${op.id.slice(0, 8)}` : body.detail || 'deny');
                      void load();
                    }}
                  >
                    Reconcile
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="space-y-3" data-testid="iadm04-ops-callback-desk">
        <h2 className="text-lg font-medium text-[#1B2433]">System ops — failed callbacks</h2>
        <p className="text-sm text-[#5B6573]">
          System Admin may inspect and retry failed callbacks only (no commercial authority).
        </p>
        {failedCallbacks.length === 0 ? (
          <p className="text-sm text-[#5B6573]" data-testid="iadm04-callback-failures-empty">
            No failed callbacks.
          </p>
        ) : (
          <ul className="space-y-2 text-sm" data-testid="iadm04-callback-failures">
            {failedCallbacks.slice(0, 20).map((cb) => (
              <li key={cb.id} className="flex flex-wrap items-center gap-2">
                <span>
                  {cb.eventId || cb.id.slice(0, 8)} — {cb.processingStatus} — retries {cb.retryCount ?? 0}
                </span>
                <button
                  type="button"
                  className="rounded border border-[#E3E8EF] px-2 py-1 text-xs"
                  data-testid={`iadm04-callback-retry-${cb.id}`}
                  onClick={async () => {
                    const res = await fetch(
                      `/api/platform/billing?view=callback-retry&id=${encodeURIComponent(cb.id)}`,
                      { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' }
                    );
                    const body = await res.json().catch(() => ({}));
                    setActionMsg(res.ok ? `Retry queued ${cb.id.slice(0, 8)}` : body.detail || 'deny');
                    void load();
                  }}
                >
                  Retry
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="space-y-3" data-testid="iadm04-renewal-desk">
        <div className="flex flex-wrap items-center gap-3">
          <h2 className="text-lg font-medium text-[#1B2433]">Renewal & expiry</h2>
          <button
            type="button"
            className="rounded border border-[#E3E8EF] px-3 py-1.5 text-sm"
            data-testid="iadm04-jobs-run"
            onClick={async () => {
              const res = await fetch('/api/platform/billing?view=jobs-run', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: '{}',
              });
              const body = await res.json().catch(() => ({}));
              setActionMsg(
                res.ok
                  ? `Jobs ran asOf=${body.asOf} grace=${body.expiryTransitions?.toGrace ?? 0}`
                  : body.detail || 'jobs failed'
              );
              void load();
            }}
          >
            Run scheduled jobs
          </button>
        </div>
        {renewals.length === 0 ? (
          <p className="text-sm text-[#5B6573]">No renewals/expiries in window.</p>
        ) : (
          <ul className="space-y-2 text-sm" data-testid="iadm04-renewal-list">
            {renewals.slice(0, 20).map((r) => (
              <li key={r.id} className="flex flex-wrap items-center gap-2">
                <span>
                  {r.tenantName || r.id.slice(0, 8)} — {r.status} / {r.bucket} — {r.planCode} — ends{' '}
                  {r.endDate || 'n/a'}
                </span>
                {r.bucket === 'DUE' || r.status === 'GRACE' ? (
                  <button
                    type="button"
                    className="rounded border border-[#E3E8EF] px-2 py-1 text-xs"
                    data-testid={`iadm04-renewal-expire-${r.id}`}
                    onClick={async () => {
                      const res = await fetch(
                        `/api/platform/billing?view=renewal-act&id=${encodeURIComponent(r.id)}`,
                        {
                          method: 'POST',
                          headers: { 'Content-Type': 'application/json' },
                          body: JSON.stringify({
                            action: r.status === 'GRACE' ? 'MARK_EXPIRED' : 'MARK_GRACE',
                            reason: 'S4 desk policy action',
                          }),
                        }
                      );
                      const body = await res.json().catch(() => ({}));
                      setActionMsg(res.ok ? `Acted ${r.id.slice(0, 8)} → ${body.toStatus}` : body.detail || 'deny');
                      void load();
                    }}
                  >
                    Apply policy
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="space-y-3" data-testid="iadm04-billing-reports">
        <h2 className="text-lg font-medium text-[#1B2433]">Billing reports</h2>
        {!report ? (
          <p className="text-sm text-[#5B6573]">No report loaded.</p>
        ) : (
          <pre className="overflow-x-auto rounded bg-[#F7F9FC] p-3 text-xs" data-testid="iadm04-report-summary">
            {JSON.stringify(report, null, 2)}
          </pre>
        )}
      </section>
    </main>
  );
}
