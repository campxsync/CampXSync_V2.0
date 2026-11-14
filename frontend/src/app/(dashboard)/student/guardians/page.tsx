'use client';

import { useEffect, useState } from 'react';
import { ShieldCheck, Users } from 'lucide-react';

type GuardianRow = {
  id?: string;
  relationship?: string;
  status?: string;
  primary?: boolean;
  firstName?: string;
  lastName?: string;
  canViewAcademics?: boolean;
  canViewFees?: boolean;
};

export default function StudentGuardiansPage() {
  const [guardians, setGuardians] = useState<GuardianRow[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [validFrom, setValidFrom] = useState('2026-11-01');
  const [validTo, setValidTo] = useState('2027-06-30');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function loadGuardians() {
    setError('');
    setLoading(true);
    try {
      const res = await fetch('/api/student/guardians?view=guardians', { cache: 'no-store' });
      const text = await res.text();
      if (!res.ok) {
        setError(text || 'Failed to load guardians');
        setGuardians([]);
        return;
      }
      const parsed = JSON.parse(text) as { items?: GuardianRow[]; guardians?: GuardianRow[] } | GuardianRow[];
      const rows = Array.isArray(parsed)
        ? parsed
        : Array.isArray(parsed.items)
          ? parsed.items
          : Array.isArray(parsed.guardians)
            ? parsed.guardians
            : [];
      setGuardians(rows);
      if (!selectedId && rows[0]?.id) {
        setSelectedId(String(rows[0].id));
      }
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to load guardians');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadGuardians();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function setPeriod() {
    setError('');
    setMessage('');
    if (!selectedId) {
      setError('Select a guardian relationship first');
      return;
    }
    const res = await fetch('/api/student/guardians', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'set-period', guardianId: selectedId, validFrom, validTo }),
    });
    const text = await res.text();
    if (!res.ok) {
      setError(text);
      return;
    }
    setMessage('Consent period saved for selected guardian.');
    await loadGuardians();
  }

  async function revokeAccess() {
    setError('');
    setMessage('');
    if (!selectedId) {
      setError('Select a guardian relationship first');
      return;
    }
    const res = await fetch('/api/student/guardians', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'revoke-access', guardianId: selectedId }),
    });
    const text = await res.text();
    if (!res.ok) {
      setError(text);
      return;
    }
    setMessage('Guardian access revoked. Active consent cleared.');
    await loadGuardians();
  }

  return (
    <main className="mx-auto max-w-4xl space-y-6 p-6">
      <div className="flex flex-col gap-2 border-b pb-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <Users className="h-6 w-6 text-[#1F6FEB]" />
            <h1 data-testid="student-guardians-heading" className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
              Guardians & Consent
            </h1>
          </div>
          <p className="mt-1 text-sm text-[#475569]">
            Review linked guardians and manage consent periods or revoke access for your own record.
          </p>
        </div>
        <div className="flex items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs font-semibold text-slate-700">
          <ShieldCheck className="h-4 w-4 text-[#1F6FEB]" />
          <span>Self-service consent (R25)</span>
        </div>
      </div>

      {message && (
        <div data-testid="student-guardians-success" className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">
          {message}
        </div>
      )}
      {error && (
        <div data-testid="student-guardians-error" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
          {error}
        </div>
      )}

      <section data-testid="student-guardians-list-section" className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm space-y-4">
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-sm font-semibold text-slate-900">Linked Guardians</h2>
          <button
            type="button"
            data-testid="student-guardians-refresh-btn"
            disabled={loading}
            onClick={() => void loadGuardians()}
            className="rounded border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50"
          >
            Refresh
          </button>
        </div>
        {guardians.length === 0 ? (
          <div data-testid="student-guardians-empty" className="rounded border border-dashed border-slate-200 p-6 text-center text-sm text-slate-500">
            No guardian relationships are linked to your student record yet.
          </div>
        ) : (
          <ul data-testid="student-guardians-list" className="divide-y divide-slate-100">
            {guardians.map((g) => {
              const id = String(g.id || '');
              return (
                <li key={id} data-testid={`student-guardian-row-${id}`} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <p className="text-sm font-semibold text-slate-900">
                      {(g.firstName || '') + ' ' + (g.lastName || '')} · {g.relationship || 'GUARDIAN'}
                    </p>
                    <p className="text-xs text-slate-500">
                      Status {g.status || 'ACTIVE'}
                      {g.primary ? ' · Primary' : ''}
                      {g.canViewAcademics ? ' · Academics' : ''}
                      {g.canViewFees ? ' · Fees' : ''}
                    </p>
                  </div>
                  <button
                    type="button"
                    data-testid={`student-guardian-select-${id}`}
                    onClick={() => setSelectedId(id)}
                    className={`rounded px-3 py-1.5 text-xs font-semibold ${
                      selectedId === id
                        ? 'bg-[#0B1F3A] text-white'
                        : 'border border-slate-300 text-slate-700 hover:bg-slate-50'
                    }`}
                  >
                    {selectedId === id ? 'Selected' : 'Select'}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <section data-testid="student-guardians-consent-section" className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm space-y-4">
        <h2 className="text-sm font-semibold text-slate-900">Consent Period & Revocation</h2>
        <p className="text-xs text-slate-500">
          Selected guardian id: <span data-testid="student-guardians-selected-id">{selectedId || 'none'}</span>
        </p>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block text-xs font-medium text-slate-600">
            Valid from
            <input
              data-testid="student-guardians-valid-from"
              type="date"
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm"
              value={validFrom}
              onChange={(e) => setValidFrom(e.target.value)}
            />
          </label>
          <label className="block text-xs font-medium text-slate-600">
            Valid to
            <input
              data-testid="student-guardians-valid-to"
              type="date"
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm"
              value={validTo}
              onChange={(e) => setValidTo(e.target.value)}
            />
          </label>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            data-testid="student-guardians-set-period-btn"
            onClick={() => void setPeriod()}
            className="rounded bg-[#0B1F3A] px-4 py-2 text-xs font-semibold text-white hover:bg-[#152E52]"
          >
            Save Consent Period
          </button>
          <button
            type="button"
            data-testid="student-guardians-revoke-btn"
            onClick={() => void revokeAccess()}
            className="rounded border border-red-300 bg-white px-4 py-2 text-xs font-semibold text-red-700 hover:bg-red-50"
          >
            Revoke Access
          </button>
        </div>
      </section>
    </main>
  );
}
