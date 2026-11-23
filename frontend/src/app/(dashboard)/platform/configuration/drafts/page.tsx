'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

type Draft = {
  draftId: string;
  settingKey?: string;
  policyCode?: string;
  proposedValue?: string;
  reason?: string;
  status?: string;
  validationErrors?: string;
};

/**
 * Drafts desk: edit (US-012), validate (US-013), submit/decide/publish (US-016…018).
 */
export default function ConfigurationDraftsPage() {
  const [items, setItems] = useState<Draft[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState('');
  const [editReason, setEditReason] = useState('');
  const [decisionReason, setDecisionReason] = useState('S2 approval decision');
  const [saving, setSaving] = useState(false);

  async function load() {
    setError(null);
    const res = await fetch('/api/platform/configuration?view=drafts');
    const body = await res.json();
    if (!res.ok) {
      setError(body.detail || body.errorCode || 'Failed to load drafts');
      return;
    }
    setItems(Array.isArray(body.items) ? body.items : []);
  }

  useEffect(() => {
    load();
  }, []);

  function canEdit(status?: string) {
    const s = (status || '').toUpperCase();
    return s === 'DRAFT' || s === 'VALIDATED' || s === 'REJECTED';
  }

  function startEdit(d: Draft) {
    setEditingId(d.draftId);
    setEditValue(String(d.proposedValue ?? ''));
    setEditReason(String(d.reason ?? ''));
    setResult(null);
  }

  function cancelEdit() {
    setEditingId(null);
    setEditValue('');
    setEditReason('');
  }

  async function saveEdit(id: string) {
    setSaving(true);
    setResult(null);
    try {
      const res = await fetch(`/api/platform/configuration/drafts/${encodeURIComponent(id)}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ proposedValue: editValue, reason: editReason }),
      });
      const body = await res.json();
      if (!res.ok) {
        setResult(body.detail || body.errorCode || 'Edit failed');
        return;
      }
      setResult(`Draft ${id} updated (${body.status || 'DRAFT'}).`);
      cancelEdit();
      await load();
    } finally {
      setSaving(false);
    }
  }

  async function postAction(path: string, payload?: object) {
    setResult(null);
    const res = await fetch(path, {
      method: 'POST',
      headers: payload ? { 'Content-Type': 'application/json' } : undefined,
      body: payload ? JSON.stringify(payload) : undefined,
    });
    const body = await res.json();
    if (!res.ok) {
      setResult(body.detail || body.errorCode || 'Action failed');
      return;
    }
    setResult(
      body.versionLabel
        ? `Published ${body.versionLabel}`
        : `OK: ${body.status || body.draftId || 'done'}`
    );
    await load();
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-6" data-testid="iadm02-drafts-desk">
      <Link href="/platform/configuration" className="text-sm underline">
        ← Catalogue
      </Link>
      <h1 className="text-2xl font-semibold">Configuration drafts</h1>
      <label className="block text-sm">
        Decision reason (approve/reject)
        <input
          className="mt-1 w-full rounded border px-2 py-1"
          value={decisionReason}
          onChange={(e) => setDecisionReason(e.target.value)}
          data-testid="iadm02-decision-reason"
        />
      </label>
      {error && <p className="text-red-700">{error}</p>}
      {result && (
        <p className="rounded border border-slate-200 bg-slate-50 p-3 text-sm" data-testid="iadm02-validate-result">
          {result}
        </p>
      )}
      <table className="min-w-full text-left text-sm" data-testid="iadm02-drafts-table">
        <thead className="border-b bg-slate-50">
          <tr>
            <th className="px-3 py-2">Key / policy</th>
            <th className="px-3 py-2">Value</th>
            <th className="px-3 py-2">Status</th>
            <th className="px-3 py-2">Reason</th>
            <th className="px-3 py-2">Actions</th>
          </tr>
        </thead>
        <tbody>
          {items.map((d) => (
            <tr key={d.draftId} className="border-b" data-testid={`iadm02-draft-${d.draftId}`}>
              <td className="px-3 py-2">{d.settingKey || d.policyCode}</td>
              <td className="px-3 py-2">
                {editingId === d.draftId ? (
                  <input
                    className="w-full rounded border px-2 py-1"
                    value={editValue}
                    onChange={(e) => setEditValue(e.target.value)}
                    data-testid={`iadm02-edit-value-${d.draftId}`}
                  />
                ) : (
                  d.proposedValue
                )}
              </td>
              <td className="px-3 py-2">{d.status}</td>
              <td className="px-3 py-2">
                {editingId === d.draftId ? (
                  <textarea
                    className="w-full rounded border px-2 py-1"
                    rows={2}
                    value={editReason}
                    onChange={(e) => setEditReason(e.target.value)}
                    data-testid={`iadm02-edit-reason-${d.draftId}`}
                  />
                ) : (
                  d.reason
                )}
              </td>
              <td className="px-3 py-2">
                <div className="flex flex-wrap gap-2">
                  {editingId === d.draftId ? (
                    <>
                      <button
                        type="button"
                        className="rounded border bg-slate-900 px-2 py-1 text-xs text-white disabled:opacity-50"
                        disabled={saving || !editReason.trim()}
                        data-testid={`iadm02-save-edit-${d.draftId}`}
                        onClick={() => saveEdit(d.draftId)}
                      >
                        Save
                      </button>
                      <button
                        type="button"
                        className="rounded border px-2 py-1 text-xs"
                        disabled={saving}
                        onClick={cancelEdit}
                      >
                        Cancel
                      </button>
                    </>
                  ) : (
                    <>
                      {canEdit(d.status) && (
                        <button
                          type="button"
                          className="rounded border px-2 py-1 text-xs"
                          data-testid={`iadm02-edit-${d.draftId}`}
                          onClick={() => startEdit(d)}
                        >
                          Edit
                        </button>
                      )}
                      <button
                        type="button"
                        className="rounded border px-2 py-1 text-xs"
                        data-testid={`iadm02-validate-${d.draftId}`}
                        onClick={() =>
                          postAction(
                            `/api/platform/configuration/drafts/${encodeURIComponent(d.draftId)}/validate`
                          )
                        }
                      >
                        Validate
                      </button>
                      {String(d.status).toUpperCase() === 'VALIDATED' && (
                        <button
                          type="button"
                          className="rounded border px-2 py-1 text-xs"
                          data-testid={`iadm02-submit-${d.draftId}`}
                          onClick={() =>
                            postAction(
                              `/api/platform/configuration/drafts/${encodeURIComponent(d.draftId)}/submit`
                            )
                          }
                        >
                          Submit
                        </button>
                      )}
                      {String(d.status).toUpperCase() === 'PENDING_APPROVAL' && (
                        <>
                          <button
                            type="button"
                            className="rounded border px-2 py-1 text-xs"
                            data-testid={`iadm02-approve-${d.draftId}`}
                            onClick={() =>
                              postAction(
                                `/api/platform/configuration/drafts/${encodeURIComponent(d.draftId)}/decide`,
                                { decision: 'APPROVE', reason: decisionReason }
                              )
                            }
                          >
                            Approve
                          </button>
                          <button
                            type="button"
                            className="rounded border px-2 py-1 text-xs"
                            data-testid={`iadm02-reject-${d.draftId}`}
                            onClick={() =>
                              postAction(
                                `/api/platform/configuration/drafts/${encodeURIComponent(d.draftId)}/decide`,
                                { decision: 'REJECT', reason: decisionReason }
                              )
                            }
                          >
                            Reject
                          </button>
                        </>
                      )}
                      {String(d.status).toUpperCase() === 'APPROVED' && (
                        <button
                          type="button"
                          className="rounded border px-2 py-1 text-xs"
                          data-testid={`iadm02-publish-${d.draftId}`}
                          onClick={() =>
                            postAction(
                              `/api/platform/configuration/drafts/${encodeURIComponent(d.draftId)}/publish`,
                              {
                                releaseNotes: 'S2 headed publish',
                                idempotencyKey: `pub-${d.draftId}`,
                              }
                            )
                          }
                        >
                          Publish
                        </button>
                      )}
                    </>
                  )}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
