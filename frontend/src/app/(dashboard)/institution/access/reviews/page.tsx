'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type ReviewItem = {
  id: string;
  roleAssignmentId?: string;
  roleCode?: string;
  subjectUserId?: string;
  grantedBy?: string;
  validFrom?: string;
  validTo?: string;
  decision?: string | null;
  note?: string;
  source?: string;
};

type ReviewDetail = {
  id: string;
  cycleName?: string;
  status?: string;
  items?: ReviewItem[];
  delegations?: Array<{ id: string; roleCode?: string; toUserId?: string }>;
  autoRevoke?: boolean;
  cadence?: string;
  notificationMessage?: string;
};

/**
 * IADM-03 US-032 — manual access review desk (no auto-revoke, cadence open).
 */
export default function AccessReviewsPage() {
  const [list, setList] = useState<Array<{ id: string; cycleName?: string; status?: string }>>([]);
  const [detail, setDetail] = useState<ReviewDetail | null>(null);
  const [cycleName, setCycleName] = useState('S4 manual review');
  const [note, setNote] = useState('');
  const [flash, setFlash] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function refreshList() {
    const res = await fetch('/api/institution/access/reviews');
    const body = await res.json();
    if (!res.ok) throw new Error(body.detail || body.errorCode || 'List failed');
    setList(Array.isArray(body.items) ? body.items : []);
  }

  async function openReview(id: string) {
    const res = await fetch(`/api/institution/access/reviews/${encodeURIComponent(id)}`);
    const body = await res.json();
    if (!res.ok) throw new Error(body.detail || body.errorCode || 'Load failed');
    setDetail(body);
  }

  useEffect(() => {
    void refreshList().catch((e) => setError(e instanceof Error ? e.message : 'Failed'));
  }, []);

  async function onStart() {
    setBusy(true);
    setError(null);
    setFlash(null);
    try {
      const res = await fetch('/api/institution/access/reviews', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ cycleName, idempotencyKey: crypto.randomUUID() }),
      });
      const body = await res.json();
      if (!res.ok) throw new Error(body.detail || body.errorCode || 'Start failed');
      setFlash(
        `Started ${body.id}; items=${body.itemCount}; autoRevoke=${String(body.autoRevoke)}; ${body.notificationMessage || ''}`
      );
      await refreshList();
      if (body.id) await openReview(body.id);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Start failed');
    } finally {
      setBusy(false);
    }
  }

  async function onDecide(itemId: string, decision: 'CERTIFY' | 'FLAG' | 'REQUEST_REVOCATION') {
    if (!detail?.id) return;
    setBusy(true);
    setError(null);
    setFlash(null);
    try {
      const res = await fetch(
        `/api/institution/access/reviews/${encodeURIComponent(detail.id)}/items/${encodeURIComponent(itemId)}/decide`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ decision, note: note || `${decision} via S4 desk` }),
        }
      );
      const body = await res.json();
      if (!res.ok) throw new Error(body.detail || body.errorCode || 'Decide failed');
      setFlash(body.message || `${decision} recorded; autoRevoke=${String(body.autoRevoke)}`);
      await openReview(detail.id);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Decide failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-8 p-6" data-testid="iadm03-reviews-desk">
      <header className="space-y-2">
        <Link href="/institution/access" className="text-sm text-slate-500 hover:underline">
          ← Institution access
        </Link>
        <h1 className="text-3xl font-semibold text-slate-900">Access reviews</h1>
        <p className="max-w-2xl text-slate-600">
          Manual campaigns only. Certify, flag, or request revocation — nothing is revoked automatically.
        </p>
      </header>

      {error ? (
        <p className="rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800" data-testid="iadm03-reviews-error">
          {error}
        </p>
      ) : null}
      {flash ? (
        <p className="rounded border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900" data-testid="iadm03-reviews-flash">
          {flash}
        </p>
      ) : null}

      <section className="space-y-3 rounded border border-slate-200 p-4" data-testid="iadm03-reviews-start">
        <label className="block text-sm">
          Cycle name
          <input
            className="mt-1 w-full rounded border border-slate-300 px-2 py-2"
            value={cycleName}
            onChange={(e) => setCycleName(e.target.value)}
            data-testid="iadm03-review-cycle"
          />
        </label>
        <button
          type="button"
          disabled={busy || !cycleName.trim()}
          onClick={() => void onStart()}
          className="rounded bg-slate-900 px-4 py-2 text-sm text-white disabled:opacity-40"
          data-testid="iadm03-review-start"
        >
          Start review
        </button>
      </section>

      <section className="space-y-2">
        <h2 className="text-lg font-semibold">Campaigns</h2>
        <ul className="divide-y divide-slate-200 rounded border border-slate-200" data-testid="iadm03-reviews-list">
          {list.map((r) => (
            <li key={r.id} className="flex items-center justify-between p-3">
              <span>
                {r.cycleName} · {r.status}
              </span>
              <button
                type="button"
                className="text-sm underline"
                data-testid={`iadm03-review-open-${r.id}`}
                onClick={() => void openReview(r.id).catch((e) => setError(e.message))}
              >
                Open
              </button>
            </li>
          ))}
        </ul>
      </section>

      {detail ? (
        <section className="space-y-3" data-testid="iadm03-review-detail">
          <h2 className="text-lg font-semibold">
            {detail.cycleName} · {detail.status}
          </h2>
          <p className="text-sm text-slate-600">
            cadence={detail.cadence || 'MANUAL_ONLY'}; autoRevoke={String(detail.autoRevoke)}
          </p>
          <label className="block text-sm">
            Decision note
            <input
              className="mt-1 w-full rounded border border-slate-300 px-2 py-2"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              data-testid="iadm03-review-note"
            />
          </label>
          <ul className="space-y-3">
            {(detail.items || []).map((it) => (
              <li key={it.id} className="rounded border border-slate-200 p-3" data-testid={`iadm03-review-item-${it.id}`}>
                <p className="font-medium">
                  {it.roleCode} · {it.subjectUserId}
                </p>
                <p className="text-sm text-slate-600">
                  assignment={it.roleAssignmentId}; decision={it.decision || '—'}; source={it.source}
                </p>
                {!it.decision ? (
                  <div className="mt-2 flex flex-wrap gap-2">
                    <button
                      type="button"
                      disabled={busy}
                      className="rounded border border-slate-300 px-3 py-1 text-sm"
                      data-testid={`iadm03-review-certify-${it.id}`}
                      onClick={() => void onDecide(it.id, 'CERTIFY')}
                    >
                      Certify
                    </button>
                    <button
                      type="button"
                      disabled={busy}
                      className="rounded border border-slate-300 px-3 py-1 text-sm"
                      data-testid={`iadm03-review-flag-${it.id}`}
                      onClick={() => void onDecide(it.id, 'FLAG')}
                    >
                      Flag
                    </button>
                    <button
                      type="button"
                      disabled={busy}
                      className="rounded bg-slate-900 px-3 py-1 text-sm text-white"
                      data-testid={`iadm03-review-revoke-req-${it.id}`}
                      onClick={() => void onDecide(it.id, 'REQUEST_REVOCATION')}
                    >
                      Request revocation
                    </button>
                  </div>
                ) : null}
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </div>
  );
}
