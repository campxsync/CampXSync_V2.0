'use client';

import React, { useState, useTransition } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ArrowLeft, Ban, CheckCircle2, AlertCircle } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';

export interface InstituteDetail {
  tenantId: string;
  code: string;
  name: string;
  status: string;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export function InstituteDetailClient({
  initial,
  loadError,
}: {
  initial: InstituteDetail | null;
  loadError: string | null;
}) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(loadError);
  const [busy, setBusy] = useState(false);

  if (!initial) {
    return (
      <div className="space-y-4" data-testid="institute-detail-missing">
        <Link href="/platform/institutes" className="text-sm text-[#1F6FEB]">
          ← Institutes
        </Link>
        <p className="text-sm text-red-700">{error || 'Institute not found'}</p>
      </div>
    );
  }

  async function act(kind: 'suspend' | 'reactivate') {
    if (reason.trim().length < 3) {
      setError('Reason is required (min 3 characters)');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const res = await fetch(`/api/platform/institutes/${initial!.tenantId}/${kind}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.detail || data.errorCode || `${kind} failed`);
      startTransition(() => router.refresh());
      setReason('');
    } catch (e) {
      setError(e instanceof Error ? e.message : `${kind} failed`);
    } finally {
      setBusy(false);
    }
  }

  const status = (initial.status || '').toUpperCase();

  return (
    <div className="space-y-6" data-testid="institute-detail-desk">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div>
          <Link
            href="/platform/institutes"
            className="inline-flex items-center text-xs text-[#1F6FEB] mb-2"
            data-testid="institute-back"
          >
            <ArrowLeft className="h-3.5 w-3.5 mr-1" /> Institutes & Tenancies
          </Link>
          <h1 className="text-2xl font-bold text-[#0B1F3A]" data-testid="institute-detail-title">
            {initial.name}
          </h1>
          <p className="text-xs text-[#6B7688] mt-1 font-mono">
            {initial.code} · {initial.tenantId}
          </p>
        </div>
        <Badge status={status}>{status}</Badge>
      </div>

      {error && (
        <div className="flex gap-2 rounded-[8px] border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
          <AlertCircle className="h-4 w-4 mt-0.5" />
          <span>{error}</span>
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Profile</CardTitle>
          <CardDescription>Institute tenancy profile (IADM-01)</CardDescription>
        </CardHeader>
        <CardContent className="text-sm space-y-1 text-[#1B2433]">
          <p>
            <span className="text-[#6B7688]">Status:</span> {status}
          </p>
          <p>
            <span className="text-[#6B7688]">Created:</span>{' '}
            {initial.createdAt ? new Date(String(initial.createdAt)).toLocaleString() : '—'}
          </p>
        </CardContent>
      </Card>

      <Card data-testid="institute-lifecycle-actions">
        <CardHeader>
          <CardTitle className="text-base">Suspend / Reactivate</CardTitle>
          <CardDescription>Reason required. Suspended institutes block sessions (IADM-01-US-006).</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <Input
            data-testid="institute-action-reason"
            placeholder="Reason (required)"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
          <div className="flex flex-wrap gap-2">
            <Button
              size="sm"
              variant="danger"
              disabled={busy || pending || status === 'SUSPENDED'}
              data-testid="institute-suspend"
              onClick={() => act('suspend')}
            >
              <Ban className="h-3.5 w-3.5 mr-1" /> Suspend
            </Button>
            <Button
              size="sm"
              variant="primary"
              disabled={busy || pending || status === 'ACTIVE'}
              data-testid="institute-reactivate"
              onClick={() => act('reactivate')}
            >
              <CheckCircle2 className="h-3.5 w-3.5 mr-1" /> Reactivate
            </Button>
            <Link href="/platform/identity">
              <Button size="sm" variant="secondary">
                Appoint Institution Admin (R03)
              </Button>
            </Link>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
