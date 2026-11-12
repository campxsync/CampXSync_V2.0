'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import {
  Activity,
  ArrowRightLeft,
  Calendar,
  CheckCircle2,
  Clock,
  FileText,
  History,
  Send,
  ShieldCheck,
  AlertCircle
} from 'lucide-react';

type StatusView = { status?: string; effectiveFrom?: string; studentId?: string };
type HistoryRow = { status: string; action: string; actorRole: string; at: string };
type RequestRow = {
  id: string;
  studentId: string;
  requestType: string;
  status: string;
  reason?: string;
  effectiveDate?: string;
  created_at?: string;
};

export default function StudentLifecyclePage() {
  const [studentId, setStudentId] = useState('');
  const [status, setStatus] = useState<StatusView | null>(null);
  const [history, setHistory] = useState<HistoryRow[]>([]);
  const [requests, setRequests] = useState<RequestRow[]>([]);
  const [activeTab, setActiveTab] = useState<'request' | 'requests' | 'history'>('request');
  const [requestType, setRequestType] = useState('TRANSFER');
  const [reason, setReason] = useState('');
  const [effectiveDate, setEffectiveDate] = useState('2026-12-01');
  const [returnDate, setReturnDate] = useState('2027-06-30');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(false);

  async function loadData() {
    setError('');
    try {
      // 1. Load Status
      const statusRes = await fetch('/api/student/lifecycle', { cache: 'no-store' });
      if (statusRes.ok) {
        const statusJson = await statusRes.json();
        setStatus(statusJson);
        if (statusJson.studentId) {
          setStudentId(statusJson.studentId);
        }
      }

      // 2. Load History
      const historyRes = await fetch('/api/student/lifecycle?view=history', { cache: 'no-store' });
      if (historyRes.ok) {
        const histJson = await historyRes.json();
        setHistory(histJson.history || []);
      }

      // 3. Load Requests
      const reqsRes = await fetch('/api/student/lifecycle?view=requests', { cache: 'no-store' });
      if (reqsRes.ok) {
        const reqsJson = await reqsRes.json();
        setRequests(reqsJson.requests || []);
      }
    } catch (e: any) {
      setError(e?.message || 'Failed to load lifecycle data.');
    }
  }

  useEffect(() => {
    void (async () => {
      await loadData();
    })();
  }, []);

  async function submitRequest() {
    if (!reason.trim() || reason.trim().length < 5) {
      setError('Please provide a detailed reason or justification for this lifecycle request (at least 5 characters).');
      return;
    }
    if ((requestType === 'LEAVE' || requestType === 'WITHDRAWAL') && !effectiveDate) {
      setError('Please provide a valid effective date.');
      return;
    }
    const todayStr = new Date().toISOString().split('T')[0];
    if ((requestType === 'LEAVE' || requestType === 'WITHDRAWAL') && effectiveDate < todayStr) {
      setError('Effective date cannot be in the past.');
      return;
    }
    if (requestType === 'LEAVE' && returnDate && returnDate <= effectiveDate) {
      setError('Expected return date must be strictly after the effective start date.');
      return;
    }
    setError('');
    setMessage('');
    setLoading(true);
    try {
      const fullReason = requestType === 'LEAVE' && returnDate
        ? `${reason.trim()} [Expected Return: ${returnDate}]`
        : reason.trim();
      const res = await fetch('/api/student/lifecycle', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          studentId: studentId || undefined,
          requestType,
          reason: fullReason,
          effectiveDate: (requestType === 'WITHDRAWAL' || requestType === 'LEAVE') ? effectiveDate : undefined,
        }),
      });
      const text = await res.text();
      if (!res.ok) {
        setError(text);
        return;
      }
      setMessage(`Lifecycle request (${requestType}) submitted successfully! It has been forwarded to the Registrar for review.`);
      setReason('');
      await loadData();
      setActiveTab('requests');
    } catch (e: any) {
      setError(e?.message || 'Network error submitting request.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="mx-auto max-w-4xl space-y-6 p-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between border-b pb-4 gap-4">
        <div>
          <div className="flex items-center gap-2">
            <Activity className="h-6 w-6 text-[#1F6FEB]" />
            <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">Student Lifecycle Desk</h1>
          </div>
          <p className="mt-1 text-sm text-[#475569]">
            Manage academic status, submit program transfer, leave of absence, and track registrar decisions.
          </p>
        </div>
        <Link
          href="/student/documents"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-[#1F6FEB] hover:underline"
        >
          <FileText className="h-4 w-4" /> Go to Documents Desk
        </Link>
      </div>

      {/* Current Status Card */}
      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">Current Academic Standing</span>
            <div className="mt-1 flex items-center gap-3">
              <span
                data-testid="lifecycle-status-badge"
                className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 border border-emerald-200 px-3 py-1 text-sm font-bold text-emerald-800"
              >
                <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                {status?.status || 'ACTIVE'}
              </span>
              {status?.effectiveFrom && (
                <span className="text-xs text-slate-500">
                  Effective: {new Date(status.effectiveFrom).toLocaleDateString()}
                </span>
              )}
            </div>
          </div>
          <div className="flex items-center gap-2 text-xs text-slate-600 bg-slate-50 p-3 rounded-lg border border-slate-100">
            <ShieldCheck className="h-4 w-4 text-[#1F6FEB]" />
            <span>Tenant RLS & Gateway Audited Workflow</span>
          </div>
        </div>
      </section>

      {/* Tabs */}
      <div className="flex gap-2 border-b border-slate-200 pb-1">
        <button
          type="button"
          onClick={() => setActiveTab('request')}
          className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-t-lg transition-colors ${
            activeTab === 'request'
              ? 'border-b-2 border-[#1F6FEB] text-[#1F6FEB] bg-blue-50/50'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          <Send className="h-4 w-4" /> Request a Change
        </button>
        <button
          type="button"
          data-testid="lifecycle-tab-requests"
          onClick={() => setActiveTab('requests')}
          className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-t-lg transition-colors ${
            activeTab === 'requests'
              ? 'border-b-2 border-[#1F6FEB] text-[#1F6FEB] bg-blue-50/50'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          <Clock className="h-4 w-4" /> My Requests ({requests.length})
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('history')}
          className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-t-lg transition-colors ${
            activeTab === 'history'
              ? 'border-b-2 border-[#1F6FEB] text-[#1F6FEB] bg-blue-50/50'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          <History className="h-4 w-4" /> Lifecycle History ({history.length})
        </button>
      </div>

      {/* Notifications */}
      {message && (
        <div data-testid="lifecycle-success-msg" className="rounded-lg bg-emerald-50 border border-emerald-200 p-4 text-sm text-emerald-800 flex items-center gap-2">
          <CheckCircle2 className="h-5 w-5 text-emerald-600 shrink-0" />
          <span>{message}</span>
        </div>
      )}
      {error && (
        <div data-testid="lifecycle-error-msg" className="rounded-lg bg-red-50 border border-red-200 p-4 text-sm text-red-800 flex items-start gap-2">
          <AlertCircle className="h-5 w-5 text-red-600 shrink-0 mt-0.5" />
          <pre className="whitespace-pre-wrap font-sans text-xs">{error}</pre>
        </div>
      )}

      {/* Tab 1: Submit Request Form */}
      {activeTab === 'request' && (
        <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm space-y-5">
          <div className="border-b pb-3">
            <h2 className="text-base font-semibold text-slate-900 flex items-center gap-2">
              <ArrowRightLeft className="h-5 w-5 text-[#1F6FEB]" />
              Initiate Lifecycle Request
            </h2>
            <p className="mt-1 text-xs text-slate-500">
              Submit a formal request for department and registrar approval. All submissions are immutable and tracked with a cryptographic trace ID.
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 mb-1">
                Request Type
              </label>
              <select
                data-testid="lifecycle-select-type"
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-800 focus:border-[#1F6FEB] focus:outline-none"
                value={requestType}
                onChange={(e) => setRequestType(e.target.value)}
              >
                <option value="TRANSFER">Program Transfer (TRANSFER)</option>
                <option value="LEAVE">Leave of Absence (LEAVE)</option>
                <option value="REINSTATE">Reinstatement (REINSTATE)</option>
                <option value="WITHDRAWAL">Voluntary Withdrawal (WITHDRAWAL)</option>
              </select>
            </div>

            {(requestType === 'WITHDRAWAL' || requestType === 'LEAVE') && (
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 mb-1">
                  Effective Start Date <span className="text-red-500">*</span>
                </label>
                <input
                  type="date"
                  data-testid="lifecycle-input-date"
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-800 focus:border-[#1F6FEB] focus:outline-none"
                  value={effectiveDate}
                  onChange={(e) => setEffectiveDate(e.target.value)}
                />
              </div>
            )}

            {requestType === 'LEAVE' && (
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 mb-1">
                  Expected Return Date
                </label>
                <input
                  type="date"
                  data-testid="lifecycle-input-return-date"
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-800 focus:border-[#1F6FEB] focus:outline-none"
                  value={returnDate}
                  onChange={(e) => setReturnDate(e.target.value)}
                />
              </div>
            )}
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 mb-1">
              Rationale / Reason for Request <span className="text-red-500">*</span>
            </label>
            <textarea
              data-testid="lifecycle-input-reason"
              rows={4}
              className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-800 placeholder-slate-400 focus:border-[#1F6FEB] focus:outline-none"
              placeholder="Detail your request rationale, proposed new programme, or supporting circumstances..."
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </div>

          <div className="flex justify-end pt-2">
            <button
              type="button"
              data-testid="lifecycle-submit-btn"
              disabled={loading}
              onClick={() => void submitRequest()}
              className="inline-flex items-center gap-2 rounded-lg bg-[#0B1F3A] hover:bg-[#1E3A8A] px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors disabled:opacity-50"
            >
              <Send className="h-4 w-4" />
              {loading ? 'Submitting...' : 'Submit Request'}
            </button>
          </div>
        </section>
      )}

      {/* Tab 2: My Requests */}
      {activeTab === 'requests' && (
        <section className="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
          <div className="p-4 border-b bg-slate-50 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-slate-900">Submitted Lifecycle Requests</h2>
            <span className="text-xs text-slate-500">Live PostgreSQL Data</span>
          </div>

          {requests.length === 0 ? (
            <div className="p-8 text-center text-sm text-slate-500">
              No lifecycle requests submitted yet. Use the &quot;Request a Change&quot; tab to initiate one.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 border-b text-xs uppercase tracking-wider text-slate-500">
                  <tr>
                    <th className="px-4 py-3">Type</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3">Reason / Details</th>
                    <th className="px-4 py-3">Submitted</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {requests.map((req, idx) => (
                    <tr key={req.id || idx} data-testid={`lifecycle-req-row-${idx}`} className="hover:bg-slate-50/50">
                      <td className="px-4 py-3 font-semibold text-slate-800" data-testid={`lifecycle-req-type-${idx}`}>
                        {req.requestType}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          data-testid={`lifecycle-req-status-${idx}`}
                          className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                            req.status === 'APPROVED'
                              ? 'bg-emerald-100 text-emerald-800 border border-emerald-200'
                              : req.status === 'REJECTED'
                              ? 'bg-red-100 text-red-800 border border-red-200'
                              : 'bg-amber-100 text-amber-800 border border-amber-200'
                          }`}
                        >
                          <Clock className="h-3 w-3" />
                          {req.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-slate-600 max-w-xs truncate" title={req.reason}>
                        {req.reason || '—'}
                      </td>
                      <td className="px-4 py-3 text-xs text-slate-500">
                        {req.created_at ? new Date(req.created_at).toLocaleDateString() : 'Just now'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {/* Tab 3: History */}
      {activeTab === 'history' && (
        <section className="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
          <div className="p-4 border-b bg-slate-50">
            <h2 className="text-sm font-semibold text-slate-900">Academic Standing History</h2>
          </div>
          {history.length === 0 ? (
            <div className="p-8 text-center text-sm text-slate-500">
              No historical status transitions recorded.
            </div>
          ) : (
            <ul className="divide-y divide-slate-100 p-4 space-y-2">
              {history.map((row, idx) => (
                <li key={idx} className="flex items-center justify-between text-xs py-2 text-slate-700">
                  <div className="flex items-center gap-2">
                    <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                    <span className="font-semibold">{row.status}</span>
                    <span className="text-slate-400">·</span>
                    <span className="text-slate-600">{row.action}</span>
                    <span className="text-slate-400">·</span>
                    <span className="text-slate-500">by {row.actorRole}</span>
                  </div>
                  <span className="text-slate-400 font-mono">{new Date(row.at).toLocaleString()}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
    </main>
  );
}
