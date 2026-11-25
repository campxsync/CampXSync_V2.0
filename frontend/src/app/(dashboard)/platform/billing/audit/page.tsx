'use client';

import { useEffect, useState } from 'react';

/**
 * IADM-04 Auditor billing evidence desk (US-036) — read-only.
 */
export default function PlatformBillingAuditPage() {
  const [payload, setPayload] = useState<{
    invoices?: Array<{ id: string; status?: string; internalRef?: string; amountMinorUnits?: number }>;
    subscriptionHistory?: Array<{ subscriptionId?: string; fromStatus?: string; toStatus?: string; reason?: string }>;
    readOnly?: boolean;
  } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [denyMsg, setDenyMsg] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      const res = await fetch('/api/platform/billing?view=audit-evidence');
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        setError(body.detail || 'Evidence unavailable');
        return;
      }
      setPayload(body);
    })();
  }, []);

  return (
    <main className="mx-auto max-w-4xl space-y-8 p-6" data-testid="iadm04-audit-evidence-desk">
      <header className="space-y-2">
        <h1 className="text-2xl font-semibold text-[#1B2433]" data-testid="iadm04-audit-title">
          Billing audit evidence
        </h1>
        <p className="text-sm text-[#5B6573]">Read-only invoices, history, and payment references. No mutations.</p>
      </header>
      {error && (
        <p className="text-sm text-red-700" data-testid="iadm04-audit-error">
          {error}
        </p>
      )}
      <section className="space-y-2" data-testid="iadm04-audit-invoices">
        <h2 className="text-lg font-medium">Invoices</h2>
        <ul className="space-y-1 text-sm">
          {(payload?.invoices || []).slice(0, 20).map((inv) => (
            <li key={inv.id}>
              {inv.internalRef || inv.id.slice(0, 8)} — {inv.status} — {inv.amountMinorUnits}
            </li>
          ))}
        </ul>
      </section>
      <section className="space-y-2" data-testid="iadm04-audit-history">
        <h2 className="text-lg font-medium">Subscription history</h2>
        <ul className="space-y-1 text-sm">
          {(payload?.subscriptionHistory || []).slice(0, 20).map((h, i) => (
            <li key={`${h.subscriptionId}-${i}`}>
              {h.subscriptionId?.slice(0, 8)} {h.fromStatus}→{h.toStatus} ({h.reason})
            </li>
          ))}
        </ul>
      </section>
      <section className="space-y-2">
        <p className="text-sm text-[#5B6573]">Mutation controls are absent on this desk.</p>
        <button
          type="button"
          className="rounded border border-[#E3E8EF] px-3 py-1.5 text-sm"
          data-testid="iadm04-audit-mutation-probe"
          onClick={async () => {
            const res = await fetch('/api/platform/billing?view=invoices', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ subscriptionId: '00000000-0000-0000-0000-000000000000' }),
            });
            const body = await res.json().catch(() => ({}));
            setDenyMsg(res.ok ? 'UNEXPECTED_ALLOW' : body.detail || `denied (${res.status})`);
          }}
        >
          Probe mutation (expect deny)
        </button>
        {denyMsg && (
          <p className="text-sm text-[#5B6573]" data-testid="iadm04-audit-deny-msg">
            {denyMsg}
          </p>
        )}
      </section>
    </main>
  );
}
