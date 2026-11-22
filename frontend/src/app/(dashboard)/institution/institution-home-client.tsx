'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { Building2, GraduationCap, Loader2, AlertCircle } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

/**
 * Minimal Institution Admin home after first login (US-005).
 * Shows own institute summary and a clear next action to register the first college.
 */
export function InstitutionHomeClient() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [institute, setInstitute] = useState<{
    name?: string;
    code?: string;
    status?: string;
    collegeCount?: number;
  } | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await fetch('/api/institution/desk', { method: 'GET' });
        const body = await res.json().catch(() => ({}));
        if (!res.ok) {
          if (!cancelled) setError(body.detail || 'unavailable');
          return;
        }
        if (!cancelled) setInstitute(body.institute || body);
      } catch {
        if (!cancelled) setError('unavailable');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6" data-testid="iadm01-r03-home">
      <div>
        <h1 className="text-2xl font-semibold text-[#0B1F3A]">Institution Admin home</h1>
        <p className="mt-1 text-sm text-slate-600">Your institute workspace and next onboarding steps.</p>
      </div>

      {loading && (
        <div className="flex items-center gap-2 text-sm text-slate-600" data-testid="iadm01-r03-home-loading">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading…
        </div>
      )}

      {error && (
        <div
          className="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800"
          data-testid="iadm01-r03-home-error"
          role="alert"
        >
          <AlertCircle className="mt-0.5 h-4 w-4" />
          <span>{error}</span>
        </div>
      )}

      {!loading && !error && institute && (
        <Card data-testid="iadm01-r03-home-summary">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <Building2 className="h-5 w-5" />
              {institute.name || 'Your institute'}
            </CardTitle>
            <CardDescription>
              Code {institute.code || '—'} · Status{' '}
              <Badge variant="outline">{institute.status || '—'}</Badge>
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-slate-700">
              Colleges: <strong data-testid="iadm01-r03-college-count">{institute.collegeCount ?? 0}</strong>
            </p>
            {(institute.collegeCount ?? 0) === 0 ? (
              <div
                className="rounded-md border border-dashed border-slate-300 p-4 text-sm"
                data-testid="iadm01-r03-home-empty"
              >
                No colleges yet. Register your first college to continue onboarding.
                <div className="mt-3">
                  <Link
                    href="/institution/colleges/new"
                    data-testid="iadm01-register-first-college"
                    className="inline-flex items-center rounded-md bg-[#0B1F3A] px-4 py-2 text-sm text-white"
                  >
                    <GraduationCap className="mr-2 h-4 w-4" /> Register your first college
                  </Link>
                </div>
              </div>
            ) : (
              <Link
                href="/institution/colleges"
                data-testid="iadm01-open-colleges"
                className="inline-flex items-center rounded-md border px-4 py-2 text-sm"
              >
                View colleges
              </Link>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
