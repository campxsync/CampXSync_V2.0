'use client';

import React, { useState, useEffect, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import {
  ShieldCheck,
  CheckCircle2,
  Eye,
  EyeOff,
  AlertCircle,
  Mail,
  Lock,
  ArrowRight,
  HelpCircle,
  FileText,
  Clock,
  Shield,
  Loader2,
} from 'lucide-react';
import { createClient } from '@/lib/supabase/client';
import { Button } from '@/components/ui/button';

export const dynamic = 'force-dynamic';

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const returnTo = searchParams ? searchParams.get('returnTo') : null;

  const [isMounted, setIsMounted] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberDevice, setRememberDevice] = useState(true);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Password reset modal state
  const [showForgotModal, setShowForgotModal] = useState(false);
  const [resetEmail, setResetEmail] = useState('');
  const [isResetting, setIsResetting] = useState(false);
  const [resetMessage, setResetMessage] = useState<string | null>(null);
  const [resetError, setResetError] = useState<string | null>(null);

  const supabase = createClient();

  useEffect(() => {
    setIsMounted(true);
  }, []);

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

    const trimmedEmail = email.trim();
    if (!trimmedEmail || !password) {
      setErrorMessage('Please enter both your institutional email address and password.');
      return;
    }

    setIsLoading(true);

    try {
      const { data, error } = await supabase.auth.signInWithPassword({
        email: trimmedEmail,
        password,
      });

      if (error) {
        setErrorMessage(error.message || 'Invalid institutional credentials.');
        setIsLoading(false);
        return;
      }

      if (data.user) {
        // Authoritative server-side role resolution and destination validation
        try {
          const res = await fetch(
            `/api/auth/resolve?returnTo=${encodeURIComponent(returnTo || '')}`,
            {
              method: 'GET',
              cache: 'no-store',
            }
          );

          if (res.ok) {
            const result = await res.json();
            if (result.success && result.destination) {
              window.location.href = result.destination;
              return;
            }
            if (!result.success && result.reason) {
              window.location.href = `/access-denied?reason=${encodeURIComponent(result.reason)}`;
              return;
            }
          }
        } catch {
          // If the resolve endpoint encounters an unexpected network issue,
          // navigating to root '/' authoritatively evaluates HomePage server-side resolveUserRole.
        }

        window.location.href = '/';
      }
    } catch (err: any) {
      setErrorMessage(err.message || 'An unexpected error occurred during sign in.');
      setIsLoading(false);
    }
  };

  if (!isMounted) {
    return (
      <div className="w-full max-w-[460px]">
        <div className="rounded-xl border border-[#E3E8EF] bg-white p-8 sm:p-10 shadow-[0_4px_24px_-2px_rgba(8,22,41,0.06)] text-center">
          <div className="mx-auto w-12 h-12 rounded-lg bg-[#EBF3FF] border border-[#D0E2FD] flex items-center justify-center text-[#1F6FEB] mb-3 shadow-sm">
            <ShieldCheck className="h-6 w-6" />
          </div>
          <h2 className="text-xl font-bold tracking-tight text-[#0B1F3A]">Sign In to Workspace</h2>
          <div className="flex items-center justify-center gap-2 text-sm text-[#6B7688] py-8">
            <Loader2 className="h-5 w-5 animate-spin text-[#1F6FEB]" />
            <span>Initializing secure session...</span>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="w-full max-w-[460px]">
      <div className="rounded-xl border border-[#E3E8EF] bg-white p-8 sm:p-10 shadow-[0_4px_24px_-2px_rgba(8,22,41,0.06)]">
        {/* Card Header & Shield */}
        <div className="text-center pb-6 border-b border-[#E3E8EF]/70 mb-6">
          <div className="mx-auto w-12 h-12 rounded-lg bg-[#EBF3FF] border border-[#D0E2FD] flex items-center justify-center text-[#1F6FEB] mb-3 shadow-sm">
            <ShieldCheck className="h-6 w-6" />
          </div>
          <h2 className="text-xl font-bold tracking-tight text-[#0B1F3A]">
            Sign In to Workspace
          </h2>
          <p className="mt-1 text-xs text-[#6B7688]">
            Enter your institution-issued credentials to access your academic cockpit.
          </p>
        </div>

        {errorMessage && (
          <div className="mb-5 flex items-start gap-2.5 rounded-lg border border-[#F8BDBD] bg-[#FDECEC] p-3 text-xs text-[#D64545]">
            <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold">Authentication Error</p>
              <p className="mt-0.5">{errorMessage}</p>
            </div>
          </div>
        )}

        <form onSubmit={handleLogin} className="space-y-4" suppressHydrationWarning>
          <div className="space-y-1.5 text-left">
            <label
              htmlFor="institutional-email"
              className="text-xs font-semibold text-[#1B2433] tracking-wide block"
            >
              Institutional Email
            </label>
            <div className="relative rounded-lg shadow-sm">
              <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-[#6B7688]">
                <Mail className="h-4 w-4" />
              </div>
              <input
                id="institutional-email"
                type="email"
                placeholder="name@university.edu"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                required
                disabled={isLoading}
                suppressHydrationWarning
                className="block w-full rounded-lg border border-[#E3E8EF] pl-9 pr-3 py-2.5 text-sm text-[#1B2433] placeholder:text-[#6B7688]/70 focus:border-[#1F6FEB] focus:ring-2 focus:ring-[#1F6FEB]/15 focus:outline-none transition-colors disabled:opacity-50"
              />
            </div>
          </div>

          <div className="space-y-1.5 text-left">
            <label
              htmlFor="password"
              className="text-xs font-semibold text-[#1B2433] tracking-wide block"
            >
              Password
            </label>
            <div className="relative rounded-lg shadow-sm">
              <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-[#6B7688]">
                <Lock className="h-4 w-4" />
              </div>
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                placeholder="••••••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                required
                disabled={isLoading}
                suppressHydrationWarning
                className="block w-full rounded-lg border border-[#E3E8EF] pl-9 pr-10 py-2.5 text-sm text-[#1B2433] placeholder:text-[#6B7688]/70 focus:border-[#1F6FEB] focus:ring-2 focus:ring-[#1F6FEB]/15 focus:outline-none transition-colors disabled:opacity-50"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute inset-y-0 right-0 flex items-center pr-3 text-[#6B7688] hover:text-[#1B2433] focus:outline-none"
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
          </div>

          <div className="flex items-center justify-between text-xs pt-0.5">
            <label className="flex items-center gap-2 cursor-pointer select-none text-[#6B7688]">
              <input
                type="checkbox"
                checked={rememberDevice}
                onChange={(e) => setRememberDevice(e.target.checked)}
                suppressHydrationWarning
                className="rounded border-[#E3E8EF] text-[#1F6FEB] focus:ring-[#1F6FEB] h-3.5 w-3.5"
              />
              <span>Remember this device for 30 days</span>
            </label>

            <button
              type="button"
              onClick={() => setShowForgotModal(true)}
              className="font-medium text-[#1F6FEB] hover:underline"
            >
              Forgot password?
            </button>
          </div>

          <div className="pt-2">
            <button
              type="submit"
              disabled={isLoading}
              suppressHydrationWarning
              className="w-full py-3 px-4 rounded-lg bg-[#0B1F3A] hover:bg-[#12315C] text-white font-medium text-sm shadow-sm transition-all duration-150 flex items-center justify-center gap-2 active:scale-[0.99] disabled:opacity-50 disabled:pointer-events-none"
            >
              {isLoading ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" />
                  <span>Authenticating...</span>
                </>
              ) : (
                <>
                  <span>Sign In to Workspace</span>
                  <ArrowRight className="h-4 w-4" />
                </>
              )}
            </button>
          </div>

          <div className="pt-2 text-center">
            <p className="text-[11px] text-[#6B7688] flex items-center justify-center gap-1.5">
              <Shield className="h-3.5 w-3.5 text-[#1E9E6A]" />
              <span>Protected by Multi-Factor Authentication &amp; Institutional Identity Controls</span>
            </p>
          </div>
        </form>

        {/* Quick Dev Test Sign-In */}
        <div className="mt-6 pt-5 border-t border-[#E3E8EF] text-center">
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2.5">
            Quick Dev Testing Sign-In
          </p>
          <div className="flex flex-wrap gap-2 justify-center">
            <a
              id="btn-quick-login-documents"
              href="/api/auth/test-session?role=college_admin&returnTo=/college/documents"
              className="px-3 py-1.5 bg-blue-50 text-blue-700 hover:bg-blue-100 border border-blue-200 rounded-md text-xs font-semibold transition"
            >
              College Admin — Documents Desk
            </a>
            <a
              id="btn-quick-login-student-documents"
              data-testid="login-quick-student-documents"
              href="/api/auth/test-session?role=student&returnTo=/student/documents"
              className="px-3 py-1.5 bg-indigo-50 text-indigo-700 hover:bg-indigo-100 border border-indigo-200 rounded-md text-xs font-semibold transition"
            >
              Student — Documents Desk
            </a>
            <a
              id="btn-quick-login-guardians"
              href="/api/auth/test-session?role=college_admin&returnTo=/college/guardians"
              className="px-3 py-1.5 bg-emerald-50 text-emerald-700 hover:bg-emerald-100 border border-emerald-200 rounded-md text-xs font-semibold transition"
            >
              College Admin — Guardians Desk
            </a>
            <a
              id="btn-quick-login-lifecycle"
              data-testid="login-quick-college-lifecycle"
              href="/api/auth/test-session?role=college_admin&returnTo=/college/lifecycle"
              className="px-3 py-1.5 bg-cyan-50 text-cyan-700 hover:bg-cyan-100 border border-cyan-200 rounded-md text-xs font-semibold transition"
            >
              College Admin — Lifecycle Desk
            </a>
            <a
              id="btn-quick-login-faculty-advisees"
              data-testid="login-quick-faculty-advisees"
              href="/api/auth/test-session?role=faculty&returnTo=/faculty/advisees"
              className="px-3 py-1.5 bg-amber-50 text-amber-700 hover:bg-amber-100 border border-amber-200 rounded-md text-xs font-semibold transition"
            >
              Faculty Advisor — Advisees Desk
            </a>
          </div>
        </div>
      </div>

      {/* Statutory Monitored Notice */}
      <div className="mt-4 px-2 text-center">
        <p className="text-[10px] text-[#6B7688] uppercase tracking-wider leading-relaxed">
          Unauthorized access is strictly monitored under 18 U.S.C. § 1030. All sessions are recorded to the institutional governance ledger.
        </p>
      </div>

      {/* Forgot Password Modal */}
      {showForgotModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
          <div className="w-full max-w-sm rounded-xl bg-white p-6 shadow-xl border border-[#E3E8EF]">
            <h3 className="text-base font-semibold text-[#0B1F3A]">Password Reset</h3>
            <p className="mt-1 text-xs text-[#6B7688] leading-relaxed">
              Enter your registered institutional email to receive a secure recovery link.
            </p>

            {resetMessage && (
              <div className="mt-3 flex items-start gap-2 rounded-md border border-[#B8E2C8] bg-[#F0FAF4] p-2.5 text-xs text-[#15803D]">
                <CheckCircle2 className="h-4 w-4 shrink-0 mt-0.5" />
                <p>{resetMessage}</p>
              </div>
            )}

            {resetError && (
              <div className="mt-3 flex items-start gap-2 rounded-md border border-[#F8BDBD] bg-[#FDECEC] p-2.5 text-xs text-[#D64545]">
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
              suppressHydrationWarning
              className="mt-4 space-y-3 text-left"
            >
              <div className="space-y-1">
                <label className="text-xs font-semibold text-[#1B2433]">
                  Registered Email
                </label>
                <input
                  type="email"
                  placeholder="name@university.edu"
                  value={resetEmail}
                  onChange={(e) => setResetEmail(e.target.value)}
                  required
                  disabled={isResetting}
                  suppressHydrationWarning
                  className="block w-full rounded-lg border border-[#E3E8EF] px-3 py-2 text-sm text-[#1B2433] placeholder:text-[#6B7688] focus:border-[#1F6FEB] focus:ring-2 focus:ring-[#1F6FEB]/15 focus:outline-none"
                />
              </div>

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
    <main className="min-h-screen flex flex-col lg:flex-row bg-[#F6F8FB]">
      {/* Early client-side recovery routing before React hydration */}
      <script
        dangerouslySetInnerHTML={{
          __html: `(function(){try{var h=window.location.hash||'';var s=window.location.search||'';var p=new URLSearchParams(s);if(p.has('code')){window.location.replace('/auth/callback?next=/reset-password&'+p.toString()+h);return;}var isRecovery=p.get('type')==='recovery'||p.has('error')||p.has('error_code')||h.indexOf('access_token')!==-1||h.indexOf('type=recovery')!==-1||h.indexOf('error=')!==-1;if(isRecovery){window.location.replace('/reset-password'+s+h);}}catch(e){}})();`,
        }}
      />

      {/* ========================================================= */}
      {/* LEFT PANEL: Deep Navy Brand & Institutional Assurance     */}
      {/* ========================================================= */}
      <section
        className="lg:w-5/12 xl:w-5/12 bg-[#0B1F3A] text-white p-8 lg:p-14 flex flex-col justify-between relative overflow-hidden border-r border-[#12315C]/40 shadow-xl"
        aria-label="Platform Overview"
      >
        {/* Subtle decorative radial glow */}
        <div className="absolute top-0 right-0 w-96 h-96 bg-[#1F6FEB]/10 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute bottom-0 left-0 w-80 h-80 bg-[#0D9488]/10 rounded-full blur-3xl pointer-events-none" />

        {/* Top Brand Identity */}
        <div className="relative z-10">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-[#1F6FEB] text-white shadow-md">
              <ShieldCheck className="h-6 w-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-2xl font-bold tracking-tight text-white">CampXSync</span>
              </div>
              <p className="text-[11px] font-medium tracking-wider uppercase text-[#E3E8EF]/70">
                Autonomous Higher Education Infrastructure
              </p>
            </div>
          </div>
        </div>

        {/* Center Hero Message & Capability Cards */}
        <div className="relative z-10 my-8 lg:my-10 max-w-lg">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-sm bg-white/5 border border-white/10 text-xs text-[#E3E8EF]/90 mb-4 backdrop-blur-sm">
            <span className="w-1.5 h-1.5 rounded-full bg-[#1E9E6A] animate-pulse" />
            <span>Academic Senate Governance Node v4.9</span>
          </div>

          <h1 className="text-2xl lg:text-3xl font-extrabold tracking-tight leading-tight text-white">
            Enterprise Academic Governance &amp; Multi-Campus Operations
          </h1>

          <p className="mt-3 text-sm text-[#E3E8EF]/80 leading-relaxed font-normal">
            Unifying academic senates, provost councils, real-time timetabling, and immutable accreditation audit ledgers across global collegiate networks.
          </p>

          {/* High-density Capability Bento Cards */}
          <div className="mt-8 grid grid-cols-1 sm:grid-cols-2 gap-3 text-left">
            <div className="p-3.5 rounded-lg bg-white/[0.04] border border-white/10 backdrop-blur-sm flex items-start gap-3 hover:bg-white/[0.07] transition-all">
              <div className="mt-0.5 p-1 rounded bg-[#1F6FEB]/20 text-[#3B82F6]">
                <ShieldCheck className="h-4 w-4" />
              </div>
              <div>
                <h2 className="text-xs font-semibold text-white">Role-Isolated Workspaces</h2>
                <p className="text-[11px] text-[#E3E8EF]/70 mt-0.5">
                  Row-level security per faculty appointment
                </p>
              </div>
            </div>

            <div className="p-3.5 rounded-lg bg-white/[0.04] border border-white/10 backdrop-blur-sm flex items-start gap-3 hover:bg-white/[0.07] transition-all">
              <div className="mt-0.5 p-1 rounded bg-[#1F6FEB]/20 text-[#3B82F6]">
                <Clock className="h-4 w-4" />
              </div>
              <div>
                <h2 className="text-xs font-semibold text-white">Timetable Safeguards</h2>
                <p className="text-[11px] text-[#E3E8EF]/70 mt-0.5">
                  Validated debarment &amp; attendance rules
                </p>
              </div>
            </div>

            <div className="p-3.5 rounded-lg bg-white/[0.04] border border-white/10 backdrop-blur-sm flex items-start gap-3 hover:bg-white/[0.07] transition-all">
              <div className="mt-0.5 p-1 rounded bg-[#1F6FEB]/20 text-[#3B82F6]">
                <FileText className="h-4 w-4" />
              </div>
              <div>
                <h2 className="text-xs font-semibold text-white">Continuous Audit Trails</h2>
                <p className="text-[11px] text-[#E3E8EF]/70 mt-0.5">
                  FERPA &amp; SOC 2 Type II immutable logs
                </p>
              </div>
            </div>

            <div className="p-3.5 rounded-lg bg-white/[0.04] border border-white/10 backdrop-blur-sm flex items-start gap-3 hover:bg-white/[0.07] transition-all">
              <div className="mt-0.5 p-1 rounded bg-[#1F6FEB]/20 text-[#3B82F6]">
                <Shield className="h-4 w-4" />
              </div>
              <div>
                <h2 className="text-xs font-semibold text-white">Zero Cross-Tenant Leakage</h2>
                <p className="text-[11px] text-[#E3E8EF]/70 mt-0.5">
                  Independent cryptographic boundary locks
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* Bottom Institutional Trust & Accreditation Baseline */}
        <div className="relative z-10 pt-6 border-t border-white/10">
          <p className="text-[11px] uppercase tracking-wider text-[#E3E8EF]/60 font-semibold mb-3">
            Authenticated by Tier-1 Consortia &amp; 48+ R1 Research Universities
          </p>
          <div className="flex flex-wrap items-center gap-2 text-xs text-[#E3E8EF]/80 font-mono">
            <span className="px-2 py-0.5 rounded bg-white/[0.06] border border-white/10 flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-[#1E9E6A]" /> SOC 2 TYPE II
            </span>
            <span className="px-2 py-0.5 rounded bg-white/[0.06] border border-white/10 flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-[#3B82F6]" /> FERPA COMPLIANT
            </span>
            <span className="px-2 py-0.5 rounded bg-white/[0.06] border border-white/10 flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-[#E39B16]" /> HECVAT READY
            </span>
            <span className="px-2 py-0.5 rounded bg-white/[0.06] border border-white/10 flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-slate-300" /> ISO 27001
            </span>
          </div>
        </div>
      </section>

      {/* ========================================================= */}
      {/* RIGHT PANEL: Institutional Authentication & Controls       */}
      {/* ========================================================= */}
      <section
        className="flex-1 flex flex-col justify-between p-6 sm:p-10 lg:p-12 relative min-h-screen"
        aria-label="Sign In Area"
      >
        {/* Top Utility Header */}
        <header className="w-full flex items-center justify-between gap-4 pb-4 border-b border-[#E3E8EF]">
          <div className="flex items-center gap-2">
            <span className="flex h-2 w-2 relative">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-[#1E9E6A] opacity-75" />
              <span className="relative inline-flex rounded-full h-2 w-2 bg-[#1E9E6A]" />
            </span>
            <span className="text-xs font-semibold text-[#6B7688]">
              Federation Gateway: Operational
            </span>
          </div>
          <nav className="flex items-center gap-4 text-xs">
            <a
              href="mailto:support@campxsync.edu"
              className="text-[#1F6FEB] hover:underline flex items-center gap-1 font-medium"
            >
              <HelpCircle className="h-3.5 w-3.5" />
              <span>Institutional Helpdesk</span>
            </a>
            <span className="text-[#E3E8EF]">|</span>
            <span className="text-[#6B7688]">Need Access? Contact Registrar</span>
          </nav>
        </header>

        {/* Center Auth Card */}
        <div className="my-auto py-8 flex justify-center items-center">
          <Suspense fallback={<div className="text-xs text-[#6B7688]">Loading sign in...</div>}>
            <LoginForm />
          </Suspense>
        </div>

        {/* Institutional Footer */}
        <footer className="w-full pt-4 border-t border-[#E3E8EF] text-center sm:text-left flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-[#6B7688]">
          <div className="space-y-0.5">
            <p className="text-[11px]">
              FERPA &amp; HIPAA Compliant Data Stewardship. All access is logged to the immutable cryptographic audit ledger.
            </p>
            <p className="text-[10px] text-[#6B7688]/80">
              &copy; 2026 CampXSync Inc. All rights reserved. Academic Governance &amp; Accreditation Standards Maintained.
            </p>
          </div>
          <div className="flex flex-wrap items-center justify-center gap-3 text-[11px]">
            <span className="hover:text-[#1B2433] cursor-default">Privacy Policy</span>
            <span className="text-[#E3E8EF]">&bull;</span>
            <span className="hover:text-[#1B2433] cursor-default">Terms of Governance</span>
            <span className="text-[#E3E8EF]">&bull;</span>
            <span className="hover:text-[#1B2433] cursor-default">Acceptable Use Policy</span>
            <span className="text-[#E3E8EF]">&bull;</span>
            <span className="hover:text-[#1B2433] cursor-default">Report Security Issue</span>
          </div>
        </footer>
      </section>
    </main>
  );
}
