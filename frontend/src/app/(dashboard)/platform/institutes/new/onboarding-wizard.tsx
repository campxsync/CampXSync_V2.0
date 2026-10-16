'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import {
  Building2,
  GraduationCap,
  UserCheck,
  CheckCircle2,
  AlertCircle,
  ArrowRight,
  ArrowLeft,
  Loader2,
  ShieldCheck,
  Globe,
  Tag,
  RefreshCw,
  ExternalLink,
} from 'lucide-react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';

interface OnboardingFormState {
  tenantName: string;
  tenantCode: string;
  subdomain: string;
  plan: 'ENTERPRISE' | 'GROWTH' | 'STANDARD' | 'TRIAL';
  collegeName: string;
  collegeCode: string;
  adminName: string;
  adminEmail: string;
  adminPhone: string;
}

interface SagaStepState {
  step: number;
  name: string;
  title: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'GATED';
  detail?: string;
}

export function OnboardingWizard() {
  const router = useRouter();
  const [step, setStep] = useState<number>(1);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [failedStepNum, setFailedStepNum] = useState<number | null>(null);
  const [successData, setSuccessData] = useState<any | null>(null);

  const [formData, setFormData] = useState<OnboardingFormState>({
    tenantName: '',
    tenantCode: '',
    subdomain: '',
    plan: 'ENTERPRISE',
    collegeName: '',
    collegeCode: '',
    adminName: '',
    adminEmail: '',
    adminPhone: '',
  });

  const [sagaSteps, setSagaSteps] = useState<SagaStepState[]>([
    { step: 1, name: 'CREATE_TENANT', title: '1. Create Institute in Onboarding Status', status: 'PENDING' },
    { step: 2, name: 'SEED_CONFIG', title: '2. Seed Tenant Config & 30 System Roles', status: 'PENDING' },
    { step: 3, name: 'PROVISION_ADMIN', title: '3. Provision First College Administrator', status: 'PENDING' },
    { step: 4, name: 'GRANT_ROLE', title: '4. Grant COLLEGE_ADMIN Role Assignment', status: 'PENDING' },
    { step: 5, name: 'ACTIVATE_NODE', title: '5. Activate Institutional Tenancy Node', status: 'PENDING' },
  ]);

  const updateField = (field: keyof OnboardingFormState, value: string) => {
    setFormData((prev) => {
      const next = { ...prev, [field]: value };
      // Auto-suggest subdomain and college code if empty
      if (field === 'tenantCode' && !prev.subdomain) {
        next.subdomain = value.toLowerCase().replace(/[^a-z0-9-]/g, '');
      }
      if (field === 'tenantName' && !prev.collegeName) {
        next.collegeName = value;
      }
      return next;
    });
    setErrorMessage(null);
    setFailedStepNum(null);
  };

  const validateStep = (currentStep: number): boolean => {
    if (currentStep === 1) {
      if (!formData.tenantName.trim() || formData.tenantName.length < 3) {
        setErrorMessage('Institute name must be at least 3 characters.');
        return false;
      }
      const codeRegex = /^[A-Z0-9_-]{2,30}$/;
      if (!codeRegex.test(formData.tenantCode.trim())) {
        setErrorMessage('Institute code must be 2-30 uppercase characters, numbers, underscores, or hyphens (e.g. XAVIER_TECH).');
        return false;
      }
      const subRegex = /^[a-z0-9-]{2,30}$/;
      const effectiveSubdomain = formData.subdomain.trim() || formData.tenantCode.toLowerCase();
      if (!subRegex.test(effectiveSubdomain)) {
        setErrorMessage('Subdomain must be 2-30 lowercase characters, numbers, or hyphens (e.g. xavier).');
        return false;
      }
    } else if (currentStep === 2) {
      if (!formData.collegeName.trim() || formData.collegeName.length < 3) {
        setErrorMessage('Primary college name must be at least 3 characters.');
        return false;
      }
      const codeRegex = /^[A-Z0-9_-]{2,30}$/;
      if (!codeRegex.test(formData.collegeCode.trim())) {
        setErrorMessage('College code must be 2-30 uppercase characters, numbers, underscores, or hyphens (e.g. ENGG).');
        return false;
      }
    } else if (currentStep === 3) {
      if (!formData.adminName.trim() || formData.adminName.length < 2) {
        setErrorMessage('First College Administrator name is required.');
        return false;
      }
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailRegex.test(formData.adminEmail.trim())) {
        setErrorMessage('A valid administrator email is required.');
        return false;
      }
    }
    setErrorMessage(null);
    setFailedStepNum(null);
    return true;
  };

  const handleNext = () => {
    if (validateStep(step)) {
      setStep((prev) => prev + 1);
    }
  };

  const handlePrev = () => {
    setErrorMessage(null);
    setFailedStepNum(null);
    setStep((prev) => Math.max(1, prev - 1));
  };

  const handleSubmit = async () => {
    setIsSubmitting(true);
    setErrorMessage(null);
    setFailedStepNum(null);
    setStep(5); // Move to saga progress view

    // Simulate animated step progression for UI responsiveness
    setSagaSteps((prev) =>
      prev.map((s, idx) => (idx === 0 ? { ...s, status: 'IN_PROGRESS' } : { ...s, status: 'PENDING' }))
    );

    try {
      const payload = {
        ...formData,
        subdomain: formData.subdomain.trim() || formData.tenantCode.toLowerCase(),
      };

      const res = await fetch('/api/platform/onboard-tenant', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      const data = await res.json();

      if (!res.ok) {
        const failStep = data.failedStep || 1;
        setFailedStepNum(failStep);
        setSagaSteps((prev) =>
          prev.map((s) => {
            if (s.step === failStep) return { ...s, status: 'FAILED' };
            if (s.step < failStep) return { ...s, status: 'COMPLETED' };
            return { ...s, status: 'PENDING' };
          })
        );
        setErrorMessage(data.detail || data.title || 'Institutional onboarding saga failed.');
        setIsSubmitting(false);
        return;
      }

      // Mark saga steps completed per backend report
      setSagaSteps((prev) =>
        prev.map((s) => ({
          ...s,
          status: data.gated && s.step === 5 ? 'GATED' : 'COMPLETED',
        }))
      );

      setSuccessData(data);
    } catch (err: any) {
      setErrorMessage(err.message || 'Network error communicating with platform service.');
      setSagaSteps((prev) => prev.map((s, idx) => (idx === 0 ? { ...s, status: 'FAILED' } : s)));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleRetry = () => {
    setErrorMessage(null);
    setFailedStepNum(null);
    handleSubmit();
  };

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      {/* Step Indicator */}
      <div className="grid grid-cols-4 gap-2">
        {[
          { num: 1, label: 'Tenant Identity', icon: Building2 },
          { num: 2, label: 'Primary College', icon: GraduationCap },
          { num: 3, label: 'Administrator', icon: UserCheck },
          { num: 4, label: 'Review & Saga', icon: CheckCircle2 },
        ].map(({ num, label, icon: Icon }) => (
          <div
            key={num}
            className={`flex items-center gap-2 p-3 rounded-[8px] border text-xs font-medium transition-all ${
              step === num
                ? 'border-[#1F6FEB] bg-[#E8F0FE] text-[#1F6FEB]'
                : step > num
                ? 'border-[#1E9E6A] bg-[#EAF8F1] text-[#1E9E6A]'
                : 'border-[#E3E8EF] bg-white text-[#6B7688]'
            }`}
          >
            <div
              className={`flex h-6 w-6 items-center justify-center rounded-full text-xs font-bold ${
                step === num
                  ? 'bg-[#1F6FEB] text-white'
                  : step > num
                  ? 'bg-[#1E9E6A] text-white'
                  : 'bg-[#F6F8FB] text-[#6B7688]'
              }`}
            >
              {step > num ? '✓' : num}
            </div>
            <span className="hidden sm:inline">{label}</span>
          </div>
        ))}
      </div>

      {/* Error Alert */}
      {errorMessage && (
        <div className="p-4 rounded-[8px] border border-[#D64545] bg-[#FDF3F3] text-xs text-[#D64545] flex items-start gap-2.5">
          <AlertCircle className="h-4 w-4 mt-0.5 shrink-0" />
          <div className="flex-1">
            <p className="font-semibold">
              {failedStepNum ? `Saga Failure at Step ${failedStepNum}` : 'Validation or Operation Error'}
            </p>
            <p className="mt-0.5">{errorMessage}</p>
          </div>
          {step === 5 && failedStepNum && (
            <Button variant="outline" size="sm" onClick={handleRetry} className="bg-white hover:bg-white text-[#D64545] border-[#D64545]">
              <RefreshCw className="mr-1 h-3.5 w-3.5" />
              Retry Step
            </Button>
          )}
        </div>
      )}

      {/* Step 1: Tenant Identity */}
      {step === 1 && (
        <Card>
          <CardHeader>
            <CardTitle>Step 1: Institutional Tenant Identity</CardTitle>
            <CardDescription>
              Define the top-level institutional entity in the multi-tenant registry.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                Institution / University Name <span className="text-[#D64545]">*</span>
              </label>
              <Input
                placeholder="e.g. St. Xavier Institute of Engineering & Technology"
                value={formData.tenantName}
                onChange={(e) => updateField('tenantName', e.target.value)}
              />
              <p className="text-[11px] text-[#6B7688] mt-1">
                Official display name displayed across institutional dashboards.
              </p>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                  Institutional Tenant Code <span className="text-[#D64545]">*</span>
                </label>
                <Input
                  placeholder="e.g. XAVIER_TECH"
                  className="font-mono uppercase"
                  value={formData.tenantCode}
                  onChange={(e) => updateField('tenantCode', e.target.value.toUpperCase())}
                />
                <p className="text-[11px] text-[#6B7688] mt-1">
                  Unique uppercase identifier (2-30 chars, e.g. DIT).
                </p>
              </div>

              <div>
                <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                  Institutional Subdomain <span className="text-[#D64545]">*</span>
                </label>
                <div className="flex rounded-[6px] border border-[#E3E8EF] focus-within:border-[#1F6FEB]">
                  <Input
                    placeholder="e.g. xavier"
                    className="border-0 focus-visible:ring-0 text-xs font-mono lowercase"
                    value={formData.subdomain}
                    onChange={(e) => updateField('subdomain', e.target.value.toLowerCase())}
                  />
                  <span className="inline-flex items-center px-3 rounded-r-[6px] bg-[#F6F8FB] text-[11px] font-mono text-[#6B7688] border-l border-[#E3E8EF]">
                    .campxsync.edu
                  </span>
                </div>
                <p className="text-[11px] text-[#6B7688] mt-1">
                  Custom tenant URL routing handle.
                </p>
              </div>
            </div>

            <div>
              <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                Subscription Plan <span className="text-[#D64545]">*</span>
              </label>
              <select
                className="w-full rounded-[6px] border border-[#E3E8EF] px-3 py-2 text-xs focus:border-[#1F6FEB] focus:outline-none bg-white text-[#0B1F3A]"
                value={formData.plan}
                onChange={(e) => updateField('plan', e.target.value)}
              >
                <option value="ENTERPRISE">Enterprise Multi-Campus (Unlimited Departments & Students)</option>
                <option value="GROWTH">Institutional Growth (Up to 10 Departments, 5,000 Students)</option>
                <option value="STANDARD">Standard Academic (Up to 4 Departments, 1,000 Students)</option>
                <option value="TRIAL">Trial Pilot (30 Days Evaluation)</option>
              </select>
            </div>
          </CardContent>
          <CardFooter className="flex justify-end border-t border-[#E3E8EF] pt-4">
            <Button variant="primary" size="sm" onClick={handleNext}>
              Next: Primary College <ArrowRight className="ml-1.5 h-4 w-4" />
            </Button>
          </CardFooter>
        </Card>
      )}

      {/* Step 2: Primary College */}
      {step === 2 && (
        <Card>
          <CardHeader>
            <CardTitle>Step 2: Primary College & Campus</CardTitle>
            <CardDescription>
              Create the initial primary academic college within this institutional tenancy.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                Primary College / Campus Name <span className="text-[#D64545]">*</span>
              </label>
              <Input
                placeholder="e.g. Faculty of Engineering and Technology"
                value={formData.collegeName}
                onChange={(e) => updateField('collegeName', e.target.value)}
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                College Code <span className="text-[#D64545]">*</span>
              </label>
              <Input
                placeholder="e.g. FET"
                className="font-mono uppercase"
                value={formData.collegeCode}
                onChange={(e) => updateField('collegeCode', e.target.value.toUpperCase())}
              />
              <p className="text-[11px] text-[#6B7688] mt-1">
                Scoped uppercase campus code (e.g. FET, ENGG, MED).
              </p>
            </div>
          </CardContent>
          <CardFooter className="flex justify-between border-t border-[#E3E8EF] pt-4">
            <Button variant="outline" size="sm" onClick={handlePrev}>
              <ArrowLeft className="mr-1.5 h-4 w-4" /> Back
            </Button>
            <Button variant="primary" size="sm" onClick={handleNext}>
              Next: Administrator <ArrowRight className="ml-1.5 h-4 w-4" />
            </Button>
          </CardFooter>
        </Card>
      )}

      {/* Step 3: Administrator */}
      {step === 3 && (
        <Card>
          <CardHeader>
            <CardTitle>Step 3: First College Administrator</CardTitle>
            <CardDescription>
              Provision the root institutional administrator account with COLLEGE_ADMIN privileges.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                Administrator Full Name <span className="text-[#D64545]">*</span>
              </label>
              <Input
                placeholder="e.g. Dr. Rajesh Kumar"
                value={formData.adminName}
                onChange={(e) => updateField('adminName', e.target.value)}
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                Administrator Email Address <span className="text-[#D64545]">*</span>
              </label>
              <Input
                type="email"
                placeholder="e.g. admin.xavier@campxsync.edu"
                value={formData.adminEmail}
                onChange={(e) => updateField('adminEmail', e.target.value)}
              />
              <p className="text-[11px] text-[#6B7688] mt-1">
                Must be an active institutional email. Receives root credential invite and RBAC assignments.
              </p>
            </div>

            <div>
              <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                Contact Phone Number (Optional)
              </label>
              <Input
                placeholder="e.g. +91 98765 43210"
                value={formData.adminPhone}
                onChange={(e) => updateField('adminPhone', e.target.value)}
              />
            </div>
          </CardContent>
          <CardFooter className="flex justify-between border-t border-[#E3E8EF] pt-4">
            <Button variant="outline" size="sm" onClick={handlePrev}>
              <ArrowLeft className="mr-1.5 h-4 w-4" /> Back
            </Button>
            <Button variant="primary" size="sm" onClick={handleNext}>
              Review Configuration <ArrowRight className="ml-1.5 h-4 w-4" />
            </Button>
          </CardFooter>
        </Card>
      )}

      {/* Step 4: Review & Confirmation */}
      {step === 4 && (
        <Card>
          <CardHeader>
            <CardTitle>Step 4: Review & Launch Onboarding Saga</CardTitle>
            <CardDescription>
              Please verify the institutional parameters before triggering the multi-step ADM provisioning saga.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="rounded-[8px] border border-[#E3E8EF] divide-y divide-[#E3E8EF] bg-[#F6F8FB]/30">
              {/* Tenant Summary */}
              <div className="p-4 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-[#0B1F3A] uppercase tracking-wider flex items-center gap-1.5">
                    <Building2 className="h-3.5 w-3.5 text-[#1F6FEB]" /> Institutional Tenancy
                  </span>
                  <Badge status="ONBOARDING" />
                </div>
                <div className="grid grid-cols-2 gap-2 text-xs">
                  <div>
                    <span className="text-[#6B7688] block text-[11px]">Institute Name</span>
                    <span className="font-semibold text-[#0B1F3A]">{formData.tenantName}</span>
                  </div>
                  <div>
                    <span className="text-[#6B7688] block text-[11px]">Tenant Code</span>
                    <span className="font-mono font-semibold text-[#0B1F3A]">{formData.tenantCode}</span>
                  </div>
                  <div>
                    <span className="text-[#6B7688] block text-[11px]">Subdomain Routing</span>
                    <span className="font-mono text-[#1F6FEB]">{formData.subdomain || formData.tenantCode.toLowerCase()}.campxsync.edu</span>
                  </div>
                  <div>
                    <span className="text-[#6B7688] block text-[11px]">Subscription Plan</span>
                    <span className="font-semibold text-[#0B1F3A]">{formData.plan}</span>
                  </div>
                </div>
              </div>

              {/* Primary College Summary */}
              <div className="p-4 space-y-2">
                <span className="text-xs font-semibold text-[#0B1F3A] uppercase tracking-wider flex items-center gap-1.5">
                  <GraduationCap className="h-3.5 w-3.5 text-[#1F6FEB]" /> Primary College & Campus
                </span>
                <div className="grid grid-cols-2 gap-2 text-xs">
                  <div>
                    <span className="text-[#6B7688] block text-[11px]">College Name</span>
                    <span className="font-semibold text-[#0B1F3A]">{formData.collegeName}</span>
                  </div>
                  <div>
                    <span className="text-[#6B7688] block text-[11px]">Campus Code</span>
                    <span className="font-mono font-semibold text-[#0B1F3A]">{formData.collegeCode}</span>
                  </div>
                </div>
              </div>

              {/* Administrator Summary */}
              <div className="p-4 space-y-2">
                <span className="text-xs font-semibold text-[#0B1F3A] uppercase tracking-wider flex items-center gap-1.5">
                  <UserCheck className="h-3.5 w-3.5 text-[#1F6FEB]" /> First College Administrator
                </span>
                <div className="grid grid-cols-2 gap-2 text-xs">
                  <div>
                    <span className="text-[#6B7688] block text-[11px]">Administrator</span>
                    <span className="font-semibold text-[#0B1F3A]">{formData.adminName}</span>
                  </div>
                  <div>
                    <span className="text-[#6B7688] block text-[11px]">Admin Email</span>
                    <span className="font-mono font-semibold text-[#0B1F3A]">{formData.adminEmail}</span>
                  </div>
                  <div>
                    <span className="text-[#6B7688] block text-[11px]">RBAC Role</span>
                    <span className="font-semibold text-[#1F6FEB]">COLLEGE_ADMIN (Root Access)</span>
                  </div>
                </div>
              </div>
            </div>

            <div className="p-3 rounded-[6px] border border-[#B8D3FC] bg-[#E8F0FE]/40 text-xs text-[#0B1F3A]">
              <p className="font-semibold flex items-center gap-1.5">
                <ShieldCheck className="h-4 w-4 text-[#1F6FEB]" /> Automated ADM Saga Execution
              </p>
              <p className="text-[11px] text-[#6B7688] mt-1">
                Submitting will initiate the 5-step transactional provisioning saga: initializing the tenant node in ONBOARDING status, generating system roles, provisioning administrator credentials, granting RBAC permissions, and activating the tenant.
              </p>
            </div>
          </CardContent>
          <CardFooter className="flex justify-between border-t border-[#E3E8EF] pt-4">
            <Button variant="outline" size="sm" onClick={handlePrev} disabled={isSubmitting}>
              <ArrowLeft className="mr-1.5 h-4 w-4" /> Edit Parameters
            </Button>
            <Button variant="primary" size="sm" onClick={handleSubmit} disabled={isSubmitting}>
              {isSubmitting ? (
                <>
                  <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />
                  Initiating Saga...
                </>
              ) : (
                <>
                  <CheckCircle2 className="mr-1.5 h-4 w-4" />
                  Confirm & Launch Onboarding Saga
                </>
              )}
            </Button>
          </CardFooter>
        </Card>
      )}

      {/* Step 5: Saga Execution Progress & Completion */}
      {step === 5 && (
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle>ADM Onboarding Saga Execution</CardTitle>
                <CardDescription>
                  Tracking multi-step institutional provisioning workflow
                </CardDescription>
              </div>
              {isSubmitting && (
                <div className="flex items-center gap-1.5 text-xs text-[#1F6FEB] font-medium font-mono">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Executing...
                </div>
              )}
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            {/* Step-by-Step Saga Progress */}
            <div className="space-y-3">
              {sagaSteps.map((s) => {
                const isComplete = s.status === 'COMPLETED';
                const isFailed = s.status === 'FAILED';
                const isGated = s.status === 'GATED';
                const inProgress = s.status === 'IN_PROGRESS';

                return (
                  <div
                    key={s.step}
                    className={`flex items-center justify-between p-3.5 rounded-[8px] border transition-all ${
                      isComplete
                        ? 'border-[#B2E6CE] bg-[#EAF8F1]/40'
                        : isFailed
                        ? 'border-[#F8BDBD] bg-[#FDECEC]/50'
                        : isGated
                        ? 'border-[#FCE2B3] bg-[#FEF7EA]/50'
                        : inProgress
                        ? 'border-[#B8D3FC] bg-[#E8F0FE]/40'
                        : 'border-[#E3E8EF] bg-white opacity-70'
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <div
                        className={`flex h-7 w-7 items-center justify-center rounded-full text-xs font-bold ${
                          isComplete
                            ? 'bg-[#1E9E6A] text-white'
                            : isFailed
                            ? 'bg-[#D64545] text-white'
                            : isGated
                            ? 'bg-[#E39B16] text-white'
                            : inProgress
                            ? 'bg-[#1F6FEB] text-white'
                            : 'bg-[#E3E8EF] text-[#6B7688]'
                        }`}
                      >
                        {isComplete ? '✓' : isFailed ? '✕' : inProgress ? (
                          <Loader2 className="h-3.5 w-3.5 animate-spin" />
                        ) : (
                          s.step
                        )}
                      </div>
                      <div>
                        <p className="text-xs font-semibold text-[#0B1F3A]">{s.title}</p>
                        <p className="text-[11px] text-[#6B7688] mt-0.5">
                          {isComplete
                            ? 'Milestone verified and locked in audit trail.'
                            : isFailed
                            ? 'Saga stopped. Error preserved; retry available.'
                            : isGated
                            ? 'Validated in simulation. Staging write gated awaiting approval.'
                            : inProgress
                            ? 'Executing transactional operation...'
                            : 'Queued in dependency order.'}
                        </p>
                      </div>
                    </div>

                    <span
                      className={`text-[10px] font-mono font-semibold px-2 py-0.5 rounded-full ${
                        isComplete
                          ? 'bg-[#1E9E6A]/10 text-[#1E9E6A]'
                          : isFailed
                          ? 'bg-[#D64545]/10 text-[#D64545]'
                          : isGated
                          ? 'bg-[#E39B16]/10 text-[#E39B16]'
                          : inProgress
                          ? 'bg-[#1F6FEB]/10 text-[#1F6FEB]'
                          : 'bg-[#F0F2F5] text-[#6B7688]'
                      }`}
                    >
                      {s.status}
                    </span>
                  </div>
                );
              })}
            </div>

            {/* Staging Write Gate Notice */}
            {successData?.gated && (
              <div className="p-4 rounded-[8px] border border-[#FCE2B3] bg-[#FEF7EA] text-xs text-[#0B1F3A] space-y-1">
                <p className="font-semibold text-[#E39B16] flex items-center gap-1.5">
                  <ShieldCheck className="h-4 w-4" /> Staging Safety Write Boundary Active
                </p>
                <p className="text-[11px] text-[#6B7688]">
                  Phase 4 saga inputs, validation schemas, and pre-flight conflict checks succeeded. Per Master Prompt Rule 3, creating permanent tenants and accounts in staging requires explicit approval: <span className="font-mono font-bold text-[#0B1F3A]">GO PROVISION-TENANT</span>.
                </p>
              </div>
            )}

            {/* Success Summary if Completed */}
            {successData && !successData.gated && (
              <div className="p-4 rounded-[8px] border border-[#B2E6CE] bg-[#EAF8F1] text-xs text-[#0B1F3A] space-y-1">
                <p className="font-semibold text-[#1E9E6A] flex items-center gap-1.5">
                  <CheckCircle2 className="h-4 w-4" /> Institute Onboarded Successfully
                </p>
                <p className="text-[11px] text-[#6B7688]">
                  {successData.message}
                </p>
              </div>
            )}
          </CardContent>
          <CardFooter className="flex justify-between border-t border-[#E3E8EF] pt-4">
            {failedStepNum ? (
              <>
                <Button variant="outline" size="sm" onClick={() => setStep(4)}>
                  <ArrowLeft className="mr-1.5 h-4 w-4" /> Edit Parameters
                </Button>
                <Button variant="primary" size="sm" onClick={handleRetry}>
                  <RefreshCw className="mr-1.5 h-4 w-4" /> Retry Onboarding Saga
                </Button>
              </>
            ) : (
              <>
                <Link href="/platform">
                  <Button variant="outline" size="sm">
                    Return to Dashboard
                  </Button>
                </Link>
                <Link href="/platform/institutes">
                  <Button variant="primary" size="sm">
                    View in Registry <ArrowRight className="ml-1.5 h-4 w-4" />
                  </Button>
                </Link>
              </>
            )}
          </CardFooter>
        </Card>
      )}
    </div>
  );
}
