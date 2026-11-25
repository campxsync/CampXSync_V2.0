import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

/**
 * IADM-06 BFF — institution analytics desk (R03 own institute).
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
  const view = searchParams.get('view') || 'overview';
  const window = searchParams.get('window') || '';
  const tenantId = searchParams.get('tenantId') || '';

  let path = '/api/v1/iadm/06/overview/institute';
  if (view === 'trends') path = '/api/v1/iadm/06/trends';
  else if (view === 'entitlements') path = '/api/v1/iadm/06/entitlements';
  else if (view === 'exports') path = '/api/v1/iadm/06/exports';
  else if (view === 'catalogue') path = '/api/v1/iadm/06/catalogue';

  const q = new URLSearchParams();
  if (window) q.set('window', window);
  if (tenantId) q.set('tenantId', tenantId);
  const qs = q.toString();
  if (qs) path = `${path}?${qs}`;

  try {
    const res = await fetchFromGateway(path, { method: 'GET', requireAuth: true });
    const text = await res.text();
    let body: unknown = text;
    try {
      body = JSON.parse(text);
    } catch {
      /* keep */
    }
    return NextResponse.json(
      typeof body === 'object' && body ? body : { errorCode: 'UNAVAILABLE', detail: 'unavailable' },
      { status: res.status }
    );
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
  try {
    const payload = await req.json().catch(() => ({}));
    const res = await fetchFromGateway('/api/v1/iadm/06/exports', {
      method: 'POST',
      requireAuth: true,
      body: JSON.stringify({ ...(payload ?? {}), scopeType: 'INSTITUTE' }),
      headers: { 'Content-Type': 'application/json' },
    });
    const text = await res.text();
    let body: unknown = text;
    try {
      body = JSON.parse(text);
    } catch {
      /* keep */
    }
    return NextResponse.json(
      typeof body === 'object' && body ? body : { errorCode: 'UNAVAILABLE', detail: 'unavailable' },
      { status: res.status }
    );
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json({ errorCode: 'UNAVAILABLE', detail: 'unavailable' }, { status: 503 });
  }
}
