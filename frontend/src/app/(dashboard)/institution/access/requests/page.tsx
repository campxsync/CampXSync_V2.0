'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type RequestRow = {
  requestId?: string;
  requestedRoleCode?: string;
  subjectUserId?: string;
  status?: string;
  justification?: string;
  createdAt?: string;
};

const SUBJECT = '69822238-2ca1-4e3b-86d4-c76d76a78024';
const MAC_TENANT = '72ecbfe3-2077-41b5-8a20-b6d3e045311b';

/**
 * IADM-03 S3 — institute access requests (US-023/024/025).
 */
export default function InstitutionAccessRequestsPage() {
  const [items, setItems] = useState<RequestRow[]>([]);
  const [role, setRole] = useState('HOD');
  const [subject, setSubject] = useState(SUBJECT);
  const [justification, setJustification] = useState('');
  const [decideReason, setDecideReason] = useState('');
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function load() {
    try {
      const res = await fetch('/api/institution/access/requests?status=ALL');
      const body = await res.json();
      if (!res.ok) throw new Error(body.detail || body.errorCode || 'Unavailable');
      setItems(Array.isArray(body.items) ? body.items : []);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load');
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function submit() {
    setBusy(true);
    setMsg(null);
    try {
      const res = await fetch('/api/institution/access/requests', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          subjectUserId: subject,
          requestedRole: role,
          tenantId: MAC_TENANT,
          justification,
        }),
      });
      const body = await res.json();
      if (!res.ok) throw new Error(body.detail || body.errorCode || 'Submit failed');
      setMsg(`Submitted ${body.requestId} — ${body.message || body.status}`);
      await load();
    } catch (e) {
      setMsg(e instanceof Error ? e.message : 'Submit failed');
    } finally {
      setBusy(false);
    }
  }

  async function decide(id: string, decision: 'APPROVE' | 'REJECT') {
    setBusy(true);
    setMsg(null);
    try {
      const res = await fetch(`/api/institution/access/requests/${encodeURIComponent(id)}/decide`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ decision, reason: decideReason }),
      });
      const body = await res.json();
      if (!res.ok) throw new Error(body.detail || body.errorCode || 'Decide failed');
      setMsg(`Decision ${body.decision} for ${body.requestId}`);
      await load();
    } catch (e) {
      setMsg(e instanceof Error ? e.message : 'Decide failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6" data-testid="iadm03-requests-desk">
      <Link href="/institution/access" className="text-sm underline" data-testid="iadm03-requests-back">
        ← Institute access
      </Link>
      <header className="space-y-2">
        <p className="text-sm uppercase tracking-wide text-slate-500">IADM-03 S3</p>
        <h1 className="text-3xl font-semibold">Access requests</h1>
        <p className="text-slate-600">
          Requests are stored and routed — never granted on submit. Approver cannot be the requester.
        </p>
      </header>

      {error && (
        <p data-testid="iadm03-requests-error" className="text-red-700">
          {error}
        </p>
      )}

      <section className="space-y-3 rounded border bg-white p-4" data-testid="iadm03-request-form">
        <h2 className="text-lg font-semibold">Submit request</h2>
        <label className="flex flex-col gap-1 text-sm">
          <span>Subject user id</span>
          <input
            data-testid="iadm03-request-subject"
            className="rounded border px-3 py-2 font-mono text-xs"
            value={subject}
            onChange={(e) => setSubject(e.target.value)}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span>Requested role</span>
          <select
            data-testid="iadm03-request-role"
            className="rounded border px-3 py-2"
            value={role}
            onChange={(e) => setRole(e.target.value)}
          >
            <option value="HOD">HOD</option>
            <option value="FACULTY">FACULTY</option>
            <option value="REGISTRAR">REGISTRAR</option>
            <option value="INSTITUTION_ADMIN">INSTITUTION_ADMIN (outside R03 ceiling → R01)</option>
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span>Justification</span>
          <textarea
            data-testid="iadm03-request-justification"
            className="rounded border px-3 py-2"
            value={justification}
            onChange={(e) => setJustification(e.target.value)}
          />
        </label>
        <button
          type="button"
          data-testid="iadm03-request-submit"
          className="rounded bg-slate-900 px-4 py-2 text-sm text-white disabled:opacity-50"
          disabled={busy || !justification.trim()}
          onClick={() => void submit()}
        >
          Submit
        </button>
        {msg && (
          <p data-testid="iadm03-request-result" className="text-sm text-slate-700">
            {msg}
          </p>
        )}
      </section>

      <label className="flex flex-col gap-1 text-sm">
        <span>Decision reason</span>
        <input
          data-testid="iadm03-request-decide-reason"
          className="rounded border px-3 py-2"
          value={decideReason}
          onChange={(e) => setDecideReason(e.target.value)}
        />
      </label>

      <ul className="space-y-2" data-testid="iadm03-requests-list">
        {items.map((r) => (
          <li
            key={r.requestId}
            className="rounded border bg-white p-3 text-sm"
            data-testid={`iadm03-request-${r.requestId}`}
          >
            <p className="font-medium">
              {r.requestedRoleCode} · {r.status}
            </p>
            <p className="text-slate-600">{r.justification}</p>
            {(r.status === 'SUBMITTED' || r.status === 'PENDING' || r.status === 'DRAFT') && (
              <div className="mt-2 flex gap-3">
                <button
                  type="button"
                  className="underline disabled:opacity-50"
                  data-testid={`iadm03-request-approve-${r.requestId}`}
                  disabled={busy || !decideReason.trim()}
                  onClick={() => void decide(String(r.requestId), 'APPROVE')}
                >
                  Approve
                </button>
                <button
                  type="button"
                  className="underline disabled:opacity-50"
                  data-testid={`iadm03-request-reject-${r.requestId}`}
                  disabled={busy || !decideReason.trim()}
                  onClick={() => void decide(String(r.requestId), 'REJECT')}
                >
                  Reject
                </button>
              </div>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
