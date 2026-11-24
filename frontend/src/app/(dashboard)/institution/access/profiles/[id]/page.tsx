'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';

type Grant = {
  assignmentId?: string | null;
  roleCode?: string;
  validFrom?: string | null;
  validTo?: string | null;
  grantedBy?: string | null;
};

export default function InstitutionAccessDetailPage() {
  const params = useParams();
  const id = typeof params?.id === 'string' ? params.id : '';
  const [profile, setProfile] = useState<Record<string, unknown> | null>(null);
  const [grants, setGrants] = useState<Grant[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [statusMsg, setStatusMsg] = useState<string | null>(null);
  const [targetRole, setTargetRole] = useState('FACULTY');
  const [grantMsg, setGrantMsg] = useState<string | null>(null);
  const [revokeReason, setRevokeReason] = useState('');
  const [ceilingMsg, setCeilingMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function load() {
    if (!id) return;
    try {
      const res = await fetch(`/api/institution/access/profiles/${encodeURIComponent(id)}`);
      const body = await res.json();
      if (!res.ok) {
        throw new Error(body.detail || body.errorCode || 'Access detail unavailable');
      }
      setProfile(body.profile || null);
      setGrants(Array.isArray(body.grants) ? body.grants : []);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load');
    }
  }

  useEffect(() => {
    void load();
  }, [id]);

  async function setStatus(next: 'ACTIVE' | 'INACTIVE') {
    setBusy(true);
    setStatusMsg(null);
    try {
      const res = await fetch(`/api/institution/access/profiles/${encodeURIComponent(id)}/status`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: next, reason }),
      });
      const body = await res.json();
      if (!res.ok) {
        throw new Error(body.detail || body.errorCode || 'Status change failed');
      }
      setStatusMsg(`Status set to ${body.status}`);
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
      const res = await fetch('/api/institution/access/grants/assign', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          subjectUserId: id,
          targetRole,
          tenantId: profile?.tenantId || undefined,
          reason: reason || 'Institute grant from access detail',
        }),
      });
      const body = await res.json();
      if (!res.ok) {
        throw new Error(body.detail || body.errorCode || 'Grant failed');
      }
      setGrantMsg(`Granted ${body.roleCode || targetRole}. ${body.claimsNote || ''}`);
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
      const res = await fetch('/api/institution/access/grants/revoke', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          assignmentId,
          reason: revokeReason || 'Revoke from institute access detail',
        }),
      });
      const body = await res.json();
      if (!res.ok) {
        throw new Error(body.detail || body.errorCode || 'Revoke failed');
      }
      setGrantMsg(
        body.queued
          ? `Queued for independent approval: ${body.requestId}`
          : `Revoked ${body.assignmentId || assignmentId}`
      );
      await load();
    } catch (e) {
      setGrantMsg(e instanceof Error ? e.message : 'Revoke failed');
    } finally {
      setBusy(false);
    }
  }

  async function denyOverCeiling() {
    setCeilingMsg(null);
    try {
      const res = await fetch('/api/institution/access/grants/assign', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          subjectUserId: id,
          targetRole: 'INSTITUTION_ADMIN',
          tenantId: profile?.tenantId || undefined,
          reason: 'US-017 denial probe — must fail',
        }),
      });
      const body = await res.json();
      if (res.ok) {
        setCeilingMsg('Unexpected success — ceiling failed open');
        return;
      }
      setCeilingMsg(`Denied: ${body.detail || body.errorCode || res.status}`);
    } catch (e) {
      setCeilingMsg(e instanceof Error ? e.message : 'Denial probe failed');
    }
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6" data-testid="iadm03-inst-access-detail">
      <Link href="/institution/access" className="text-sm underline" data-testid="iadm03-inst-back">
        ← Institute access
      </Link>
      <h1 className="text-3xl font-semibold">Access detail</h1>
      {error && (
        <p data-testid="iadm03-inst-detail-error" className="text-red-700">
          {error}
        </p>
      )}
      {profile && (
        <section className="rounded border border-slate-200 bg-white p-4" data-testid="iadm03-inst-detail-profile">
          <p>{String(profile.fullName || '—')}</p>
          <p className="text-slate-600">{String(profile.email || '—')}</p>
          <p data-testid="iadm03-inst-detail-status">Status: {String(profile.status || '—')}</p>
        </section>
      )}
      <ul className="space-y-2" data-testid="iadm03-inst-grants">
        {grants.map((g, i) => (
          <li key={i} className="rounded border bg-white p-3 text-sm" data-testid={`iadm03-inst-grant-${i}`}>
            {g.roleCode} · {g.validFrom || '—'} → {g.validTo || 'open'}
            {g.assignmentId ? (
              <button
                type="button"
                className="ml-3 underline disabled:opacity-50"
                data-testid={`iadm03-inst-revoke-${i}`}
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
          data-testid="iadm03-inst-revoke-reason"
          className="rounded border px-3 py-2"
          value={revokeReason}
          onChange={(e) => setRevokeReason(e.target.value)}
        />
      </label>

      <section className="space-y-2 rounded border bg-white p-4" data-testid="iadm03-inst-grant-form">
        <h2 className="text-lg font-semibold">Assign eligible role (US-016)</h2>
        <label className="flex flex-col gap-1 text-sm">
          <span>Target role</span>
          <select
            data-testid="iadm03-inst-grant-role"
            className="rounded border px-3 py-2"
            value={targetRole}
            onChange={(e) => setTargetRole(e.target.value)}
          >
            <option value="FACULTY">FACULTY</option>
            <option value="REGISTRAR">REGISTRAR</option>
            <option value="COLLEGE_ADMIN">COLLEGE_ADMIN</option>
            <option value="HOD">HOD</option>
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span>Reason</span>
          <textarea
            data-testid="iadm03-inst-grant-reason"
            className="rounded border px-3 py-2"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </label>
        <button
          type="button"
          data-testid="iadm03-inst-grant-submit"
          className="rounded bg-slate-900 px-4 py-2 text-sm text-white disabled:opacity-50"
          disabled={busy || !reason.trim()}
          onClick={() => void assignGrant()}
        >
          Assign
        </button>
        <button
          type="button"
          data-testid="iadm03-inst-deny-r03"
          className="ml-2 rounded border px-4 py-2 text-sm"
          onClick={() => void denyOverCeiling()}
        >
          Probe R03→R03 denial
        </button>
        {grantMsg && <p data-testid="iadm03-inst-grant-result">{grantMsg}</p>}
        {ceilingMsg && (
          <p data-testid="iadm03-inst-ceiling-result" className="text-sm text-red-700">
            {ceilingMsg}
          </p>
        )}
      </section>

      <section className="space-y-2 rounded border bg-white p-4">
        <label className="flex flex-col gap-1 text-sm">
          <span>Reason for status change</span>
          <textarea
            data-testid="iadm03-inst-status-reason"
            className="rounded border px-3 py-2"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </label>
        <div className="flex gap-2">
          <button
            type="button"
            data-testid="iadm03-inst-status-inactive"
            className="rounded border px-4 py-2 text-sm disabled:opacity-50"
            disabled={busy || !reason.trim()}
            onClick={() => void setStatus('INACTIVE')}
          >
            Set inactive
          </button>
          <button
            type="button"
            data-testid="iadm03-inst-status-active"
            className="rounded bg-slate-900 px-4 py-2 text-sm text-white disabled:opacity-50"
            disabled={busy || !reason.trim()}
            onClick={() => void setStatus('ACTIVE')}
          >
            Set active
          </button>
        </div>
        {statusMsg && <p data-testid="iadm03-inst-status-result">{statusMsg}</p>}
      </section>
    </div>
  );
}
