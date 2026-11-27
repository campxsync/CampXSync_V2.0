'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type ReviewItem = {
  id: string;
  subjectUserId: string;
  roleCode: string;
  owningService: string;
  scopeType: string;
  decision: string;
  revocationRequestId?: string | null;
};

type Campaign = {
  id: string;
  title: string;
  scopeType: string;
  dueAt: string;
  cadenceSource: string;
  status: string;
  items: ReviewItem[];
};

export default function AccessReviewsPage() {
  const [campaigns, setCampaigns] = useState<Campaign[]>([]);
  const [title, setTitle] = useState('Q4 Access Review');
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  async function load() {
    setErr(null);
    const res = await fetch('/api/platform/identity/v2/access-reviews');
    const data = await res.json();
    if (!res.ok) { setErr(data.detail || data.errorCode); return; }
    setCampaigns(Array.isArray(data.campaigns) ? data.campaigns : []);
  }

  useEffect(() => { load(); }, []);

  async function createCampaign(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null); setErr(null);
    const res = await fetch('/api/platform/identity/v2/access-reviews', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title, scopeType: 'PLATFORM' }),
    });
    const data = await res.json();
    if (!res.ok) { setErr(`${data.errorCode}: ${data.detail}`); return; }
    setMsg(`Created campaign ${data.id} (${data.itemCount} items, ${data.cadenceSource})`);
    await load();
  }

  async function decideItem(campaignId: string, itemId: string, decision: 'ATTEST' | 'REVOKE' | 'EXCEPTION') {
    setMsg(null); setErr(null);
    const body = decision === 'EXCEPTION'
      ? { decision, reason: 'Temporary exception', exceptionOwner: 'R01', exceptionExpiresAt: new Date(Date.now() + 86400000 * 30).toISOString() }
      : { decision, reason: `Review decision: ${decision}` };
    const res = await fetch(`/api/platform/identity/v2/access-reviews/${campaignId}/items/${itemId}/decision`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    const data = await res.json();
    if (!res.ok) { setErr(`${data.errorCode}: ${data.detail}`); return; }
    setMsg(`Item ${itemId} -> ${data.decision}${data.revocationRequestId ? ` (revocation request ${data.revocationRequestId})` : ''}`);
    await load();
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6" data-testid="iadm03-reviews-desk">
      <header className="flex items-center justify-between">
        <div>
          <p className="text-xs uppercase tracking-wide text-slate-500">IADM-03 · Access Review Campaigns</p>
          <h1 className="text-2xl font-semibold text-slate-900">Access Reviews</h1>
        </div>
        <Link href="/platform/identity" className="rounded border border-slate-300 px-3 py-1.5 text-sm">Back to IADM-03</Link>
      </header>
      {msg && <div className="rounded border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-900" data-testid="iadm03-review-msg">{msg}</div>}
      {err && <div className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800" data-testid="iadm03-review-err">{err}</div>}

      <form onSubmit={createCampaign} className="flex gap-3 rounded border border-slate-200 bg-white p-4" data-testid="iadm03-review-create-form">
        <input type="text" value={title} onChange={(e) => setTitle(e.target.value)} className="flex-1 rounded border border-slate-300 px-3 py-2 text-sm" data-testid="iadm03-review-title" required />
        <button type="submit" className="rounded bg-slate-900 px-4 py-2 text-sm text-white" data-testid="iadm03-review-create-btn">Create Campaign (R01)</button>
      </form>

      <div className="space-y-4">
        {campaigns.map((c) => (
          <section key={c.id} className="rounded border border-slate-200 bg-white p-4" data-testid={`iadm03-campaign-${c.id}`}>
            <div className="flex justify-between text-sm font-semibold">
              <span>{c.title} ({c.scopeType})</span>
              <span className="text-xs text-slate-500">{c.cadenceSource} · Due {c.dueAt}</span>
            </div>
            <div className="mt-3 space-y-2">
              {c.items.slice(0, 10).map((it) => (
                <div key={it.id} className="flex flex-wrap items-center justify-between gap-2 border-t pt-2 text-xs">
                  <span className="font-mono">{it.subjectUserId} · {it.roleCode} · <strong>{it.decision}</strong></span>
                  <div className="flex gap-2">
                    <button type="button" onClick={() => decideItem(c.id, it.id, 'ATTEST')} className="rounded border px-2 py-1" data-testid={`iadm03-attest-${it.id}`}>Attest</button>
                    <button type="button" onClick={() => decideItem(c.id, it.id, 'REVOKE')} className="rounded bg-amber-700 px-2 py-1 text-white" data-testid={`iadm03-revoke-item-${it.id}`}>Revoke</button>
                    <button type="button" onClick={() => decideItem(c.id, it.id, 'EXCEPTION')} className="rounded bg-slate-700 px-2 py-1 text-white" data-testid={`iadm03-exception-${it.id}`}>Exception</button>
                  </div>
                </div>
              ))}
            </div>
          </section>
        ))}
      </div>
    </div>
  );
}
