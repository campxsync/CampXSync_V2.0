'use client';

import React, { useEffect, useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { Activity, Search, RefreshCw, AlertCircle, ShieldCheck } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';

export interface OpsHealthRow {
  serviceName: string;
  status: string;
  lastHeartbeat?: string | null;
}

export interface AuditEventRow {
  id: string;
  action: string;
  actorId?: string | null;
  createdAt?: string | null;
  tenantId?: string | null;
}

export interface SelfCheckRow {
  checkName: string;
  passed: boolean;
  detail?: string | null;
}

interface Props {
  initialServices: OpsHealthRow[];
  initialAuditItems: AuditEventRow[];
  initialSelfChecks: SelfCheckRow[];
  initialAuditScope: string | null;
  loadError: string | null;
}

function shortId(id?: string | null) {
  if (!id) return '—';
  return id.length > 12 ? `${id.slice(0, 8)}…` : id;
}

export function OpsAuditClient({
  initialServices,
  initialAuditItems,
  initialSelfChecks,
  initialAuditScope,
  loadError,
}: Props) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [actor, setActor] = useState('');
  const [action, setAction] = useState('');
  const [auditItems, setAuditItems] = useState(initialAuditItems);
  const [auditScope, setAuditScope] = useState(initialAuditScope);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [searching, setSearching] = useState(false);

  useEffect(() => {
    setAuditItems(initialAuditItems);
    setAuditScope(initialAuditScope);
  }, [initialAuditItems, initialAuditScope]);

  function refresh() {
    startTransition(() => router.refresh());
  }

  async function runSearch(e?: React.FormEvent) {
    e?.preventDefault();
    setSearching(true);
    setSearchError(null);
    try {
      const params = new URLSearchParams();
      if (actor.trim()) params.set('actor', actor.trim());
      if (action.trim()) params.set('action', action.trim());
      const qs = params.toString() ? `?${params.toString()}` : '';
      const res = await fetch(`/api/platform/ops/audit-search${qs}`, {
        method: 'GET',
        headers: { Accept: 'application/json' },
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(data.detail || data.errorCode || `Search failed (${res.status})`);
      }
      setAuditItems(Array.isArray(data.items) ? data.items : []);
      setAuditScope(typeof data.scope === 'string' ? data.scope : null);
    } catch (err) {
      setSearchError(err instanceof Error ? err.message : 'Audit search failed');
    } finally {
      setSearching(false);
    }
  }

  return (
    <div className="space-y-6" data-testid="platform-ops-desk">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]" data-testid="ops-desk-title">
            Ops & Audit Search
          </h1>
          <p className="text-xs text-[#6B7688] mt-1">
            Platform service health and immutable audit search (IADM-08). Distinct from legacy Platform Audit.
          </p>
        </div>
        <Button
          variant="secondary"
          size="sm"
          onClick={refresh}
          disabled={pending}
          data-testid="ops-refresh"
        >
          <RefreshCw className={`mr-1.5 h-4 w-4 ${pending ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      {(loadError || searchError) && (
        <div
          className="flex items-start gap-2 rounded-[8px] border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
          data-testid="ops-error"
        >
          <AlertCircle className="h-4 w-4 mt-0.5 shrink-0" />
          <span>{searchError || loadError}</span>
        </div>
      )}

      <Card data-testid="ops-health-panel">
        <CardHeader>
          <CardTitle className="text-base flex items-center gap-2">
            <Activity className="h-4 w-4" />
            Service health
          </CardTitle>
          <CardDescription>Heartbeat rows from plat.service_health</CardDescription>
        </CardHeader>
        <CardContent>
          {initialServices.length === 0 ? (
            <p className="text-sm text-[#6B7688]" data-testid="ops-health-empty">
              No health rows (table empty or probe warning).
            </p>
          ) : (
            <ul className="divide-y divide-[#E3E8EF]">
              {initialServices.map((s, idx) => (
                <li
                  key={`${s.serviceName}-${idx}`}
                  className="py-2 flex items-center justify-between gap-2"
                  data-testid={`ops-health-row-${s.serviceName}`}
                >
                  <span className="text-sm font-medium text-[#0B1F3A]">{s.serviceName}</span>
                  <div className="flex items-center gap-2">
                    <Badge status={(s.status || 'INACTIVE').toUpperCase()}>{s.status}</Badge>
                    <span className="text-xs text-[#6B7688]">
                      {s.lastHeartbeat ? new Date(s.lastHeartbeat).toLocaleString() : '—'}
                    </span>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <Card data-testid="ops-self-check-panel">
        <CardHeader>
          <CardTitle className="text-base flex items-center gap-2">
            <ShieldCheck className="h-4 w-4" />
            Audit self-check
          </CardTitle>
          <CardDescription>Immutability probes via audit.self_check_immutable()</CardDescription>
        </CardHeader>
        <CardContent>
          {initialSelfChecks.length === 0 ? (
            <p className="text-sm text-[#6B7688]" data-testid="ops-self-check-empty">
              No self-check rows returned.
            </p>
          ) : (
            <ul className="divide-y divide-[#E3E8EF]">
              {initialSelfChecks.map((c) => (
                <li
                  key={c.checkName}
                  className="py-2 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-1"
                  data-testid={`ops-check-${c.checkName}`}
                >
                  <span className="text-sm font-medium text-[#0B1F3A]">{c.checkName}</span>
                  <div className="flex items-center gap-2">
                    <Badge status={c.passed ? 'APPROVED' : 'REJECTED'}>
                      {c.passed ? 'PASSED' : 'FAILED'}
                    </Badge>
                    {c.detail && <span className="text-xs text-[#6B7688] truncate max-w-xs">{c.detail}</span>}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <Card data-testid="ops-audit-search-panel">
        <CardHeader>
          <CardTitle className="text-base flex items-center gap-2">
            <Search className="h-4 w-4" />
            Audit search
          </CardTitle>
          <CardDescription>
            Filter audit.events by actor and action
            {auditScope ? ` · scope ${auditScope}` : ''}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <form
            className="flex flex-col sm:flex-row gap-2"
            onSubmit={runSearch}
            data-testid="ops-audit-search-form"
          >
            <Input
              placeholder="Actor (principal id)"
              value={actor}
              onChange={(e) => setActor(e.target.value)}
              data-testid="ops-audit-actor"
              className="sm:max-w-xs"
            />
            <Input
              placeholder="Action"
              value={action}
              onChange={(e) => setAction(e.target.value)}
              data-testid="ops-audit-action"
              className="sm:max-w-xs"
            />
            <Button type="submit" size="sm" isLoading={searching} data-testid="ops-audit-search-submit">
              Search
            </Button>
          </form>

          {auditItems.length === 0 ? (
            <p className="text-sm text-[#6B7688]" data-testid="ops-audit-empty">
              No audit events matched.
            </p>
          ) : (
            <ul className="divide-y divide-[#E3E8EF]" data-testid="ops-audit-results">
              {auditItems.map((ev) => (
                <li key={ev.id} className="py-2 space-y-0.5" data-testid={`ops-audit-row-${ev.id}`}>
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-semibold text-[#0B1F3A]">{ev.action}</span>
                    <span className="text-xs text-[#6B7688]">
                      {ev.createdAt ? new Date(ev.createdAt).toLocaleString() : '—'}
                    </span>
                  </div>
                  <p className="text-xs text-[#6B7688]">
                    actor {shortId(ev.actorId)}
                    {ev.tenantId ? ` · tenant ${shortId(ev.tenantId)}` : ''}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
