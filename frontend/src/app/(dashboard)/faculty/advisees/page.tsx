'use client';

import { useEffect, useState } from 'react';
import { Users, ShieldCheck, RefreshCw, AlertCircle, Calendar, GraduationCap } from 'lucide-react';

type Advisee = { studentId: string; status: string; effectiveFrom: string };

export default function FacultyAdviseesPage() {
  const [advisees, setAdvisees] = useState<Advisee[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function loadAdvisees() {
    setLoading(true);
    setError('');
    try {
      const res = await fetch('/api/faculty/lifecycle', { cache: 'no-store' });
      const text = await res.text();
      if (!res.ok) {
        setError(text);
        return;
      }
      const json = JSON.parse(text);
      setAdvisees(json.advisees || []);
    } catch (e: any) {
      setError(e?.message || 'Failed to load assigned advisees.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadAdvisees();
  }, []);

  return (
    <main className="mx-auto max-w-5xl space-y-6 p-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between border-b pb-4 gap-4">
        <div>
          <div className="flex items-center gap-2">
            <Users className="h-6 w-6 text-[#1F6FEB]" />
            <h1 data-testid="faculty-advisees-heading" className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
              Faculty Advisor & Advisee Tracking Desk
            </h1>
          </div>
          <p className="mt-1 text-sm text-[#475569]">
            Monitor academic standing and lifecycle status for students assigned to your advisory roster.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <div className="inline-flex items-center gap-1.5 rounded-full border border-blue-200 bg-blue-50 px-3 py-1 text-xs font-semibold text-blue-700">
            <ShieldCheck className="h-4 w-4" />
            <span>Role: Faculty Advisor (R32 / R10)</span>
          </div>
          <button
            type="button"
            data-testid="faculty-advisees-refresh-btn"
            disabled={loading}
            onClick={() => void loadAdvisees()}
            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 shadow-sm hover:bg-slate-50 transition-colors disabled:opacity-50"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        </div>
      </div>

      {/* Error Notice */}
      {error && (
        <div data-testid="faculty-advisees-error" className="rounded-lg bg-red-50 border border-red-200 p-4 text-sm text-red-800 flex items-start gap-2">
          <AlertCircle className="h-5 w-5 text-red-600 shrink-0 mt-0.5" />
          <pre className="whitespace-pre-wrap font-sans text-xs">{error}</pre>
        </div>
      )}

      {/* Advisee Roster Table */}
      <section className="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
        <div className="p-4 border-b bg-slate-50 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <GraduationCap className="h-5 w-5 text-[#1F6FEB]" />
            <h2 className="text-sm font-semibold text-slate-900">Assigned Student Advisees</h2>
            <span
              data-testid="faculty-advisees-count"
              className="ml-2 rounded-full bg-blue-100 text-blue-800 px-2 py-0.5 text-xs font-bold"
            >
              {advisees.length} Advisees
            </span>
          </div>
        </div>

        {advisees.length === 0 ? (
          <div data-testid="faculty-advisees-empty" className="p-8 text-center text-sm text-slate-500">
            No advisees have been assigned to your profile by the Registrar yet.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table data-testid="faculty-advisees-table" className="w-full text-left text-sm">
              <thead className="bg-slate-50 border-b text-xs uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="px-4 py-3">Student Record ID</th>
                  <th className="px-4 py-3">Academic Standing</th>
                  <th className="px-4 py-3">Effective Date</th>
                  <th className="px-4 py-3">Advisory Clearance</th>
                </tr>
              </thead>
              <tbody data-testid="faculty-advisees-list" className="divide-y divide-slate-100">
                {advisees.map((advisee) => (
                  <tr key={advisee.studentId} data-testid={`faculty-advisee-row-${advisee.studentId}`} className="hover:bg-slate-50/50">
                    <td className="px-4 py-3 font-mono text-xs text-slate-800 font-semibold">
                      {advisee.studentId}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        data-testid={`faculty-advisee-status-${advisee.studentId}`}
                        className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-bold ${
                          advisee.status === 'ACTIVE'
                            ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                            : advisee.status === 'ON_LEAVE'
                            ? 'bg-purple-50 text-purple-700 border border-purple-200'
                            : advisee.status === 'GRADUATED'
                            ? 'bg-teal-50 text-teal-700 border border-teal-200'
                            : 'bg-slate-100 text-slate-700 border border-slate-200'
                        }`}
                      >
                        {advisee.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs text-slate-600">
                      <span className="inline-flex items-center gap-1">
                        <Calendar className="h-3 w-3 text-slate-400" />
                        {advisee.effectiveFrom ? advisee.effectiveFrom.substring(0, 10) : '—'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs text-emerald-600 font-medium">
                      Active Guidance Link
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </main>
  );
}
