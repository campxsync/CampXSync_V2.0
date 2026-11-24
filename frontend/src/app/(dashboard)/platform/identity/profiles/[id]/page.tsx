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
 * IADM-03 — subject access detail + status + grant/revoke (US-011, US-014, US-016…020).
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
  const [grantRole, setGrantRole] = useState('SYSTEM_ADMIN');
  const [grantMsg, setGrantMsg] = useState<string | null>(null);
  const [revokeReason, setRevokeReason] = useState('');
  const [decideId, setDecideId] = useState('');
  const [decideReason, setDecideReason] = useState('');
  const [ceilingMsg, setCeilingMsg] = useState<string | null>(null);
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

  async function assignGrant() {
    setBusy(true);
    setGrantMsg(null);
    try {
      const res = await fetch('/api/platform/identity/grants/assign', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          subjectUserId: id,
          targetRole: grantRole,
          tenantId: profile?.tenantId || undefined,
          reason: reason || 'Platform grant from access detail',
        }),
      });
      const body = await res.json();
      if (!res.ok) {
        throw new Error(body.detail || body.errorCode || 'Grant failed');
      }
      setGrantMsg(
        body.queued
          ? `Queued: ${body.message || body.requestId}`
          : `Granted ${body.roleCode || grantRole}. ${body.claimsNote || ''}`
      );
      await load();
    } catch (e) {
      setGrantMsg(e instanceof Error ? e.message : 'Grant failed');
    } finally {
      setBusy(false);
    }
  }

  async function revokeGrant(assignmentId: string) {
    setBusy(true);
    setGrantMsg(null);
    try {
      const res = await fetch('/api/platform/identity/grants/revoke', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          assignmentId,
          reason: revokeReason || 'Revoke from platform access detail',
        }),
      });
      const body = await res.json();
      if (!res.ok) {
        throw new Error(body.detail || body.errorCode || 'Revoke failed');
      }
      setGrantMsg(
        body.queued
          ? `R03 change queued for approval: ${body.requestId}. ${body.message || ''}`
          : `Revoked ${body.assignmentId || assignmentId}. ${body.claimsNote || ''}`
      );
      if (body.requestId) setDecideId(String(body.requestId));
      await load();
    } catch (e) {
      setGrantMsg(e instanceof Error ? e.message : 'Revoke failed');
    } finally {
      setBusy(false);
    }
  }

  async function decideChange(decision: 'APPROVE' | 'REJECT') {
    setBusy(true);
    setGrantMsg(null);
    try {
      const res = await fetch(
        `/api/platform/identity/grants/r03-change/${encodeURIComponent(decideId)}/decide`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ decision, reason: decideReason }),
        }
      );
      const body = await res.json();
      if (!res.ok) {
        throw new Error(body.detail || body.errorCode || 'Decision failed');
      }
      setGrantMsg(`Decision ${body.decision}.`);
      await load();
    } catch (e) {
      setGrantMsg(e instanceof Error ? e.message : 'Decision failed');
    } finally {
      setBusy(false);
    }
  }

  async function previewCeilingDenial() {
    setCeilingMsg(null);
    try {
      const res = await fetch('/api/platform/identity/ceilings/evaluate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ grantor: 'R03', targetRole: 'INSTITUTION_ADMIN', selfGrant: false }),
      });
      const body = await res.json();
      if (!res.ok) {
        throw new Error(body.detail || 'Ceiling check failed');
      }
      setCeilingMsg(
        body.allowed
          ? 'Unexpected allow'
          : `Denied (US-017): ${body.denyReason || 'ceiling'}`
      );
    } catch (e) {
      setCeilingMsg(e instanceof Error ? e.message : 'Ceiling check failed');
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
              {g.assignmentId ? (
                <button
                  type="button"
                  className="mt-2 text-sm underline disabled:opacity-50"
                  data-testid={`iadm03-revoke-${i}`}
                  disabled={busy || !revokeReason.trim()}
                  onClick={() => void revokeGrant(String(g.assignmentId))}
                >
                  Revoke
                </button>
              ) : null}
            </li>
          ))}
        </ul>
        <label className="flex flex-col gap-1 text-sm">
          <span>Revoke reason</span>
          <input
            data-testid="iadm03-revoke-reason"
            className="rounded border border-slate-300 px-3 py-2"
            value={revokeReason}
            onChange={(e) => setRevokeReason(e.target.value)}
          />
        </label>
      </section>

      <section className="space-y-3 rounded-lg border border-slate-200 bg-white p-4" data-testid="iadm03-grant-form">
        <h2 className="text-lg font-semibold">Assign grant</h2>
        <label className="flex flex-col gap-1 text-sm">
          <span>Target role</span>
          <select
            data-testid="iadm03-grant-role"
            className="rounded border border-slate-300 px-3 py-2"
            value={grantRole}
            onChange={(e) => setGrantRole(e.target.value)}
          >
            <option value="SYSTEM_ADMIN">System Admin (R02)</option>
            <option value="INSTITUTION_ADMIN">Institution Admin (R03)</option>
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span>Reason</span>
          <textarea
            data-testid="iadm03-grant-reason"
            className="rounded border border-slate-300 px-3 py-2"
            rows={2}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </label>
        <button
          type="button"
          data-testid="iadm03-grant-submit"
          className="rounded bg-slate-900 px-4 py-2 text-sm text-white disabled:opacity-50"
          disabled={busy || !reason.trim()}
          onClick={() => void assignGrant()}
        >
          Assign
        </button>
        {grantMsg && (
          <p data-testid="iadm03-grant-result" className="text-sm text-slate-700">
            {grantMsg}
          </p>
        )}
      </section>

      <section className="space-y-3 rounded-lg border border-slate-200 bg-white p-4" data-testid="iadm03-r03-decide-form">
        <h2 className="text-lg font-semibold">Decide R03 grant change (US-020)</h2>
        <label className="flex flex-col gap-1 text-sm">
          <span>Request id</span>
          <input
            data-testid="iadm03-decide-id"
            className="rounded border border-slate-300 px-3 py-2 font-mono text-xs"
            value={decideId}
            onChange={(e) => setDecideId(e.target.value)}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span>Decision reason</span>
          <input
            data-testid="iadm03-decide-reason"
            className="rounded border border-slate-300 px-3 py-2"
            value={decideReason}
            onChange={(e) => setDecideReason(e.target.value)}
          />
        </label>
        <div className="flex gap-2">
          <button
            type="button"
            data-testid="iadm03-decide-approve"
            className="rounded bg-slate-900 px-4 py-2 text-sm text-white disabled:opacity-50"
            disabled={busy || !decideId.trim() || !decideReason.trim()}
            onClick={() => void decideChange('APPROVE')}
          >
            Approve
          </button>
          <button
            type="button"
            data-testid="iadm03-decide-reject"
            className="rounded border border-slate-300 px-4 py-2 text-sm disabled:opacity-50"
            disabled={busy || !decideId.trim() || !decideReason.trim()}
            onClick={() => void decideChange('REJECT')}
          >
            Reject
          </button>
        </div>
      </section>

      <section className="space-y-2 rounded-lg border border-slate-200 bg-white p-4" data-testid="iadm03-ceiling-preview">
        <h2 className="text-lg font-semibold">Ceiling denial preview (US-017)</h2>
        <button
          type="button"
          data-testid="iadm03-ceiling-r03-r03"
          className="rounded border border-slate-300 px-4 py-2 text-sm"
          onClick={() => void previewCeilingDenial()}
        >
          Evaluate R03 → INSTITUTION_ADMIN
        </button>
        {ceilingMsg && (
          <p data-testid="iadm03-ceiling-result" className="text-sm text-slate-700">
            {ceilingMsg}
          </p>
        )}
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
