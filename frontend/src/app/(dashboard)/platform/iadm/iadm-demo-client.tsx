'use client';

import { useState, useTransition } from 'react';
import { IADM_SERVICES, iadmStoryTotals, type IadmService } from '@/lib/iadm/story-catalog';

type ProbeResult = {
  code: string;
  name: string;
  port: number;
  path: string;
  httpStatus: number;
  ok: boolean;
  authGated?: boolean;
  latencyMs: number;
  body: unknown;
  storyCount: number;
  completedStories: number;
  deferredStories: number;
};

type ProbeResponse = {
  probedAt: string;
  servicesUp: number;
  servicesTotal: number;
  results: ProbeResult[];
};

export function IadmDemoClient() {
  const totals = iadmStoryTotals();
  const [probe, setProbe] = useState<ProbeResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();
  const [expanded, setExpanded] = useState<string | null>('03');

  function runProbe() {
    startTransition(async () => {
      setError(null);
      try {
        const res = await fetch('/api/platform/iadm/probe', { cache: 'no-store' });
        const data = await res.json();
        if (!res.ok) {
          setError(data.detail || data.errorCode || `HTTP ${res.status}`);
          setProbe(null);
          return;
        }
        setProbe(data as ProbeResponse);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Probe failed');
      }
    });
  }

  return (
    <div className="mx-auto max-w-6xl px-6 py-8" data-testid="iadm-demo-desk">
      <header className="mb-8 border-b border-slate-200 pb-6">
        <p className="text-sm font-medium tracking-wide text-teal-800">CampXSync · Platform</p>
        <h1 className="mt-1 text-3xl font-semibold text-slate-900" data-testid="iadm-demo-title">
          IADM story demo desk
        </h1>
        <p className="mt-2 max-w-2xl text-slate-600">
          Live browse of all IADM-01…08 user stories. Health probes go UI → BFF → API Gateway → Java
          microservices (no direct DB from the browser).
        </p>
        <div className="mt-4 flex flex-wrap gap-3 text-sm" data-testid="iadm-demo-totals">
          <span className="rounded-md bg-emerald-50 px-3 py-1 text-emerald-800">
            {totals.completed} completed
          </span>
          <span className="rounded-md bg-amber-50 px-3 py-1 text-amber-900">
            {totals.deferred} deferred
          </span>
          <span className="rounded-md bg-slate-100 px-3 py-1 text-slate-700">
            {totals.total} stories · 8 services
          </span>
        </div>
        <div className="mt-5 flex flex-wrap items-center gap-3">
          <button
            type="button"
            data-testid="iadm-demo-probe"
            onClick={runProbe}
            disabled={pending}
            className="rounded-md bg-teal-800 px-4 py-2 text-sm font-medium text-white hover:bg-teal-900 disabled:opacity-60"
          >
            {pending ? 'Probing gateway…' : 'Run live health probe (all 8)'}
          </button>
          {probe ? (
            <span className="text-sm text-slate-600" data-testid="iadm-demo-probe-summary">
              {probe.servicesUp}/{probe.servicesTotal} up · {new Date(probe.probedAt).toLocaleTimeString()}
            </span>
          ) : null}
        </div>
        {error ? (
          <p className="mt-3 text-sm text-red-700" data-testid="iadm-demo-error">
            {error}
          </p>
        ) : null}
      </header>

      <div className="space-y-4">
        {IADM_SERVICES.map((svc) => (
          <ServiceCard
            key={svc.code}
            service={svc}
            probe={probe?.results.find((r) => r.code === svc.code)}
            expanded={expanded === svc.code}
            onToggle={() => setExpanded(expanded === svc.code ? null : svc.code)}
          />
        ))}
      </div>
    </div>
  );
}

function ServiceCard({
  service,
  probe,
  expanded,
  onToggle,
}: {
  service: IadmService;
  probe?: ProbeResult;
  expanded: boolean;
  onToggle: () => void;
}) {
  const completed = service.stories.filter((s) => s.status === 'completed').length;
  const deferred = service.stories.length - completed;
  return (
    <section
      className="rounded-lg border border-slate-200 bg-white shadow-sm"
      data-testid={`iadm-service-${service.code}`}
    >
      <button
        type="button"
        onClick={onToggle}
        className="flex w-full items-start justify-between gap-4 px-4 py-3 text-left"
        data-testid={`iadm-service-toggle-${service.code}`}
      >
        <div>
          <h2 className="text-lg font-semibold text-slate-900">
            IADM-{service.code} · {service.name}
          </h2>
          <p className="text-sm text-slate-500">
            :{service.port} · {service.gatewayPrefix} · {completed}/{service.stories.length} completed
            {deferred ? ` · ${deferred} deferred` : ''}
          </p>
        </div>
        <ProbeBadge probe={probe} />
      </button>
      {expanded ? (
        <ul className="border-t border-slate-100 px-4 py-3" data-testid={`iadm-stories-${service.code}`}>
          {service.stories.map((story) => (
            <li
              key={story.id}
              className="flex items-center justify-between gap-3 border-b border-slate-50 py-2 last:border-0"
              data-testid={story.id}
            >
              <div>
                <p className="font-mono text-xs text-slate-500">{story.id}</p>
                <p className="text-sm text-slate-800">{story.title}</p>
              </div>
              <StatusPill status={story.status} />
            </li>
          ))}
          {probe ? (
            <li className="mt-2 rounded-md bg-slate-50 p-3 font-mono text-xs text-slate-700" data-testid={`iadm-probe-detail-${service.code}`}>
              HTTP {probe.httpStatus} · {probe.latencyMs}ms · {JSON.stringify(probe.body)}
            </li>
          ) : null}
        </ul>
      ) : null}
    </section>
  );
}

function ProbeBadge({ probe }: { probe?: ProbeResult }) {
  if (!probe) {
    return <span className="rounded bg-slate-100 px-2 py-1 text-xs text-slate-500">not probed</span>;
  }
  if (probe.ok && probe.authGated) {
    return (
      <span className="rounded bg-emerald-100 px-2 py-1 text-xs font-medium text-emerald-800" data-testid="iadm-probe-ok">
        UP (HMAC) {probe.latencyMs}ms
      </span>
    );
  }
  if (probe.ok) {
    return (
      <span className="rounded bg-emerald-100 px-2 py-1 text-xs font-medium text-emerald-800" data-testid="iadm-probe-ok">
        UP {probe.latencyMs}ms
      </span>
    );
  }
  return (
    <span className="rounded bg-red-100 px-2 py-1 text-xs font-medium text-red-800" data-testid="iadm-probe-down">
      DOWN {probe.httpStatus || 'err'}
    </span>
  );
}

function StatusPill({ status }: { status: 'completed' | 'DEFERRED' }) {
  if (status === 'completed') {
    return <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-xs text-emerald-800">completed</span>;
  }
  return <span className="rounded-full bg-amber-50 px-2 py-0.5 text-xs text-amber-900">DEFERRED</span>;
}
