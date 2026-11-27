'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type GrantRequest = {
  id: string;
  tenantId?: string | null;
  requesterUserId: string;
  subjectUserId: string;
  targetKind: string;
  targetCode: string;
  scopeType: string;
  reason: string;
  status: string;
  validationDiff?: string | null;
  decisionReason?: string | null;
  recordedAt?: string | null;
};

export default function GrantQueuePage() {
  const [items, setItems] = useState<GrantRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const [subjectUserId, setSubjectUserId] = useState('');
  const [targetKind, setTargetKind] = useState('ROLE');
  const [targetCode, setTargetCode] = useState('PRINCIPAL');
  const [scopeType, setScopeType] = useState('INSTITUTE');
  const [reason, setReason] = useState('');
  const [decisionReason, setDecisionReason] = useState('Approved record-only after diff review');
  const [auditDisabledDemo, setAuditDisabledDemo] = useState(false);

  async function load() {
    setLoading(true);
    setErr(null);
    try {
      const res = await fetch('/api/platform/identity/grant-requests');
      const data = await res.json();
      if (!res.ok) throw new Error(data.detail || data.errorCode || 'Unable to load grant queue');
      setItems(Array.isArray(data.items) ? data.items : []);
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Failed to load grant queue');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, []);

  async function submitRequest(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    setErr(null);
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (auditDisabledDemo) headers['X-Demo-Audit-Disabled'] = 'true';
    const res = await fetch('/api/platform/identity/grant-requests', {
      method: 'POST',
      headers,
      body: JSON.stringify({ subjectUserId, targetKind, targetCode, scopeType, reason }),
    });
    const data = await res.json();
    if (!res.ok) {
      setErr(`${data.errorCode || res.status}: ${data.detail || 'Refused'}`);
      return;
    }
    setMsg(`Submitted ${data.id} (${data.status}) — recordedOnly=${data.recordedOnly}`);
    setReason('');
    await load();
  }

  async function validateReq(id: string) {
    setMsg(null);
    setErr(null);
    const res = await fetch(`/api/platform/identity/grant-requests/${id}/validate`, { method: 'POST', body: '{}' });
    const data = await res.json();
    if (!res.ok) {
      setErr(`${data.errorCode || res.status}: ${data.detail || 'Validation failed'}`);
      return;
    }
    setMsg(`Validated ${id} — catalogueTouched=${data.catalogueTouched}`);
    await load();
  }

  async function decideReq(id: string, decision: 'APPROVE' | 'REJECT') {
    setMsg(null);
    setErr(null);
    const res = await fetch(`/api/platform/identity/grant-requests/${id}/decision`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ decision, reason: decisionReason }),
    });
    const data = await res.json();
    if (!res.ok) {
      setErr(`${data.errorCode || res.status}: ${data.detail || 'Decision refused'}`);
      return;
    }
    setMsg(`${data.status}: ${data.statement}`);
    await load();
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6" data-testid="iadm03-grant-queue-desk">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-wide text-slate-500">IADM-03 · Record-Only Grant Workflow</p>
          <h1 className="text-2xl font-semibold text-slate-900">Grant Change Queue</h1>
          <p className="text-sm text-slate-600">
            Approvals end as <strong>Recorded (recorded, not applied)</strong>. No apply endpoint exists.
          </p>
        </div>
        <Link href="/platform/identity" className="rounded border border-slate-300 px-3 py-1.5 text-sm text-slate-700">
          Back to IADM-03
        </Link>
      </header>

      {msg && <div className="rounded border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-900" data-testid="iadm03-grant-msg">{msg}</div>}
      {err && <div className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800" data-testid="iadm03-grant-err">{err}</div>}

      <form onSubmit={submitRequest} className="space-y-3 rounded border border-slate-200 bg-white p-4" data-testid="iadm03-grant-submit-form">
        <h2 className="text-sm font-semibold text-slate-900">Submit Record-Only Grant Request</h2>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
          <input
            type="text"
            value={subjectUserId}
            onChange={(e) => setSubjectUserId(e.target.value)}
            placeholder="Subject User UUID"
            className="rounded border border-slate-300 px-3 py-2 text-sm"
            data-testid="iadm03-grant-subject"
            required
          />
          <select value={targetKind} onChange={(e) => setTargetKind(e.target.value)} className="rounded border border-slate-300 px-3 py-2 text-sm" data-testid="iadm03-grant-kind">
            <option value="ROLE">ROLE</option>
            <option value="PERMISSION">PERMISSION</option>
          </select>
          <input
            type="text"
            value={targetCode}
            onChange={(e) => setTargetCode(e.target.value)}
            placeholder="Role or Permission Code"
            className="rounded border border-slate-300 px-3 py-2 text-sm"
            data-testid="iadm03-grant-target"
            required
          />
          <select value={scopeType} onChange={(e) => setScopeType(e.target.value)} className="rounded border border-slate-300 px-3 py-2 text-sm" data-testid="iadm03-grant-scope">
            <option value="INSTITUTE">INSTITUTE</option>
            <option value="COLLEGE">COLLEGE</option>
            <option value="PLATFORM">PLATFORM</option>
          </select>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <input
            type="text"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Justification reason"
            className="min-w-[280px] flex-1 rounded border border-slate-300 px-3 py-2 text-sm"
            data-testid="iadm03-grant-reason"
            required
          />
          <label className="flex items-center gap-2 text-xs text-slate-600">
            <input
              type="checkbox"
              checked={auditDisabledDemo}
              onChange={(e) => setAuditDisabledDemo(e.target.checked)}
              data-testid="iadm03-demo-audit-toggle"
            />
            DEMO ONLY: Simulate audit unavailable
          </label>
          <button type="submit" className="rounded bg-slate-900 px-4 py-2 text-sm text-white" data-testid="iadm03-grant-submit-btn">
            Submit Request
          </button>
        </div>
      </form>

      <div className="flex items-center gap-3">
        <label className="text-xs font-medium text-slate-700">Decision reason (R01):</label>
        <input
          type="text"
          value={decisionReason}
          onChange={(e) => setDecisionReason(e.target.value)}
          className="flex-1 rounded border border-slate-300 px-3 py-1.5 text-sm"
          data-testid="iadm03-decision-reason"
        />
      </div>

      {loading ? (
        <p className="text-sm text-slate-500">Loading grant requests...</p>
      ) : (
        <div className="overflow-x-auto rounded border border-slate-200">
          <table className="min-w-full divide-y divide-slate-200 text-left text-sm" data-testid="iadm03-grant-table">
            <thead className="bg-slate-50 text-xs uppercase text-slate-600">
              <tr>
                <th className="px-3 py-2">Target</th>
                <th className="px-3 py-2">Scope</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2">Diff / Statement</th>
                <th className="px-3 py-2">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {items.map((r) => (
                <tr key={r.id}>
                  <td className="px-3 py-2 font-mono text-xs">{r.targetKind}: {r.targetCode}</td>
                  <td className="px-3 py-2 text-xs">{r.scopeType}</td>
                  <td className="px-3 py-2 text-xs font-semibold">{r.status}</td>
                  <td className="px-3 py-2 font-mono text-xs text-slate-600">
                    {r.status === 'RECORDED' ? 'recorded, not applied' : (r.validationDiff || r.reason)}
                  </td>
                  <td className="flex flex-wrap gap-2 px-3 py-2 text-xs">
                    <button type="button" onClick={() => validateReq(r.id)} className="rounded border border-slate-300 px-2 py-1" data-testid={`iadm03-validate-${r.id}`}>
                      Validate (R02)
                    </button>
                    <button type="button" onClick={() => decideReq(r.id, 'APPROVE')} className="rounded bg-emerald-700 px-2 py-1 text-white" data-testid={`iadm03-approve-${r.id}`}>
                      Approve (R01)
                    </button>
                    <button type="button" onClick={() => decideReq(r.id, 'REJECT')} className="rounded bg-red-700 px-2 py-1 text-white" data-testid={`iadm03-reject-${r.id}`}>
                      Reject (R01)
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
