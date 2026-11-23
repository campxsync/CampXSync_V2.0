'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type Policy = {
  policyCode: string;
  name?: string;
  category?: string;
  enforcementMode?: string;
};

/**
 * Policy catalogue (US-014 read) — propose draft via API from key desk later; list is real seed data.
 */
export default function ConfigurationPoliciesPage() {
  const [items, setItems] = useState<Policy[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [code, setCode] = useState('IADM02_POL_PLATFORM_BASELINE');
  const [reason, setReason] = useState('');
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      const res = await fetch('/api/platform/configuration?view=policies');
      const body = await res.json();
      if (!res.ok) {
        setError(body.detail || 'Failed to load policies');
        return;
      }
      setItems(Array.isArray(body.items) ? body.items : []);
    })();
  }, []);

  async function propose() {
    setMessage(null);
    const res = await fetch('/api/platform/configuration/policy-drafts', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        policyCode: code,
        proposedValue: '{"schemaVersion":1,"note":"proposed change"}',
        reason,
        idempotencyKey: `ui-policy-${Date.now()}`,
      }),
    });
    const body = await res.json();
    if (!res.ok) {
      setMessage(body.detail || body.errorCode || 'Failed');
      return;
    }
    setMessage(`Policy draft ${body.draftId} created (${body.status}).`);
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6" data-testid="iadm02-policies-desk">
      <Link href="/platform/configuration" className="text-sm underline">
        ← Catalogue
      </Link>
      <h1 className="text-2xl font-semibold">Policy catalogue</h1>
      {error && <p className="text-red-700">{error}</p>}
      <ul className="space-y-2" data-testid="iadm02-policies-list">
        {items.map((p) => (
          <li key={p.policyCode} className="rounded border bg-white p-3 text-sm">
            <p className="font-medium">{p.policyCode}</p>
            <p className="text-slate-600">
              {p.name} · {p.category} · {p.enforcementMode}
            </p>
          </li>
        ))}
      </ul>
      <section className="space-y-3 rounded border bg-white p-4" data-testid="iadm02-policy-draft">
        <h2 className="font-medium">Propose policy draft (R01)</h2>
        <input
          className="w-full rounded border px-3 py-2 text-sm"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          data-testid="iadm02-policy-code"
        />
        <textarea
          className="w-full rounded border px-3 py-2 text-sm"
          rows={2}
          placeholder="Reason"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          data-testid="iadm02-policy-reason"
        />
        <button
          type="button"
          className="rounded bg-slate-900 px-3 py-2 text-sm text-white disabled:opacity-50"
          disabled={!reason.trim()}
          onClick={propose}
          data-testid="iadm02-policy-submit"
        >
          Create policy draft
        </button>
        {message && <p data-testid="iadm02-policy-message">{message}</p>}
      </section>
    </div>
  );
}
