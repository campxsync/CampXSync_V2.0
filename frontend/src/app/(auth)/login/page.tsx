'use client';

import React, { useState, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import {
  ShieldCheck,
  CheckCircle2,
  Eye,
  EyeOff,
  Building,
  GraduationCap,
  CalendarCheck2,
  AlertCircle
} from 'lucide-react';
import { createClient } from '@/lib/supabase/client';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

export const dynamic = 'force-dynamic';

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const returnTo = searchParams ? searchParams.get('returnTo') : null;

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [showForgotModal, setShowForgotModal] = useState(false);

  const supabase = createClient();

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    if (!email.trim() || !password) {
      setErrorMessage('Please enter both your email address and password.');
      return;
    }

    setIsLoading(true);

    try {
      const { data, error } = await supabase.auth.signInWithPassword({
        email: email.trim(),
        password,
      });

      if (error) {
        setErrorMessage(error.message || 'Invalid email or password.');
        setIsLoading(false);
        return;
      }

      if (data.user) {
        // Successful login, hard navigate to destination to propagate cookies immediately
        const destination = returnTo || '/';
        window.location.href = destination;
      }
    } catch (err: any) {
      setErrorMessage(err.message || 'An unexpected error occurred during sign in.');
      setIsLoading(false);
    }
  };

  const handleQuickDemo = async (role: string) => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const res = await fetch('/api/auth/test-session', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ role }),
      });
      const data = await res.json();
      if (data.success) {
        router.refresh();
        router.push(data.redirectTo || '/');
      } else {
        setErrorMessage(data.error || 'Failed to start demo session');
      }
    } catch (err: any) {
      setErrorMessage(err.message || 'Demo session failed');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="w-full max-w-md rounded-[12px] border border-[#E3E8EF] bg-white p-8 shadow-sm">
      <div className="mb-6">
        <h2 className="text-xl font-bold text-[#0B1F3A]">Sign In</h2>
        <p className="mt-1 text-xs text-[#6B7688]">
          Enter your institutional credentials to access your workspace.
        </p>
      </div>

      {errorMessage && (
        <div className="mb-5 flex items-start gap-2.5 rounded-[8px] border border-[#F8BDBD] bg-[#FDECEC] p-3 text-xs text-[#D64545]">
          <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
          <div>
            <p className="font-semibold">Authentication Error</p>
            <p className="mt-0.5">{errorMessage}</p>
          </div>
        </div>
      )}

      <form onSubmit={handleLogin} className="space-y-4">
        <div>
          <Input
            label="Email Address"
            type="email"
            placeholder="you@institution.edu"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            disabled={isLoading}
          />
        </div>

        <div className="relative">
          <Input
            label="Password"
            type={showPassword ? 'text' : 'password'}
            placeholder="••••••••••••"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            disabled={isLoading}
          />
          <button
            type="button"
            onClick={() => setShowPassword(!showPassword)}
            className="absolute right-3 top-8 text-[#6B7688] hover:text-[#1B2433] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#1F6FEB] rounded p-1"
            aria-label={showPassword ? 'Hide password' : 'Show password'}
          >
            {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
          </button>
        </div>

        <div className="flex items-center justify-between text-xs">
          <label className="flex items-center gap-2 cursor-pointer text-[#6B7688]">
            <input
              type="checkbox"
              className="rounded border-[#E3E8EF] text-[#1F6FEB] focus:ring-[#1F6FEB]"
            />
            Remember this device
          </label>

          <button
            type="button"
            onClick={() => setShowForgotModal(true)}
            className="font-medium text-[#1F6FEB] hover:underline"
          >
            Forgot password?
          </button>
        </div>

        <Button
          type="submit"
          variant="primary"
          size="md"
          className="w-full mt-2"
          isLoading={isLoading}
        >
          Sign In to Workspace
        </Button>
      </form>

      {/* Quick Demo Credentials Assistant */}
      <div className="mt-8 border-t border-[#E3E8EF] pt-4">
        <span className="text-[11px] font-semibold uppercase tracking-wider text-[#6B7688]">
          Trial Demo Accounts
        </span>
        <div className="mt-2.5 grid grid-cols-3 gap-2">
          <button
            type="button"
            onClick={() => handleQuickDemo('platform_admin')}
            disabled={isLoading}
            className="flex flex-col items-center justify-center p-2.5 rounded-[6px] border border-[#E3E8EF] bg-[#F6F8FB] hover:border-[#1F6FEB] text-xs font-semibold text-[#0B1F3A] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#1F6FEB] min-h-[44px]"
          >
            <Building className="h-4 w-4 mb-1 text-[#0B1F3A]" />
            Platform
          </button>
          <button
            type="button"
            onClick={() => handleQuickDemo('college_admin')}
            disabled={isLoading}
            className="flex flex-col items-center justify-center p-2.5 rounded-[6px] border border-[#E3E8EF] bg-[#F6F8FB] hover:border-[#1F6FEB] text-xs font-semibold text-[#0B1F3A] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#1F6FEB] min-h-[44px]"
          >
            <ShieldCheck className="h-4 w-4 mb-1 text-[#1F6FEB]" />
            College Admin
          </button>
          <button
            type="button"
            onClick={() => handleQuickDemo('faculty')}
            disabled={isLoading}
            className="flex flex-col items-center justify-center p-2.5 rounded-[6px] border border-[#E3E8EF] bg-[#F6F8FB] hover:border-[#0D9488] text-xs font-semibold text-[#0B1F3A] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#1F6FEB] min-h-[44px]"
          >
            <CalendarCheck2 className="h-4 w-4 mb-1 text-[#0D9488]" />
            Faculty
          </button>
        </div>
      </div>

      {/* Forgot Password Modal */}
      {showForgotModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-sm rounded-[12px] bg-white p-6 shadow-xl">
            <h3 className="text-base font-semibold text-[#0B1F3A]">Password Reset</h3>
            <p className="mt-2 text-xs text-[#6B7688] leading-relaxed">
              Institutional accounts are managed directly by your organization. Please contact your college administrator or platform IT coordinator to reset your credentials.
            </p>
            <div className="mt-5 flex justify-end">
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setShowForgotModal(false)}
              >
                Close
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default function LoginPage() {
  return (
    <main className="flex min-h-screen w-full flex-col lg:flex-row bg-[#F6F8FB]">
      {/* Left: Navy Brand Panel */}
      <section className="flex flex-col justify-between bg-[#0B1F3A] p-8 lg:p-14 text-white lg:w-1/2" aria-label="Platform Overview">
        <div>
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-[8px] bg-[#1F6FEB] text-white shadow-md">
              <ShieldCheck className="h-6 w-6" />
            </div>
            <span className="text-2xl font-bold tracking-tight">CampXSync</span>
          </div>

          <div className="mt-12 max-w-lg">
            <h1 className="text-3xl lg:text-4xl font-extrabold tracking-tight leading-tight text-white">
              Institutional Operations & Real-Time Academic Governance
            </h1>
            <p className="mt-4 text-sm text-[#E3E8EF]/80 leading-relaxed">
              Enterprise operating system for colleges and universities. Single-origin multi-tenancy with PostgreSQL row-level security and authoritative academic records.
            </p>

            <div className="mt-10 space-y-4">
              <div className="flex items-start gap-3">
                <div className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[#1F6FEB]/20 text-[#3B82F6]">
                  <CheckCircle2 className="h-4 w-4" />
                </div>
                <div>
                  <h2 className="text-sm font-semibold text-white">Timetable-Validated Attendance</h2>
                  <p className="text-xs text-[#E3E8EF]/70">
                    Sessions strictly bind to roster membership and scheduled slots with duplicate rejection.
                  </p>
                </div>
              </div>

              <div className="flex items-start gap-3">
                <div className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[#1F6FEB]/20 text-[#3B82F6]">
                  <CheckCircle2 className="h-4 w-4" />
                </div>
                <div>
                  <h2 className="text-sm font-semibold text-white">Role-Isolated Workspaces</h2>
                  <p className="text-xs text-[#E3E8EF]/70">
                    Platform Operator, College Administrator, and Faculty dashboards with zero cross-tenant leakage.
                  </p>
                </div>
              </div>

              <div className="flex items-start gap-3">
                <div className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[#1F6FEB]/20 text-[#3B82F6]">
                  <CheckCircle2 className="h-4 w-4" />
                </div>
                <div>
                  <h2 className="text-sm font-semibold text-white">Immutable Correction Trail</h2>
                  <p className="text-xs text-[#E3E8EF]/70">
                    Audited attendance overrides with mandatory reason capture and optimistic concurrency.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="mt-12 pt-6 border-t border-white/10 flex items-center justify-between text-xs text-[#E3E8EF]/60">
          <span>Staging Ref: kpwdvvcschankgggitnj</span>
          <span>v2.0 Trial Baseline</span>
        </div>
      </section>

      {/* Right: Clean Login Card wrapped in Suspense */}
      <section className="flex flex-1 items-center justify-center p-6 lg:p-12" aria-label="Sign In Area">
        <Suspense fallback={<div className="text-xs text-[#6B7688]">Loading sign in...</div>}>
          <LoginForm />
        </Suspense>
      </section>
    </main>
  );
}
