'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import {
  Building2,
  UserCheck,
  CheckCircle2,
  AlertCircle,
  ArrowRight,
  ArrowLeft,
  Loader2,
  RefreshCw,
} from 'lucide-react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';

type StepStatus = 'PENDING' | 'IN_PROGRESS' | 'SUCCEEDED' | 'FAILED' | 'RETRYABLE';

interface FormState {
  name: string;
  code: string;
  type: string;
  planReference: string;
  region: string;
  adminEmail: string;
  adminDisplayName: string;
}

/**
 * Platform Operator wizard for IADM-01 Journey 1.
 * Plain language: register institute → invite first Institution Admin (R03) login → profile → appoint.
 * Does NOT create a college (Institution Admin does that later).
 */
export function OnboardingWizard() {
  const router = useRouter();
  const [uiStep, setUiStep] = useState<1 | 2 | 3 | 4>(1);
  const [busy, setBusy] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [notificationNote, setNotificationNote] = useState<string | null>(null);
  const [instituteId, setInstituteId] = useState<string | null>(null);
  const [onboardingSteps, setOnboardingSteps] = useState<
    Array<{ stepKey: string; status: StepStatus; lastError?: string | null }>
  >([]);
  const [deliberateFail, setDeliberateFail] = useState(true);

  const [form, setForm] = useState<FormState>({
    name: '',
    code: '',
    type: 'UNIVERSITY',
    planReference: 'ENTERPRISE',
    region: '',
    adminEmail: '',
    adminDisplayName: '',
  });

  const update = (field: keyof FormState, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  const registerInstitute = async () => {
    setBusy(true);
    setErrorMessage(null);
    try {
      const res = await fetch('/api/platform/institutes', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: form.name.trim(),
          code: form.code.trim().toUpperCase(),
          type: form.type,
          planReference: form.planReference,
          region: form.region,
        }),
      });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        setErrorMessage(body.detail || body.message || 'Institute could not be registered.');
        return;
      }
      const id = body.instituteId || body.tenantId;
      setInstituteId(id);
      if (body.notificationMessage) setNotificationNote(body.notificationMessage);
      setUiStep(3);
    } catch {
      setErrorMessage('unavailable');
    } finally {
      setBusy(false);
    }
  };

  const runFirstAdminOnboarding = async (simulateFail: boolean) => {
    if (!instituteId) return;
    setBusy(true);
    setErrorMessage(null);
    try {
      const res = await fetch(`/api/platform/institutes/${instituteId}/first-admin/onboarding`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: form.adminEmail.trim(),
          displayName: form.adminDisplayName.trim() || form.adminEmail.trim(),
          simulateInviteFailure: simulateFail,
        }),
      });
      const body = await res.json().catch(() => ({}));
      if (Array.isArray(body.steps)) {
        setOnboardingSteps(body.steps);
      }
      if (!res.ok) {
        setErrorMessage(body.detail || body.message || 'Invitation could not be sent. Retry invitation.');
        setDeliberateFail(false);
        return;
      }
      if (body.notificationMessage) setNotificationNote(body.notificationMessage);
      if (body.complete) {
        setUiStep(4);
      } else {
        // continue remaining steps without simulate
        const again = await fetch(`/api/platform/institutes/${instituteId}/first-admin/onboarding`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            email: form.adminEmail.trim(),
            displayName: form.adminDisplayName.trim() || form.adminEmail.trim(),
            simulateInviteFailure: false,
          }),
        });
        const againBody = await again.json().catch(() => ({}));
        if (Array.isArray(againBody.steps)) setOnboardingSteps(againBody.steps);
        if (!again.ok) {
          setErrorMessage(againBody.detail || againBody.message || 'Onboarding step failed. Retry.');
          return;
        }
        if (againBody.complete) setUiStep(4);
      }
    } catch {
      setErrorMessage('unavailable');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6" data-testid="iadm01-onboard-wizard">
      <div>
        <h1 className="text-2xl font-semibold text-[#0B1F3A]">Onboard Institute</h1>
        <p className="mt-1 text-sm text-slate-600">
          Register an institute (tenant), then invite its first Institution Admin. Colleges are created later by that
          administrator — not in this wizard.
        </p>
      </div>

      <div className="flex flex-wrap gap-2" data-testid="iadm01-onboard-stepper">
        {[
          { n: 1, label: 'Details' },
          { n: 2, label: 'Review' },
          { n: 3, label: 'First Institution Admin' },
          { n: 4, label: 'Done' },
        ].map((s) => (
          <Badge key={s.n} variant={uiStep === s.n ? 'default' : 'outline'}>
            {s.n}. {s.label}
          </Badge>
        ))}
      </div>

      {errorMessage && (
        <div
          className="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800"
          data-testid="iadm01-onboard-error"
          role="alert"
        >
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
          <span>{errorMessage}</span>
        </div>
      )}

      {notificationNote && (
        <div
          className="rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900"
          data-testid="iadm01-notification-deferred"
        >
          {notificationNote}
        </div>
      )}

      {uiStep === 1 && (
        <Card data-testid="iadm01-onboard-step-details">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <Building2 className="h-5 w-5" /> Institute details
            </CardTitle>
            <CardDescription>Code must be unique across the platform.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <label className="text-sm font-medium">Institute name</label>
              <Input
                data-testid="iadm01-field-name"
                value={form.name}
                onChange={(e) => update('name', e.target.value)}
                placeholder="Example University"
              />
            </div>
            <div>
              <label className="text-sm font-medium">Institute code</label>
              <Input
                data-testid="iadm01-field-code"
                value={form.code}
                onChange={(e) => update('code', e.target.value.toUpperCase())}
                placeholder="EXU01"
              />
            </div>
            <div>
              <label className="text-sm font-medium">Type</label>
              <Input data-testid="iadm01-field-type" value={form.type} onChange={(e) => update('type', e.target.value)} />
            </div>
            <div>
              <label className="text-sm font-medium">Plan reference</label>
              <Input
                data-testid="iadm01-field-plan"
                value={form.planReference}
                onChange={(e) => update('planReference', e.target.value)}
              />
            </div>
            <div>
              <label className="text-sm font-medium">Region / contact</label>
              <Input
                data-testid="iadm01-field-region"
                value={form.region}
                onChange={(e) => update('region', e.target.value)}
              />
            </div>
          </CardContent>
          <CardFooter className="justify-end">
            <Button
              data-testid="iadm01-details-next"
              disabled={!form.name.trim() || !form.code.trim()}
              onClick={() => setUiStep(2)}
            >
              Review <ArrowRight className="ml-2 h-4 w-4" />
            </Button>
          </CardFooter>
        </Card>
      )}

      {uiStep === 2 && (
        <Card data-testid="iadm01-onboard-step-review">
          <CardHeader>
            <CardTitle className="text-lg">Review and register</CardTitle>
            <CardDescription>Institute starts in ONBOARDING. No college is created here.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2 text-sm">
            <p>
              <strong>Name:</strong> {form.name}
            </p>
            <p>
              <strong>Code:</strong> {form.code}
            </p>
            <p>
              <strong>Type / plan:</strong> {form.type} / {form.planReference}
            </p>
          </CardContent>
          <CardFooter className="justify-between">
            <Button variant="outline" onClick={() => setUiStep(1)}>
              <ArrowLeft className="mr-2 h-4 w-4" /> Back
            </Button>
            <Button data-testid="iadm01-register-submit" disabled={busy} onClick={registerInstitute}>
              {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              Register institute
            </Button>
          </CardFooter>
        </Card>
      )}

      {uiStep === 3 && (
        <Card data-testid="iadm01-onboard-step-admin">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <UserCheck className="h-5 w-5" /> First Institution Admin
            </CardTitle>
            <CardDescription>
              Backend invites or creates the login, then creates the profile and appoints Institution Admin (R03).
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <div className="sm:col-span-2 text-xs text-slate-500">
              Institute id: <code data-testid="iadm01-registered-id">{instituteId}</code>
            </div>
            <div>
              <label className="text-sm font-medium">Admin email</label>
              <Input
                data-testid="iadm01-field-admin-email"
                type="email"
                value={form.adminEmail}
                onChange={(e) => update('adminEmail', e.target.value)}
              />
            </div>
            <div>
              <label className="text-sm font-medium">Display name</label>
              <Input
                data-testid="iadm01-field-admin-name"
                value={form.adminDisplayName}
                onChange={(e) => update('adminDisplayName', e.target.value)}
              />
            </div>
            {onboardingSteps.length > 0 && (
              <div className="sm:col-span-2 space-y-1" data-testid="iadm01-onboarding-steps">
                {onboardingSteps.map((s) => (
                  <div key={s.stepKey} className="flex items-center justify-between rounded border px-3 py-2 text-sm">
                    <span>{s.stepKey}</span>
                    <Badge variant={s.status === 'SUCCEEDED' ? 'default' : 'outline'}>
                      {s.status}
                    </Badge>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
          <CardFooter className="justify-between gap-2">
            <Button
              variant="outline"
              data-testid="iadm01-invite-with-fail"
              disabled={busy || !form.adminEmail.trim()}
              onClick={() => runFirstAdminOnboarding(deliberateFail)}
            >
              {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <RefreshCw className="mr-2 h-4 w-4" />}
              {deliberateFail ? 'Invite (demo: fail once)' : 'Retry invitation'}
            </Button>
            <Button
              data-testid="iadm01-invite-submit"
              disabled={busy || !form.adminEmail.trim()}
              onClick={() => runFirstAdminOnboarding(false)}
            >
              Invite and appoint
            </Button>
          </CardFooter>
        </Card>
      )}

      {uiStep === 4 && (
        <Card data-testid="iadm01-onboard-success">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg text-emerald-700">
              <CheckCircle2 className="h-5 w-5" /> Institute ready for Institution Admin
            </CardTitle>
            <CardDescription>
              The invited person can log in and will see the Institution Admin home. Register the first college from that
              desk.
            </CardDescription>
          </CardHeader>
          <CardFooter className="justify-end gap-2">
            <Link
              href="/platform/institutes"
              className="inline-flex items-center rounded-md border px-4 py-2 text-sm"
            >
              Institutes list
            </Link>
            <Button data-testid="iadm01-open-detail" onClick={() => router.push(`/platform/institutes/${instituteId}`)}>
              Open institute
            </Button>
          </CardFooter>
        </Card>
      )}
    </div>
  );
}
