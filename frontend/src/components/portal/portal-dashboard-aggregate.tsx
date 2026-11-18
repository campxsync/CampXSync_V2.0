'use client';

import { useCallback, useEffect, useState, useTransition } from 'react';
import { AlertTriangle, CheckCircle2, Loader2, RefreshCw } from 'lucide-react';
import type {
  PortalActivityItem,
  PortalDashboardAggregate,
  PortalSectionId,
} from '@/lib/portal/dashboard-aggregator';

const SECTION_LABELS: Record<PortalSectionId, string> = {
  profile: 'Academic profile',
  linkStatus: 'Account link status',
  preferences: 'Portal preferences',
  documents: 'Document summaries',
  enrollment: 'Enrolment',
  lifecycle: 'Lifecycle standing',
  admissions: 'Admissions applications',
  attendance: 'Attendance summary',
  activities: 'Recent portal activities',
};

interface Props {
  /** Optional initial aggregate from the server (avoids double-fetch on first paint). */
  initial?: PortalDashboardAggregate | null;
}

/**
 * STM-10-EN-002 / STM-10-US-018 — portal section status with partial-failure UX and retry.
 */
export function PortalDashboardAggregatePanel({ initial = null }: Props) {
  const [aggregate, setAggregate] = useState<PortalDashboardAggregate | null>(initial);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();
  const [retryingSection, setRetryingSection] = useState<PortalSectionId | null>(null);

  const load = useCallback((refresh: boolean, sectionHint?: PortalSectionId | null) => {
    startTransition(async () => {
      setLoadError(null);
      if (sectionHint) setRetryingSection(sectionHint);
      try {
        const url = refresh
          ? '/api/student/portal-dashboard?refresh=1'
          : '/api/student/portal-dashboard';
        const res = await fetch(url, { cache: 'no-store', credentials: 'same-origin' });
        if (res.status === 401) {
          setLoadError('Sign-in is required to load the portal dashboard.');
          return;
        }
        if (res.status === 403) {
          setLoadError('You do not have access to the student portal dashboard.');
          return;
        }
        if (!res.ok) {
          setLoadError('The portal dashboard could not be assembled right now.');
          return;
        }
        const data = (await res.json()) as PortalDashboardAggregate;
        setAggregate(data);
      } catch {
        setLoadError('Could not reach the portal dashboard. Try again.');
      } finally {
        setRetryingSection(null);
      }
    });
  }, []);

  useEffect(() => {
    if (!initial) {
      load(false);
    }
  }, [initial, load]);

  const failedCount = aggregate?.summary.failed.length ?? 0;
  const loadedCount = aggregate?.summary.loaded.length ?? 0;

  return (
    <section
      className="rounded-[8px] border border-[#E3E8EF] bg-white p-4"
      data-testid="student-portal-aggregate"
      aria-label="Portal data sections"
    >
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-sm font-semibold text-[#0B1F3A]">Portal data status</h2>
          <p className="mt-1 text-xs text-[#6B7688]">
            Each section loads independently. If one service is down, the others still appear.
          </p>
        </div>
        <button
          type="button"
          className="inline-flex items-center gap-1.5 rounded-[6px] border border-[#E3E8EF] px-3 py-1.5 text-xs font-medium text-[#0B1F3A] hover:bg-[#F8FAFC] disabled:opacity-60"
          data-testid="student-portal-aggregate-retry"
          onClick={() => load(true)}
          disabled={pending}
        >
          {pending && !retryingSection ? (
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
          ) : (
            <RefreshCw className="h-3.5 w-3.5" />
          )}
          Retry failed sections
        </button>
      </div>

      {loadError && (
        <div
          className="mt-3 flex items-start gap-2 rounded-md border border-amber-300 bg-amber-50 p-3 text-xs text-amber-900"
          data-testid="student-portal-aggregate-error"
          role="alert"
        >
          <AlertTriangle className="h-4 w-4 flex-shrink-0 text-amber-600" />
          <span>{loadError}</span>
        </div>
      )}

      {aggregate && (
        <ul
          className="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-2"
          data-testid="student-portal-aggregate-sections"
        >
          {(Object.keys(SECTION_LABELS) as PortalSectionId[]).map((id) => {
            const section = aggregate.sections[id];
            const label = SECTION_LABELS[id];
            const failed = section?.status === 'failed';
            const isRetrying = retryingSection === id && pending;
            return (
              <li
                key={id}
                className={`rounded-[6px] border p-3 text-xs ${
                  failed ? 'border-amber-300 bg-amber-50' : 'border-[#E3E8EF] bg-[#F8FAFC]'
                }`}
                data-testid={`student-portal-section-${id}`}
                data-section-status={section?.status || 'unknown'}
              >
                <div className="flex items-start justify-between gap-2">
                  <span className="font-medium text-[#0B1F3A]">{label}</span>
                  {failed ? (
                    <AlertTriangle className="h-3.5 w-3.5 text-amber-600" aria-hidden />
                  ) : (
                    <CheckCircle2 className="h-3.5 w-3.5 text-[#1E9E6A]" aria-hidden />
                  )}
                </div>
                {failed ? (
                  <>
                    <p
                      className="mt-1 text-amber-900"
                      data-testid={`student-portal-section-${id}-error`}
                    >
                      {section.error?.message || 'This section could not be loaded right now.'}
                    </p>
                    <button
                      type="button"
                      className="mt-2 inline-flex items-center gap-1 rounded-[4px] border border-amber-400 bg-white px-2 py-1 text-[11px] font-medium text-amber-900 hover:bg-amber-100 disabled:opacity-60"
                      data-testid={`student-portal-section-${id}-retry`}
                      onClick={() => load(true, id)}
                      disabled={pending}
                    >
                      {isRetrying ? (
                        <Loader2 className="h-3 w-3 animate-spin" />
                      ) : (
                        <RefreshCw className="h-3 w-3" />
                      )}
                      Retry this section
                    </button>
                  </>
                ) : (
                  <p className="mt-1 text-[#6B7688]" data-testid={`student-portal-section-${id}-ok`}>
                    {section?.status === 'empty' ? 'Loaded — no items yet.' : 'Loaded.'}
                  </p>
                )}
              </li>
            );
          })}
        </ul>
      )}

      {aggregate?.sections.activities?.status === 'ok' &&
        Array.isArray(
          (aggregate.sections.activities.data as { items?: PortalActivityItem[] } | undefined)
            ?.items
        ) && (
          <div className="mt-4" data-testid="student-recent-portal-activities">
            <h3 className="text-xs font-semibold uppercase tracking-wide text-[#6B7688]">
              Recent portal activities
            </h3>
            <ul className="mt-2 space-y-2">
              {(
                (aggregate.sections.activities.data as { items: PortalActivityItem[] }).items || []
              ).map((item) => (
                <li
                  key={item.id}
                  className="rounded-[6px] border border-[#E3E8EF] bg-[#F8FAFC] px-3 py-2 text-xs text-[#0B1F3A]"
                  data-testid="student-recent-activity-item"
                >
                  <div className="flex flex-wrap items-baseline justify-between gap-2">
                    <span className="font-medium">{item.action}</span>
                    {item.occurredAt && (
                      <time className="text-[11px] text-[#6B7688]" dateTime={item.occurredAt}>
                        {item.occurredAt}
                      </time>
                    )}
                  </div>
                  <p className="mt-0.5 text-[#6B7688]">{item.summary}</p>
                </li>
              ))}
            </ul>
          </div>
        )}

      {aggregate?.sections.activities?.status === 'empty' && (
        <p
          className="mt-4 text-xs text-[#6B7688]"
          data-testid="student-recent-portal-activities-empty"
        >
          No recent portal activities yet.
        </p>
      )}

      {aggregate && failedCount > 0 && (
        <p
          className="mt-3 text-xs text-[#6B7688]"
          data-testid="student-portal-aggregate-partial"
          role="status"
        >
          {failedCount} section{failedCount === 1 ? '' : 's'} unavailable; {loadedCount} still
          available. The rest of your portal stays usable.
        </p>
      )}

      {aggregate && failedCount === 0 && loadedCount > 0 && (
        <p className="mt-3 text-xs text-[#1E9E6A]" data-testid="student-portal-aggregate-all-ok">
          All portal sections loaded.
        </p>
      )}
    </section>
  );
}
