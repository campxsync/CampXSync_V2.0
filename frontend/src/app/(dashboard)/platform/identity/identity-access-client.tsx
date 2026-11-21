'use client';

import React, { useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { Shield, KeyRound, CheckCircle2, XCircle, Lock, RefreshCw, AlertCircle } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';

export interface QuorumRequest {
  requestId: string;
  tenantId: string;
  targetUserId: string;
  action: string;
  status: string;
  reason?: string | null;
  requestedBy?: string | null;
  requestedTrack?: string | null;
  decidedBy?: string | null;
  decidedTrack?: string | null;
  createdAt?: string | null;
}

export interface BreakGlassRequest {
  requestId: string;
  tenantId: string;
  requestedBy: string;
  requesterRole?: string | null;
  reason?: string | null;
  requestedMinutes?: number;
  status: string;
  sessionId?: string | null;
  createdAt?: string | null;
}

export interface BreakGlassSession {
  sessionId: string;
  tenantId: string;
  requestId: string;
  openedBy: string;
  openerTrack?: string | null;
  reason?: string | null;
  expiresAt?: string | null;
  active?: boolean;
  closedAt?: string | null;
}

interface Props {
  initialQuorum: QuorumRequest[];
  initialBreakGlassRequests: BreakGlassRequest[];
  initialBreakGlassSessions: BreakGlassSession[];
  loadError: string | null;
}

function statusBadge(status: string) {
  const s = (status || '').toUpperCase();
  return <Badge status={s || 'DRAFT'}>{s || '—'}</Badge>;
}

function shortId(id?: string | null) {
  if (!id) return '—';
  return id.length > 12 ? `${id.slice(0, 8)}…` : id;
}

export function IdentityAccessClient({
  initialQuorum,
  initialBreakGlassRequests,
  initialBreakGlassSessions,
  loadError,
}: Props) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [actionError, setActionError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [quorumTenantId, setQuorumTenantId] = useState('a0000000-0000-0000-0000-000000000001');
  const [quorumTargetId, setQuorumTargetId] = useState('c03a0000-0000-4000-8000-000000000003');
  const [quorumAction, setQuorumAction] = useState<'APPOINT' | 'REMOVE'>('APPOINT');
  const [quorumReason, setQuorumReason] = useState('Appoint Institution Admin (R03)');
  const [bgTenantId, setBgTenantId] = useState('a0000000-0000-0000-0000-000000000001');
  const [bgReason, setBgReason] = useState('Emergency break-glass recovery');
  const [bgMinutes, setBgMinutes] = useState(60);

  async function postJson(url: string, body: Record<string, unknown>) {
    setActionError(null);
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(body),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(data.detail || data.errorCode || `Request failed (${res.status})`);
    }
    return data;
  }

  function refresh() {
    startTransition(() => router.refresh());
  }

  async function decideQuorum(id: string, decision: 'APPROVE' | 'REJECT') {
    setBusyId(id);
    try {
      await postJson(`/api/platform/identity/quorum/${id}/decide`, { decision, note: '' });
      refresh();
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Quorum decide failed');
    } finally {
      setBusyId(null);
    }
  }

  async function decideBreakGlass(id: string, decision: 'APPROVE' | 'REJECT') {
    setBusyId(id);
    try {
      await postJson(`/api/platform/identity/break-glass/${id}/decide`, { decision, note: '' });
      refresh();
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Break-glass decide failed');
    } finally {
      setBusyId(null);
    }
  }

  async function closeSession(id: string) {
    setBusyId(id);
    try {
      await postJson(`/api/platform/identity/break-glass/sessions/${id}/close`, {
        reason: 'Closed from Identity & Access desk',
      });
      refresh();
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Close session failed');
    } finally {
      setBusyId(null);
    }
  }

  const pendingQuorum = initialQuorum.filter((q) => (q.status || '').toUpperCase() === 'PENDING');
  const pendingBg = initialBreakGlassRequests.filter((r) => (r.status || '').toUpperCase() === 'PENDING');
  const activeSessions = initialBreakGlassSessions.filter((s) => s.active);

  return (
    <div className="space-y-6" data-testid="platform-identity-desk">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]" data-testid="identity-desk-title">
            Identity & Access
          </h1>
          <p className="text-xs text-[#6B7688] mt-1">
            Two-person quorum for institution admin elevation and break-glass emergency access (IADM-03).
          </p>
        </div>
        <Button
          variant="secondary"
          size="sm"
          onClick={refresh}
          disabled={pending}
          data-testid="identity-refresh"
        >
          <RefreshCw className={`mr-1.5 h-4 w-4 ${pending ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      {(loadError || actionError) && (
        <div
          className="flex items-start gap-2 rounded-[8px] border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
          data-testid="identity-error"
        >
          <AlertCircle className="h-4 w-4 mt-0.5 shrink-0" />
          <span>{actionError || loadError}</span>
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <Card>
          <CardContent className="pt-5 pb-4 flex items-center gap-3">
            <Shield className="h-8 w-8 text-[#0B1F3A]" />
            <div>
              <p className="text-2xl font-bold text-[#0B1F3A]" data-testid="quorum-pending-count">
                {pendingQuorum.length}
              </p>
              <p className="text-xs text-[#6B7688]">Pending quorum</p>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-5 pb-4 flex items-center gap-3">
            <KeyRound className="h-8 w-8 text-[#0B1F3A]" />
            <div>
              <p className="text-2xl font-bold text-[#0B1F3A]" data-testid="bg-pending-count">
                {pendingBg.length}
              </p>
              <p className="text-xs text-[#6B7688]">Pending break-glass</p>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-5 pb-4 flex items-center gap-3">
            <Lock className="h-8 w-8 text-[#0B1F3A]" />
            <div>
              <p className="text-2xl font-bold text-[#0B1F3A]" data-testid="bg-active-count">
                {activeSessions.length}
              </p>
              <p className="text-xs text-[#6B7688]">Active sessions</p>
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <Card data-testid="quorum-start-panel">
          <CardHeader>
            <CardTitle className="text-base">Start Institution Admin quorum</CardTitle>
            <CardDescription>Create an APPOINT or REMOVE request for Institution Admin (R03)</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            <Input
              data-testid="quorum-tenant-id"
              placeholder="Tenant id"
              value={quorumTenantId}
              onChange={(e) => setQuorumTenantId(e.target.value)}
            />
            <Input
              data-testid="quorum-target-user-id"
              placeholder="Target user id"
              value={quorumTargetId}
              onChange={(e) => setQuorumTargetId(e.target.value)}
            />
            <div className="flex gap-2">
              <Button
                type="button"
                size="sm"
                variant={quorumAction === 'APPOINT' ? 'primary' : 'secondary'}
                onClick={() => setQuorumAction('APPOINT')}
              >
                APPOINT
              </Button>
              <Button
                type="button"
                size="sm"
                variant={quorumAction === 'REMOVE' ? 'primary' : 'secondary'}
                onClick={() => setQuorumAction('REMOVE')}
              >
                REMOVE
              </Button>
            </div>
            <Input
              data-testid="quorum-reason"
              placeholder="Reason"
              value={quorumReason}
              onChange={(e) => setQuorumReason(e.target.value)}
            />
            <Button
              size="sm"
              data-testid="quorum-start-submit"
              disabled={busyId === 'quorum-create'}
              onClick={async () => {
                setBusyId('quorum-create');
                try {
                  await postJson('/api/platform/identity/quorum/create', {
                    tenantId: quorumTenantId,
                    targetUserId: quorumTargetId,
                    action: quorumAction,
                    reason: quorumReason,
                  });
                  refresh();
                } catch (e) {
                  setActionError(e instanceof Error ? e.message : 'Quorum create failed');
                } finally {
                  setBusyId(null);
                }
              }}
            >
              Submit quorum request
            </Button>
          </CardContent>
        </Card>

        <Card data-testid="bg-start-panel">
          <CardHeader>
            <CardTitle className="text-base">Start break-glass request</CardTitle>
            <CardDescription>Emergency access request (approved only by R01/R02)</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            <Input
              data-testid="bg-tenant-id"
              placeholder="Tenant id"
              value={bgTenantId}
              onChange={(e) => setBgTenantId(e.target.value)}
            />
            <Input
              data-testid="bg-reason"
              placeholder="Reason"
              value={bgReason}
              onChange={(e) => setBgReason(e.target.value)}
            />
            <Input
              data-testid="bg-minutes"
              type="number"
              placeholder="Minutes (15–240)"
              value={String(bgMinutes)}
              onChange={(e) => setBgMinutes(Number(e.target.value) || 60)}
            />
            <Button
              size="sm"
              data-testid="bg-start-submit"
              disabled={busyId === 'bg-create'}
              onClick={async () => {
                setBusyId('bg-create');
                try {
                  await postJson('/api/platform/identity/break-glass/create', {
                    tenantId: bgTenantId,
                    reason: bgReason,
                    requestedMinutes: bgMinutes,
                  });
                  refresh();
                } catch (e) {
                  setActionError(e instanceof Error ? e.message : 'Break-glass create failed');
                } finally {
                  setBusyId(null);
                }
              }}
            >
              Submit break-glass request
            </Button>
          </CardContent>
        </Card>
      </div>

      <Card data-testid="quorum-panel">
        <CardHeader>
          <CardTitle className="text-base">Institution Admin (R03) quorum</CardTitle>
          <CardDescription>
            Two-person approval (Super Admin R01 + System Admin R02) to appoint or remove an Institution Admin (R03) — not College Admin
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {initialQuorum.length === 0 ? (
            <p className="text-sm text-[#6B7688]" data-testid="quorum-empty">
              No quorum requests.
            </p>
          ) : (
            <ul className="divide-y divide-[#E3E8EF]">
              {initialQuorum.map((q) => (
                <li
                  key={q.requestId}
                  className="py-3 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2"
                  data-testid={`quorum-row-${q.requestId}`}
                >
                  <div className="space-y-1 min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      {statusBadge(q.status)}
                      <span className="text-sm font-semibold text-[#0B1F3A]">{q.action}</span>
                      <span className="text-xs text-[#6B7688]">tenant {shortId(q.tenantId)}</span>
                    </div>
                    <p className="text-xs text-[#6B7688] truncate">
                      target {shortId(q.targetUserId)} · by {shortId(q.requestedBy)} ({q.requestedTrack || '—'})
                      {q.reason ? ` · ${q.reason}` : ''}
                    </p>
                  </div>
                  {(q.status || '').toUpperCase() === 'PENDING' && (
                    <div className="flex gap-2 shrink-0">
                      <Button
                        size="sm"
                        variant="primary"
                        disabled={busyId === q.requestId}
                        onClick={() => decideQuorum(q.requestId, 'APPROVE')}
                        data-testid={`quorum-approve-${q.requestId}`}
                      >
                        <CheckCircle2 className="mr-1 h-3.5 w-3.5" />
                        Approve
                      </Button>
                      <Button
                        size="sm"
                        variant="secondary"
                        disabled={busyId === q.requestId}
                        onClick={() => decideQuorum(q.requestId, 'REJECT')}
                        data-testid={`quorum-reject-${q.requestId}`}
                      >
                        <XCircle className="mr-1 h-3.5 w-3.5" />
                        Reject
                      </Button>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <Card data-testid="break-glass-panel">
        <CardHeader>
          <CardTitle className="text-base">Break-glass requests</CardTitle>
          <CardDescription>Emergency elevated access — R01/R02 approve, then close sessions</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {initialBreakGlassRequests.length === 0 ? (
            <p className="text-sm text-[#6B7688]" data-testid="bg-empty">
              No break-glass requests.
            </p>
          ) : (
            <ul className="divide-y divide-[#E3E8EF]">
              {initialBreakGlassRequests.map((r) => (
                <li
                  key={r.requestId}
                  className="py-3 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2"
                  data-testid={`bg-row-${r.requestId}`}
                >
                  <div className="space-y-1 min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      {statusBadge(r.status)}
                      <span className="text-xs text-[#6B7688]">
                        {r.requestedMinutes ?? '—'} min · tenant {shortId(r.tenantId)}
                      </span>
                    </div>
                    <p className="text-xs text-[#6B7688] truncate">
                      by {shortId(r.requestedBy)} ({r.requesterRole || '—'})
                      {r.reason ? ` · ${r.reason}` : ''}
                    </p>
                  </div>
                  {(r.status || '').toUpperCase() === 'PENDING' && (
                    <div className="flex gap-2 shrink-0">
                      <Button
                        size="sm"
                        variant="primary"
                        disabled={busyId === r.requestId}
                        onClick={() => decideBreakGlass(r.requestId, 'APPROVE')}
                        data-testid={`bg-approve-${r.requestId}`}
                      >
                        <CheckCircle2 className="mr-1 h-3.5 w-3.5" />
                        Approve
                      </Button>
                      <Button
                        size="sm"
                        variant="secondary"
                        disabled={busyId === r.requestId}
                        onClick={() => decideBreakGlass(r.requestId, 'REJECT')}
                        data-testid={`bg-reject-${r.requestId}`}
                      >
                        <XCircle className="mr-1 h-3.5 w-3.5" />
                        Reject
                      </Button>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <Card data-testid="bg-sessions-panel">
        <CardHeader>
          <CardTitle className="text-base">Break-glass sessions</CardTitle>
          <CardDescription>Active emergency sessions that can be closed by R01/R02</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {initialBreakGlassSessions.length === 0 ? (
            <p className="text-sm text-[#6B7688]" data-testid="bg-sessions-empty">
              No sessions.
            </p>
          ) : (
            <ul className="divide-y divide-[#E3E8EF]">
              {initialBreakGlassSessions.map((s) => (
                <li
                  key={s.sessionId}
                  className="py-3 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2"
                  data-testid={`bg-session-${s.sessionId}`}
                >
                  <div className="space-y-1 min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      {s.active ? (
                        <Badge status="PENDING">ACTIVE</Badge>
                      ) : (
                        <Badge status="CLOSED">CLOSED</Badge>
                      )}
                      <span className="text-xs text-[#6B7688]">
                        expires {s.expiresAt ? new Date(s.expiresAt).toLocaleString() : '—'}
                      </span>
                    </div>
                    <p className="text-xs text-[#6B7688] truncate">
                      {shortId(s.sessionId)} · tenant {shortId(s.tenantId)} · {s.openerTrack || '—'}
                      {s.reason ? ` · ${s.reason}` : ''}
                    </p>
                  </div>
                  {s.active && (
                    <Button
                      size="sm"
                      variant="secondary"
                      disabled={busyId === s.sessionId}
                      onClick={() => closeSession(s.sessionId)}
                      data-testid={`bg-close-${s.sessionId}`}
                    >
                      <Lock className="mr-1 h-3.5 w-3.5" />
                      Close
                    </Button>
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
