'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';

type Detail = {
  key?: string;
  dataType?: string;
  domain?: string;
  description?: string;
  activeValue?: string | number | null;
  defaultValue?: string | number | null;
  activeVersionLabel?: string | null;
  constraints?: string;
};

/**
 * IADM-02 key detail (US-009) + create draft (US-011).
 */
export default function ConfigurationKeyPage() {
  const params = useParams();
  const key = decodeURIComponent(String(params.key || ''));
  const [detail, setDetail] = useState<Detail | null>(null);
  const [proposedValue, setProposedValue] = useState('');
  const [reason, setReason] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [draftId, setDraftId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await fetch(
          `/api/platform/configuration/keys/${encodeURIComponent(key)}`
        );
        const body = await res.json();
        if (!res.ok) throw new Error(body.detail || body.errorCode || 'Not found');
        if (!cancelled) {
          setDetail(body);
          setProposedValue(String(body.activeValue ?? body.defaultValue ?? ''));
        }
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Failed to load key');
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [key]);

  async function createDraft() {
    setMessage(null);
    setError(null);
    const res = await fetch('/api/platform/configuration/drafts', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        key,
        proposedValue,
        reason,
        idempotencyKey: `ui-draft-${key}-${Date.now()}`,
      }),
    });
    const body = await res.json();
    if (!res.ok) {
      setError(body.detail || body.errorCode || 'Draft failed');
      return;
    }
    setDraftId(body.draftId || null);
    setMessage(`Draft ${body.draftId} created (${body.status}).`);
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6 p-6" data-testid="iadm02-key-detail">
      <Link href="/platform/configuration" className="text-sm text-slate-600 underline">
        ← Catalogue
      </Link>
      <h1 className="text-2xl font-semibold" data-testid="iadm02-key-title">
        {key}
      </h1>
      {error && (
        <p className="text-red-700" data-testid="iadm02-key-error">
          {error}
        </p>
      )}
      {detail && (
        <dl className="grid gap-3 rounded border border-slate-200 bg-white p-4 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-slate-500">Domain</dt>
            <dd>{detail.domain}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Type</dt>
            <dd>{detail.dataType}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Active value</dt>
            <dd data-testid="iadm02-key-active-value">{detail.activeValue ?? '—'}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Constraints</dt>
            <dd>{detail.constraints || '—'}</dd>
          </div>
          <div className="sm:col-span-2">
            <dt className="text-slate-500">Description</dt>
            <dd>{detail.description}</dd>
          </div>
        </dl>
      )}

      {key !== 'iam.delegation.max_validity_days' && (
        <section className="space-y-3 rounded border border-slate-200 bg-white p-4" data-testid="iadm02-create-draft">
          <h2 className="text-lg font-medium">Create draft</h2>
          <label className="block text-sm">
            Proposed value
            <input
              data-testid="iadm02-draft-value"
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={proposedValue}
              onChange={(e) => setProposedValue(e.target.value)}
            />
          </label>
          <label className="block text-sm">
            Reason (required)
            <textarea
              data-testid="iadm02-draft-reason"
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </label>
          <button
            type="button"
            data-testid="iadm02-draft-submit"
            className="rounded bg-slate-900 px-4 py-2 text-sm text-white disabled:opacity-50"
            disabled={!reason.trim()}
            onClick={createDraft}
          >
            Create draft
          </button>
          {message && (
            <p className="text-green-800" data-testid="iadm02-draft-success">
              {message}
            </p>
          )}
          {draftId && (
            <Link
              href="/platform/configuration/drafts"
              className="text-sm underline"
              data-testid="iadm02-goto-drafts"
            >
              Open drafts desk
            </Link>
          )}
        </section>
      )}
    </div>
  );
}
