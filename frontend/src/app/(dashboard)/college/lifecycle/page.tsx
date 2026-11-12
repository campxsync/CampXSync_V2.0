'use client';

import { useEffect, useState } from 'react';
import {
  Activity,
  AlertCircle,
  CheckCircle2,
  Clock,
  FileText,
  HelpCircle,
  History,
  ShieldCheck,
  User,
  XCircle,
  ArrowRightLeft,
  Calendar,
  Send,
  Sparkles,
  BarChart3
} from 'lucide-react';

type LifecycleReport = {
  studentCount: number;
  pendingRequests: number;
  byStatus: Record<string, number>;
};

type RequestRow = {
  id: string;
  studentId: string;
  requestType: string;
  status: string;
  currentStatus?: string;
  reason?: string;
  effectiveDate?: string;
  created_at?: string;
};

function parseProblemDetails(text: string): { message: string; isDecided: boolean } {
  try {
    const parsed = JSON.parse(text);
    const mainMsg = parsed.detail || parsed.title || parsed.message || text;
    const isDecided = parsed.code === 'REQUEST_ALREADY_DECIDED' || parsed.status === 409;
    const meta = [parsed.code, parsed.correlationId ? `Trace: ${parsed.correlationId}` : null]
      .filter(Boolean)
      .join(' • ');
    return {
      message: meta ? `${mainMsg} [${meta}]` : mainMsg,
      isDecided,
    };
  } catch {
    return { message: text, isDecided: false };
  }
}

export default function CollegeLifecycleDeskPage() {
  const [requests, setRequests] = useState<RequestRow[]>([]);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [studentId, setStudentId] = useState('');
  const [programId, setProgramId] = useState('');
  const [graduatedOn, setGraduatedOn] = useState('2026-11-07');
  const [report, setReport] = useState('');
  const [reportData, setReportData] = useState<LifecycleReport | null>(null);
  const [historyText, setHistoryText] = useState('');
  const [advisorUserId, setAdvisorUserId] = useState('');
  const [fromStatus, setFromStatus] = useState('ACTIVE');
  const [toStatus, setToStatus] = useState('ON_LEAVE');
  const [requiresApproval, setRequiresApproval] = useState(true);
  const [loading, setLoading] = useState(false);

  // Administrative standing management state
  const [lookupStudentId, setLookupStudentId] = useState('');
  const [studentStanding, setStudentStanding] = useState<string | null>(null);
  const [targetAdminStatus, setTargetAdminStatus] = useState('ACTIVE');
  const [adminTransitionReason, setAdminTransitionReason] = useState('Administrative standing adjustment by Registrar');

  // Rejection modal state
  const [rejectingRequestId, setRejectingRequestId] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  async function fetchStudentStanding(idToFetch: string) {
    if (!idToFetch) return;
    try {
      const res = await fetch(`/api/college/lifecycle?view=status&studentId=${encodeURIComponent(idToFetch)}`, { cache: 'no-store' });
      if (res.ok) {
        const data = await res.json();
        setStudentStanding(data.status || 'UNKNOWN');
      } else {
        setStudentStanding('NOT_FOUND');
      }
    } catch {
      setStudentStanding('ERROR');
    }
  }

  async function performAdminTransition() {
    if (!lookupStudentId) {
      setError('Please provide a Student Record ID to update standing.');
      return;
    }
    setError('');
    setMessage('');
    setLoading(true);
    try {
      const res = await fetch('/api/college/lifecycle', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          action: 'transition',
          studentId: lookupStudentId,
          targetStatus: targetAdminStatus,
          reason: adminTransitionReason,
        }),
      });
      const text = await res.text();
      if (!res.ok) {
        setError(parseProblemDetails(text).message);
        return;
      }
      setMessage(`Student ${lookupStudentId} standing successfully updated to ${targetAdminStatus}.`);
      await fetchStudentStanding(lookupStudentId);
      await load();
    } catch (e: any) {
      setError(e?.message || 'Failed to update student standing.');
    } finally {
      setLoading(false);
    }
  }

  async function load() {
    setLoading(true);
    try {
      const [res, reportRes] = await Promise.all([
        fetch('/api/college/lifecycle', { cache: 'no-store' }),
        fetch('/api/college/lifecycle?view=report', { cache: 'no-store' }),
      ]);
      const text = await res.text();
      if (!res.ok) {
        setError(parseProblemDetails(text).message);
        return;
      }
      const json = JSON.parse(text);
      setRequests(json.requests || []);
      if (reportRes.ok) {
        const reportText = await reportRes.text();
        setReport(reportText);
        try {
          setReportData(JSON.parse(reportText));
        } catch (_) {}
      }
    } catch (e: any) {
      setError(e?.message || 'Failed to load lifecycle data.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function decide(requestId: string, decision: 'APPROVED' | 'REJECTED', reasonStr?: string) {
    if (decision === 'REJECTED' && (!reasonStr || reasonStr.trim().length < 5)) {
      setError('Please provide a mandatory reason for rejecting this lifecycle request (at least 5 characters).');
      return;
    }
    setError('');
    setMessage('');
    setLoading(true);
    try {
      const res = await fetch('/api/college/lifecycle', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          action: 'decide',
          requestId,
          decision,
          reason: reasonStr?.trim() || undefined,
        }),
      });
      const text = await res.text();
      if (!res.ok) {
        const { message: errMessage, isDecided } = parseProblemDetails(text);
        setError(errMessage);
        // Always refresh queue on response to ensure UI is in sync with PostgreSQL state
        await load();
        return;
      }
      setMessage(`Lifecycle request ${decision === 'APPROVED' ? 'APPROVED' : 'REJECTED'} successfully! Student record and status updated.`);
      setRejectingRequestId(null);
      setRejectReason('');
      await load();
    } catch (e: any) {
      setError(e?.message || 'Failed to process decision.');
    } finally {
      setLoading(false);
    }
  }

  async function graduate(action: 'graduate-validate' | 'graduate-record') {
    setError('');
    setMessage('');
    try {
      const res = await fetch('/api/college/lifecycle', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action, studentId, programId, graduatedOn, notes: 'Recorded from lifecycle desk' }),
      });
      const text = await res.text();
      if (!res.ok) {
        setError(parseProblemDetails(text).message);
        return;
      }
      let successMsg = text;
      try {
        const json = JSON.parse(text);
        if (action === 'graduate-validate') {
          if (json.eligible) {
            successMsg = 'Student is ELIGIBLE for graduation. All academic standing criteria and open petition checks passed.';
          } else {
            setError(`Student is NOT eligible for graduation. Blockers: ${(json.failures || []).join(', ')}`);
            return;
          }
        } else if (action === 'graduate-record') {
          successMsg = 'Graduation certified and officially recorded! Student standing updated to GRADUATED.';
        }
      } catch (_) {}
      setMessage(successMsg);
      if (action === 'graduate-record') {
        await load();
      }
    } catch (e: any) {
      setError(e?.message || 'Failed to execute graduation action.');
    }
  }

  async function assignAdvisorAction() {
    setError('');
    setMessage('');
    if (!studentId || !advisorUserId) {
      setError('Please provide both Student ID and Faculty Advisor User ID.');
      return;
    }
    setLoading(true);
    try {
      const res = await fetch('/api/college/lifecycle', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'assign-advisor', advisorUserId, studentId }),
      });
      const text = await res.text();
      if (!res.ok) {
        setError(parseProblemDetails(text).message);
        return;
      }
      setMessage(`Faculty advisor ${advisorUserId} successfully assigned to student ${studentId}.`);
    } catch (e: any) {
      setError(e?.message || 'Failed to assign faculty advisor.');
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
            <Activity className="h-6 w-6 text-[#1F6FEB]" />
            <h1 data-testid="college-lifecycle-heading" className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
              Student Lifecycle & Leave Review Desk
            </h1>
          </div>
          <p className="mt-1 text-sm text-[#475569]">
            Review, approve, and reject formal student leave-of-absence, program transfer, and status transition requests.
          </p>
        </div>
        <div className="flex items-center gap-2 bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-xs font-semibold text-slate-700">
          <ShieldCheck className="h-4 w-4 text-[#1F6FEB]" />
          <span>Role: Registrar / College Administrator (R31)</span>
        </div>
      </div>

      {/* Notifications */}
      {message && (
        <div data-testid="college-lifecycle-success-msg" className="rounded-lg bg-emerald-50 border border-emerald-200 p-4 text-sm text-emerald-800 flex items-center gap-2">
          <CheckCircle2 className="h-5 w-5 text-emerald-600 shrink-0" />
          <span>{message}</span>
        </div>
      )}
      {error && (
        <div data-testid="college-lifecycle-error-msg" className="rounded-lg bg-red-50 border border-red-200 p-4 text-sm text-red-800 flex items-start gap-2">
          <AlertCircle className="h-5 w-5 text-red-600 shrink-0 mt-0.5" />
          <pre className="whitespace-pre-wrap font-sans text-xs">{error}</pre>
        </div>
      )}

      {/* Lifecycle Summary & Status Transition Analytics */}
      <section data-testid="college-lifecycle-summary-section" className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 border-b pb-3">
          <div>
            <h2 className="text-sm font-semibold text-slate-900 flex items-center gap-2">
              <BarChart3 className="h-4 w-4 text-[#1F6FEB]" />
              Institutional Lifecycle Summary & Status Transition Analytics
            </h2>
            <p className="text-xs text-slate-500">
              Aggregated institutional breakdown of active student standing and pending transition petitions.
            </p>
          </div>
          <span className="text-[11px] font-mono text-slate-400">Live Snapshot</span>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <div className="rounded-lg border border-slate-100 bg-slate-50/70 p-3">
            <span className="text-[11px] font-medium text-slate-500 uppercase tracking-wider">Total Enrolled</span>
            <p data-testid="college-lifecycle-stat-total" className="mt-1 text-2xl font-bold text-slate-900">
              {reportData?.studentCount ?? 0}
            </p>
          </div>
          <div className="rounded-lg border border-amber-200 bg-amber-50/50 p-3">
            <span className="text-[11px] font-medium text-amber-700 uppercase tracking-wider">Pending Petitions</span>
            <p data-testid="college-lifecycle-stat-pending" className="mt-1 text-2xl font-bold text-amber-900">
              {reportData?.pendingRequests ?? 0}
            </p>
          </div>
          <div className="rounded-lg border border-emerald-200 bg-emerald-50/50 p-3">
            <span className="text-[11px] font-medium text-emerald-700 uppercase tracking-wider">Active Standing</span>
            <p data-testid="college-lifecycle-stat-active" className="mt-1 text-2xl font-bold text-emerald-900">
              {reportData?.byStatus?.['ACTIVE'] ?? 0}
            </p>
          </div>
          <div className="rounded-lg border border-purple-200 bg-purple-50/50 p-3">
            <span className="text-[11px] font-medium text-purple-700 uppercase tracking-wider">On Approved Leave</span>
            <p data-testid="college-lifecycle-stat-on-leave" className="mt-1 text-2xl font-bold text-purple-900">
              {reportData?.byStatus?.['ON_LEAVE'] ?? 0}
            </p>
          </div>
        </div>

        <div className="grid grid-cols-3 gap-3 pt-1">
          <div className="rounded-lg border border-teal-200 bg-teal-50/40 p-2.5 text-center">
            <span className="text-[10px] font-medium text-teal-700 uppercase">Graduated</span>
            <p data-testid="college-lifecycle-stat-graduated" className="text-lg font-bold text-teal-900">
              {reportData?.byStatus?.['GRADUATED'] ?? 0}
            </p>
          </div>
          <div className="rounded-lg border border-blue-200 bg-blue-50/40 p-2.5 text-center">
            <span className="text-[10px] font-medium text-blue-700 uppercase">Transferred</span>
            <p data-testid="college-lifecycle-stat-transferred" className="text-lg font-bold text-blue-900">
              {reportData?.byStatus?.['TRANSFERRED'] ?? 0}
            </p>
          </div>
          <div className="rounded-lg border border-rose-200 bg-rose-50/40 p-2.5 text-center">
            <span className="text-[10px] font-medium text-rose-700 uppercase">Withdrawn</span>
            <p data-testid="college-lifecycle-stat-withdrawn" className="text-lg font-bold text-rose-900">
              {reportData?.byStatus?.['WITHDRAWN'] ?? 0}
            </p>
          </div>
        </div>
      </section>

      {/* Pending Requests Section */}
      <section className="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
        <div className="p-4 border-b bg-slate-50 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Clock className="h-5 w-5 text-[#1F6FEB]" />
            <h2 className="text-sm font-semibold text-slate-900">Pending Lifecycle & Leave Requests</h2>
            <span
              data-testid="college-lifecycle-req-count"
              className="ml-2 rounded-full bg-blue-100 text-blue-800 px-2 py-0.5 text-xs font-bold"
            >
              {requests.length} Pending
            </span>
          </div>
          <button
            type="button"
            onClick={() => void load()}
            className="text-xs text-[#1F6FEB] font-medium hover:underline"
          >
            Refresh Queue
          </button>
        </div>

        {requests.length === 0 ? (
          <div className="p-8 text-center text-sm text-slate-500">
            No pending student lifecycle requests in queue.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 border-b text-xs uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="px-4 py-3">Student Record</th>
                  <th className="px-4 py-3">Type</th>
                  <th className="px-4 py-3">Reason / Justification</th>
                  <th className="px-4 py-3">Effective Date</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3 text-right">Decision Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {requests.map((req, idx) => (
                  <tr key={req.id} data-testid={`college-lifecycle-req-row-${req.id}`} className="hover:bg-slate-50/50">
                    <td className="px-4 py-3 font-mono text-xs text-slate-700">
                      <div className="flex flex-col">
                        <button
                          type="button"
                          onClick={() => {
                            setLookupStudentId(req.studentId);
                            void fetchStudentStanding(req.studentId);
                          }}
                          className="font-semibold text-left text-slate-800 hover:text-[#1F6FEB] hover:underline"
                          title="Click to inspect and manage standing for this student"
                        >
                          {req.studentId.substring(0, 18)}...
                        </button>
                        {req.currentStatus && (
                          <span className="text-[10px] font-sans mt-0.5">
                            Standing:{' '}
                            <span
                              className={`font-bold px-1.5 py-0.2 rounded ${
                                req.currentStatus === 'ACTIVE'
                                  ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                                  : req.currentStatus === 'ON_LEAVE'
                                  ? 'bg-purple-50 text-purple-700 border border-purple-200'
                                  : req.currentStatus === 'TRANSFERRED'
                                  ? 'bg-blue-50 text-blue-700 border border-blue-200'
                                  : 'bg-rose-50 text-rose-700 border border-rose-200'
                              }`}
                            >
                              {req.currentStatus}
                            </span>
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3 font-semibold text-slate-800">
                      <span
                        data-testid={`college-lifecycle-req-type-${req.id}`}
                        className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-bold ${
                          req.requestType === 'LEAVE'
                            ? 'bg-purple-50 text-purple-700 border border-purple-200'
                            : req.requestType === 'TRANSFER'
                            ? 'bg-blue-50 text-blue-700 border border-blue-200'
                            : 'bg-slate-100 text-slate-700 border border-slate-200'
                        }`}
                      >
                        {req.requestType}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-slate-600 max-w-sm">
                      <p className="line-clamp-2 text-xs" title={req.reason}>
                        {req.reason || '—'}
                      </p>
                    </td>
                    <td className="px-4 py-3 text-xs text-slate-600 whitespace-nowrap">
                      {req.effectiveDate ? (
                        <span className="inline-flex items-center gap-1">
                          <Calendar className="h-3 w-3 text-slate-400" />
                          {req.effectiveDate}
                        </span>
                      ) : (
                        '—'
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        data-testid={`college-lifecycle-req-status-${req.id}`}
                        className="inline-flex items-center gap-1 rounded-full bg-amber-50 border border-amber-200 px-2.5 py-0.5 text-xs font-semibold text-amber-800"
                      >
                        <Clock className="h-3 w-3" />
                        {req.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          type="button"
                          data-testid={`college-lifecycle-approve-btn-${req.id}`}
                          disabled={loading}
                          onClick={() => void decide(req.id, 'APPROVED')}
                          className="inline-flex items-center gap-1 rounded-lg bg-emerald-600 px-3 py-1 text-xs font-semibold text-white shadow-sm hover:bg-emerald-700 transition-colors disabled:opacity-50"
                        >
                          <CheckCircle2 className="h-3.5 w-3.5" />
                          Approve
                        </button>
                        <button
                          type="button"
                          data-testid={`college-lifecycle-reject-btn-${req.id}`}
                          disabled={loading}
                          onClick={() => {
                            setRejectingRequestId(req.id);
                            setRejectReason('');
                          }}
                          className="inline-flex items-center gap-1 rounded-lg border border-red-300 bg-white px-3 py-1 text-xs font-semibold text-red-600 hover:bg-red-50 transition-colors disabled:opacity-50"
                        >
                          <XCircle className="h-3.5 w-3.5" />
                          Reject
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Rejection Remarks Modal */}
      {rejectingRequestId && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl border max-w-md w-full p-6 space-y-4">
            <div className="flex items-center gap-2 text-red-600 font-semibold text-base">
              <XCircle className="h-5 w-5" />
              <span>Reject Student Lifecycle Request</span>
            </div>
            <p className="text-xs text-slate-500">
              Provide formal regulatory justification for rejecting this request. The student will view this notice in their lifecycle history.
            </p>
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">
                Rejection Reason / Regulatory Remarks
              </label>
              <textarea
                data-testid="college-lifecycle-reject-reason-input"
                rows={3}
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                placeholder="State the institutional reason for rejection (e.g. Insufficient medical documentation)..."
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-red-500 focus:outline-none focus:ring-1 focus:ring-red-500"
              />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                data-testid="college-lifecycle-cancel-reject-btn"
                onClick={() => setRejectingRequestId(null)}
                className="rounded-lg border px-3 py-1.5 text-xs font-semibold text-slate-600 hover:bg-slate-50"
              >
                Cancel
              </button>
              <button
                type="button"
                data-testid="college-lifecycle-confirm-reject-btn"
                onClick={() => void decide(rejectingRequestId, 'REJECTED', rejectReason)}
                className="rounded-lg bg-red-600 px-4 py-1.5 text-xs font-semibold text-white shadow-sm hover:bg-red-700"
              >
                Confirm Rejection
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Student Academic Standing Inspector & Administrative Status Transition */}
      <section data-testid="college-lifecycle-standing-section" className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm space-y-4">
        <div>
          <h2 className="text-sm font-semibold text-slate-900 flex items-center gap-2">
            <User className="h-4 w-4 text-[#1F6FEB]" />
            Student Academic Standing Inspector & Administrative Status Transition
          </h2>
          <p className="text-xs text-slate-500">
            Inspect a student&apos;s current standing in PostgreSQL and administratively transition or reinstate their status when processing exceptional petitions.
          </p>
        </div>

        <div className="grid gap-3 sm:grid-cols-3">
          <div className="sm:col-span-2">
            <label className="block text-xs font-medium text-slate-600 mb-1">Student Record ID (UUID)</label>
            <div className="flex gap-2">
              <input
                data-testid="college-lifecycle-lookup-student-input"
                className="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-mono"
                placeholder="e.g. 81dd67d3-1d5f-4d8e-8061-a6764861f924"
                value={lookupStudentId}
                onChange={(e) => setLookupStudentId(e.target.value.trim())}
              />
              <button
                type="button"
                data-testid="college-lifecycle-lookup-btn"
                disabled={loading || !lookupStudentId}
                onClick={() => void fetchStudentStanding(lookupStudentId)}
                className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50 shrink-0"
              >
                Inspect Standing
              </button>
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Current Standing</label>
            <div className="flex items-center h-8 px-3 rounded-lg border border-slate-200 bg-slate-50 text-xs font-bold">
              {studentStanding ? (
                <span
                  data-testid="college-lifecycle-current-standing-val"
                  className={
                    studentStanding === 'ACTIVE'
                      ? 'text-emerald-700'
                      : studentStanding === 'ON_LEAVE'
                      ? 'text-purple-700'
                      : studentStanding === 'TRANSFERRED'
                      ? 'text-blue-700'
                      : 'text-amber-700'
                  }
                >
                  {studentStanding}
                </span>
              ) : (
                <span className="text-slate-400 font-normal">Enter ID to check</span>
              )}
            </div>
          </div>
        </div>

        <div className="border-t border-slate-100 pt-3 grid gap-3 sm:grid-cols-2">
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Target Administrative Status</label>
            <select
              data-testid="college-lifecycle-admin-target-status"
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs"
              value={targetAdminStatus}
              onChange={(e) => setTargetAdminStatus(e.target.value)}
            >
              <option value="ACTIVE">ACTIVE (Enrolled & Good Standing)</option>
              <option value="ON_LEAVE">ON_LEAVE (Approved Leave of Absence)</option>
              <option value="TRANSFERRED">TRANSFERRED (Transferred Programme/Institute)</option>
              <option value="WITHDRAWN">WITHDRAWN (Voluntary Withdrawal)</option>
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Reason / Justification</label>
            <input
              data-testid="college-lifecycle-admin-reason-input"
              className="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs"
              value={adminTransitionReason}
              onChange={(e) => setAdminTransitionReason(e.target.value)}
              placeholder="e.g. Administrative clearance / Reinstatement"
            />
          </div>
        </div>

        <button
          type="button"
          data-testid="college-lifecycle-admin-transition-btn"
          disabled={loading || !lookupStudentId}
          onClick={() => void performAdminTransition()}
          className="rounded-lg bg-[#0B1F3A] px-4 py-2 text-xs font-semibold text-white hover:bg-[#152E52] disabled:opacity-50 transition-colors"
        >
          Update Student Standing
        </button>
      </section>

      {/* Transition Rules Configuration */}
      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm space-y-4">
        <div>
          <h2 className="text-sm font-semibold text-slate-900 flex items-center gap-2">
            <ArrowRightLeft className="h-4 w-4 text-[#1F6FEB]" />
            Configure Lifecycle Transition Rules
          </h2>
          <p className="text-xs text-slate-500">
            Define authorized status state machine transitions and whether registrar approval is mandatory.
          </p>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">From Status</label>
            <select
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs"
              value={fromStatus}
              onChange={(e) => setFromStatus(e.target.value)}
            >
              <option value="ACTIVE">ACTIVE</option>
              <option value="ON_LEAVE">ON_LEAVE</option>
              <option value="WITHDRAWN">WITHDRAWN</option>
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">To Status</label>
            <select
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs"
              value={toStatus}
              onChange={(e) => setToStatus(e.target.value)}
            >
              <option value="ON_LEAVE">ON_LEAVE</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="TRANSFERRED">TRANSFERRED</option>
              <option value="WITHDRAWN">WITHDRAWN</option>
            </select>
          </div>
        </div>
        <label className="flex items-center gap-2 text-xs font-medium text-slate-700">
          <input
            type="checkbox"
            className="rounded border-slate-300 text-[#1F6FEB]"
            checked={requiresApproval}
            onChange={(e) => setRequiresApproval(e.target.checked)}
          />
          Requires Registrar (R31) formal approval
        </label>
        <button
          type="button"
          className="rounded-lg bg-[#0B1F3A] px-4 py-2 text-xs font-semibold text-white hover:bg-[#152E52]"
          onClick={async () => {
            setError('');
            setMessage('');
            try {
              const res = await fetch('/api/college/lifecycle', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ action: 'rule', fromStatus, toStatus, requiresApproval, active: true }),
              });
              const text = await res.text();
              if (!res.ok) {
                setError(text);
                return;
              }
              setMessage(`Transition rule saved: ${fromStatus} → ${toStatus}.`);
            } catch (e: any) {
              setError(e?.message || 'Failed to save rule.');
            }
          }}
        >
          Save Transition Rule
        </button>
      </section>

      {/* Faculty Advisor Assignment */}
      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm space-y-4">
        <div>
          <h2 className="text-sm font-semibold text-slate-900 flex items-center gap-2">
            <User className="h-4 w-4 text-[#1F6FEB]" />
            Faculty Advisor Assignment & Advisee Allocation
          </h2>
          <p className="text-xs text-slate-500">
            Assign designated faculty members to students for academic mentorship and advisee lifecycle status tracking.
          </p>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Student Record ID (UUID)</label>
            <input
              data-testid="college-lifecycle-advisor-student-input"
              className="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-mono"
              placeholder="e.g. 81dd67d3-1d5f-4d8e-8061-a6764861f924"
              value={studentId}
              onChange={(e) => setStudentId(e.target.value.trim())}
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Faculty Advisor User ID (UUID)</label>
            <input
              data-testid="college-lifecycle-advisor-user-input"
              className="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-mono"
              placeholder="e.g. 11111111-9e05-0000-0000-000000000001"
              value={advisorUserId}
              onChange={(e) => setAdvisorUserId(e.target.value.trim())}
            />
          </div>
        </div>
        <button
          type="button"
          data-testid="college-lifecycle-advisor-assign-btn"
          disabled={loading}
          className="rounded-lg bg-[#0B1F3A] px-4 py-2 text-xs font-semibold text-white hover:bg-[#152E52] disabled:opacity-50 transition-colors"
          onClick={() => void assignAdvisorAction()}
        >
          Assign Faculty Advisor
        </button>
      </section>

      {/* Graduation Management */}
      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm space-y-4">
        <div>
          <h2 className="text-sm font-semibold text-slate-900 flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-[#1F6FEB]" />
            Graduation Eligibility & Recording
          </h2>
          <p className="text-xs text-slate-500">
            Verify programme completion criteria and record irreversible graduation.
          </p>
        </div>
        <div className="grid gap-3 sm:grid-cols-3">
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Student Record ID</label>
            <input
              data-testid="college-lifecycle-grad-student-input"
              className="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs"
              value={studentId}
              onChange={(e) => setStudentId(e.target.value)}
              placeholder="e.g. 81dd67d3-1d5f-4d8e-8061-a6764861f924"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Programme ID</label>
            <input
              data-testid="college-lifecycle-grad-program-input"
              className="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs"
              value={programId}
              onChange={(e) => setProgramId(e.target.value)}
              placeholder="Programme UUID"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Graduation Date</label>
            <input
              data-testid="college-lifecycle-grad-date-input"
              className="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs"
              type="date"
              value={graduatedOn}
              onChange={(e) => setGraduatedOn(e.target.value)}
            />
          </div>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            data-testid="college-lifecycle-grad-check-btn"
            className="rounded-lg border px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
            onClick={() => void graduate('graduate-validate')}
          >
            Check Eligibility
          </button>
          <button
            type="button"
            data-testid="college-lifecycle-grad-record-btn"
            className="rounded-lg bg-[#0B1F3A] px-4 py-1.5 text-xs font-semibold text-white hover:bg-[#152E52]"
            onClick={() => void graduate('graduate-record')}
          >
            Record Graduation
          </button>
        </div>
      </section>
    </main>
  );
}
