'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type Version = {
  versionId: string;
  versionLabel?: string;
  status?: string;
  effectiveFrom?: string;
  releaseNotes?: string;
};

/**
 * Version history + compare (IADM-02-US-019).
 */
export default function ConfigurationVersionsPage() {
  const [items, setItems] = useState<Version[]>([]);
  const [left, setLeft] = useState('');
  const [right, setRight] = useState('');
  const [compare, setCompare] = useState<unknown>(null);
  const [error, setError] = useState<string | null>(null);
  const [correctiveMsg, setCorrectiveMsg] = useState<string | null>(null);
  const [correctiveBusy, setCorrectiveBusy] = useState(false);

  useEffect(() => {
    (async () => {
      const res = await fetch('/api/platform/configuration?view=versions');
      const body = await res.json();
      if (!res.ok) {
        setError(body.detail || 'Failed to load versions');
        return;
      }
      const list = Array.isArray(body.items) ? body.items : [];
      setItems(list);
      if (list.length > 0) setLeft(list[0].versionId);
      if (list.length > 1) setRight(list[1].versionId);
      else if (list.length === 1) setRight(list[0].versionId);
    })();
  }, []);

  async function runCompare() {
    setCompare(null);
    if (!left || !right) return;
    const res = await fetch(
      `/api/platform/configuration/versions/compare?left=${encodeURIComponent(left)}&right=${encodeURIComponent(right)}`
    );
    const body = await res.json();
    if (!res.ok) {
      setError(body.detail || 'Compare failed');
      return;
    }
    setCompare(body);
  }

  async function runCorrective(sourceId: string, label?: string) {
    setCorrectiveMsg(null);
    setError(null);
    setCorrectiveBusy(true);
    try {
      const res = await fetch(
        `/api/platform/configuration/versions/${encodeURIComponent(sourceId)}/corrective-publish`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            reason: `Corrective republish from ${label || sourceId} (S4 demo)`,
            releaseNotes: `Restore payload from ${label || sourceId}`,
            idempotencyKey: `s4-corrective-${sourceId}-${Date.now()}`,
          }),
        }
      );
      const body = await res.json();
      if (!res.ok) {
        setError(body.detail || body.errorCode || 'Corrective publish failed');
        return;
      }
      setCorrectiveMsg(
        `Published ${body.versionLabel || 'new version'} from ${body.restoredFromVersionLabel || label}` +
          (body.notificationDeferred ? ' · notification delayed' : '')
      );
      const refresh = await fetch('/api/platform/configuration?view=versions');
      const refreshed = await refresh.json();
      if (refresh.ok && Array.isArray(refreshed.items)) {
        setItems(refreshed.items);
      }
    } finally {
      setCorrectiveBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6" data-testid="iadm02-versions-desk">
      <Link href="/platform/configuration" className="text-sm underline">
        ← Catalogue
      </Link>
      <h1 className="text-2xl font-semibold">Configuration versions</h1>
      {error && (
        <p className="text-red-700" data-testid="iadm02-versions-error">
          {error}
        </p>
      )}
      {correctiveMsg && (
        <p className="text-emerald-800" data-testid="iadm02-corrective-result">
          {correctiveMsg}
        </p>
      )}
      <ul className="space-y-2" data-testid="iadm02-versions-list">
        {items.map((v) => (
          <li key={v.versionId} className="rounded border bg-white p-3 text-sm">
            <p className="font-medium">
              {v.versionLabel} · {v.status}
            </p>
            <p className="text-slate-600">
              {v.effectiveFrom} — {v.releaseNotes}
            </p>
            {(v.status === 'PUBLISHED' || v.status === 'ACTIVE' || !v.status) && (
              <button
                type="button"
                className="mt-2 rounded border border-slate-300 px-3 py-1 text-xs"
                data-testid={`iadm02-corrective-${v.versionId}`}
                disabled={correctiveBusy}
                onClick={() => runCorrective(v.versionId, v.versionLabel)}
              >
                Corrective republish from this version
              </button>
            )}
          </li>
        ))}
      </ul>
      <section className="space-y-3 rounded border bg-white p-4">
        <h2 className="font-medium">Compare</h2>
        <div className="flex flex-wrap gap-2">
          <select
            className="rounded border px-2 py-1 text-sm"
            value={left}
            onChange={(e) => setLeft(e.target.value)}
            data-testid="iadm02-compare-left"
          >
            {items.map((v) => (
              <option key={`L-${v.versionId}`} value={v.versionId}>
                {v.versionLabel}
              </option>
            ))}
          </select>
          <select
            className="rounded border px-2 py-1 text-sm"
            value={right}
            onChange={(e) => setRight(e.target.value)}
            data-testid="iadm02-compare-right"
          >
            {items.map((v) => (
              <option key={`R-${v.versionId}`} value={v.versionId}>
                {v.versionLabel}
              </option>
            ))}
          </select>
          <button
            type="button"
            className="rounded border px-3 py-1 text-sm"
            data-testid="iadm02-compare-run"
            onClick={runCompare}
          >
            Compare
          </button>
        </div>
        {compare != null && (
          <pre
            className="overflow-auto rounded bg-slate-50 p-3 text-xs"
            data-testid="iadm02-compare-result"
          >
            {JSON.stringify(compare, null, 2)}
          </pre>
        )}
      </section>
    </div>
  );
}
