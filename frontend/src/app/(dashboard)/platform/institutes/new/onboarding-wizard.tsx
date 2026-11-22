'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
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

/** Institute kinds for registration (US-001). Stored as profile.type — not a college type. */
const INSTITUTE_TYPES = [
  { value: 'UNIVERSITY', label: 'University' },
  { value: 'DEEMED_UNIVERSITY', label: 'Deemed university' },
  { value: 'AUTONOMOUS_COLLEGE', label: 'Autonomous college (institute tenant)' },
  { value: 'AFFILIATED_COLLEGE_GROUP', label: 'Affiliated college group' },
  { value: 'POLYTECHNIC', label: 'Polytechnic / technical institute' },
  { value: 'SCHOOL_GROUP', label: 'School group' },
  { value: 'OTHER', label: 'Other' },
] as const;

/**
 * Plan reference codes (US-001). Link/reference only — IADM-04 owns real plan limits.
 * Aligns with existing onboard-tenant enum plus Custom / Trial 30 days.
 */
const PLAN_REFERENCES = [
  { value: 'ENTERPRISE', label: 'Enterprise' },
  { value: 'GROWTH', label: 'Growth' },
  { value: 'STANDARD', label: 'Standard' },
  { value: 'TRIAL_30_DAYS', label: 'Trial for 30 days' },
  { value: 'CUSTOM', label: 'Custom' },
] as const;

const selectClassName =
  'flex h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 py-2 text-sm text-[#1B2433] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#1F6FEB] focus-visible:ring-offset-1';

interface FormState {
  name: string;
  code: string;
  type: string;
  planReference: string;
  /** Primary contact for platform outreach (email or phone) — US-001 "contact". */
  contact: string;
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
  const searchParams = useSearchParams();
  const resumeId = searchParams.get('resume');
  const [uiStep, setUiStep] = useState<1 | 2 | 3 | 4>(1);
  const [busy, setBusy] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [notificationNote, setNotificationNote] = useState<string | null>(null);
  const [instituteId, setInstituteId] = useState<string | null>(null);
  const [resumeMode, setResumeMode] = useState(false);
  const [onboardingSteps, setOnboardingSteps] = useState<
    Array<{ stepKey: string; status: StepStatus; lastError?: string | null }>
  >([]);
  const [deliberateFail, setDeliberateFail] = useState(true);

  const [form, setForm] = useState<FormState>({
    name: '',
    code: '',
    type: '',
    planReference: '',
    contact: '',
    adminEmail: '',
    adminDisplayName: '',
  });

  // Resume first-admin for an existing ONBOARDING institute (do not POST a new tenant).
  useEffect(() => {
    if (!resumeId) return;
    let cancelled = false;
    (async () => {
      setBusy(true);
      setErrorMessage(null);
      try {
        const [detailRes, stepsRes] = await Promise.all([
          fetch(`/api/platform/institutes/${resumeId}`, { cache: 'no-store' }),
          fetch(`/api/platform/institutes/${resumeId}/first-admin/onboarding`, { cache: 'no-store' }),
        ]);
        const detail = await detailRes.json().catch(() => ({}));
        const stepsBody = await stepsRes.json().catch(() => ({}));
        if (cancelled) return;
        if (!detailRes.ok) {
          setErrorMessage(detail.detail || 'Could not load institute to resume.');
          return;
        }
        setResumeMode(true);
        setInstituteId(resumeId);
        setForm((prev) => ({
          ...prev,
          name: detail.name || prev.name,
          code: detail.code || prev.code,
          contact: detail.contact || prev.contact,
          type: detail.type || prev.type,
          planReference: detail.planReference || prev.planReference,
        }));
        if (Array.isArray(stepsBody.steps)) setOnboardingSteps(stepsBody.steps);
        setUiStep(3);
      } catch {
        if (!cancelled) setErrorMessage('Could not load institute to resume.');
      } finally {
        if (!cancelled) setBusy(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [resumeId]);

  const update = (field: keyof FormState, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  const detailsReady =
    form.name.trim().length > 0 &&
    form.code.trim().length > 0 &&
    form.type.length > 0 &&
    form.planReference.length > 0 &&
    form.contact.trim().length > 0;

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
          contact: form.contact.trim(),
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
        <h1 className="text-2xl font-semibold text-[#0B1F3A]">
          {resumeMode ? 'Resume first Institution Admin' : 'Onboard Institute'}
        </h1>
        <p className="mt-1 text-sm text-slate-600">
          {resumeMode
            ? 'You are continuing onboarding for an existing institute. This will not create a new tenant.'
            : 'Register an institute (tenant), then invite its first Institution Admin. Colleges are created later by that administrator — not in this wizard.'}
        </p>
        {resumeMode && instituteId ? (
          <p className="mt-1 font-mono text-xs text-[#6B7688]" data-testid="iadm01-resume-institute-id">
            Resuming institute: {form.code || instituteId}
          </p>
        ) : null}
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
              <label htmlFor="iadm01-field-type" className="text-sm font-medium">
                Institute type
              </label>
              <select
                id="iadm01-field-type"
                data-testid="iadm01-field-type"
                className={selectClassName}
                value={form.type}
                onChange={(e) => update('type', e.target.value)}
              >
                <option value="">Select institute type…</option>
                {INSTITUTE_TYPES.map((t) => (
                  <option key={t.value} value={t.value}>
                    {t.label}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor="iadm01-field-plan" className="text-sm font-medium">
                Plan reference
              </label>
              <select
                id="iadm01-field-plan"
                data-testid="iadm01-field-plan"
                className={selectClassName}
                value={form.planReference}
                onChange={(e) => update('planReference', e.target.value)}
              >
                <option value="">Select plan…</option>
                {PLAN_REFERENCES.map((p) => (
                  <option key={p.value} value={p.value}>
                    {p.label}
                  </option>
                ))}
              </select>
              <p className="mt-1 text-xs text-slate-500">
                Reference only — subscription limits live in Billing & Entitlements (IADM-04), not copied here.
              </p>
            </div>
            <div className="sm:col-span-2">
              <label htmlFor="iadm01-field-contact" className="text-sm font-medium">
                Primary contact
              </label>
              <Input
                id="iadm01-field-contact"
                data-testid="iadm01-field-contact"
                value={form.contact}
                onChange={(e) => update('contact', e.target.value)}
                placeholder="ops@maharani.edu or +91-98XXXXXXXX"
              />
              <p className="mt-1 text-xs text-slate-500">
                How the platform reaches this institute during onboarding (email or phone). Not the Institution Admin
                login — that is invited on the next step.
              </p>
            </div>
          </CardContent>
          <CardFooter className="justify-end">
            <Button
              data-testid="iadm01-details-next"
              disabled={!detailsReady}
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
              <strong>Type:</strong>{' '}
              {INSTITUTE_TYPES.find((t) => t.value === form.type)?.label || form.type}
            </p>
            <p>
              <strong>Plan:</strong>{' '}
              {PLAN_REFERENCES.find((p) => p.value === form.planReference)?.label || form.planReference}
            </p>
            <p>
              <strong>Primary contact:</strong> {form.contact}
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
