'use client';

import React, { useState } from 'react';
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
  ShieldAlert,
} from 'lucide-react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

interface OnboardingFormState {
  tenantName: string;
  tenantCode: string;
  collegeName: string;
  collegeCode: string;
  adminName: string;
  adminEmail: string;
  adminPhone: string;
}

export function OnboardingWizard() {
  const router = useRouter();
  const [step, setStep] = useState<number>(1);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successData, setSuccessData] = useState<any | null>(null);

  const [formData, setFormData] = useState<OnboardingFormState>({
    tenantName: '',
    tenantCode: '',
    collegeName: '',
    collegeCode: '',
    adminName: '',
    adminEmail: '',
    adminPhone: '',
  });

  const updateField = (field: keyof OnboardingFormState, value: string) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
    setErrorMessage(null);
  };

  const validateStep = (currentStep: number): boolean => {
    if (currentStep === 1) {
      if (!formData.tenantName.trim() || formData.tenantName.length < 3) {
        setErrorMessage('Institute name must be at least 3 characters.');
        return false;
      }
      const codeRegex = /^[A-Z0-9_-]{2,30}$/;
      if (!codeRegex.test(formData.tenantCode.trim())) {
        setErrorMessage('Tenant code must be 2-30 uppercase characters, numbers, underscores, or hyphens (e.g. DIT_TECH).');
        return false;
      }
    } else if (currentStep === 2) {
      if (!formData.collegeName.trim() || formData.collegeName.length < 3) {
        setErrorMessage('Primary college name must be at least 3 characters.');
        return false;
      }
      const codeRegex = /^[A-Z0-9_-]{2,30}$/;
      if (!codeRegex.test(formData.collegeCode.trim())) {
        setErrorMessage('College code must be 2-30 uppercase characters, numbers, underscores, or hyphens (e.g. DIT).');
        return false;
      }
    } else if (currentStep === 3) {
      if (!formData.adminName.trim() || formData.adminName.length < 2) {
        setErrorMessage('Administrator name is required.');
        return false;
      }
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailRegex.test(formData.adminEmail.trim())) {
        setErrorMessage('A valid administrator email is required.');
        return false;
      }
    }
    setErrorMessage(null);
    return true;
  };

  const handleNext = () => {
    if (validateStep(step)) {
      setStep((prev) => prev + 1);
    }
  };

  const handlePrev = () => {
    setErrorMessage(null);
    setStep((prev) => Math.max(1, prev - 1));
  };

  const handleSubmit = async () => {
    setIsSubmitting(true);
    setErrorMessage(null);

    try {
      const res = await fetch('/api/platform/onboard-tenant', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData),
      });

      const data = await res.json();

      if (!res.ok) {
        setErrorMessage(data.detail || data.title || 'Institutional onboarding failed.');
        setIsSubmitting(false);
        return;
      }

      setSuccessData(data);
      setStep(5); // Complete state
    } catch (err: any) {
      setErrorMessage(err.message || 'Network error communicating with platform service.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      {/* Step Indicator */}
      <div className="grid grid-cols-4 gap-2">
        {[
          { num: 1, label: 'Tenant Identity', icon: Building2 },
          { num: 2, label: 'Primary College', icon: GraduationCap },
          { num: 3, label: 'Administrator', icon: UserCheck },
          { num: 4, label: 'Review & Launch', icon: CheckCircle2 },
        ].map(({ num, label, icon: Icon }) => (
          <div
            key={num}
            className={`flex items-center gap-2 p-3 rounded-[8px] border text-xs font-medium transition-all ${
              step === num
                ? 'border-[#1F6FEB] bg-[#E8F0FE] text-[#1F6FEB]'
                : step > num
                ? 'border-[#107C41] bg-[#DFF6DD]/50 text-[#107C41]'
                : 'border-[#E3E8EF] bg-white text-[#6B7688]'
            }`}
          >
            <div
              className={`flex h-6 w-6 items-center justify-center rounded-full text-xs font-bold ${
                step === num
                  ? 'bg-[#1F6FEB] text-white'
                  : step > num
                  ? 'bg-[#107C41] text-white'
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
        <div className="p-4 rounded-[8px] border border-[#D64545] bg-[#FDF3F3] text-xs text-[#D64545] flex items-start gap-2">
          <AlertCircle className="h-4 w-4 mt-0.5 shrink-0" />
          <div>
            <p className="font-semibold">Validation or Operation Error</p>
            <p className="mt-0.5">{errorMessage}</p>
          </div>
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

            <div>
              <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                Institutional Tenant Code <span className="text-[#D64545]">*</span>
              </label>
              <Input
                placeholder="e.g. SXIET"
                value={formData.tenantCode}
                onChange={(e) => updateField('tenantCode', e.target.value.toUpperCase())}
                className="font-mono uppercase"
              />
              <p className="text-[11px] text-[#6B7688] mt-1">
                Must match regex <code>^[A-Z0-9_-]&#123;2,30&#125;$</code>. Used for subdomain and tenant-routing keys.
              </p>
            </div>
          </CardContent>
          <CardFooter className="flex justify-between">
            <Button variant="ghost" onClick={() => router.push('/platform/institutes')}>
              Cancel
            </Button>
            <Button variant="primary" onClick={handleNext}>
              Next: Primary College
              <ArrowRight className="ml-1.5 h-4 w-4" />
            </Button>
          </CardFooter>
        </Card>
      )}

      {/* Step 2: Primary College */}
      {step === 2 && (
        <Card>
          <CardHeader>
            <CardTitle>Step 2: Primary College Setup</CardTitle>
            <CardDescription>
              Establish the initial operational college unit inside this institutional tenant.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                Primary College Name <span className="text-[#D64545]">*</span>
              </label>
              <Input
                placeholder="e.g. Faculty of Engineering and Applied Sciences"
                value={formData.collegeName}
                onChange={(e) => updateField('collegeName', e.target.value)}
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                College Code <span className="text-[#D64545]">*</span>
              </label>
              <Input
                placeholder="e.g. FEAS"
                value={formData.collegeCode}
                onChange={(e) => updateField('collegeCode', e.target.value.toUpperCase())}
                className="font-mono uppercase"
              />
            </div>
          </CardContent>
          <CardFooter className="flex justify-between">
            <Button variant="outline" onClick={handlePrev}>
              <ArrowLeft className="mr-1.5 h-4 w-4" />
              Back
            </Button>
            <Button variant="primary" onClick={handleNext}>
              Next: Administrator
              <ArrowRight className="ml-1.5 h-4 w-4" />
            </Button>
          </CardFooter>
        </Card>
      )}

      {/* Step 3: Administrator */}
      {step === 3 && (
        <Card>
          <CardHeader>
            <CardTitle>Step 3: Initial College Administrator Account</CardTitle>
            <CardDescription>
              Designate the primary administrator responsible for academic setup, faculty rosters, and timetables.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                Administrator Full Name <span className="text-[#D64545]">*</span>
              </label>
              <Input
                placeholder="e.g. Dr. Arthur Pendelton"
                value={formData.adminName}
                onChange={(e) => updateField('adminName', e.target.value)}
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                Official Email Address <span className="text-[#D64545]">*</span>
              </label>
              <Input
                type="email"
                placeholder="e.g. admin@sxiet.edu"
                value={formData.adminEmail}
                onChange={(e) => updateField('adminEmail', e.target.value)}
              />
              <p className="text-[11px] text-[#6B7688] mt-1">
                A verification link and institutional activation invite will be linked to this email address.
              </p>
            </div>

            <div>
              <label className="block text-xs font-medium text-[#0B1F3A] mb-1">
                Phone Number (Optional)
              </label>
              <Input
                placeholder="e.g. +91 98765 43210"
                value={formData.adminPhone}
                onChange={(e) => updateField('adminPhone', e.target.value)}
              />
            </div>
          </CardContent>
          <CardFooter className="flex justify-between">
            <Button variant="outline" onClick={handlePrev}>
              <ArrowLeft className="mr-1.5 h-4 w-4" />
              Back
            </Button>
            <Button variant="primary" onClick={handleNext}>
              Next: Review & Launch
              <ArrowRight className="ml-1.5 h-4 w-4" />
            </Button>
          </CardFooter>
        </Card>
      )}

      {/* Step 4: Review & Finalize */}
      {step === 4 && (
        <Card>
          <CardHeader>
            <CardTitle>Step 4: Review Institutional Onboarding</CardTitle>
            <CardDescription>
              Confirm tenancy details before triggering tenant provisioning and system role triggers.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="grid grid-cols-2 gap-4 text-xs">
              <div className="p-3 bg-[#F6F8FB] rounded-[8px] space-y-1">
                <span className="text-[#6B7688]">Tenant Name</span>
                <p className="font-semibold text-[#0B1F3A]">{formData.tenantName}</p>
              </div>
              <div className="p-3 bg-[#F6F8FB] rounded-[8px] space-y-1">
                <span className="text-[#6B7688]">Tenant Code</span>
                <p className="font-mono font-semibold text-[#0B1F3A]">{formData.tenantCode}</p>
              </div>
              <div className="p-3 bg-[#F6F8FB] rounded-[8px] space-y-1">
                <span className="text-[#6B7688]">Primary College</span>
                <p className="font-semibold text-[#0B1F3A]">{formData.collegeName} ({formData.collegeCode})</p>
              </div>
              <div className="p-3 bg-[#F6F8FB] rounded-[8px] space-y-1">
                <span className="text-[#6B7688]">Administrator</span>
                <p className="font-semibold text-[#0B1F3A]">{formData.adminName} &lt;{formData.adminEmail}&gt;</p>
              </div>
            </div>

            <div className="p-4 rounded-[8px] bg-[#E8F0FE] border border-[#1F6FEB]/20 text-xs text-[#12315C] flex items-start gap-3">
              <ShieldAlert className="h-5 w-5 text-[#1F6FEB] shrink-0 mt-0.5" />
              <div className="space-y-1">
                <p className="font-semibold">Security & Multi-Tenant Isolation Guarantee</p>
                <p className="text-[11px] leading-relaxed text-[#1B2433]">
                  Creating this record activates PostgreSQL trigger <code>trg_seed_roles</code>, seeding 30 system roles and 2,138 permission mappings in isolated schemas. Row-Level Security ensures data from this tenant is strictly invisible to other colleges.
                </p>
              </div>
            </div>
          </CardContent>
          <CardFooter className="flex justify-between">
            <Button variant="outline" onClick={handlePrev} disabled={isSubmitting}>
              <ArrowLeft className="mr-1.5 h-4 w-4" />
              Back
            </Button>
            <Button variant="primary" onClick={handleSubmit} isLoading={isSubmitting}>
              {isSubmitting ? 'Provisioning Tenant...' : 'Confirm & Onboard Tenant'}
            </Button>
          </CardFooter>
        </Card>
      )}

      {/* Step 5: Success */}
      {step === 5 && (
        <Card className="border-[#107C41]/30">
          <CardContent className="pt-8 pb-8 text-center space-y-4">
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-[#DFF6DD] text-[#107C41]">
              <CheckCircle2 className="h-8 w-8" />
            </div>
            <div>
              <h2 className="text-xl font-bold text-[#0B1F3A]">Tenant Onboarded Successfully</h2>
              <p className="text-xs text-[#6B7688] mt-1 max-w-md mx-auto">
                {formData.tenantName} ({formData.tenantCode}) is now registered in <code>core.tenants</code>.
              </p>
            </div>

            {successData && (
              <div className="max-w-md mx-auto text-left p-3 rounded-[8px] bg-[#F6F8FB] border border-[#E3E8EF] text-xs font-mono space-y-1">
                <div className="flex justify-between">
                  <span className="text-[#6B7688]">Tenant ID:</span>
                  <span className="text-[#0B1F3A] font-semibold">{successData.tenant?.id}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-[#6B7688]">College ID:</span>
                  <span className="text-[#0B1F3A] font-semibold">{successData.college?.id || 'Pending setup'}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-[#6B7688]">Status:</span>
                  <span className="text-[#107C41] font-semibold">{successData.tenant?.status}</span>
                </div>
              </div>
            )}

            <div className="pt-4 flex justify-center gap-3">
              <Button variant="primary" onClick={() => router.push('/platform/institutes')}>
                View Institutes Registry
              </Button>
              <Button
                variant="outline"
                onClick={() => {
                  setFormData({
                    tenantName: '',
                    tenantCode: '',
                    collegeName: '',
                    collegeCode: '',
                    adminName: '',
                    adminEmail: '',
                    adminPhone: '',
                  });
                  setStep(1);
                  setSuccessData(null);
                }}
              >
                Onboard Another
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
