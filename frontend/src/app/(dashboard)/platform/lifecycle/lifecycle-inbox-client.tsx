'use client';

import React, { useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { CheckCircle2, XCircle, RefreshCw, AlertCircle } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

export interface LifecycleRequest {
  requestId: string;
  tenantId: string;
  requestType: string;
  status: string;
  reason?: string | null;
  requestedBy?: string | null;
  createdAt?: string | null;
}

export function LifecycleInboxClient({
  initial,
  loadError,
}: {
  initial: LifecycleRequest[];
  loadError: string | null;
}) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [error, setError] = useState<string | null>(loadError);
  const [busyId, setBusyId] = useState<string | null>(null);

  async function decide(id: string, decision: 'APPROVE' | 'REJECT') {
    setBusyId(id);
    setError(null);
    try {
      const res = await fetch(`/api/platform/lifecycle/${id}/decide`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ decision, note: `${decision} from Platform Operator desk` }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.detail || data.errorCode || 'Decide failed');
      startTransition(() => router.refresh());
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Decide failed');
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="space-y-6" data-testid="platform-lifecycle-inbox">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-[#0B1F3A]" data-testid="lifecycle-inbox-title">
            Tenant Lifecycle Inbox
          </h1>
          <p className="text-xs text-[#6B7688] mt-1">
            Approve or reject institute suspend / decommission / export requests (IADM-01-US-007).
          </p>
        </div>
        <Button size="sm" variant="secondary" disabled={pending} onClick={() => startTransition(() => router.refresh())}>
          <RefreshCw className={`h-4 w-4 mr-1 ${pending ? 'animate-spin' : ''}`} /> Refresh
        </Button>
      </div>

      {error && (
        <div className="flex gap-2 rounded-[8px] border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
          <AlertCircle className="h-4 w-4 mt-0.5" />
          <span>{error}</span>
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Pending requests</CardTitle>
          <CardDescription>{initial.length} item(s)</CardDescription>
        </CardHeader>
        <CardContent>
          {initial.length === 0 ? (
            <p className="text-sm text-[#6B7688]" data-testid="lifecycle-empty">
              No pending lifecycle requests.
            </p>
          ) : (
            <ul className="divide-y divide-[#E3E8EF]">
              {initial.map((r) => (
                <li
                  key={r.requestId}
                  className="py-3 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2"
                  data-testid={`lifecycle-row-${r.requestId}`}
                >
                  <div className="min-w-0 space-y-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge status={r.status}>{r.status}</Badge>
                      <span className="text-sm font-semibold">{r.requestType}</span>
                    </div>
                    <p className="text-xs text-[#6B7688] truncate">
                      tenant {r.tenantId.slice(0, 8)}… · {r.reason || '—'}
                    </p>
                  </div>
                  {(r.status || '').toUpperCase() === 'PENDING' && (
                    <div className="flex gap-2">
                      <Button
                        size="sm"
                        disabled={busyId === r.requestId}
                        onClick={() => decide(r.requestId, 'APPROVE')}
                        data-testid={`lifecycle-approve-${r.requestId}`}
                      >
                        <CheckCircle2 className="h-3.5 w-3.5 mr-1" /> Approve
                      </Button>
                      <Button
                        size="sm"
                        variant="secondary"
                        disabled={busyId === r.requestId}
                        onClick={() => decide(r.requestId, 'REJECT')}
                        data-testid={`lifecycle-reject-${r.requestId}`}
                      >
                        <XCircle className="h-3.5 w-3.5 mr-1" /> Reject
                      </Button>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
