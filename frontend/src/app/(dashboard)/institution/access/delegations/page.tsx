'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type DelegationRow = {
  delegationId?: string;
  fromUserId?: string;
  toUserId?: string;
  roleCode?: string;
  validFrom?: string | null;
  validTo?: string | null;
  status?: string;
  reason?: string;
};

const SUBJECT = '69822238-2ca1-4e3b-86d4-c76d76a78024';

/**
 * IADM-03 S3 — institute delegations desk (US-026/027/029).
 */
export default function InstitutionDelegationsPage() {
  const [items, setItems] = useState<DelegationRow[]>([]);
  const [toUserId, setToUserId] = useState(SUBJECT);
  const [roleCode, setRoleCode] = useState('INSTITUTION_ADMIN');
  const [reason, setReason] = useState('');
  const [msg, setMsg] = useState<string | null>(null);
  const [contract, setContract] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setError(null);
    try {
      const res = await fetch('/api/institution/access/delegations');
      const body = await res.json();
      if (!res.ok) {
        throw new Error(body.detail || body.errorCode || 'Delegations unavailable');
      }
      setItems(Array.isArray(body.items) ? body.items : []);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load');
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function createDelegation() {
    setBusy(true);
    setMsg(null);
    try {
      const res = await fetch('/api/institution/access/delegations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ toUserId, roleCode, reason }),
      });
      const body = await res.json();
      if (!res.ok) {
        throw new Error(body.detail || body.errorCode || 'Create failed');
      }
      setMsg(
        `Created ${body.delegationId} (max ${body.maxValidityDays}d). ${body.activeCheckNote || ''}`
      );
      await load();
    } catch (e) {
      setMsg(e instanceof Error ? e.message : 'Create failed');
    } finally {
      setBusy(false);
    }
  }

  async function revoke(id: string) {
    setBusy(true);
    setMsg(null);
    try {
      const res = await fetch(`/api/institution/access/delegations/${encodeURIComponent(id)}/revoke`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason: reason || 'Revoke from institute desk' }),
      });
      const body = await res.json();
      if (!res.ok) {
        throw new Error(body.detail || body.errorCode || 'Revoke failed');
      }
      setMsg(`Revoked ${body.delegationId}`);
      await load();
    } catch (e) {
      setMsg(e instanceof Error ? e.message : 'Revoke failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6" data-testid="iadm03-delegations-desk">
      <Link href="/institution/access" className="text-sm underline" data-testid="iadm03-delegations-back">
        ← Institute access
      </Link>
      <header className="space-y-2">
        <p className="text-sm uppercase tracking-wide text-slate-500">IADM-03 S3</p>
        <h1 className="text-3xl font-semibold">Delegations</h1>
        <p className="text-slate-600">
          Scoped time-bound authority on existing <code>iam.delegations</code>. Max validity comes from IADM-02
          (never hard-coded). Active-check stays on Path C.
        </p>
      </header>

      {error && (
        <p data-testid="iadm03-delegations-error" className="text-red-700">
          {error}
        </p>
      )}

      <section className="space-y-3 rounded border bg-white p-4" data-testid="iadm03-delegation-form">
        <h2 className="text-lg font-semibold">Create delegation (US-026)</h2>
        <label className="flex flex-col gap-1 text-sm">
          <span>Delegate user id</span>
          <input
            data-testid="iadm03-delegation-to"
            className="rounded border px-3 py-2 font-mono text-xs"
            value={toUserId}
            onChange={(e) => setToUserId(e.target.value)}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span>Role</span>
          <select
            data-testid="iadm03-delegation-role"
            className="rounded border px-3 py-2"
            value={roleCode}
            onChange={(e) => setRoleCode(e.target.value)}
          >
            <option value="INSTITUTION_ADMIN">INSTITUTION_ADMIN (must hold)</option>
            <option value="REGISTRAR">REGISTRAR (must hold)</option>
            <option value="COLLEGE_ADMIN">COLLEGE_ADMIN (must hold)</option>
            <option value="FACULTY">FACULTY (must hold)</option>
            <option value="HOD">HOD (must hold)</option>
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span>Reason</span>
          <textarea
            data-testid="iadm03-delegation-reason"
            className="rounded border px-3 py-2"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </label>
        <button
          type="button"
          data-testid="iadm03-delegation-submit"
          className="rounded bg-slate-900 px-4 py-2 text-sm text-white disabled:opacity-50"
          disabled={busy || !reason.trim() || !toUserId.trim()}
          onClick={() => void createDelegation()}
        >
          Create
        </button>
        {msg && (
          <p data-testid="iadm03-delegation-result" className="text-sm text-slate-700">
            {msg}
          </p>
        )}
        {contract && (
          <p data-testid="iadm03-delegation-contract" className="text-xs text-slate-500">
            {contract}
          </p>
        )}
        <button
          type="button"
          className="text-sm underline"
          data-testid="iadm03-delegation-contract-btn"
          onClick={() => {
            setContract(
              'Active-check: /api/v1/admin/internal/delegations/active-check · max key iam.delegation.max_validity_days'
            );
          }}
        >
          Show contract note (US-028/029)
        </button>
      </section>

      <ul className="space-y-2" data-testid="iadm03-delegations-list">
        {items.map((d) => (
          <li
            key={d.delegationId}
            className="rounded border bg-white p-3 text-sm"
            data-testid={`iadm03-delegation-${d.delegationId}`}
          >
            <p className="font-medium">
              {d.roleCode} · {d.status}
            </p>
            <p className="text-slate-600">
              {d.validFrom || '—'} → {d.validTo || '—'}
            </p>
            {d.status === 'ACTIVE' ? (
              <button
                type="button"
                className="mt-2 underline disabled:opacity-50"
                data-testid={`iadm03-delegation-revoke-${d.delegationId}`}
                disabled={busy || !reason.trim()}
                onClick={() => void revoke(String(d.delegationId))}
              >
                Revoke
              </button>
            ) : null}
          </li>
        ))}
      </ul>
    </div>
  );
}
