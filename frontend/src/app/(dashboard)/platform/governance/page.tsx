'use client';

import { useEffect, useState } from 'react';

type ClassItem = {
  id: string;
  code: string;
  name: string;
  status?: string;
  versionNo?: number;
  ownerLabel?: string;
  demoLabel?: string;
  policyId?: string;
};

type RetentionItem = {
  id: string;
  policyCode?: string;
  title?: string;
  domainCode?: string;
  recordCategory?: string;
  durationNote?: string;
  status?: string;
  versionNo?: number;
  demoLabel?: string;
  policyId?: string;
};

type PolicyItem = {
  id: string;
  policyType?: string;
  code?: string;
  title?: string;
  status?: string;
  versionNo?: number;
  demoLabel?: string;
};

/**
 * IADM-05 platform governance desk (US-001…004, 013, 018).
 * Plain language: Super Admin / System Admin work on classification and retention policies.
 */
export default function PlatformGovernancePage() {
  const [classes, setClasses] = useState<ClassItem[]>([]);
  const [retention, setRetention] = useState<RetentionItem[]>([]);
  const [policies, setPolicies] = useState<PolicyItem[]>([]);
  const [history, setHistory] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionMsg, setActionMsg] = useState<string | null>(null);
  const [form, setForm] = useState({
    code: 'DEMO-CLASS-NEW',
    title: 'Demo proposed classification',
    className: 'Demo Proposed',
    description: 'DEMO ONLY proposal',
  });
  const [retForm, setRetForm] = useState({
    code: 'DEMO-RET-NEW',
    title: 'Demo retention proposal',
    domainCode: 'STM',
    recordCategory: 'demo_record',
  });

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [cRes, rRes, pRes] = await Promise.all([
        fetch('/api/platform/governance?view=classifications'),
        fetch('/api/platform/governance?view=retention'),
        fetch('/api/platform/governance?view=policies'),
      ]);
      const cBody = await cRes.json();
      const rBody = await rRes.json();
      const pBody = await pRes.json();
      if (!cRes.ok) throw new Error(cBody.detail || 'Classifications unavailable');
      setClasses(Array.isArray(cBody.items) ? cBody.items : []);
      setRetention(Array.isArray(rBody.items) ? rBody.items : []);
      setPolicies(Array.isArray(pBody.items) ? pBody.items : []);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load governance desk');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const post = async (view: string, body: Record<string, unknown>, id?: string) => {
    setActionMsg(null);
    const qs = new URLSearchParams({ view });
    if (id) qs.set('id', id);
    const res = await fetch(`/api/platform/governance?${qs.toString()}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      setActionMsg(data.detail || data.errorCode || 'Action failed');
      return;
    }
    setActionMsg(`OK: ${view}`);
    await load();
  };

  const loadHistory = async (policyId: string) => {
    const res = await fetch(`/api/platform/governance?view=history&id=${encodeURIComponent(policyId)}`);
    const data = await res.json();
    if (res.ok) setHistory(data);
    else setActionMsg(data.detail || 'History unavailable');
  };

  return (
    <main data-testid="iadm05-platform-desk" className="mx-auto max-w-6xl space-y-8 p-6">
      <header>
        <h1 className="text-2xl font-semibold tracking-tight">Data governance</h1>
        <p className="mt-1 text-sm text-neutral-600">
          IADM-05 policy catalogue — DEMO ONLY labels; retention durations stay TBC. Domain services
          execute export/erasure; this desk governs policy only.
        </p>
      </header>

      {loading && <p data-testid="iadm05-loading">Loading…</p>}
      {error && (
        <p data-testid="iadm05-error" className="text-sm text-red-700">
          {error}
        </p>
      )}
      {actionMsg && (
        <p data-testid="iadm05-action-msg" className="text-sm text-neutral-800">
          {actionMsg}
        </p>
      )}

      <section data-testid="iadm05-classification-catalogue" className="space-y-3">
        <h2 className="text-lg font-medium">Classification catalogue</h2>
        <p className="text-xs text-amber-800">DEMO ONLY / NOT LEGAL CLASSIFICATIONS (G-06 open)</p>
        <ul className="divide-y border border-neutral-200">
          {classes.map((c) => (
            <li key={c.id} data-testid={`iadm05-class-row-${c.code}`} className="flex justify-between gap-4 p-3 text-sm">
              <div>
                <div className="font-medium">
                  {c.name} <span className="text-neutral-500">({c.code})</span>
                </div>
                <div className="text-neutral-600">
                  v{c.versionNo} · {c.status} · {c.ownerLabel}
                </div>
              </div>
              {c.policyId && (
                <button
                  type="button"
                  data-testid={`iadm05-history-${c.policyId}`}
                  className="underline"
                  onClick={() => void loadHistory(c.policyId!)}
                >
                  History
                </button>
              )}
            </li>
          ))}
          {!loading && classes.length === 0 && (
            <li className="p-3 text-sm text-neutral-500">No classifications yet</li>
          )}
        </ul>

        <div data-testid="iadm05-propose-classification" className="grid gap-2 border border-dashed border-neutral-300 p-3 sm:grid-cols-2">
          <input
            data-testid="iadm05-class-code"
            className="border px-2 py-1 text-sm"
            value={form.code}
            onChange={(e) => setForm({ ...form, code: e.target.value })}
            placeholder="code"
          />
          <input
            data-testid="iadm05-class-title"
            className="border px-2 py-1 text-sm"
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
            placeholder="title"
          />
          <input
            data-testid="iadm05-class-name"
            className="border px-2 py-1 text-sm"
            value={form.className}
            onChange={(e) => setForm({ ...form, className: e.target.value })}
            placeholder="className"
          />
          <input
            data-testid="iadm05-class-description"
            className="border px-2 py-1 text-sm"
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
            placeholder="description"
          />
          <button
            type="button"
            data-testid="iadm05-class-propose-btn"
            className="border bg-neutral-900 px-3 py-1 text-sm text-white sm:col-span-2"
            onClick={() => void post('classifications', form)}
          >
            Propose classification revision
          </button>
        </div>
      </section>

      <section data-testid="iadm05-retention-register" className="space-y-3">
        <h2 className="text-lg font-medium">Retention register</h2>
        <ul className="divide-y border border-neutral-200">
          {retention.map((r) => (
            <li key={r.id} data-testid={`iadm05-retention-row-${r.policyCode}`} className="p-3 text-sm">
              <div className="font-medium">
                {r.title} <span className="text-neutral-500">({r.policyCode})</span>
              </div>
              <div className="text-neutral-600">
                {r.domainCode} / {r.recordCategory} · duration {r.durationNote} · v{r.versionNo} ·{' '}
                {r.status}
              </div>
            </li>
          ))}
        </ul>
        <div data-testid="iadm05-propose-retention" className="grid gap-2 border border-dashed border-neutral-300 p-3 sm:grid-cols-2">
          <input
            data-testid="iadm05-ret-code"
            className="border px-2 py-1 text-sm"
            value={retForm.code}
            onChange={(e) => setRetForm({ ...retForm, code: e.target.value })}
          />
          <input
            data-testid="iadm05-ret-title"
            className="border px-2 py-1 text-sm"
            value={retForm.title}
            onChange={(e) => setRetForm({ ...retForm, title: e.target.value })}
          />
          <input
            data-testid="iadm05-ret-domain"
            className="border px-2 py-1 text-sm"
            value={retForm.domainCode}
            onChange={(e) => setRetForm({ ...retForm, domainCode: e.target.value })}
          />
          <input
            data-testid="iadm05-ret-category"
            className="border px-2 py-1 text-sm"
            value={retForm.recordCategory}
            onChange={(e) => setRetForm({ ...retForm, recordCategory: e.target.value })}
          />
          <button
            type="button"
            data-testid="iadm05-ret-propose-btn"
            className="border bg-neutral-900 px-3 py-1 text-sm text-white sm:col-span-2"
            onClick={() =>
              void post('retention', { ...retForm, durationNote: 'TBC', ruleReference: 'TBC' })
            }
          >
            Define retention rule (duration TBC)
          </button>
        </div>
      </section>

      <section data-testid="iadm05-policy-queue" className="space-y-3">
        <h2 className="text-lg font-medium">Policy versions</h2>
        <ul className="divide-y border border-neutral-200">
          {policies.map((p) => (
            <li key={p.id} data-testid={`iadm05-policy-row-${p.code}-${p.versionNo}`} className="flex flex-wrap items-center justify-between gap-2 p-3 text-sm">
              <div>
                <span className="font-medium">{p.code}</span> v{p.versionNo} · {p.policyType} ·{' '}
                {p.status}
              </div>
              <div className="flex gap-2">
                {p.status === 'DRAFT' || p.status === 'REJECTED' ? (
                  <button
                    type="button"
                    data-testid={`iadm05-submit-${p.id}`}
                    className="underline"
                    onClick={() => void post('submit-review', {}, p.id)}
                  >
                    Submit review
                  </button>
                ) : null}
                {p.status === 'IN_REVIEW' ? (
                  <>
                    <button
                      type="button"
                      data-testid={`iadm05-approve-${p.id}`}
                      className="underline"
                      onClick={() =>
                        void post('decide', { decision: 'APPROVE', rationaleRef: 'desk-approve' }, p.id)
                      }
                    >
                      Approve
                    </button>
                    <button
                      type="button"
                      data-testid={`iadm05-reject-${p.id}`}
                      className="underline"
                      onClick={() =>
                        void post(
                          'decide',
                          { decision: 'REJECT', rationaleRef: 'desk-reject-ref' },
                          p.id
                        )
                      }
                    >
                      Reject
                    </button>
                  </>
                ) : null}
                {p.status === 'APPROVED' ? (
                  <button
                    type="button"
                    data-testid={`iadm05-publish-${p.id}`}
                    className="underline"
                    onClick={() =>
                      void post(
                        'publish',
                        { effectiveFrom: new Date().toISOString() },
                        p.id
                      )
                    }
                  >
                    Publish
                  </button>
                ) : null}
                <button
                  type="button"
                  className="underline"
                  onClick={() => void loadHistory(p.id)}
                >
                  History
                </button>
              </div>
            </li>
          ))}
        </ul>
      </section>

      {history && (
        <section data-testid="iadm05-policy-history" className="border border-neutral-200 p-3 text-sm">
          <h2 className="font-medium">Policy history</h2>
          <pre className="mt-2 overflow-auto text-xs">{JSON.stringify(history, null, 2)}</pre>
        </section>
      )}
    </main>
  );
}
