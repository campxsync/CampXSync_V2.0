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
  const [policy, setPolicy] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState('');
  const [checkResult, setCheckResult] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const qs = statusFilter ? `&status=${encodeURIComponent(statusFilter)}` : '';
      const [planRes, subRes, polRes] = await Promise.all([
        fetch('/api/platform/billing?view=plans'),
        fetch(`/api/platform/billing?view=subscriptions${qs}`),
        fetch('/api/platform/billing?view=trial-policy'),
      ]);
      const planBody = await planRes.json();
      const subBody = await subRes.json();
      const polBody = await polRes.json();
      if (!planRes.ok) throw new Error(planBody.detail || 'Plans unavailable');
      setPlans(Array.isArray(planBody.items) ? planBody.items : []);
      setSubs(Array.isArray(subBody.items) ? subBody.items : []);
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
    </main>
  );
}
