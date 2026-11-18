/**
 * STM-10-EN-002 — Student portal dashboard aggregation.
 * Bounded per-call timeouts, short-lived cache, partial-failure shape.
 * Reads only existing gateway APIs; never invents data or relaxes ownership.
 */

export type PortalSectionId =
  | 'profile'
  | 'linkStatus'
  | 'preferences'
  | 'documents'
  | 'enrollment'
  | 'lifecycle'
  | 'admissions'
  | 'attendance';

export type PortalSectionStatus = 'ok' | 'empty' | 'failed';

export interface PortalSectionError {
  /** Stable machine code for tests / retry wiring */
  code: string;
  /** Plain-language reason shown to the student (no stack / internals) */
  message: string;
}

export interface PortalSectionResult<T = unknown> {
  status: PortalSectionStatus;
  data?: T;
  error?: PortalSectionError;
  durationMs: number;
}

export interface PortalDashboardAggregate {
  generatedAt: string;
  cacheHit: boolean;
  studentId: string | null;
  sections: Record<PortalSectionId, PortalSectionResult>;
  summary: {
    loaded: PortalSectionId[];
    failed: Array<{ section: PortalSectionId; reason: string }>;
  };
}

export interface AggregateFetchResult {
  ok: boolean;
  status: number;
  json?: unknown;
  text?: string;
  timedOut?: boolean;
  networkError?: boolean;
}

export type AggregateFetcher = (
  url: string,
  init: { method: string; headers: Record<string, string>; signal: AbortSignal }
) => Promise<AggregateFetchResult>;

export interface AggregateOptions {
  gatewayBaseUrl: string;
  headers: Record<string, string>;
  /** Per downstream call timeout in ms (default 3500) */
  timeoutMs?: number;
  /** Short-lived cache TTL in ms (default 30000) */
  cacheTtlMs?: number;
  /** Cache key = userId + tenantId; omit userId to skip cache */
  cacheKey?: string;
  /** Bypass cache (retry / refresh) */
  bypassCache?: boolean;
  fetcher?: AggregateFetcher;
  now?: () => number;
}

const SECTION_ORDER: PortalSectionId[] = [
  'profile',
  'linkStatus',
  'preferences',
  'documents',
  'enrollment',
  'lifecycle',
  'admissions',
  'attendance',
];

const DEFAULT_TIMEOUT_MS = 3500;
const DEFAULT_CACHE_TTL_MS = 30_000;

interface CacheEntry {
  expiresAt: number;
  value: PortalDashboardAggregate;
}

const globalForPortalDash = globalThis as unknown as {
  __campx_portal_dashboard_cache?: Map<string, CacheEntry>;
};

function cacheMap(): Map<string, CacheEntry> {
  if (!globalForPortalDash.__campx_portal_dashboard_cache) {
    globalForPortalDash.__campx_portal_dashboard_cache = new Map();
  }
  return globalForPortalDash.__campx_portal_dashboard_cache;
}

/** Test helper: clear short-lived portal dashboard cache. */
export function clearPortalDashboardCache(): void {
  cacheMap().clear();
}

export function sanitizeGatewayError(status: number, bodyText?: string): PortalSectionError {
  if (status === 401) {
    return { code: 'UNAUTHENTICATED', message: 'Sign-in is required to load this section.' };
  }
  if (status === 403) {
    return { code: 'FORBIDDEN', message: 'You do not have access to this section.' };
  }
  if (status === 404) {
    return { code: 'NOT_FOUND', message: 'No data was found for this section.' };
  }
  if (status === 408 || status === 504) {
    return { code: 'TIMEOUT', message: 'This section took too long to respond.' };
  }
  if (status >= 500) {
    return {
      code: 'SERVICE_UNAVAILABLE',
      message: 'This section is temporarily unavailable. Other sections may still work.',
    };
  }
  // Never forward raw gateway/body text to the student (may contain internals).
  void bodyText;
  return {
    code: 'UPSTREAM_ERROR',
    message: 'This section could not be loaded right now.',
  };
}

export function plainTimeoutError(): PortalSectionError {
  return {
    code: 'TIMEOUT',
    message: 'This section took too long to respond.',
  };
}

export function plainNetworkError(): PortalSectionError {
  return {
    code: 'NETWORK',
    message: 'Could not reach this service. Check your connection or try again.',
  };
}

const defaultFetcher: AggregateFetcher = async (url, init) => {
  try {
    const res = await fetch(url, {
      method: init.method,
      headers: init.headers,
      signal: init.signal,
      cache: 'no-store',
    });
    const text = await res.text();
    let json: unknown;
    try {
      json = text ? JSON.parse(text) : undefined;
    } catch {
      json = undefined;
    }
    return { ok: res.ok, status: res.status, json, text };
  } catch (err: unknown) {
    const name = err instanceof Error ? err.name : '';
    if (name === 'AbortError' || name === 'TimeoutError') {
      return { ok: false, status: 408, timedOut: true };
    }
    return { ok: false, status: 0, networkError: true };
  }
};

async function callSection(
  fetcher: AggregateFetcher,
  url: string,
  headers: Record<string, string>,
  timeoutMs: number,
  now: () => number
): Promise<PortalSectionResult> {
  const started = now();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const result = await fetcher(url, {
      method: 'GET',
      headers,
      signal: controller.signal,
    });
    const durationMs = Math.max(0, now() - started);
    if (result.timedOut) {
      return { status: 'failed', error: plainTimeoutError(), durationMs };
    }
    if (result.networkError) {
      return { status: 'failed', error: plainNetworkError(), durationMs };
    }
    if (!result.ok) {
      return {
        status: 'failed',
        error: sanitizeGatewayError(result.status, result.text),
        durationMs,
      };
    }
    const data = result.json;
    const empty =
      data == null ||
      (Array.isArray(data) && data.length === 0) ||
      (typeof data === 'object' &&
        !Array.isArray(data) &&
        Object.keys(data as Record<string, unknown>).length === 0);
    return {
      status: empty ? 'empty' : 'ok',
      data,
      durationMs,
    };
  } finally {
    clearTimeout(timer);
  }
}

function emptyFailed(message: string, code = 'DEPENDENT_FAILED'): PortalSectionResult {
  return {
    status: 'failed',
    error: { code, message },
    durationMs: 0,
  };
}

function buildSummary(
  sections: Record<PortalSectionId, PortalSectionResult>
): PortalDashboardAggregate['summary'] {
  const loaded: PortalSectionId[] = [];
  const failed: Array<{ section: PortalSectionId; reason: string }> = [];
  for (const id of SECTION_ORDER) {
    const s = sections[id];
    if (s.status === 'ok' || s.status === 'empty') {
      loaded.push(id);
    } else {
      failed.push({
        section: id,
        reason: s.error?.message || 'This section could not be loaded right now.',
      });
    }
  }
  return { loaded, failed };
}

/**
 * Aggregate student portal dashboard sections from existing gateway routes.
 * Ownership: profile always from GET /api/v1/students/me; downstream paths use that id only.
 */
export async function aggregateStudentPortalDashboard(
  options: AggregateOptions
): Promise<PortalDashboardAggregate> {
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const cacheTtlMs = options.cacheTtlMs ?? DEFAULT_CACHE_TTL_MS;
  const fetcher = options.fetcher ?? defaultFetcher;
  const now = options.now ?? Date.now;
  const base = options.gatewayBaseUrl.replace(/\/$/, '');
  const headers = { ...options.headers, Accept: 'application/json' };

  if (options.cacheKey && !options.bypassCache) {
    const hit = cacheMap().get(options.cacheKey);
    if (hit && hit.expiresAt > now()) {
      return { ...hit.value, cacheHit: true };
    }
  }

  const sections = {} as Record<PortalSectionId, PortalSectionResult>;

  // 1) Profile (anchor for ownership)
  const profile = await callSection(
    fetcher,
    `${base}/api/v1/students/me`,
    headers,
    timeoutMs,
    now
  );
  sections.profile = profile;

  const profileData =
    profile.status === 'ok' || profile.status === 'empty'
      ? (profile.data as { id?: string; studentId?: string } | undefined)
      : undefined;
  const studentId =
    profileData?.id != null
      ? String(profileData.id)
      : profileData?.studentId != null
        ? String(profileData.studentId)
        : null;

  if (!studentId) {
    const reason =
      profile.error?.message ||
      'Your student profile could not be loaded, so related sections were skipped.';
    for (const id of SECTION_ORDER) {
      if (id === 'profile') continue;
      sections[id] = emptyFailed(reason, 'PROFILE_UNAVAILABLE');
    }
  } else {
    const enc = encodeURIComponent(studentId);
    const withStudentHeader = {
      ...headers,
      'X-Student-Record-Id': studentId,
    };

    // Fan-out remaining sections in parallel (each still has its own timeout).
    const [
      linkStatus,
      preferences,
      documents,
      enrollment,
      lifecycle,
      admissions,
      attendance,
    ] = await Promise.all([
      callSection(
        fetcher,
        `${base}/api/v1/students/${enc}/link-status`,
        headers,
        timeoutMs,
        now
      ),
      callSection(
        fetcher,
        `${base}/api/v1/students/${enc}/portal-preferences`,
        headers,
        timeoutMs,
        now
      ),
      callSection(
        fetcher,
        `${base}/api/v1/documents/submissions`,
        withStudentHeader,
        timeoutMs,
        now
      ),
      callSection(
        fetcher,
        `${base}/api/v1/enrollment/enrolments`,
        withStudentHeader,
        timeoutMs,
        now
      ),
      callSection(
        fetcher,
        `${base}/api/v1/lifecycle/students/${enc}/status`,
        withStudentHeader,
        timeoutMs,
        now
      ),
      callSection(
        fetcher,
        `${base}/api/v1/admissions/applications`,
        headers,
        timeoutMs,
        now
      ),
      callSection(
        fetcher,
        `${base}/api/v1/academics/attendance/summaries/student/${enc}`,
        headers,
        timeoutMs,
        now
      ),
    ]);

    sections.linkStatus = linkStatus;
    sections.preferences = preferences;
    sections.documents = documents;
    sections.enrollment = enrollment;
    sections.lifecycle = lifecycle;
    sections.admissions = admissions;
    sections.attendance = attendance;
  }

  const aggregate: PortalDashboardAggregate = {
    generatedAt: new Date(now()).toISOString(),
    cacheHit: false,
    studentId,
    sections,
    summary: buildSummary(sections),
  };

  if (options.cacheKey) {
    cacheMap().set(options.cacheKey, {
      expiresAt: now() + cacheTtlMs,
      value: aggregate,
    });
  }

  return aggregate;
}

/** Invalidate one cache key (used by retry after outage recovery). */
export function invalidatePortalDashboardCache(cacheKey: string): void {
  cacheMap().delete(cacheKey);
}
