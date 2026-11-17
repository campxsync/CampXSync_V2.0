'use client';

import { useState } from 'react';
import { History, Link2, RefreshCw, Shield } from 'lucide-react';

type LinkStatus = {
  studentId?: string;
  linkStatus?: string;
  verificationStatus?: string;
  studentStatus?: string;
  portalAccessActive?: boolean;
  linkedIamUserId?: string;
  provisioningRequestCount?: number;
};

type ProvisioningRow = {
  id: string;
  studentId?: string;
  requestStatus?: string;
  failureReason?: string;
  requestedAt?: string;
};

type AuditRow = {
  id?: string;
  action?: string;
  studentId?: string;
  actorUserId?: string;
  eventTime?: string;
  detailJson?: string;
};

export default function CollegeAccountProvisioningDeskPage() {
  const [studentId, setStudentId] = useState('');
  const [linkStatus, setLinkStatus] = useState<LinkStatus | null>(null);
  const [requests, setRequests] = useState<ProvisioningRow[]>([]);
  const [auditRows, setAuditRows] = useState<AuditRow[]>([]);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(false);

  async function loadLinkStatus() {
    setError('');
    setMessage('');
    setLoading(true);
    try {
      const res = await fetch(
        `/api/college/account-provisioning?view=link-status&studentId=${encodeURIComponent(studentId)}`,
        { cache: 'no-store' }
      );
      const text = await res.text();
      if (!res.ok) {
        setError(text || `Link status failed (${res.status})`);
        setLinkStatus(null);
        return;
      }
      setLinkStatus(JSON.parse(text));
      setMessage('Account link status loaded');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load link status');
    } finally {
      setLoading(false);
    }
  }

  async function loadProvisioningHistory() {
    setError('');
    setMessage('');
    setLoading(true);
    try {
      const res = await fetch(
        `/api/college/account-provisioning?view=provisioning-requests&studentId=${encodeURIComponent(studentId)}`,
        { cache: 'no-store' }
      );
      const text = await res.text();
      if (!res.ok) {
        setError(text || `Provisioning history failed (${res.status})`);
        setRequests([]);
        return;
      }
      const data = JSON.parse(text);
      setRequests(Array.isArray(data) ? data : []);
      setMessage('Past login requests loaded');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load provisioning history');
    } finally {
      setLoading(false);
    }
  }

  async function retryFailed(requestId: string) {
    setError('');
    setMessage('');
    setLoading(true);
    try {
      const res = await fetch('/api/college/account-provisioning', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'retry-provisioning', studentId, requestId }),
      });
      const text = await res.text();
      if (!res.ok) {
        setError(text || `Retry failed (${res.status})`);
        return;
      }
      setMessage(`Retry accepted for request ${requestId}`);
      await loadProvisioningHistory();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Retry failed');
    } finally {
      setLoading(false);
    }
  }

  async function loadAudit() {
    setError('');
    setMessage('');
    setLoading(true);
    try {
      const res = await fetch('/api/college/account-provisioning?view=account-role-audit&limit=50', {
        cache: 'no-store',
      });
      const text = await res.text();
      if (!res.ok) {
        setError(text || `Audit list failed (${res.status})`);
        setAuditRows([]);
        return;
      }
      const data = JSON.parse(text);
      setAuditRows(Array.isArray(data) ? data : []);
      setMessage('Account and role audit loaded');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load audit');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-8 p-6" data-testid="college-account-provisioning-desk">
      <header>
        <h1 className="text-2xl font-semibold text-[#0B1F3A]">Account provisioning desk</h1>
        <p className="mt-1 text-sm text-[#6B7688]">
          Staff view of login link status, past login requests, failed-setup retry, and account/role audit.
        </p>
      </header>

      <section className="rounded-lg border border-[#D7DEE8] bg-white p-4" data-testid="account-link-status-panel">
        <div className="mb-3 flex items-center gap-2 text-[#0B1F3A]">
          <Link2 className="h-4 w-4" />
          <h2 className="font-medium">Staff view of account link status</h2>
        </div>
        <label className="block text-xs text-[#6B7688]" htmlFor="ap-student-id">
          Student ID
        </label>
        <div className="mt-1 flex flex-wrap gap-2">
          <input
            id="ap-student-id"
            data-testid="account-provisioning-student-id"
            className="min-w-[280px] flex-1 rounded border border-[#D7DEE8] px-3 py-2 text-sm"
            value={studentId}
            onChange={(e) => setStudentId(e.target.value)}
            placeholder="Student UUID"
          />
          <button
            type="button"
            data-testid="account-link-status-load"
            className="rounded bg-[#0B1F3A] px-3 py-2 text-sm text-white disabled:opacity-50"
            disabled={!studentId || loading}
            onClick={loadLinkStatus}
          >
            Load link status
          </button>
        </div>
        {linkStatus && (
          <dl className="mt-4 grid gap-2 text-sm sm:grid-cols-2" data-testid="account-link-status-result">
            <div>
              <dt className="text-[#6B7688]">Link status</dt>
              <dd className="font-medium" data-testid="account-link-status-value">
                {linkStatus.linkStatus || '—'}
              </dd>
            </div>
            <div>
              <dt className="text-[#6B7688]">Verification</dt>
              <dd className="font-medium">{linkStatus.verificationStatus || '—'}</dd>
            </div>
            <div>
              <dt className="text-[#6B7688]">Student status</dt>
              <dd className="font-medium">{linkStatus.studentStatus || '—'}</dd>
            </div>
            <div>
              <dt className="text-[#6B7688]">Portal access</dt>
              <dd className="font-medium">{linkStatus.portalAccessActive ? 'Active' : 'Inactive'}</dd>
            </div>
          </dl>
        )}
      </section>

      <section className="rounded-lg border border-[#D7DEE8] bg-white p-4" data-testid="provisioning-history-panel">
        <div className="mb-3 flex items-center gap-2 text-[#0B1F3A]">
          <History className="h-4 w-4" />
          <h2 className="font-medium">See past login requests</h2>
        </div>
        <button
          type="button"
          data-testid="provisioning-history-load"
          className="rounded border border-[#D7DEE8] px-3 py-2 text-sm disabled:opacity-50"
          disabled={!studentId || loading}
          onClick={loadProvisioningHistory}
        >
          Load request history
        </button>
        <ul className="mt-3 space-y-2" data-testid="provisioning-history-list">
          {requests.map((row) => (
            <li
              key={row.id}
              className="flex flex-wrap items-center justify-between gap-2 rounded border border-[#EEF2F7] px-3 py-2 text-sm"
              data-testid={`provisioning-request-${row.id}`}
            >
              <span>
                {row.id.slice(0, 8)}… — <strong>{row.requestStatus}</strong>
                {row.failureReason ? ` (${row.failureReason})` : ''}
              </span>
              {row.requestStatus === 'FAILED' && (
                <button
                  type="button"
                  data-testid={`provisioning-retry-${row.id}`}
                  className="inline-flex items-center gap-1 rounded bg-[#0B1F3A] px-2 py-1 text-xs text-white"
                  onClick={() => retryFailed(row.id)}
                  disabled={loading}
                >
                  <RefreshCw className="h-3 w-3" />
                  Retry failed login setup
                </button>
              )}
            </li>
          ))}
          {requests.length === 0 && (
            <li className="text-sm text-[#6B7688]" data-testid="provisioning-history-empty">
              No requests loaded yet.
            </li>
          )}
        </ul>
      </section>

      <section className="rounded-lg border border-[#D7DEE8] bg-white p-4" data-testid="account-role-audit-panel">
        <div className="mb-3 flex items-center gap-2 text-[#0B1F3A]">
          <Shield className="h-4 w-4" />
          <h2 className="font-medium">Audit who changed accounts and roles</h2>
        </div>
        <button
          type="button"
          data-testid="account-role-audit-load"
          className="rounded border border-[#D7DEE8] px-3 py-2 text-sm disabled:opacity-50"
          disabled={loading}
          onClick={loadAudit}
        >
          Load account/role audit
        </button>
        <ul className="mt-3 space-y-2" data-testid="account-role-audit-list">
          {auditRows.map((row, idx) => (
            <li
              key={row.id || String(idx)}
              className="rounded border border-[#EEF2F7] px-3 py-2 text-sm"
              data-testid="account-role-audit-row"
            >
              <strong>{row.action}</strong> — student {row.studentId || '—'} by {row.actorUserId || '—'}
              {row.eventTime ? ` at ${row.eventTime}` : ''}
            </li>
          ))}
          {auditRows.length === 0 && (
            <li className="text-sm text-[#6B7688]" data-testid="account-role-audit-empty">
              No audit rows loaded yet.
            </li>
          )}
        </ul>
      </section>

      {message && (
        <p className="text-sm text-emerald-700" data-testid="account-provisioning-message">
          {message}
        </p>
      )}
      {error && (
        <p className="text-sm text-red-700" data-testid="account-provisioning-error">
          {error}
        </p>
      )}
    </div>
  );
}
