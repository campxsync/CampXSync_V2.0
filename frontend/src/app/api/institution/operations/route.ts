import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

function withQuery(path: string, params: URLSearchParams): string {
  const qs = params.toString();
  return qs ? `${path}?${qs}` : path;
}

/**
 * IADM-08 BFF — institution operations (R03 own-institute audit only; no health).
 */
export async function GET(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success) {
    return NextResponse.json({ errorCode: 'UNAUTHORIZED', detail: 'Sign in required' }, { status: 401 });
  }
  const role = resolution.session.role;
  if (role !== 'institution_admin' && role !== 'platform_admin' && role !== 'system_admin' && role !== 'auditor') {
    return NextResponse.json({ errorCode: 'IADM08_ACCESS_DENIED', detail: 'You are not authorized for this view.' }, { status: 403 });
  }

  const sp = req.nextUrl.searchParams;
  const view = sp.get('view') || 'audit-search';
  if (view === 'health-overview' || view === 'health-detail' || view === 'health-inventory') {
    return NextResponse.json(
      { errorCode: 'IADM08_ACCESS_DENIED', detail: 'Institution Admin has no service-health view.' },
      { status: 403 }
    );
  }

  let path: string | null = null;
  if (view === 'audit-search') {
    const q = new URLSearchParams();
    for (const k of ['from', 'to', 'action', 'tenantId', 'denialOnly', 'page']) {
      const v = sp.get(k);
      if (v) q.set(k, v);
    }
    path = withQuery('/api/v1/iadm/08/audit/search', q);
  } else if (view === 'audit-detail') {
    const id = sp.get('id');
    if (!id) return NextResponse.json({ errorCode: 'IADM08_BAD_REQUEST', detail: 'id required' }, { status: 400 });
    path = `/api/v1/iadm/08/audit/events/${encodeURIComponent(id)}`;
  } else if (view === 'alerts') {
    const q = new URLSearchParams();
    const tenantId = sp.get('tenantId');
    if (tenantId) q.set('tenantId', tenantId);
    path = withQuery('/api/v1/iadm/08/alerts', q);
  }

  if (!path) {
    return NextResponse.json({ errorCode: 'IADM08_NOT_FOUND', detail: 'Unknown view' }, { status: 404 });
  }
  try {
    const res = await fetchFromGateway(path, { requireAuth: true, method: 'GET' });
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
  } catch (e) {
    if (e instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: 'UNAUTHORIZED', detail: e.message }, { status: 401 });
    }
    return NextResponse.json({ errorCode: 'UNAVAILABLE', detail: 'Gateway unavailable' }, { status: 503 });
  }
}
