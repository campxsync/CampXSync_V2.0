'use client';

import React, { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { AlertCircle, ArrowLeft, CheckCircle2, Loader2, ShieldAlert } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';

type OnboardingStep = { stepKey: string; status: string; lastError?: string | null };

export function CollegeDetailClient({ collegeId }: { collegeId: string }) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [college, setCollege] = useState<{
    code?: string;
    name?: string;
    status?: string;
    campusCode?: string;
    location?: string;
    handoverStatus?: string;
    handedOver?: boolean;
  } | null>(null);
  const [identity, setIdentity] = useState({ name: '', campusCode: '', location: '' });
  const [adminEmail, setAdminEmail] = useState('');
  const [adminName, setAdminName] = useState('');
  const [onboarding, setOnboarding] = useState<{
    steps?: OnboardingStep[];
    resumeStep?: string | null;
    complete?: boolean;
  } | null>(null);
  const [handoverNotes, setHandoverNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionOk, setActionOk] = useState<string | null>(null);

  const handedOver =
    Boolean(college?.handedOver) ||
    (college?.handoverStatus || '').toUpperCase() === 'COMPLETED';

  const loadCollege = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(`/api/institution/colleges/${collegeId}`, { method: 'GET' });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        setError(typeof body.detail === 'string' ? body.detail : 'unavailable');
        setCollege(null);
        return;
      }
      setCollege(body);
      setIdentity({
        name: String(body.name || ''),
        campusCode: String(body.campusCode || body.campus_code || ''),
        location: String(body.location || ''),
      });
    } catch {
      setError('unavailable');
    } finally {
      setLoading(false);
    }
  }, [collegeId]);

  const loadOnboarding = useCallback(async () => {
    try {
      const res = await fetch(`/api/institution/colleges/${collegeId}/first-admin/onboarding`, {
        method: 'GET',
      });
      const body = await res.json().catch(() => ({}));
      if (res.ok) setOnboarding(body);
    } catch {
      /* optional tab */
    }
  }, [collegeId]);

  useEffect(() => {
    void loadCollege();
    void loadOnboarding();
  }, [loadCollege, loadOnboarding]);

  const saveIdentity = async () => {
    if (handedOver) return;
    setBusy(true);
    setActionError(null);
    setActionOk(null);
    try {
      const res = await fetch(`/api/institution/colleges/${collegeId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: identity.name.trim(),
          campusCode: identity.campusCode.trim(),
          location: identity.location.trim(),
        }),
      });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        setActionError(typeof body.detail === 'string' ? body.detail : 'Update failed');
        return;
      }
      setActionOk('College identity saved.');
      await loadCollege();
    } catch {
      setActionError('unavailable');
    } finally {
      setBusy(false);
    }
  };

  const runR04Onboarding = async (simulateInviteFailure: boolean) => {
    setBusy(true);
    setActionError(null);
    setActionOk(null);
    try {
      const res = await fetch(`/api/institution/colleges/${collegeId}/first-admin/onboarding`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: adminEmail.trim(),
          displayName: adminName.trim() || adminEmail.trim(),
          simulateInviteFailure,
        }),
      });
      const body = await res.json().catch(() => ({}));
      if (Array.isArray(body.steps)) setOnboarding(body);
      if (!res.ok) {
        setActionError(
          typeof body.detail === 'string'
            ? body.detail
            : 'Invitation could not be sent. Retry invitation.'
        );
        return;
      }
      if (body.complete) {
        setActionOk('First College Admin provisioning complete.');
      }
      await loadCollege();
      await loadOnboarding();
    } catch {
      setActionError('unavailable');
    } finally {
      setBusy(false);
    }
  };

  const completeHandover = async () => {
    setBusy(true);
    setActionError(null);
    setActionOk(null);
    try {
      const res = await fetch(`/api/institution/colleges/${collegeId}/handover`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ notes: handoverNotes.trim() }),
      });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        setActionError(typeof body.detail === 'string' ? body.detail : 'Handover could not be completed.');
        return;
      }
      setActionOk('Handover completed. College is now ACTIVE.');
      await loadCollege();
    } catch {
      setActionError('unavailable');
    } finally {
      setBusy(false);
    }
  };

  const status = (college?.status || '').toUpperCase();
  const identityReadOnly = handedOver;

  return (
    <div className="mx-auto max-w-3xl space-y-6 p-6" data-testid="iadm01-college-detail">
      <Link
        href="/institution/colleges"
        className="inline-flex items-center text-xs text-[#1F6FEB]"
        data-testid="iadm01-college-back"
      >
        <ArrowLeft className="mr-1 h-3.5 w-3.5" /> Colleges
      </Link>

      {loading && (
        <p className="flex items-center gap-2 text-sm text-slate-600" data-testid="iadm01-college-loading">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading…
        </p>
      )}

      {error && (
        <div className="flex gap-2 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800" role="alert">
          <AlertCircle className="mt-0.5 h-4 w-4" />
          <span data-testid="iadm01-college-error">{error}</span>
        </div>
      )}

      {!loading && !error && college && (
        <>
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h1 className="text-2xl font-bold text-[#0B1F3A]" data-testid="iadm01-college-title">
                {college.name}
              </h1>
              <p className="text-xs font-mono text-[#6B7688]">
                {college.code} · {collegeId}
              </p>
            </div>
            <Badge variant="outline">{status || '—'}</Badge>
          </div>

          {handedOver && (
            <div
              className="flex gap-2 rounded-[8px] border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900"
              data-testid="iadm01-college-boundary"
              role="status"
            >
              <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
              <span>
                This college has been handed over to College Administration. You can no longer assign or manage
                college-scoped users here; profile changes belong in CADM-01.
              </span>
            </div>
          )}

          {(actionError || actionOk) && (
            <div
              className={`flex gap-2 rounded-md border px-3 py-2 text-sm ${
                actionError ? 'border-red-200 bg-red-50 text-red-800' : 'border-green-200 bg-green-50 text-green-900'
              }`}
              data-testid="iadm01-college-action-banner"
            >
              {actionError ? <AlertCircle className="h-4 w-4" /> : <CheckCircle2 className="h-4 w-4" />}
              <span>{actionError || actionOk}</span>
            </div>
          )}

          <Card data-testid="iadm01-college-identity">
            <CardHeader>
              <CardTitle className="text-base">Identity</CardTitle>
              <CardDescription>
                {identityReadOnly
                  ? 'Read-only after handover (CADM-01 owns profile changes).'
                  : 'Editable until handover (US-020). College code is fixed after creation.'}
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <p className="text-sm text-[#6B7688]">
                Code: <span className="font-mono text-[#0B1F3A]">{college.code}</span>
              </p>
              <Input
                data-testid="iadm01-college-field-name"
                disabled={identityReadOnly || busy}
                value={identity.name}
                onChange={(e) => setIdentity((v) => ({ ...v, name: e.target.value }))}
                placeholder="Display name"
              />
              <Input
                data-testid="iadm01-college-field-campus"
                disabled={identityReadOnly || busy}
                value={identity.campusCode}
                onChange={(e) => setIdentity((v) => ({ ...v, campusCode: e.target.value }))}
                placeholder="Campus code"
              />
              <Input
                data-testid="iadm01-college-field-location"
                disabled={identityReadOnly || busy}
                value={identity.location}
                onChange={(e) => setIdentity((v) => ({ ...v, location: e.target.value }))}
                placeholder="Location"
              />
              {!identityReadOnly && (
                <Button
                  size="sm"
                  variant="primary"
                  disabled={busy}
                  data-testid="iadm01-college-save-identity"
                  onClick={() => void saveIdentity()}
                >
                  Save identity
                </Button>
              )}
            </CardContent>
          </Card>

          <Card data-testid="iadm01-college-first-admin">
            <CardHeader>
              <CardTitle className="text-base">First College Admin (R04)</CardTitle>
              <CardDescription>Invite login, create profile, appoint role (US-007).</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <Input
                data-testid="iadm01-college-admin-email"
                disabled={busy || handedOver}
                value={adminEmail}
                onChange={(e) => setAdminEmail(e.target.value)}
                placeholder="Email"
              />
              <Input
                data-testid="iadm01-college-admin-name"
                disabled={busy || handedOver}
                value={adminName}
                onChange={(e) => setAdminName(e.target.value)}
                placeholder="Display name"
              />
              <ul className="space-y-1 text-sm" data-testid="iadm01-college-onboarding-steps">
                {(onboarding?.steps || []).map((s) => (
                  <li key={s.stepKey} className="flex justify-between rounded border px-2 py-1">
                    <span>{s.stepKey}</span>
                    <Badge variant="outline">{s.status}</Badge>
                  </li>
                ))}
              </ul>
              <div className="flex flex-wrap gap-2">
                <Button
                  size="sm"
                  variant="primary"
                  disabled={busy || handedOver || !adminEmail.trim()}
                  data-testid="iadm01-college-invite-submit"
                  onClick={() => void runR04Onboarding(false)}
                >
                  Send invitation
                </Button>
                <Button
                  size="sm"
                  variant="secondary"
                  disabled={busy || handedOver}
                  data-testid="iadm01-college-invite-retry"
                  onClick={() => void runR04Onboarding(false)}
                >
                  Retry invitation
                </Button>
              </div>
            </CardContent>
          </Card>

          {status === 'PENDING_HANDOVER' && !handedOver && (
            <Card data-testid="iadm01-college-handover">
              <CardHeader>
                <CardTitle className="text-base">Hand over to College Administration</CardTitle>
                <CardDescription>US-008 — available when the first College Admin is provisioned.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                <Input
                  data-testid="iadm01-college-handover-notes"
                  value={handoverNotes}
                  onChange={(e) => setHandoverNotes(e.target.value)}
                  placeholder="Notes (optional)"
                  disabled={busy}
                />
                <Button
                  variant="primary"
                  size="sm"
                  disabled={busy}
                  data-testid="iadm01-college-handover-submit"
                  onClick={() => void completeHandover()}
                >
                  Complete handover
                </Button>
              </CardContent>
            </Card>
          )}
        </>
      )}
    </div>
  );
}