'use client';

import React, { useEffect, useState, useTransition } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ArrowLeft, Ban, CheckCircle2, AlertCircle, Loader2 } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';

export interface InstituteDetail {
  tenantId: string;
  code: string;
  name: string;
  status: string;
  createdAt?: string | null;
  updatedAt?: string | null;
}

type Tab = 'profile' | 'colleges' | 'administrators' | 'audit';

/**
 * Institute detail — IADM01-US-017.
 * Tabs: Profile, Colleges, Administrators (onboarding), Audit trail.
 */
export function InstituteDetailClient({
  initial,
  loadError,
}: {
  initial: InstituteDetail | null;
  loadError: string | null;
}) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [tab, setTab] = useState<Tab>('profile');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(loadError);
  const [busy, setBusy] = useState(false);
  const [onboarding, setOnboarding] = useState<{
    steps?: Array<{ stepKey: string; status: string; lastError?: string | null }>;
    resumeStep?: string | null;
  } | null>(null);
  const [colleges, setColleges] = useState<Array<{ code?: string; name?: string; status?: string }>>([]);
  const [tabError, setTabError] = useState<string | null>(null);
  const [tabLoading, setTabLoading] = useState(false);

  useEffect(() => {
    if (!initial?.tenantId) return;
    if (tab !== 'administrators' && tab !== 'colleges') return;
    let cancelled = false;
    (async () => {
      setTabLoading(true);
      setTabError(null);
      try {
        if (tab === 'administrators') {
          const res = await fetch(
            `/api/platform/institutes/${initial.tenantId}/first-admin/onboarding`,
            { method: 'GET' }
          );
          const body = await res.json().catch(() => ({}));
          if (!res.ok) {
            if (!cancelled) setTabError(body.detail || 'unavailable');
            return;
          }
          if (!cancelled) setOnboarding(body);
        } else {
          const res = await fetchFromColleges(initial.tenantId);
          if (!cancelled) setColleges(res);
        }
      } catch {
        if (!cancelled) setTabError('unavailable');
      } finally {
        if (!cancelled) setTabLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [tab, initial?.tenantId]);

  if (!initial) {
    return (
      <div className="space-y-4" data-testid="institute-detail-missing">
        <Link href="/platform/institutes" className="text-sm text-[#1F6FEB]">
          ← Institutes
        </Link>
        <p className="text-sm text-red-700">{error || 'Institute not found'}</p>
      </div>
    );
  }

  async function act(kind: 'suspend' | 'reactivate') {
    if (reason.trim().length < 3) {
      setError('Reason is required (min 3 characters)');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const res = await fetch(`/api/platform/institutes/${initial!.tenantId}/${kind}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.detail || data.errorCode || `${kind} failed`);
      startTransition(() => router.refresh());
      setReason('');
    } catch (e) {
      setError(e instanceof Error ? e.message : `${kind} failed`);
    } finally {
      setBusy(false);
    }
  }

  const status = (initial.status || '').toUpperCase();

  return (
    <div className="space-y-6" data-testid="institute-detail-desk">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div>
          <Link
            href="/platform/institutes"
            className="inline-flex items-center text-xs text-[#1F6FEB] mb-2"
            data-testid="institute-back"
          >
            <ArrowLeft className="h-3.5 w-3.5 mr-1" /> Institutes & Tenancies
          </Link>
          <h1 className="text-2xl font-bold text-[#0B1F3A]" data-testid="institute-detail-title">
            {initial.name}
          </h1>
          <p className="text-xs text-[#6B7688] mt-1 font-mono">
            {initial.code} · {initial.tenantId}
          </p>
        </div>
        <Badge status={status}>{status}</Badge>
      </div>

      {error && (
        <div className="flex gap-2 rounded-[8px] border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
          <AlertCircle className="h-4 w-4 mt-0.5" />
          <span>{error}</span>
        </div>
      )}

      <div className="flex flex-wrap gap-1.5" data-testid="iadm01-detail-tabs">
        {(
          [
            ['profile', 'Profile'],
            ['colleges', 'Colleges'],
            ['administrators', 'Administrators'],
            ['audit', 'Audit trail'],
          ] as const
        ).map(([id, label]) => (
          <button
            key={id}
            type="button"
            onClick={() => setTab(id)}
            className={`rounded-[6px] px-3 py-1.5 text-xs font-semibold ${
              tab === id
                ? 'bg-[#0B1F3A] text-white'
                : 'border border-[#E3E8EF] bg-white text-[#6B7688]'
            }`}
            data-testid={`iadm01-tab-${id}`}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === 'profile' && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Profile</CardTitle>
            <CardDescription>Institute tenancy profile (IADM-01)</CardDescription>
          </CardHeader>
          <CardContent className="text-sm space-y-1 text-[#1B2433]">
            <p>
              <span className="text-[#6B7688]">Status:</span> {status}
            </p>
            <p>
              <span className="text-[#6B7688]">Created:</span>{' '}
              {initial.createdAt ? new Date(String(initial.createdAt)).toLocaleString() : '—'}
            </p>
          </CardContent>
        </Card>
      )}

      {tab === 'colleges' && (
        <Card data-testid="iadm01-detail-colleges">
          <CardHeader>
            <CardTitle className="text-base">Colleges</CardTitle>
            <CardDescription>
              Colleges are registered by the Institution Admin after appointment — not by the Platform wizard.
            </CardDescription>
          </CardHeader>
          <CardContent>
            {tabLoading && (
              <p className="flex items-center gap-2 text-sm text-slate-600">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading…
              </p>
            )}
            {tabError && <p className="text-sm text-amber-800">{tabError}</p>}
            {!tabLoading && !tabError && colleges.length === 0 && (
              <p className="text-sm text-slate-600" data-testid="iadm01-detail-colleges-empty">
                No colleges yet.
              </p>
            )}
            <ul className="space-y-2 text-sm">
              {colleges.map((c, i) => (
                <li key={i} className="rounded border px-3 py-2">
                  {c.name} ({c.code}) — {c.status}
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      {tab === 'administrators' && (
        <Card data-testid="iadm01-detail-admins">
          <CardHeader>
            <CardTitle className="text-base">Administrators & onboarding</CardTitle>
            <CardDescription>First Institution Admin (R03) onboarding steps</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {tabLoading && (
              <p className="flex items-center gap-2 text-sm text-slate-600">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading…
              </p>
            )}
            {tabError && <p className="text-sm text-amber-800">{tabError}</p>}
            {onboarding?.resumeStep && (
              <p className="text-sm text-amber-900" data-testid="iadm01-resume-step">
                Resume at: {onboarding.resumeStep}
              </p>
            )}
            <ul className="space-y-2 text-sm" data-testid="iadm01-onboarding-steps">
              {(onboarding?.steps || []).map((s) => (
                <li key={s.stepKey} className="flex items-center justify-between rounded border px-3 py-2">
                  <span>{s.stepKey}</span>
                  <Badge variant="outline">{s.status}</Badge>
                </li>
              ))}
            </ul>
            <Link href={`/platform/institutes/new`}>
              <Button size="sm" variant="secondary" data-testid="iadm01-continue-onboarding">
                Continue onboarding wizard
              </Button>
            </Link>
          </CardContent>
        </Card>
      )}

      {tab === 'audit' && (
        <Card data-testid="iadm01-detail-audit">
          <CardHeader>
            <CardTitle className="text-base">Audit trail</CardTitle>
            <CardDescription>
              Lifecycle entries are written to shared audit on each mutation. Full Ops search lands in Slice S4
              (IADM01-US-025).
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Link href="/platform/audit" className="text-sm text-[#1F6FEB] underline">
              Open Ops & Audit Search
            </Link>
          </CardContent>
        </Card>
      )}

      {tab === 'profile' && (
        <Card data-testid="institute-lifecycle-actions">
          <CardHeader>
            <CardTitle className="text-base">Lifecycle controls</CardTitle>
            <CardDescription>
              Suspend / reactivate (S3). Reason required. Destructive execute is Super Admin only.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <Input
              data-testid="institute-action-reason"
              placeholder="Reason (required)"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
            <div className="flex flex-wrap gap-2">
              <Button
                size="sm"
                variant="danger"
                disabled={busy || pending || status === 'SUSPENDED'}
                data-testid="institute-suspend"
                onClick={() => act('suspend')}
              >
                <Ban className="h-3.5 w-3.5 mr-1" /> Suspend
              </Button>
              <Button
                size="sm"
                variant="primary"
                disabled={busy || pending || status === 'ACTIVE'}
                data-testid="institute-reactivate"
                onClick={() => act('reactivate')}
              >
                <CheckCircle2 className="h-3.5 w-3.5 mr-1" /> Reactivate
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

async function fetchFromColleges(tenantId: string) {
  const res = await fetch(`/api/platform/institutes/${tenantId}/colleges`, { method: 'GET' });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(body.detail || 'unavailable');
  return Array.isArray(body.colleges) ? body.colleges : [];
}
