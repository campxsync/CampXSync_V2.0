'use client';

import { useEffect, useState } from 'react';

type LinkedView = {
  guardianshipId?: string;
  studentId?: string;
  categories?: string[];
  validFrom?: string;
  validTo?: string;
};

/**
 * STM-10-US-021 UI — permitted linked views.
 * Demo note: no dedicated guardian test account in Dev (autonomy H).
 */
export default function GuardianLinkedViewsPage() {
  const [items, setItems] = useState<LinkedView[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await fetch('/api/guardian/linked-views', { cache: 'no-store' });
        const body = await res.json().catch(() => ({}));
        if (!res.ok) {
          if (!cancelled) {
            setError(body?.detail || body?.title || `Unable to load linked views (${res.status})`);
          }
          return;
        }
        const list = Array.isArray(body?.items) ? body.items : Array.isArray(body) ? body : [];
        if (!cancelled) setItems(list as LinkedView[]);
      } catch (e: unknown) {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Failed to load');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="space-y-4" data-testid="guardian-linked-views-page">
      <div>
        <h1 className="text-2xl font-bold text-[#0B1F3A]" data-testid="guardian-linked-views-title">
          Permitted linked views
        </h1>
        <p className="mt-1 text-xs text-[#6B7688]">
          Categories your linked student has authorised for you. Unrelated student data is never shown.
        </p>
      </div>

      {loading && (
        <p className="text-sm text-[#6B7688]" data-testid="guardian-linked-views-loading">
          Loading…
        </p>
      )}
      {error && (
        <p className="text-sm text-red-600" data-testid="guardian-linked-views-error" role="alert">
          {error}
        </p>
      )}
      {!loading && !error && items.length === 0 && (
        <p className="text-sm text-[#6B7688]" data-testid="guardian-linked-views-empty">
          No active permitted views for your account. (Browser demo: no guardian test account in Dev.)
        </p>
      )}
      {!loading && !error && items.length > 0 && (
        <ul className="space-y-3" data-testid="guardian-linked-views-list">
          {items.map((item) => (
            <li
              key={item.guardianshipId || item.studentId}
              className="rounded-[8px] border border-[#E3E8EF] bg-white p-4 text-sm"
              data-testid="guardian-linked-view-item"
            >
              <div className="font-medium text-[#0B1F3A]">Student {item.studentId}</div>
              <div className="mt-1 text-xs text-[#6B7688]">
                Categories: {(item.categories || []).join(', ') || '—'}
              </div>
              <div className="mt-1 text-xs text-[#6B7688]">
                Valid: {item.validFrom || '—'} → {item.validTo || '—'}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
