'use client';

import { useEffect, useState } from 'react';
import { Users, ShieldCheck, RefreshCw, AlertCircle, Calendar, GraduationCap, CheckCircle2, FileCheck, ThumbsUp, XCircle, MessageSquare } from 'lucide-react';

type Advisee = { studentId: string; status: string; effectiveFrom: string };
type AdviseeRequest = {
  id: string;
  studentId: string;
  requestType: string;
  status: string;
  reason?: string;
  effectiveDate?: string;
  endorsement?: string;
};

export default function FacultyAdviseesPage() {
  const [advisees, setAdvisees] = useState<Advisee[]>([]);
  const [requests, setRequests] = useState<AdviseeRequest[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  // Endorsement modal state
  const [endorsingReqId, setEndorsingReqId] = useState<string | null>(null);
  const [endorsementType, setEndorsementType] = useState('RECOMMENDED');
  const [endorsementRemarks, setEndorsementRemarks] = useState('');

  async function loadData() {
    setLoading(true);
    setError('');
    try {
      const [advRes, reqRes] = await Promise.all([
        fetch('/api/faculty/lifecycle', { cache: 'no-store' }),
        fetch('/api/faculty/lifecycle?view=requests', { cache: 'no-store' }),
      ]);
      const advText = await advRes.text();
      if (!advRes.ok) {
        setError(advText);
        return;
      }
      const advJson = JSON.parse(advText);
      setAdvisees(advJson.advisees || []);

      if (reqRes.ok) {
        const reqJson = await reqRes.json();
        setRequests(reqJson.requests || []);
      }
    } catch (e: any) {
      setError(e?.message || 'Failed to load advisory data.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadData();
  }, []);

  async function submitEndorsement() {
    if (!endorsingReqId) return;
    if (endorsementRemarks.trim().length < 5) {
      setError('Please provide departmental endorsement remarks (at least 5 characters).');
      return;
    }
    setError('');
    setMessage('');
    setLoading(true);
    try {
      const res = await fetch('/api/faculty/lifecycle', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          action: 'endorse',
          requestId: endorsingReqId,
          endorsement: endorsementType,
          remarks: endorsementRemarks.trim(),
        }),
      });
      const text = await res.text();
      if (!res.ok) {
        setError(text);
        return;
      }
      setMessage(`Petition successfully endorsed as ${endorsementType}! Forwarded to Registrar Lifecycle Review Desk.`);
      setEndorsingReqId(null);
      setEndorsementRemarks('');
      await loadData();
    } catch (e: any) {
      setError(e?.message || 'Failed to submit endorsement.');
    } finally {
      setLoading(false);
    }
  }

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
            Monitor academic standing and review student lifecycle petitions for students assigned to your advisory roster.
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
            onClick={() => void loadData()}
            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 shadow-sm hover:bg-slate-50 transition-colors disabled:opacity-50"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        </div>
      </div>

      {/* Success Notification */}
      {message && (
        <div data-testid="faculty-advisees-success" className="rounded-lg bg-emerald-50 border border-emerald-200 p-4 text-sm text-emerald-800 flex items-start gap-2">
          <CheckCircle2 className="h-5 w-5 text-emerald-600 shrink-0 mt-0.5" />
          <p className="font-medium text-xs">{message}</p>
        </div>
      )}

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

      {/* Pending Advisee Petitions Section */}
      <section data-testid="faculty-advisee-requests-section" className="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
        <div className="p-4 border-b bg-slate-50 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <FileCheck className="h-5 w-5 text-[#1F6FEB]" />
            <h2 className="text-sm font-semibold text-slate-900">Pending Advisee Petitions & Departmental Endorsement</h2>
            <span
              data-testid="faculty-advisee-requests-count"
              className="ml-2 rounded-full bg-amber-100 text-amber-800 px-2 py-0.5 text-xs font-bold"
            >
              {requests.length} Pending
            </span>
          </div>
        </div>

        {requests.length === 0 ? (
          <div data-testid="faculty-advisee-requests-empty" className="p-8 text-center text-sm text-slate-500">
            No pending leave or program change petitions submitted by your advisees.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table data-testid="faculty-advisee-requests-table" className="w-full text-left text-sm">
              <thead className="bg-slate-50 border-b text-xs uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="px-4 py-3">Student Record</th>
                  <th className="px-4 py-3">Petition Type</th>
                  <th className="px-4 py-3">Reason / Justification</th>
                  <th className="px-4 py-3">Effective Date</th>
                  <th className="px-4 py-3">Departmental Endorsement</th>
                  <th className="px-4 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {requests.map((req) => (
                  <tr key={req.id} data-testid={`faculty-advisee-req-row-${req.id}`} className="hover:bg-slate-50/50">
                    <td className="px-4 py-3 font-mono text-xs text-slate-800 font-semibold">
                      {req.studentId.substring(0, 18)}...
                    </td>
                    <td className="px-4 py-3 font-semibold text-slate-800">
                      <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-bold ${
                        req.requestType === 'LEAVE'
                          ? 'bg-purple-50 text-purple-700 border border-purple-200'
                          : req.requestType === 'TRANSFER'
                          ? 'bg-blue-50 text-blue-700 border border-blue-200'
                          : 'bg-slate-100 text-slate-700 border border-slate-200'
                      }`}>
                        {req.requestType}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs text-slate-600 max-w-xs truncate" title={req.reason}>
                      {req.reason || '—'}
                    </td>
                    <td className="px-4 py-3 text-xs text-slate-600 whitespace-nowrap">
                      {req.effectiveDate || '—'}
                    </td>
                    <td className="px-4 py-3">
                      {req.endorsement ? (
                        <span
                          data-testid={`faculty-request-endorsement-${req.id}`}
                          className="inline-flex items-center gap-1 rounded bg-teal-50 border border-teal-200 px-2 py-0.5 text-xs text-teal-800 font-semibold"
                        >
                          <CheckCircle2 className="h-3 w-3 text-teal-600" />
                          {req.endorsement.split(':')[0]}
                        </span>
                      ) : (
                        <span
                          data-testid={`faculty-request-endorsement-${req.id}`}
                          className="inline-flex items-center gap-1 rounded bg-amber-50 border border-amber-200 px-2 py-0.5 text-xs text-amber-800 font-semibold"
                        >
                          Awaiting HOD Review
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-right">
                      {req.endorsement ? (
                        <span className="text-xs text-teal-700 font-medium">Endorsed</span>
                      ) : (
                        <button
                          type="button"
                          data-testid={`faculty-endorse-btn-${req.id}`}
                          disabled={loading}
                          onClick={() => {
                            setEndorsingReqId(req.id);
                            setEndorsementRemarks('');
                            setEndorsementType('RECOMMENDED');
                          }}
                          className="inline-flex items-center gap-1 rounded-lg bg-[#1F6FEB] px-3 py-1 text-xs font-semibold text-white shadow-sm hover:bg-[#12315C] transition-colors disabled:opacity-50"
                        >
                          <ThumbsUp className="h-3.5 w-3.5" />
                          Endorse Petition
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Endorsement Modal */}
      {endorsingReqId && (
        <div data-testid="faculty-endorse-modal" className="fixed inset-0 bg-black/40 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl border max-w-md w-full p-6 space-y-4">
            <div className="flex items-center gap-2 text-[#0B1F3A] font-semibold text-base">
              <FileCheck className="h-5 w-5 text-[#1F6FEB]" />
              <span>Departmental Review & Endorsement</span>
            </div>
            <p className="text-xs text-slate-500">
              Provide your faculty recommendation for this student lifecycle petition before the Registrar performs final administrative processing.
            </p>

            <div className="space-y-3">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Endorsement Recommendation
                </label>
                <select
                  data-testid="faculty-endorse-select"
                  value={endorsementType}
                  onChange={(e) => setEndorsementType(e.target.value)}
                  className="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs text-slate-800 focus:border-[#1F6FEB] focus:outline-none"
                >
                  <option value="RECOMMENDED">RECOMMENDED — Fulfills Departmental Criteria</option>
                  <option value="CONDITIONALLY_RECOMMENDED">CONDITIONALLY_RECOMMENDED — Requires Remediation / Verification</option>
                  <option value="NOT_RECOMMENDED">NOT_RECOMMENDED — Academic Conflict or Unsatisfactory Standing</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Departmental Remarks & Justification *
                </label>
                <textarea
                  data-testid="faculty-endorse-remarks-input"
                  rows={3}
                  value={endorsementRemarks}
                  onChange={(e) => setEndorsementRemarks(e.target.value)}
                  placeholder="Enter departmental review findings and faculty recommendation rationale (min 5 characters)..."
                  className="w-full rounded-lg border border-slate-300 p-2 text-xs text-slate-800 placeholder:text-slate-400 focus:border-[#1F6FEB] focus:outline-none"
                />
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 pt-2 border-t">
              <button
                type="button"
                data-testid="faculty-endorse-cancel-btn"
                onClick={() => setEndorsingReqId(null)}
                className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                data-testid="faculty-endorse-confirm-btn"
                disabled={loading || endorsementRemarks.trim().length < 5}
                onClick={() => void submitEndorsement()}
                className="inline-flex items-center gap-1 rounded-lg bg-[#1F6FEB] px-3 py-1.5 text-xs font-semibold text-white shadow-sm hover:bg-[#12315C] transition-colors disabled:opacity-50"
              >
                <CheckCircle2 className="h-3.5 w-3.5" />
                Submit Endorsement
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
