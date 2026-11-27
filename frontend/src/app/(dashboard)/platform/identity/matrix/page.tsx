'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type RoleMatrixRow = {
  rCode: string;
  roleCode: string;
  rbacV3Posture: string;
  scope: string;
  templateExists: boolean;
  effectiveKeyCount: number;
  effectiveKeys: string[];
  differenceNote?: string | null;
};

export default function RoleMatrixPage() {
  const [roles, setRoles] = useState<RoleMatrixRow[]>([]);
  const [differences, setDifferences] = useState<string[]>([]);
  const [sourceVersion, setSourceVersion] = useState('RBAC v3.0');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const res = await fetch('/api/platform/identity/matrix');
        const data = await res.json();
        if (!res.ok) throw new Error(data.detail || data.errorCode || 'Role matrix unavailable');
        if (!cancelled) {
          setRoles(Array.isArray(data.roles) ? data.roles : []);
          setDifferences(Array.isArray(data.differences) ? data.differences : []);
          setSourceVersion(data.sourceVersion || 'RBAC v3.0');
        }
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Failed to load role matrix');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, []);

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6" data-testid="iadm03-matrix-desk">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-wide text-slate-500">IADM-03 · Read-only Matrix</p>
          <h1 className="text-2xl font-semibold text-slate-900">Role-to-Permission Matrix (R01–R39)</h1>
          <p className="text-sm text-slate-600" data-testid="iadm03-matrix-meta">
            Source: <strong>{sourceVersion}</strong> · No implicit inheritance · Read-only
          </p>
        </div>
        <Link href="/platform/identity" className="rounded border border-slate-300 px-3 py-1.5 text-sm text-slate-700">
          Back to IADM-03
        </Link>
      </header>

      {error && (
        <div className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800" data-testid="iadm03-matrix-error">
          {error}
        </div>
      )}

      {differences.length > 0 && (
        <section className="rounded border border-amber-300 bg-amber-50 p-4" data-testid="iadm03-matrix-differences">
          <h2 className="text-sm font-semibold text-amber-900">Flagged Matrix vs Live Grant Differences</h2>
          <ul className="mt-2 list-disc space-y-1 pl-5 text-xs text-amber-800">
            {differences.map((d, i) => <li key={i}>{d}</li>)}
          </ul>
        </section>
      )}

      {loading ? (
        <p className="text-sm text-slate-500" data-testid="iadm03-matrix-loading">Loading role matrix...</p>
      ) : (
        <div className="overflow-x-auto rounded border border-slate-200">
          <table className="min-w-full divide-y divide-slate-200 text-left text-sm" data-testid="iadm03-matrix-table">
            <thead className="bg-slate-50 text-xs uppercase text-slate-600">
              <tr>
                <th className="px-3 py-2">R-Code</th>
                <th className="px-3 py-2">Role Code</th>
                <th className="px-3 py-2">RBAC v3.0 Posture</th>
                <th className="px-3 py-2">Scope</th>
                <th className="px-3 py-2">Effective Keys</th>
                <th className="px-3 py-2">Difference Flag</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {roles.map((r) => (
                <tr key={r.rCode}>
                  <td className="px-3 py-2 font-mono text-xs font-semibold text-slate-900">{r.rCode}</td>
                  <td className="px-3 py-2 font-mono text-xs text-slate-700">{r.roleCode}</td>
                  <td className="px-3 py-2 text-xs text-slate-700">{r.rbacV3Posture}</td>
                  <td className="px-3 py-2 text-xs text-slate-600">{r.scope}</td>
                  <td className="px-3 py-2 text-xs text-slate-700">{r.effectiveKeyCount}</td>
                  <td className="px-3 py-2 text-xs text-amber-700">{r.differenceNote || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
