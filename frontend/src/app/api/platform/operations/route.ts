import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

const PLATFORM_ROLES = new Set(['platform_admin', 'system_admin', 'auditor']);

function withQuery(path: string, params: URLSearchParams): string {
  const qs = params.toString();
  return qs ? `${path}?${qs}` : path;
}

function gatewayGetPath(view: string, sp: URLSearchParams, id: string): string | null {
  if (view === 'audit-search') {
    const q = new URLSearchParams();
    for (const k of ['from', 'to', 'action', 'tenantId', 'denialOnly', 'page']) {
      const v = sp.get(k);
      if (v) q.set(k, v);
    }
    return withQuery('/api/v1/iadm/08/audit/search', q);
  }
  if (view === 'audit-detail' && id) {
    return `/api/v1/iadm/08/audit/events/${encodeURIComponent(id)}`;
  }
  if (view === 'timeline' && id) {
    const q = new URLSearchParams();
    for (const k of ['from', 'to']) {
      const v = sp.get(k);
      if (v) q.set(k, v);
    }
    return withQuery(`/api/v1/iadm/08/audit/timeline/${encodeURIComponent(id)}`, q);
  }
  if (view === 'health-overview') return '/api/v1/iadm/08/health/overview';
  if (view === 'health-inventory') return '/api/v1/iadm/08/health/inventory';
  if (view === 'health-detail' && id) {
    return `/api/v1/iadm/08/health/targets/${encodeURIComponent(id)}`;
  }
  if (view === 'alerts') {
    const q = new URLSearchParams();
    const tenantId = sp.get('tenantId');
    if (tenantId) q.set('tenantId', tenantId);
    return withQuery('/api/v1/iadm/08/alerts', q);
  }
  if (view === 'iadm07-evidence') {
    const q = new URLSearchParams();
    const kind = sp.get('kind');
    if (kind) q.set('kind', kind);
    return withQuery('/api/v1/iadm/08/iadm07/evidence', q);
  }
  if (view === 'sla') return '/api/v1/iadm/08/sla';
  if (view === 'limits') return '/api/v1/iadm/08/limits';
  if (view === 'health') return '/api/v1/iadm/08/health';
  if (view === 'outage') return '/api/v1/iadm/08/dev/outage-flags';
  if (view === 'export' || view === 'acknowledge') {
    return `/api/v1/iadm/08/${view}`;
  }
  return null;
}

async function proxyGateway(path: string, init: RequestInit) {
  const res = await fetchFromGateway(path, { requireAuth: true, ...init });
  const text = await res.text();
  let body: unknown = text;
  try {
    body = JSON.parse(text);
  } catch {
    /* keep */
  }
  return NextResponse.json(
    typeof body === 'object' && body !== null ? body : { errorCode: 'UNAVAILABLE', detail: 'unavailable' },
    { status: res.status }
  );
}

/**
 * IADM-08 BFF — platform operations desk (audit / health / alerts).
 * UI → BFF → Gateway → IADM-08 (:8106). No direct DB.
 */
export async function GET(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || !PLATFORM_ROLES.has(resolution.session.role)) {
    return NextResponse.json(
      { errorCode: 'IADM08_ACCESS_DENIED', detail: 'You are not authorized for this view.' },
      { status: 403 }
    );
  }

  const sp = req.nextUrl.searchParams;
  const view = sp.get('view') || 'health-overview';
  const id = sp.get('id') || '';
  const path = gatewayGetPath(view, sp, id);
  if (!path) {
    return NextResponse.json({ errorCode: 'IADM08_NOT_FOUND', detail: 'Unknown view' }, { status: 404 });
  }
  try {
    return await proxyGateway(path, { method: 'GET' });
  } catch (e) {
    if (e instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: 'UNAUTHORIZED', detail: e.message }, { status: 401 });
    }
    return NextResponse.json({ errorCode: 'UNAVAILABLE', detail: 'Gateway unavailable' }, { status: 503 });
  }
}

export async function POST(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || !PLATFORM_ROLES.has(resolution.session.role)) {
    return NextResponse.json(
      { errorCode: 'IADM08_ACCESS_DENIED', detail: 'You are not authorized for this view.' },
      { status: 403 }
    );
  }
  const sp = req.nextUrl.searchParams;
  const view = sp.get('view') || '';
  // Deferred: acknowledge / export — refuse
  if (view === 'acknowledge' || view === 'export') {
    return NextResponse.json({ errorCode: 'IADM08_NOT_AVAILABLE', detail: 'This feature is not available.' }, { status: 404 });
  }
  if (view === 'outage') {
    const body = await req.text();
    try {
      return await proxyGateway('/api/v1/iadm/08/dev/outage-flags', { method: 'POST', body, headers: { 'Content-Type': 'application/json' } });
    } catch (e) {
      return NextResponse.json({ errorCode: 'UNAVAILABLE', detail: 'Gateway unavailable' }, { status: 503 });
    }
  }
  return NextResponse.json({ errorCode: 'IADM08_NOT_FOUND', detail: 'Unknown POST view' }, { status: 404 });
}
