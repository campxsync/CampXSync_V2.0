import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

function withQuery(path: string, params: URLSearchParams): string {
  const qs = params.toString();
  return qs ? `${path}?${qs}` : path;
}

function gatewayGetPath(view: string, sp: URLSearchParams): string | null {
  const tenantQ = new URLSearchParams();
  const tenantId = sp.get('tenantId');
  if (tenantId) tenantQ.set('tenantId', tenantId);

  if (view === 'posture' || view === 'overview') {
    return withQuery('/api/v1/iadm/07/posture/overview', tenantQ);
  }
  if (view === 'findings') return withQuery('/api/v1/iadm/07/findings', tenantQ);
  if (view === 'alerts') return withQuery('/api/v1/iadm/07/alerts', tenantQ);
  if (view === 'exceptions') return withQuery('/api/v1/iadm/07/exceptions', tenantQ);
  if (view === 'reports') return withQuery('/api/v1/iadm/07/reports/counts', tenantQ);
  return null;
}

async function proxy(path: string, init: RequestInit) {
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
 * IADM-07 BFF — institution security desk (R03 own institute).
 */
export async function GET(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'institution_admin') {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You do not have access to this view' },
      { status: 403 }
    );
  }
  const { searchParams } = new URL(req.url);
  const view = searchParams.get('view') || 'posture';
  const path = gatewayGetPath(view, searchParams);
  if (!path) {
    return NextResponse.json({ errorCode: 'BAD_REQUEST', detail: 'Unknown view' }, { status: 400 });
  }
  try {
    return await proxy(path, { method: 'GET' });
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json({ errorCode: 'UNAVAILABLE', detail: 'unavailable' }, { status: 503 });
  }
}

export async function POST(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'institution_admin') {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You do not have access to this view' },
      { status: 403 }
    );
  }
  const { searchParams } = new URL(req.url);
  const view = searchParams.get('view') || '';
  const id = searchParams.get('id') || '';
  let path: string | null = null;
  if (view === 'finding-transition' && id) {
    path = `/api/v1/iadm/07/findings/${encodeURIComponent(id)}/transition`;
  } else if (view === 'alert-transition' && id) {
    path = `/api/v1/iadm/07/alerts/${encodeURIComponent(id)}/transition`;
  } else if (view === 'exception') {
    path = '/api/v1/iadm/07/exceptions';
  } else if (view === 'evidence') {
    path = '/api/v1/iadm/07/evidence';
  } else if (view === 'standards' || view === 'standard-transition') {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'Institution Admin cannot edit security standards' },
      { status: 403 }
    );
  } else {
    return NextResponse.json({ errorCode: 'BAD_REQUEST', detail: 'Unknown action' }, { status: 400 });
  }

  const payload = await req.json().catch(() => ({}));
  const toStatus = String((payload as { toStatus?: string }).toStatus || '').toUpperCase();
  if (view === 'finding-transition' && toStatus === 'RESOLVED') {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'Institution Admin cannot resolve findings' },
      { status: 403 }
    );
  }

  try {
    return await proxy(path, {
      method: 'POST',
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

export async function PATCH(req: NextRequest) {
  return POST(req);
}