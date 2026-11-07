'use client';

import { useEffect, useState } from 'react';

type RequestRow = {
  id: string;
  studentId: string;
  requestType: string;
  status: string;
  reason?: string;
  effectiveDate?: string;
};

export default function CollegeLifecycleDeskPage() {
  const [requests, setRequests] = useState<RequestRow[]>([]);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [studentId, setStudentId] = useState('');
  const [programId, setProgramId] = useState('');
  const [graduatedOn, setGraduatedOn] = useState('2026-11-07');
  const [report, setReport] = useState('');
  const [historyText, setHistoryText] = useState('');
  const [advisorUserId, setAdvisorUserId] = useState('');
  const [fromStatus, setFromStatus] = useState('ACTIVE');
  const [toStatus, setToStatus] = useState('ON_LEAVE');
  const [requiresApproval, setRequiresApproval] = useState(true);

  async function load() {
    const res = await fetch('/api/college/lifecycle', { cache: 'no-store' });
    const text = await res.text();
    if (!res.ok) {
      setError(text);
      return;
    }
    const json = JSON.parse(text);
    setRequests(json.requests || []);
    const reportRes = await fetch('/api/college/lifecycle?view=report', { cache: 'no-store' });
    if (reportRes.ok) {
      setReport(await reportRes.text());
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function decide(requestId: string, decision: string) {
    setError('');
    setMessage('');
    const res = await fetch('/api/college/lifecycle', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'decide', requestId, decision }),
    });
    const text = await res.text();
    if (!res.ok) {
      setError(text);
      return;
    }
    setMessage(`Request ${decision.toLowerCase()}.`);
    await load();
  }

  async function graduate(action: 'graduate-validate' | 'graduate-record') {
    setError('');
    setMessage('');
    const res = await fetch('/api/college/lifecycle', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action, studentId, programId, graduatedOn, notes: 'Recorded from lifecycle desk' }),
    });
    const text = await res.text();
    if (!res.ok) {
      setError(text);
      return;
    }
    setMessage(text);
    if (action === 'graduate-record') {
      await load();
    }
  }

  return (
    <main className="mx-auto max-w-3xl space-y-6 p-6">
      <div>
        <h1 className="text-xl font-semibold text-[#0B1F3A]">Student lifecycle desk</h1>
        <p className="text-sm text-[#475569]">
          Approve leave, transfer, withdrawal, and reinstatement requests, then record graduation when the student is eligible.
        </p>
      </div>
      {report ? <pre className="overflow-auto rounded bg-[#F8FAFC] p-3 text-xs">{report}</pre> : null}
      <section className="space-y-2 rounded border p-4">
        <h2 className="text-sm font-semibold">Review a student</h2>
        <input className="w-full rounded border px-2 py-1" value={studentId} onChange={(e) => setStudentId(e.target.value)} placeholder="Student record id" />
        <button
          type="button"
          className="rounded border px-3 py-2 text-sm"
          onClick={async () => {
            const res = await fetch(`/api/college/lifecycle?view=history&studentId=${encodeURIComponent(studentId)}`, { cache: 'no-store' });
            setHistoryText(await res.text());
          }}
        >
          Load lifecycle history
        </button>
        {historyText ? <pre className="overflow-auto text-xs">{historyText}</pre> : null}
        <input className="w-full rounded border px-2 py-1" value={advisorUserId} onChange={(e) => setAdvisorUserId(e.target.value)} placeholder="Advisor user id" />
        <button
          type="button"
          className="rounded border px-3 py-2 text-sm"
          onClick={() =>
            void fetch('/api/college/lifecycle', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ action: 'assign-advisor', advisorUserId, studentId }),
            }).then(async (res) => setMessage(await res.text()))
          }
        >
          Assign advisor
        </button>
      </section>
      <section className="space-y-2">
        <h2 className="text-sm font-semibold">Pending requests</h2>
        {requests.length === 0 ? <p className="text-sm text-[#475569]">No pending lifecycle requests.</p> : null}
        <ul className="space-y-2">
          {requests.map((request) => (
            <li key={request.id} className="rounded border p-3 text-sm">
              <p className="font-medium">
                {request.requestType} · {request.status}
              </p>
              <p className="text-[#475569]">Student {request.studentId}</p>
              {request.reason ? <p>{request.reason}</p> : null}
              {request.effectiveDate ? <p className="text-[#475569]">Effective {request.effectiveDate}</p> : null}
              <div className="mt-2 flex gap-2">
                <button type="button" className="rounded bg-[#15803D] px-3 py-1 text-white" onClick={() => void decide(request.id, 'APPROVED')}>
                  Approve
                </button>
                <button type="button" className="rounded border px-3 py-1" onClick={() => void decide(request.id, 'REJECTED')}>
                  Reject
                </button>
              </div>
            </li>
          ))}
        </ul>
      </section>
      <section className="space-y-2 rounded border p-4">
        <h2 className="text-sm font-semibold">Transition rules</h2>
        <p className="text-sm text-[#475569]">Choose which status changes are allowed, and whether they need approval.</p>
        <div className="grid gap-2 sm:grid-cols-2">
          <select className="rounded border px-2 py-1" value={fromStatus} onChange={(e) => setFromStatus(e.target.value)}>
            <option value="ACTIVE">ACTIVE</option>
            <option value="ON_LEAVE">ON_LEAVE</option>
            <option value="WITHDRAWN">WITHDRAWN</option>
          </select>
          <select className="rounded border px-2 py-1" value={toStatus} onChange={(e) => setToStatus(e.target.value)}>
            <option value="ON_LEAVE">ON_LEAVE</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="TRANSFERRED">TRANSFERRED</option>
            <option value="WITHDRAWN">WITHDRAWN</option>
          </select>
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={requiresApproval} onChange={(e) => setRequiresApproval(e.target.checked)} />
          Requires registrar approval
        </label>
        <button
          type="button"
          className="rounded bg-[#0B1F3A] px-3 py-2 text-sm text-white"
          onClick={async () => {
            setError('');
            setMessage('');
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
            setMessage(`Rule saved: ${fromStatus} → ${toStatus}.`);
          }}
        >
          Save transition rule
        </button>
      </section>
      <section className="space-y-2 rounded border p-4">
        <h2 className="text-sm font-semibold">Graduation</h2>
        <label className="block text-sm">
          Student record id
          <input className="mt-1 w-full rounded border px-2 py-1" value={studentId} onChange={(e) => setStudentId(e.target.value)} placeholder="Student record id" />
        </label>
        <label className="block text-sm">
          Programme id
          <input className="mt-1 w-full rounded border px-2 py-1" value={programId} onChange={(e) => setProgramId(e.target.value)} placeholder="Programme id" />
        </label>
        <label className="block text-sm">
          Graduation date
          <input className="mt-1 w-full rounded border px-2 py-1" type="date" value={graduatedOn} onChange={(e) => setGraduatedOn(e.target.value)} />
        </label>
        <div className="flex gap-2">
          <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => void graduate('graduate-validate')}>
            Check eligibility
          </button>
          <button type="button" className="rounded bg-[#0B1F3A] px-3 py-2 text-sm text-white" onClick={() => void graduate('graduate-record')}>
            Record graduation
          </button>
        </div>
      </section>
      {message ? <pre className="overflow-auto rounded bg-[#F8FAFC] p-3 text-xs">{message}</pre> : null}
      {error ? <pre className="overflow-auto rounded bg-[#FEF2F2] p-3 text-xs text-[#991B1B]">{error}</pre> : null}
    </main>
  );
}
