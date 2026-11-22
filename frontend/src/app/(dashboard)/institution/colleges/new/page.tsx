'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { AlertCircle, ArrowLeft, Loader2 } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

/** IADM01-US-006 — register college form. */
export default function RegisterCollegePage() {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState({
    code: '',
    name: '',
    campusCode: '',
    location: '',
  });

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res = await fetch('/api/institution/colleges', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          code: form.code.trim().toUpperCase(),
          name: form.name.trim(),
          campusCode: form.campusCode.trim().toUpperCase(),
          location: form.location.trim(),
        }),
      });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        setError(typeof body.detail === 'string' ? body.detail : 'College could not be registered.');
        return;
      }
      const id = body.collegeId || body.id;
      if (id) router.push(`/institution/colleges/${id}`);
      else router.push('/institution/colleges');
    } catch {
      setError('unavailable');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-2xl space-y-6 p-6" data-testid="iadm01-college-new">
      <Link
        href="/institution/colleges"
        className="inline-flex items-center text-xs text-[#1F6FEB]"
        data-testid="iadm01-college-new-back"
      >
        <ArrowLeft className="mr-1 h-3.5 w-3.5" /> Colleges
      </Link>
      <div>
        <h1 className="text-2xl font-bold text-[#0B1F3A]">Register college</h1>
        <p className="mt-1 text-sm text-[#6B7688]">
          Creates a college under your institute in ONBOARDING state (US-006).
        </p>
      </div>

      {error && (
        <div
          className="flex gap-2 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800"
          data-testid="iadm01-college-new-error"
          role="alert"
        >
          <AlertCircle className="mt-0.5 h-4 w-4" />
          <span>{error}</span>
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">College identity</CardTitle>
          <CardDescription>Code must be unique within your institute.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={submit}>
            <div className="space-y-1">
              <label className="text-sm font-medium" htmlFor="iadm01-college-field-code">
                College code
              </label>
              <Input
                id="iadm01-college-field-code"
                data-testid="iadm01-college-field-code"
                required
                value={form.code}
                onChange={(e) => setForm((f) => ({ ...f, code: e.target.value }))}
              />
            </div>
            <div className="space-y-1">
              <label className="text-sm font-medium" htmlFor="iadm01-college-field-name">
                Display name
              </label>
              <Input
                id="iadm01-college-field-name"
                data-testid="iadm01-college-field-name"
                required
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              />
            </div>
            <div className="space-y-1">
              <label className="text-sm font-medium" htmlFor="iadm01-college-field-campus">
                Campus code
              </label>
              <Input
                id="iadm01-college-field-campus"
                data-testid="iadm01-college-field-campus"
                required
                value={form.campusCode}
                onChange={(e) => setForm((f) => ({ ...f, campusCode: e.target.value }))}
              />
            </div>
            <div className="space-y-1">
              <label className="text-sm font-medium" htmlFor="iadm01-college-field-location">
                Location
              </label>
              <Input
                id="iadm01-college-field-location"
                data-testid="iadm01-college-field-location"
                required
                value={form.location}
                onChange={(e) => setForm((f) => ({ ...f, location: e.target.value }))}
              />
            </div>
            <Button type="submit" variant="primary" disabled={busy} data-testid="iadm01-college-submit">
              {busy ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" /> Registering…
                </>
              ) : (
                'Register college'
              )}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}