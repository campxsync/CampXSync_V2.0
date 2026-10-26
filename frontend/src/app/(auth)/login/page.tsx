'use client';

import React, { useState, useEffect, Suspense } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import {
  ShieldCheck,
  CheckCircle2,
  Eye,
  EyeOff,
  Building,
  GraduationCap,
  CalendarCheck2,
  AlertCircle,
  Mail,
  Send
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
  const [resetEmail, setResetEmail] = useState('');
  const [isResetting, setIsResetting] = useState(false);
  const [resetMessage, setResetMessage] = useState<string | null>(null);
  const [resetError, setResetError] = useState<string | null>(null);

  const supabase = createClient();

  useEffect(() => {
    function checkAndRedirectRecovery() {
      if (typeof window === 'undefined') return false;
      const hash = window.location.hash;
      const search = window.location.search;
      const params = new URLSearchParams(search);
      const hasCode = params.has('code');
      const hasHashTokens = hash.includes('access_token');
      const isRecovery =
        hasCode ||
        hasHashTokens ||
        hash.includes('type=recovery') ||
        params.get('type') === 'recovery' ||
        params.get('next')?.includes('reset-password') ||
        hash.includes('error=') ||
        params.has('error') ||
        params.has('error_code');

      if (hasCode) {
        window.location.replace(`/auth/callback?next=/reset-password&${params.toString()}${hash}`);
        return true;
      }

      if (isRecovery) {
        window.location.replace(`/reset-password${search}${hash}`);
        return true;
      }
      return false;
    }

    if (checkAndRedirectRecovery()) return;

    window.addEventListener('hashchange', checkAndRedirectRecovery);

    const { data: { subscription } } = supabase.auth.onAuthStateChange((event) => {
      if (event === 'PASSWORD_RECOVERY') {
        window.location.replace('/reset-password');
      }
    });

    return () => {
      window.removeEventListener('hashchange', checkAndRedirectRecovery);
      subscription.unsubscribe();
    };
  }, [supabase]);

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

  const handleSelectDemoAccount = (role: string) => {
    setErrorMessage(null);
    setPassword(''); // Explicitly wipe password when switching accounts to prevent lingering credentials
    if (role === 'platform_admin') {
      setEmail('platformadmin.campx@gmail.com');
    } else if (role === 'college_admin') {
      setEmail('campxsync@gmail.com');
    } else if (role === 'faculty') {
      setEmail('faculty.campx@gmail.com');
    }
  };

  return (
    <div className="w-full max-w-md rounded-[12px] border border-[#E3E8EF] bg-white p-8 shadow-sm">
      <div className="mb-6">
        <Link href="/" className="mb-4 inline-flex text-xs font-semibold text-[#1F6FEB] hover:underline">
          Back to home
        </Link>
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
            autoComplete="email"
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
            autoComplete="current-password"
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

      {/* Staging Accounts Selection Assistant */}
      <div className="mt-8 border-t border-[#E3E8EF] pt-4">
        <div className="flex items-center justify-between">
          <span className="text-[11px] font-semibold uppercase tracking-wider text-[#6B7688]">
            Staging Accounts
          </span>
          <span className="text-[10px] text-[#6B7688]">Click to select account</span>
        </div>
        <div className="mt-2.5 grid grid-cols-3 gap-2">
          <button
            type="button"
            onClick={() => handleSelectDemoAccount('platform_admin')}
            disabled={isLoading}
            className={`flex flex-col items-center justify-center p-2.5 rounded-[6px] border text-xs font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#1F6FEB] min-h-[44px] ${
              email === 'platformadmin.campx@gmail.com'
                ? 'border-[#1F6FEB] bg-[#EBF3FF] text-[#1F6FEB]'
                : 'border-[#E3E8EF] bg-[#F6F8FB] hover:border-[#1F6FEB] text-[#0B1F3A]'
            }`}
          >
            <Building className="h-4 w-4 mb-1 text-current" />
            Platform
          </button>
          <button
            type="button"
            onClick={() => handleSelectDemoAccount('college_admin')}
            disabled={isLoading}
            className={`flex flex-col items-center justify-center p-2.5 rounded-[6px] border text-xs font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#1F6FEB] min-h-[44px] ${
              email === 'campxsync@gmail.com'
                ? 'border-[#1F6FEB] bg-[#EBF3FF] text-[#1F6FEB]'
                : 'border-[#E3E8EF] bg-[#F6F8FB] hover:border-[#1F6FEB] text-[#0B1F3A]'
            }`}
          >
            <ShieldCheck className="h-4 w-4 mb-1 text-current" />
            College Admin
          </button>
          <button
            type="button"
            onClick={() => handleSelectDemoAccount('faculty')}
            disabled={isLoading}
            className={`flex flex-col items-center justify-center p-2.5 rounded-[6px] border text-xs font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#1F6FEB] min-h-[44px] ${
              email === 'faculty.campx@gmail.com'
                ? 'border-[#0D9488] bg-[#F0FAF9] text-[#0D9488]'
                : 'border-[#E3E8EF] bg-[#F6F8FB] hover:border-[#0D9488] text-[#0B1F3A]'
            }`}
          >
            <CalendarCheck2 className="h-4 w-4 mb-1 text-current" />
            Faculty
          </button>
        </div>
      </div>

      {/* Forgot Password Modal */}
      {showForgotModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-sm rounded-[12px] bg-white p-6 shadow-xl">
            <h3 className="text-base font-semibold text-[#0B1F3A]">Password Reset</h3>
            <p className="mt-1 text-xs text-[#6B7688] leading-relaxed">
              Enter your registered institutional email to receive a secure recovery link.
            </p>

            {resetMessage && (
              <div className="mt-3 flex items-start gap-2 rounded-[6px] border border-[#B8E2C8] bg-[#F0FAF4] p-2.5 text-xs text-[#15803D]">
                <CheckCircle2 className="h-4 w-4 shrink-0 mt-0.5" />
                <p>{resetMessage}</p>
              </div>
            )}

            {resetError && (
              <div className="mt-3 flex items-start gap-2 rounded-[6px] border border-[#F8BDBD] bg-[#FDECEC] p-2.5 text-xs text-[#D64545]">
                <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                <p>{resetError}</p>
              </div>
            )}

            <form
              onSubmit={async (e) => {
                e.preventDefault();
                setResetError(null);
                setResetMessage(null);
                if (!resetEmail.trim()) {
                  setResetError('Please enter your institutional email address.');
                  return;
                }
                setIsResetting(true);
                try {
                  const redirectTo = `${window.location.origin}/auth/callback?next=/reset-password`;
                  const { error } = await supabase.auth.resetPasswordForEmail(resetEmail.trim(), {
                    redirectTo,
                  });
                  if (error) {
                    setResetError(error.message || 'Failed to send reset email.');
                  } else {
                    setResetMessage('Recovery link sent! Please check your email inbox.');
                  }
                } catch (err: any) {
                  setResetError(err.message || 'An unexpected error occurred.');
                } finally {
                  setIsResetting(false);
                }
              }}
              className="mt-4 space-y-3"
            >
              <Input
                label="Registered Email"
                type="email"
                placeholder="you@institution.edu"
                value={resetEmail}
                onChange={(e) => setResetEmail(e.target.value)}
                required
                disabled={isResetting}
              />

              <p className="text-[11px] text-[#6B7688] leading-tight">
                Institutional accounts may also be reset directly by your college administrator or platform IT coordinator.
              </p>

              <div className="mt-4 flex items-center justify-end gap-2 pt-2">
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  onClick={() => {
                    setShowForgotModal(false);
                    setResetMessage(null);
                    setResetError(null);
                  }}
                  disabled={isResetting}
                >
                  Close
                </Button>
                <Button
                  type="submit"
                  variant="primary"
                  size="sm"
                  isLoading={isResetting}
                >
                  Send Recovery Link
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export default function LoginPage() {
  return (
    <main className="flex min-h-screen w-full flex-col-reverse lg:flex-row bg-[#F6F8FB]">
      {/* Early client-side recovery routing before React hydration */}
      <script
        dangerouslySetInnerHTML={{
          __html: `(function(){try{var h=window.location.hash||'';var s=window.location.search||'';var p=new URLSearchParams(s);if(p.has('code')){window.location.replace('/auth/callback?next=/reset-password&'+p.toString()+h);return;}var isRecovery=p.get('type')==='recovery'||p.has('error')||p.has('error_code')||h.indexOf('access_token')!==-1||h.indexOf('type=recovery')!==-1||h.indexOf('error=')!==-1;if(isRecovery){window.location.replace('/reset-password'+s+h);}}catch(e){}})();`,
        }}
      />
      {/* Left: Navy Brand Panel */}
      <section className="flex flex-col justify-between bg-[#0B1F3A] p-8 lg:p-14 text-white lg:w-1/2" aria-label="Platform Overview">
        <div>
          <Link href="/" className="flex w-fit items-center gap-3 rounded-[8px] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white">
            <div className="flex h-10 w-10 items-center justify-center rounded-[8px] bg-[#1F6FEB] text-white shadow-md">
              <ShieldCheck className="h-6 w-6" />
            </div>
            <span className="text-2xl font-bold tracking-tight">CampXSync</span>
          </Link>

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
