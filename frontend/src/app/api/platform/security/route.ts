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
  const tenantQ = new URLSearchParams();
  const tenantId = sp.get('tenantId');
  if (tenantId) tenantQ.set('tenantId', tenantId);

  if (view === 'posture' || view === 'posture-overview') {
    return withQuery('/api/v1/iadm/07/posture/overview', tenantQ);
  }
  if (view === 'standards') {
    const status = sp.get('status');
    const q = new URLSearchParams();
    if (status) q.set('status', status);
    return withQuery('/api/v1/iadm/07/standards', q);
  }
  if (view === 'findings') return withQuery('/api/v1/iadm/07/findings', tenantQ);
  if (view === 'risks') return withQuery('/api/v1/iadm/07/risks', tenantQ);
  if (view === 'signals') return withQuery('/api/v1/iadm/07/signals', tenantQ);
  if (view === 'alerts') return withQuery('/api/v1/iadm/07/alerts', tenantQ);
  if (view === 'exceptions') return withQuery('/api/v1/iadm/07/exceptions', tenantQ);
  if (view === 'controls') return withQuery('/api/v1/iadm/07/controls', tenantQ);
  if (view === 'enforcement') return withQuery('/api/v1/iadm/07/enforcement', tenantQ);
  if (view === 'reports') return withQuery('/api/v1/iadm/07/reports/counts', tenantQ);
  if (view === 'audit') {
    const q = new URLSearchParams();
    const limit = sp.get('limit');
    if (limit) q.set('limit', limit);
    return withQuery('/api/v1/iadm/07/audit', q);
  }
  if (view === 'stale-threshold') return '/api/v1/iadm/07/settings/stale-threshold';
  if (view === 'outage') return '/api/v1/iadm/07/dev/outage-flags';
  if (view === 'health') return '/api/v1/iadm/07/health';
  if (view === 'transition' && id) {
    const kind = sp.get('kind') || 'findings';
    return `/api/v1/iadm/07/${kind}/${encodeURIComponent(id)}/transition`;
  }
  return null;
}

async function proxyGateway(path: string, init: RequestInit, isCsv = false) {
  const res = await fetchFromGateway(path, { requireAuth: true, ...init });
  const text = await res.text();
  if (isCsv) {
    return new NextResponse(text, {
      status: res.status,
      headers: { 'Content-Type': 'text/csv; charset=utf-8' },
    });
  }
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
 * IADM-07 BFF — platform security & compliance desk.
 * UI → BFF → Gateway → IADM-07 (:8105). No direct DB.
 */
export async function GET(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || !PLATFORM_ROLES.has(resolution.session.role)) {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You do not have access to this view' },
      { status: 403 }
    );
  }
  const { searchParams } = new URL(req.url);
  const view = searchParams.get('view') || 'posture';
  const id = searchParams.get('id') || '';
  const path = gatewayGetPath(view, searchParams, id);
  if (!path) {
    return NextResponse.json({ errorCode: 'BAD_REQUEST', detail: 'Unknown view' }, { status: 400 });
  }
  try {
    return await proxyGateway(path, { method: 'GET' });
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json({ errorCode: 'UNAVAILABLE', detail: 'unavailable' }, { status: 503 });
  }
}

function gatewayPostPath(view: string, id: string, role: string): { path: string } | { error: NextResponse } {
  if (role === 'auditor') {
    return {
      error: NextResponse.json(
        { errorCode: 'FORBIDDEN', detail: 'Auditor cannot change security registers' },
        { status: 403 }
      ),
    };
  }
  if (view === 'propose-standard') return { path: '/api/v1/iadm/07/standards' };
  if (view === 'finding-create') return { path: '/api/v1/iadm/07/findings' };
  if (view === 'exception') return { path: '/api/v1/iadm/07/exceptions' };
  if (view === 'evidence') return { path: '/api/v1/iadm/07/evidence' };
  if (view === 'stale-threshold') return { path: '/api/v1/iadm/07/settings/stale-threshold' };
  if (view === 'outage') return { path: '/api/v1/iadm/07/dev/outage-flags' };
  if (view === 'exception-decide' && id) {
    if (role !== 'platform_admin') {
      return {
        error: NextResponse.json(
          { errorCode: 'FORBIDDEN', detail: 'Only Super Admin may decide exceptions' },
          { status: 403 }
        ),
      };
    }
    return { path: `/api/v1/iadm/07/exceptions/${encodeURIComponent(id)}/decide` };
  }
  if (view === 'standard-transition' && id) {
    return { path: `/api/v1/iadm/07/standards/${encodeURIComponent(id)}/transition` };
  }
  if (view === 'finding-transition' && id) {
    return { path: `/api/v1/iadm/07/findings/${encodeURIComponent(id)}/transition` };
  }
  if (view === 'signal-transition' && id) {
    return { path: `/api/v1/iadm/07/signals/${encodeURIComponent(id)}/transition` };
  }
  if (view === 'alert-transition' && id) {
    return { path: `/api/v1/iadm/07/alerts/${encodeURIComponent(id)}/transition` };
  }
  if (view === 'risk-transition' && id) {
    return { path: `/api/v1/iadm/07/risks/${encodeURIComponent(id)}/transition` };
  }
  if (view === 'control-transition' && id) {
    return { path: `/api/v1/iadm/07/controls/${encodeURIComponent(id)}/transition` };
  }
  return { error: NextResponse.json({ errorCode: 'BAD_REQUEST', detail: 'Unknown action' }, { status: 400 }) };
}

async function handleMutate(req: NextRequest, method: 'POST' | 'PATCH') {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success) {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You do not have access to this view' },
      { status: 403 }
    );
  }
  const role = resolution.session.role;
  if (!PLATFORM_ROLES.has(role)) {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You do not have access to this view' },
      { status: 403 }
    );
  }
  const { searchParams } = new URL(req.url);
  const view = searchParams.get('view') || '';
  const id = searchParams.get('id') || '';
  const resolved = gatewayPostPath(view, id, role);
  if ('error' in resolved) return resolved.error;

  const payload = await req.json().catch(() => ({}));
  if (view === 'standard-transition' && role === 'system_admin') {
    const toStatus = String((payload as { toStatus?: string }).toStatus || '').toUpperCase();
    if (toStatus === 'APPROVED' || toStatus === 'PUBLISHED') {
      return NextResponse.json(
        { errorCode: 'FORBIDDEN', detail: 'System Admin cannot approve or publish standards' },
        { status: 403 }
      );
    }
  }

  const gatewayMethod = method === 'PATCH' ? 'POST' : 'POST';
  try {
    return await proxyGateway(resolved.path, {
      method: gatewayMethod,
      body: JSON.stringify(payload ?? {}),
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json({ errorCode: 'UNAVAILABLE', detail: 'unavailable' }, { status: 503 });
  }
}

export async function POST(req: NextRequest) {
  return handleMutate(req, 'POST');
}

export async function PATCH(req: NextRequest) {
  return handleMutate(req, 'PATCH');
}