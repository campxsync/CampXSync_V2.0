'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type ProfileRow = {
  id: string;
  email?: string | null;
  fullName?: string | null;
  status?: string;
  tenantId?: string | null;
  tenantCode?: string | null;
  roleCode?: string;
  store?: string;
};

const MAC_TENANT = '72ecbfe3-2077-41b5-8a20-b6d3e045311b';

/**
 * IADM-03 — list / invite institute administrator profiles (US-012, US-013).
 */
export default function PlatformIdentityProfilesPage() {
  const [items, setItems] = useState<ProfileRow[]>([]);
  const [status, setStatus] = useState('ALL');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [targetRole, setTargetRole] = useState('INSTITUTION_ADMIN');
  const [tenantId, setTenantId] = useState(MAC_TENANT);
  const [inviteMsg, setInviteMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(`/api/platform/identity?view=profiles&status=${encodeURIComponent(status)}`);
      const body = await res.json();
      if (!res.ok) {
        throw new Error(body.detail || body.errorCode || 'Profiles unavailable');
      }
      setItems(Array.isArray(body.items) ? body.items : []);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load profiles');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, [status]);

  async function invite() {
    setBusy(true);
    setInviteMsg(null);
    try {
      const res = await fetch('/api/platform/identity/profiles/invite', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email,
          displayName,
          targetRole,
          tenantId: targetRole === 'SYSTEM_ADMIN' || targetRole === 'R02' ? undefined : tenantId,
        }),
      });
      const body = await res.json();
      if (!res.ok) {
        throw new Error(body.detail || body.errorCode || 'Invite failed');
      }
      const note = body.notificationMessage || body.invitationStatus || 'Invite accepted';
      setInviteMsg(
        `Profile ${body.profileId || ''} — ${note}${body.notificationDeferred ? ' (notification delayed)' : ''}`
      );
      await load();
    } catch (e) {
      setInviteMsg(e instanceof Error ? e.message : 'Invite failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-6xl space-y-8 p-6" data-testid="iadm03-profiles-desk">
      <header className="space-y-2">
        <p className="text-sm uppercase tracking-wide text-slate-500">IADM-03</p>
        <h1 className="text-3xl font-semibold text-slate-900">Administrator profiles</h1>
        <p className="max-w-2xl text-slate-600">
          Population is limited to System Admin (R02) and Institution Admin (R03). Invitation email is sent by
          Supabase Auth.
        </p>
        <Link href="/platform/identity" className="text-sm text-slate-700 underline" data-testid="iadm03-back-desk">
          ← Identity desk
        </Link>
      </header>

      <section className="space-y-3 rounded-lg border border-slate-200 bg-white p-4" data-testid="iadm03-invite-form">
        <h2 className="text-lg font-semibold">Invite / create</h2>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="flex flex-col gap-1 text-sm">
            <span>Email</span>
            <input
              data-testid="iadm03-invite-email"
              className="rounded border border-slate-300 px-3 py-2"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span>Display name</span>
            <input
              data-testid="iadm03-invite-name"
              className="rounded border border-slate-300 px-3 py-2"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span>Target role</span>
            <select
              data-testid="iadm03-invite-role"
              className="rounded border border-slate-300 px-3 py-2"
              value={targetRole}
              onChange={(e) => setTargetRole(e.target.value)}
            >
              <option value="INSTITUTION_ADMIN">Institution Admin (R03)</option>
              <option value="SYSTEM_ADMIN">System Admin (R02)</option>
            </select>
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span>Tenant ID (R03)</span>
            <input
              data-testid="iadm03-invite-tenant"
              className="rounded border border-slate-300 px-3 py-2 font-mono text-xs"
              value={tenantId}
              onChange={(e) => setTenantId(e.target.value)}
              disabled={targetRole === 'SYSTEM_ADMIN'}
            />
          </label>
        </div>
        <button
          type="button"
          data-testid="iadm03-invite-submit"
          className="rounded bg-slate-900 px-4 py-2 text-sm text-white disabled:opacity-50"
          disabled={busy || !email.trim()}
          onClick={() => void invite()}
        >
          {busy ? 'Working…' : 'Invite / create'}
        </button>
        {inviteMsg && (
          <p data-testid="iadm03-invite-result" className="text-sm text-slate-700">
            {inviteMsg}
          </p>
        )}
      </section>

      <section className="space-y-3">
        <div className="flex items-end gap-3">
          <label className="flex flex-col gap-1 text-sm">
            <span>Status filter</span>
            <select
              data-testid="iadm03-profile-status-filter"
              className="rounded border border-slate-300 px-3 py-2"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
            >
              <option value="ALL">All</option>
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
            </select>
          </label>
        </div>
        {loading && <p data-testid="iadm03-profiles-loading">Loading profiles…</p>}
        {error && (
          <p data-testid="iadm03-profiles-error" className="text-red-700">
            {error}
          </p>
        )}
        {!loading && !error && items.length === 0 && (
          <p data-testid="iadm03-profiles-empty" className="text-slate-500">
            No administrator profiles in this filter.
          </p>
        )}
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
          <table className="min-w-full text-left text-sm" data-testid="iadm03-profiles-table">
            <thead className="border-b bg-slate-50 text-slate-600">
              <tr>
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Email</th>
                <th className="px-4 py-3">Role</th>
                <th className="px-4 py-3">Tenant</th>
                <th className="px-4 py-3">Status</th>
              </tr>
            </thead>
            <tbody>
              {items.map((row) => (
                <tr key={row.id} className="border-b last:border-0" data-testid={`iadm03-profile-row-${row.id}`}>
                  <td className="px-4 py-3">
                    <Link
                      href={`/platform/identity/profiles/${encodeURIComponent(row.id)}`}
                      className="font-medium underline-offset-2 hover:underline"
                      data-testid={`iadm03-profile-link-${row.id}`}
                    >
                      {row.fullName || row.id}
                    </Link>
                  </td>
                  <td className="px-4 py-3">{row.email || '—'}</td>
                  <td className="px-4 py-3">{row.roleCode || '—'}</td>
                  <td className="px-4 py-3">{row.tenantCode || row.tenantId || '—'}</td>
                  <td className="px-4 py-3">{row.status || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
