'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';

type Grant = {
  assignmentId?: string | null;
  roleCode?: string;
  collegeId?: string | null;
  departmentId?: string | null;
  validFrom?: string | null;
  validTo?: string | null;
  grantedBy?: string | null;
  tenantId?: string | null;
  note?: string;
};

/**
 * IADM-03 — subject access detail + status change (US-011, US-014).
 */
export default function PlatformIdentityProfileDetailPage() {
  const params = useParams();
  const id = typeof params?.id === 'string' ? params.id : '';
  const [profile, setProfile] = useState<Record<string, unknown> | null>(null);
  const [grants, setGrants] = useState<Grant[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [reason, setReason] = useState('');
  const [statusMsg, setStatusMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function load() {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(`/api/platform/identity/profiles/${encodeURIComponent(id)}`);
      const body = await res.json();
      if (!res.ok) {
        throw new Error(body.detail || body.errorCode || 'Access detail unavailable');
      }
      setProfile(body.profile || null);
      setGrants(Array.isArray(body.grants) ? body.grants : []);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, [id]);

  async function setStatus(next: 'ACTIVE' | 'INACTIVE') {
    setBusy(true);
    setStatusMsg(null);
    try {
      const res = await fetch(`/api/platform/identity/profiles/${encodeURIComponent(id)}/status`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: next, reason }),
      });
      const body = await res.json();
      if (!res.ok) {
        throw new Error(body.detail || body.errorCode || 'Status change failed');
      }
      setStatusMsg(`Status set to ${body.status}. ${body.claimsNote || ''}`);
      await load();
    } catch (e) {
      setStatusMsg(e instanceof Error ? e.message : 'Status change failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-4xl space-y-8 p-6" data-testid="iadm03-access-detail">
      <header className="space-y-2">
        <p className="text-sm uppercase tracking-wide text-slate-500">IADM-03</p>
        <h1 className="text-3xl font-semibold text-slate-900">Access detail</h1>
        <Link
          href="/platform/identity/profiles"
          className="text-sm text-slate-700 underline"
          data-testid="iadm03-back-profiles"
        >
          ← Profiles
        </Link>
      </header>

      {loading && <p data-testid="iadm03-detail-loading">Loading…</p>}
      {error && (
        <p data-testid="iadm03-detail-error" className="text-red-700">
          {error}
        </p>
      )}

      {profile && (
        <section className="rounded-lg border border-slate-200 bg-white p-4 space-y-2" data-testid="iadm03-detail-profile">
          <p>
            <span className="text-slate-500">Name:</span> {String(profile.fullName || '—')}
          </p>
          <p>
            <span className="text-slate-500">Email:</span> {String(profile.email || '—')}
          </p>
          <p>
            <span className="text-slate-500">Status:</span>{' '}
            <span data-testid="iadm03-detail-status">{String(profile.status || '—')}</span>
          </p>
          <p>
            <span className="text-slate-500">Tenant:</span> {String(profile.tenantId || '—')}
          </p>
          <p>
            <span className="text-slate-500">Store:</span> {String(profile.store || '—')}
          </p>
        </section>
      )}

      <section className="space-y-3">
        <h2 className="text-xl font-semibold">Grants</h2>
        {grants.length === 0 && (
          <p data-testid="iadm03-grants-empty" className="text-slate-500">
            No grants for this person.
          </p>
        )}
        <ul className="space-y-2" data-testid="iadm03-grants-list">
          {grants.map((g, i) => (
            <li
              key={`${g.assignmentId || g.roleCode}-${i}`}
              className="rounded border border-slate-200 bg-white p-3 text-sm"
              data-testid={`iadm03-grant-${i}`}
            >
              <p className="font-medium">{g.roleCode}</p>
              <p className="text-slate-600">
                Validity: {g.validFrom || '—'} → {g.validTo || 'open'} · Granted by: {g.grantedBy || '—'}
              </p>
              {g.note ? <p className="text-xs text-slate-500">{g.note}</p> : null}
            </li>
          ))}
        </ul>
      </section>

      {profile && profile.store === 'IAM_PROFILE' && (
        <section className="space-y-3 rounded-lg border border-slate-200 bg-white p-4" data-testid="iadm03-status-form">
          <h2 className="text-lg font-semibold">Change account status</h2>
          <p className="text-sm text-slate-600">
            Changes profile status only — never Supabase Auth credentials. Reason is required.
          </p>
          <label className="flex flex-col gap-1 text-sm">
            <span>Reason</span>
            <textarea
              data-testid="iadm03-status-reason"
              className="rounded border border-slate-300 px-3 py-2"
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </label>
          <div className="flex gap-2">
            <button
              type="button"
              data-testid="iadm03-status-active"
              className="rounded bg-slate-900 px-4 py-2 text-sm text-white disabled:opacity-50"
              disabled={busy || !reason.trim()}
              onClick={() => void setStatus('ACTIVE')}
            >
              Set active
            </button>
            <button
              type="button"
              data-testid="iadm03-status-inactive"
              className="rounded border border-slate-300 px-4 py-2 text-sm disabled:opacity-50"
              disabled={busy || !reason.trim()}
              onClick={() => void setStatus('INACTIVE')}
            >
              Set inactive
            </button>
          </div>
          {statusMsg && (
            <p data-testid="iadm03-status-result" className="text-sm text-slate-700">
              {statusMsg}
            </p>
          )}
        </section>
      )}
    </div>
  );
}
