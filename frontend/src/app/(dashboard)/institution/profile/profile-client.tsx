'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { AlertCircle, ArrowLeft, CheckCircle2, Loader2 } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';

function parseProfileField(profile: unknown, key: string): string {
  if (!profile || typeof profile !== 'object') return '';
  const direct = (profile as Record<string, unknown>)[key];
  if (typeof direct === 'string') return direct;
  if (typeof profile === 'string') {
    try {
      const parsed = JSON.parse(profile) as Record<string, unknown>;
      const v = parsed[key];
      return typeof v === 'string' ? v : '';
    } catch {
      return '';
    }
  }
  return '';
}

/** US-018 editable institute identity; code/status/plan read-only. */
export function ProfileClient() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [readOnly, setReadOnly] = useState({ code: '', status: '', plan: '' });
  const [form, setForm] = useState({ name: '', contact: '', location: '' });
  const [busy, setBusy] = useState(false);
  const [banner, setBanner] = useState<{ kind: 'ok' | 'err'; text: string } | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await fetch('/api/institution/profile', { method: 'GET' });
        const body = await res.json().catch(() => ({}));
        if (!res.ok) {
          if (!cancelled) setError(typeof body.detail === 'string' ? body.detail : 'unavailable');
          return;
        }
        if (cancelled) return;
        const plan =
          String(body.planReference || '') ||
          parseProfileField(body.profile, 'planReference') ||
          parseProfileField(body.profile, 'plan');
        const contact =
          String(body.contact || '') || parseProfileField(body.profile, 'contact');
        const location =
          String(body.location || '') || parseProfileField(body.profile, 'location');
        setReadOnly({
          code: String(body.code || ''),
          status: String(body.status || ''),
          plan,
        });
        setForm({
          name: String(body.name || ''),
          contact,
          location,
        });
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

  const save = async () => {
    setBusy(true);
    setBanner(null);
    try {
      const res = await fetch('/api/institution/profile', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: form.name.trim(),
          contact: form.contact.trim(),
          location: form.location.trim(),
        }),
      });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        setBanner({ kind: 'err', text: typeof body.detail === 'string' ? body.detail : 'Update failed' });
        return;
      }
      setBanner({ kind: 'ok', text: 'Institute profile saved.' });
      if (body.name) setForm((f) => ({ ...f, name: String(body.name) }));
    } catch {
      setBanner({ kind: 'err', text: 'unavailable' });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-2xl space-y-6 p-6" data-testid="iadm01-profile-desk">
      <Link
        href="/institution"
        className="inline-flex items-center text-xs text-[#1F6FEB]"
        data-testid="iadm01-profile-back"
      >
        <ArrowLeft className="mr-1 h-3.5 w-3.5" /> Institution home
      </Link>
      <div>
        <h1 className="text-2xl font-bold text-[#0B1F3A]">Institute profile</h1>
        <p className="mt-1 text-sm text-[#6B7688]">Update permitted identity fields for your institute (US-018).</p>
      </div>

      {loading && (
        <p className="flex items-center gap-2 text-sm text-slate-600" data-testid="iadm01-profile-loading">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading…
        </p>
      )}

      {error && (
        <div
          className="flex gap-2 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800"
          data-testid="iadm01-profile-error"
          role="alert"
        >
          <AlertCircle className="mt-0.5 h-4 w-4" />
          <span>{error}</span>
        </div>
      )}

      {banner && (
        <div
          className={`flex gap-2 rounded-md border px-3 py-2 text-sm ${
            banner.kind === 'err'
              ? 'border-red-200 bg-red-50 text-red-800'
              : 'border-green-200 bg-green-50 text-green-900'
          }`}
          data-testid="iadm01-profile-banner"
        >
          {banner.kind === 'err' ? <AlertCircle className="h-4 w-4" /> : <CheckCircle2 className="h-4 w-4" />}
          <span>{banner.text}</span>
        </div>
      )}

      {!loading && !error && (
        <Card data-testid="iadm01-profile-card">
          <CardHeader>
            <CardTitle className="text-base">Identity</CardTitle>
            <CardDescription>Lifecycle and plan are controlled by the platform.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <ReadOnlyLine label="Institute code" testId="iadm01-profile-code" value={readOnly.code} />
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span className="text-[#6B7688]">Status</span>
              <Badge variant="outline" data-testid="iadm01-profile-status">
                {readOnly.status || '—'}
              </Badge>
            </div>
            <ReadOnlyLine label="Plan" testId="iadm01-profile-plan" value={readOnly.plan || '—'} />

            <div className="space-y-1 pt-2">
              <label className="text-sm font-medium" htmlFor="iadm01-profile-name">
                Display name
              </label>
              <Input
                id="iadm01-profile-name"
                data-testid="iadm01-profile-field-name"
                disabled={busy}
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              />
            </div>
            <div className="space-y-1">
              <label className="text-sm font-medium" htmlFor="iadm01-profile-contact">
                Contact
              </label>
              <Input
                id="iadm01-profile-contact"
                data-testid="iadm01-profile-field-contact"
                disabled={busy}
                value={form.contact}
                onChange={(e) => setForm((f) => ({ ...f, contact: e.target.value }))}
              />
            </div>
            <div className="space-y-1">
              <label className="text-sm font-medium" htmlFor="iadm01-profile-location">
                Location
              </label>
              <Input
                id="iadm01-profile-location"
                data-testid="iadm01-profile-field-location"
                disabled={busy}
                value={form.location}
                onChange={(e) => setForm((f) => ({ ...f, location: e.target.value }))}
              />
            </div>
            <Button
              variant="primary"
              size="sm"
              disabled={busy}
              data-testid="iadm01-profile-save"
              onClick={() => void save()}
            >
              Save profile
            </Button>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

function ReadOnlyLine({
  label,
  value,
  testId,
}: {
  label: string;
  value: string;
  testId: string;
}) {
  return (
    <p className="text-sm">
      <span className="text-[#6B7688]">{label}: </span>
      <span className="font-mono text-[#0B1F3A]" data-testid={testId}>
        {value || '—'}
      </span>
    </p>
  );
}