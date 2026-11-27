'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type Registration = {
  id: string;
  serviceCode: string;
  keyManifest: string;
  migrationRef: string;
  rollbackRef: string;
  status: string;
};

export default function ServiceRegistrationsPage() {
  const [items, setItems] = useState<Registration[]>([]);
  const [serviceCode, setServiceCode] = useState('IADM-09-DEMO');
  const [keyManifest, setKeyManifest] = useState('["iadm09.demo.view"]');
  const [migrationRef, setMigrationRef] = useState('20261127990000_demo.sql');
  const [rollbackRef, setRollbackRef] = useState('20261127990000_rollback__demo.sql');
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  async function load() {
    const res = await fetch('/api/platform/identity/service-registrations');
    const data = await res.json();
    if (!res.ok) { setErr(data.detail || data.errorCode); return; }
    setItems(Array.isArray(data.items) ? data.items : []);
  }

  useEffect(() => { load(); }, []);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null); setErr(null);
    const res = await fetch('/api/platform/identity/service-registrations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        serviceCode,
        keyManifest,
        roleGrantProposal: '{"R01":["iadm09.demo.view"]}',
        migrationRef,
        rollbackRef,
        testEvidenceRef: 'unit-suite-pass',
        auditEvidenceRef: 'audit-event-verified',
      }),
    });
    const data = await res.json();
    if (!res.ok) { setErr(`${data.errorCode}: ${data.detail}`); return; }
    setMsg(`Submitted ${data.serviceCode} (${data.status}, additiveOnly=${data.additiveOnly})`);
    await load();
  }

  async function validate(id: string) {
    const res = await fetch(`/api/platform/identity/service-registrations/${id}/validate`, { method: 'POST', body: '{}' });
    const data = await res.json();
    if (!res.ok) { setErr(`${data.errorCode}: ${data.detail}`); return; }
    setMsg(`Validated ${id} -> ${data.status}`);
    await load();
  }

  async function decide(id: string, decision: 'APPROVE' | 'REJECT') {
    const res = await fetch(`/api/platform/identity/service-registrations/${id}/decision`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ decision, reason: 'Additive manifest checked (never applied by IADM-03)' }),
    });
    const data = await res.json();
    if (!res.ok) { setErr(`${data.errorCode}: ${data.detail}`); return; }
    setMsg(`Decided ${id} -> ${data.status} (appliedMigration=${data.appliedMigration})`);
    await load();
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6" data-testid="iadm03-registrations-desk">
      <header className="flex items-center justify-between">
        <div>
          <p className="text-xs uppercase tracking-wide text-slate-500">IADM-03 · Additive Service Registrations</p>
          <h1 className="text-2xl font-semibold text-slate-900">Service Permission Registrations</h1>
        </div>
        <Link href="/platform/identity" className="rounded border border-slate-300 px-3 py-1.5 text-sm">Back to IADM-03</Link>
      </header>
      {msg && <div className="rounded border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-900" data-testid="iadm03-reg-msg">{msg}</div>}
      {err && <div className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800" data-testid="iadm03-reg-err">{err}</div>}

      <form onSubmit={submit} className="grid grid-cols-1 gap-3 rounded border border-slate-200 bg-white p-4 md:grid-cols-2" data-testid="iadm03-reg-form">
        <input type="text" value={serviceCode} onChange={(e) => setServiceCode(e.target.value)} className="rounded border px-3 py-2 text-sm" data-testid="iadm03-reg-service" required />
        <input type="text" value={keyManifest} onChange={(e) => setKeyManifest(e.target.value)} className="rounded border px-3 py-2 text-sm font-mono" data-testid="iadm03-reg-manifest" required />
        <input type="text" value={migrationRef} onChange={(e) => setMigrationRef(e.target.value)} className="rounded border px-3 py-2 text-sm" data-testid="iadm03-reg-migration" required />
        <input type="text" value={rollbackRef} onChange={(e) => setRollbackRef(e.target.value)} className="rounded border px-3 py-2 text-sm" data-testid="iadm03-reg-rollback" required />
        <button type="submit" className="rounded bg-slate-900 px-4 py-2 text-sm text-white md:col-span-2" data-testid="iadm03-reg-submit">Submit Additive Manifest</button>
      </form>

      <div className="space-y-2">
        {items.map((r) => (
          <div key={r.id} className="flex flex-wrap items-center justify-between gap-2 rounded border bg-white p-3 text-xs">
            <div>
              <strong>{r.serviceCode}</strong> · {r.status} · <span className="font-mono">{r.keyManifest}</span>
            </div>
            <div className="flex gap-2">
              <button type="button" onClick={() => validate(r.id)} className="rounded border px-2 py-1" data-testid={`iadm03-reg-validate-${r.id}`}>Validate (R02)</button>
              <button type="button" onClick={() => decide(r.id, 'APPROVE')} className="rounded bg-emerald-700 px-2 py-1 text-white" data-testid={`iadm03-reg-approve-${r.id}`}>Approve (R01)</button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
