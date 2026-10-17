'use client';

import React, { useState, useEffect, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import {
  ShieldCheck,
  CheckCircle2,
  Eye,
  EyeOff,
  AlertCircle,
  KeyRound,
  ArrowLeft,
  Loader2,
  Lock
} from 'lucide-react';
import { createClient } from '@/lib/supabase/client';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

export const dynamic = 'force-dynamic';

function ResetPasswordForm() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const [status, setStatus] = useState<'loading' | 'form' | 'error' | 'success'>('loading');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [userEmail, setUserEmail] = useState<string | null>(null);

  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const supabase = createClient();

  useEffect(() => {
    let isMounted = true;

    async function evaluateRecoverySession() {
      // 1. Check for query error parameters (e.g. from GoTrue or auth/callback)
      const queryError = searchParams?.get('error');
      const queryErrorDesc = searchParams?.get('error_description');
      const queryErrorCode = searchParams?.get('error_code');

      if (queryError || queryErrorCode) {
        if (!isMounted) return;
        setStatus('error');
        setErrorMessage(
          queryErrorDesc?.replace(/\+/g, ' ') ||
          (queryErrorCode === 'otp_expired'
            ? 'This password recovery link has expired. Please request a new recovery link.'
            : 'The recovery link is invalid or has expired.')
        );
        return;
      }

      // 2. Check for hash parameters (implicit flow error or tokens)
      if (typeof window !== 'undefined' && window.location.hash) {
        const hashParams = new URLSearchParams(window.location.hash.replace(/^#/, ''));
        const hashError = hashParams.get('error');
        const hashErrorDesc = hashParams.get('error_description');
        const hashErrorCode = hashParams.get('error_code');

        if (hashError || hashErrorCode) {
          if (!isMounted) return;
          setStatus('error');
          setErrorMessage(
            hashErrorDesc?.replace(/\+/g, ' ') ||
            (hashErrorCode === 'otp_expired'
              ? 'This password recovery link has expired. Please request a new recovery link.'
              : 'The recovery link is invalid or has expired.')
          );
          return;
        }

        // Explicitly set session if tokens are in URL hash
        const accessToken = hashParams.get('access_token');
        const refreshToken = hashParams.get('refresh_token');
        if (accessToken) {
          try {
            const { data, error } = await supabase.auth.setSession({
              access_token: accessToken,
              refresh_token: refreshToken || '',
            });

            if (error) {
              if (!isMounted) return;
              setStatus('error');
              setErrorMessage(error.message || 'Failed to establish session from recovery tokens.');
              return;
            }

            if (data?.user) {
              if (!isMounted) return;
              setUserEmail(data.user.email || null);
              setStatus('form');
              window.history.replaceState(null, '', window.location.pathname);
              return;
            }
          } catch (err: any) {
            if (!isMounted) return;
            setStatus('error');
            setErrorMessage(err.message || 'Error establishing recovery session.');
            return;
          }
        }
      }

      // 3. Check for PKCE authorization code
      const code = searchParams?.get('code');
      if (code) {
        try {
          const { data, error } = await supabase.auth.exchangeCodeForSession(code);
          if (error) {
            if (!isMounted) return;
            setStatus('error');
            setErrorMessage(error.message || 'The authorization code is invalid or has expired.');
            return;
          }
          if (data?.user) {
            if (!isMounted) return;
            setUserEmail(data.user.email || null);
            setStatus('form');
            if (typeof window !== 'undefined') {
              window.history.replaceState(null, '', window.location.pathname);
            }
            return;
          }
        } catch (err: any) {
          if (!isMounted) return;
          setStatus('error');
          setErrorMessage(err.message || 'Failed to exchange authorization code.');
          return;
        }
      }

      // 4. Verify existing session (e.g., from callback cookie or previous setup)
      try {
        const { data: { session }, error } = await supabase.auth.getSession();
        if (error) {
          if (!isMounted) return;
          setStatus('error');
          setErrorMessage(error.message || 'Failed to verify session.');
          return;
        }

        if (session?.user) {
          if (!isMounted) return;
          setUserEmail(session.user.email || null);
          setStatus('form');
          if (typeof window !== 'undefined' && window.location.hash) {
            window.history.replaceState(null, '', window.location.pathname);
          }
          return;
        }

        // Grace period for background hydration from cookies or storage
        setTimeout(async () => {
          if (!isMounted) return;
          const { data: { session: retrySession } } = await supabase.auth.getSession();
          if (retrySession?.user) {
            setUserEmail(retrySession.user.email || null);
            setStatus('form');
          } else {
            setStatus('error');
            setErrorMessage('No active password recovery session found. Please use the password reset link sent to your email.');
          }
        }, 500);
      } catch (err: any) {
        if (!isMounted) return;
        setStatus('error');
        setErrorMessage(err.message || 'An error occurred while establishing recovery session.');
      }
    }

    // Subscribe to auth state changes for PASSWORD_RECOVERY
    const { data: { subscription } } = supabase.auth.onAuthStateChange((event, session) => {
      if (event === 'PASSWORD_RECOVERY' || (event === 'SIGNED_IN' && session)) {
        if (!isMounted) return;
        if (session?.user) {
          setUserEmail(session.user.email || null);
        }
        setStatus('form');
        if (typeof window !== 'undefined' && window.location.hash) {
          window.history.replaceState(null, '', window.location.pathname);
        }
      }
    });

    evaluateRecoverySession();

    return () => {
      isMounted = false;
      subscription.unsubscribe();
    };
  }, [searchParams, supabase]);

  const handleUpdatePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitError(null);

    if (!password || !confirmPassword) {
      setSubmitError('Please fill in both password fields.');
      return;
    }

    if (password.length < 8) {
      setSubmitError('Password must be at least 8 characters long.');
      return;
    }

    if (password !== confirmPassword) {
      setSubmitError('Passwords do not match. Please verify both fields.');
      return;
    }

    setIsSubmitting(true);

    try {
      const { data: { session } } = await supabase.auth.getSession();
      if (!session?.user) {
        setSubmitError('Your password recovery session has expired. Please request a new recovery link.');
        setIsSubmitting(false);
        return;
      }

      const { error } = await supabase.auth.updateUser({
        password,
      });

      if (error) {
        setSubmitError(error.message || 'Failed to update password. Please try again.');
        setIsSubmitting(false);
        return;
      }

      // Sign out temporary recovery session so the user signs in fresh
      await supabase.auth.signOut();
      setStatus('success');
    } catch (err: any) {
      setSubmitError(err.message || 'An unexpected error occurred while updating your password.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="w-full max-w-md rounded-[12px] border border-[#E3E8EF] bg-white p-8 shadow-sm">
      {/* 1. Loading State */}
      {status === 'loading' && (
        <div className="flex flex-col items-center justify-center py-10 text-center" role="status">
          <Loader2 className="h-8 w-8 animate-spin text-[#1F6FEB]" />
          <p className="mt-4 text-sm font-medium text-[#0B1F3A]">Verifying recovery link...</p>
          <p className="mt-1 text-xs text-[#6B7688]">Authenticating your password recovery session.</p>
        </div>
      )}

      {/* 2. Error State */}
      {status === 'error' && (
        <div>
          <div className="mb-5 flex items-start gap-3 rounded-[8px] border border-[#F8BDBD] bg-[#FDECEC] p-4 text-xs text-[#D64545]" role="alert">
            <AlertCircle className="h-5 w-5 shrink-0 mt-0.5" />
            <div>
              <p className="text-sm font-bold">Password Reset Unavailable</p>
              <p className="mt-1 text-xs text-[#6B7688] leading-relaxed">
                {errorMessage || 'This password reset link is invalid or has expired.'}
              </p>
            </div>
          </div>

          <p className="text-xs text-[#6B7688] leading-relaxed mb-6">
            For security reasons, password recovery tokens are single-use and expire after a limited duration. Please request a new recovery link or contact your institution administrator.
          </p>

          <Button
            type="button"
            variant="primary"
            size="md"
            className="w-full"
            onClick={() => router.push('/login')}
          >
            <ArrowLeft className="mr-2 h-4 w-4" />
            Return to Sign In
          </Button>
        </div>
      )}

      {/* 3. Form State */}
      {status === 'form' && (
        <div>
          <div className="mb-6">
            <div className="flex items-center gap-2 mb-2">
              <div className="flex h-8 w-8 items-center justify-center rounded-[6px] bg-[#E8F0FE] text-[#1F6FEB]">
                <KeyRound className="h-4 w-4" />
              </div>
              <h2 className="text-xl font-bold text-[#0B1F3A]">Set New Password</h2>
            </div>
            <p className="text-xs text-[#6B7688]">
              {userEmail ? (
                <>Resetting credentials for <span className="font-semibold text-[#0B1F3A]">{userEmail}</span>.</>
              ) : (
                'Create a strong, unique password for your institutional account.'
              )}
            </p>
          </div>

          {submitError && (
            <div className="mb-5 flex items-start gap-2.5 rounded-[8px] border border-[#F8BDBD] bg-[#FDECEC] p-3 text-xs text-[#D64545]" role="alert">
              <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
              <div>
                <p className="font-semibold">Validation Error</p>
                <p className="mt-0.5">{submitError}</p>
              </div>
            </div>
          )}

          <form onSubmit={handleUpdatePassword} className="space-y-4">
            <div className="relative">
              <Input
                label="New Password"
                type={showPassword ? 'text' : 'password'}
                placeholder="At least 8 characters"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                disabled={isSubmitting}
                autoComplete="new-password"
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

            <div className="relative">
              <Input
                label="Confirm New Password"
                type={showConfirmPassword ? 'text' : 'password'}
                placeholder="Re-enter your new password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                required
                disabled={isSubmitting}
                autoComplete="new-password"
              />
              <button
                type="button"
                onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                className="absolute right-3 top-8 text-[#6B7688] hover:text-[#1B2433] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#1F6FEB] rounded p-1"
                aria-label={showConfirmPassword ? 'Hide confirm password' : 'Show confirm password'}
              >
                {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>

            <div className="rounded-[6px] bg-[#F6F8FB] border border-[#E3E8EF] p-3 text-xs text-[#6B7688] space-y-1">
              <div className="flex items-center gap-1.5">
                <Lock className="h-3.5 w-3.5 text-[#1F6FEB]" />
                <span className="font-semibold text-[#0B1F3A]">Password Requirements:</span>
              </div>
              <ul className="list-disc list-inside text-[11px] text-[#6B7688] space-y-0.5 ml-1">
                <li className={password.length >= 8 ? 'text-[#15803D] font-medium' : ''}>
                  Minimum 8 characters in length
                </li>
                <li className={password && confirmPassword && password === confirmPassword ? 'text-[#15803D] font-medium' : ''}>
                  Both passwords must match exactly
                </li>
              </ul>
            </div>

            <Button
              type="submit"
              variant="primary"
              size="md"
              className="w-full mt-2"
              isLoading={isSubmitting}
            >
              Update Password
            </Button>

            <div className="text-center pt-2">
              <Link
                href="/login"
                className="text-xs font-medium text-[#1F6FEB] hover:underline inline-flex items-center gap-1"
              >
                <ArrowLeft className="h-3 w-3" />
                Cancel and return to sign in
              </Link>
            </div>
          </form>
        </div>
      )}

      {/* 4. Success State */}
      {status === 'success' && (
        <div className="text-center py-4">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-[#EAF7ED] text-[#15803D] mb-4">
            <CheckCircle2 className="h-7 w-7" />
          </div>
          <h2 className="text-xl font-bold text-[#0B1F3A]">Password Updated</h2>
          <p className="mt-2 text-xs text-[#6B7688] leading-relaxed">
            Your institutional password has been updated successfully. Your temporary recovery session has been closed.
          </p>
          <div className="mt-6">
            <Button
              type="button"
              variant="primary"
              size="md"
              className="w-full"
              onClick={() => router.push('/login')}
            >
              Sign In with New Password
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}

export default function ResetPasswordPage() {
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
              Institutional Credential Management & Security
            </h1>
            <p className="mt-4 text-sm text-[#E3E8EF]/80 leading-relaxed">
              Secure authentication recovery for faculty, administrators, and students. Managed cryptographic session validation backed by Supabase Auth and PostgreSQL security policies.
            </p>

            <div className="mt-10 space-y-4">
              <div className="flex items-start gap-3">
                <div className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[#1F6FEB]/20 text-[#3B82F6]">
                  <CheckCircle2 className="h-4 w-4" />
                </div>
                <div>
                  <h2 className="text-sm font-semibold text-white">Single-Use Recovery Validation</h2>
                  <p className="text-xs text-[#E3E8EF]/70">
                    Recovery links are verified with time-limited cryptographic tokens to protect account ownership.
                  </p>
                </div>
              </div>

              <div className="flex items-start gap-3">
                <div className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[#1F6FEB]/20 text-[#3B82F6]">
                  <CheckCircle2 className="h-4 w-4" />
                </div>
                <div>
                  <h2 className="text-sm font-semibold text-white">Zero Token Leakage</h2>
                  <p className="text-xs text-[#E3E8EF]/70">
                    Sensitive tokens are immediately purged from browser history once sessions are authenticated.
                  </p>
                </div>
              </div>

              <div className="flex items-start gap-3">
                <div className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[#1F6FEB]/20 text-[#3B82F6]">
                  <CheckCircle2 className="h-4 w-4" />
                </div>
                <div>
                  <h2 className="text-sm font-semibold text-white">Fresh Session Sign-In</h2>
                  <p className="text-xs text-[#E3E8EF]/70">
                    Completing password reset securely logs out temporary recovery sessions, requiring fresh sign-in.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="mt-12 pt-6 border-t border-white/10 flex items-center justify-between text-xs text-[#E3E8EF]/60">
          <span>Staging Ref: kpwdvvcschankgggitnj</span>
          <span>v2.0 Security Baseline</span>
        </div>
      </section>

      {/* Right: Reset Password Form wrapped in Suspense */}
      <section className="flex flex-1 items-center justify-center p-6 lg:p-12" aria-label="Password Reset Area">
        <Suspense fallback={<div className="text-xs text-[#6B7688]">Loading password recovery...</div>}>
          <ResetPasswordForm />
        </Suspense>
      </section>
    </main>
  );
}
