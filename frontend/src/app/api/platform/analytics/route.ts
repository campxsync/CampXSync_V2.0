import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

const PLATFORM_ROLES = new Set(['platform_admin', 'system_admin', 'auditor']);

/**
 * IADM-06 BFF — platform analytics desk.
 * Plain language: UI → BFF → Gateway → IADM-06 (:8104). No direct DB.
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
  const view = searchParams.get('view') || 'overview';
  const window = searchParams.get('window') || '';
  const tenantId = searchParams.get('tenantId') || '';
  const id = searchParams.get('id') || '';

  let path = '/api/v1/iadm/06/overview/platform';
  if (view === 'catalogue') path = '/api/v1/iadm/06/catalogue';
  else if (view === 'comparison') path = '/api/v1/iadm/06/comparison';
  else if (view === 'entitlements') path = '/api/v1/iadm/06/entitlements';
  else if (view === 'trends') path = '/api/v1/iadm/06/trends';
  else if (view === 'exports') path = '/api/v1/iadm/06/exports';
  else if (view === 'export' && id) path = `/api/v1/iadm/06/exports/${encodeURIComponent(id)}`;
  else if (view === 'export-csv' && id) path = `/api/v1/iadm/06/exports/${encodeURIComponent(id)}/csv`;
  else if (view === 'suppression') path = '/api/v1/iadm/06/settings/suppression';

  const q = new URLSearchParams();
  if (window) q.set('window', window);
  if (tenantId) q.set('tenantId', tenantId);
  const qs = q.toString();
  if (qs && !path.includes('?')) path = `${path}?${qs}`;

  try {
    const res = await fetchFromGateway(path, { method: 'GET', requireAuth: true });
    const text = await res.text();
    if (view === 'export-csv') {
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
  if (!resolution.success) {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You do not have access to this view' },
      { status: 403 }
    );
  }
  const role = resolution.session.role;
  const isExporter = role === 'platform_admin' || role === 'system_admin';
  const { searchParams } = new URL(req.url);
  const view = searchParams.get('view') || '';
  const id = searchParams.get('id') || '';

  // Auditor (R28) cannot export or mutate
  if (role === 'auditor') {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'Auditor cannot export or change analytics settings' },
      { status: 403 }
    );
  }

  let path = '/api/v1/iadm/06/exports';
  if (view === 'export') path = '/api/v1/iadm/06/exports';
  else if (view === 'export-retry' && id) path = `/api/v1/iadm/06/exports/${encodeURIComponent(id)}/retry`;
  else if (view === 'suppression') {
    if (!isExporter) {
      return NextResponse.json({ errorCode: 'FORBIDDEN', detail: 'Not authorized' }, { status: 403 });
    }
    path = '/api/v1/iadm/06/settings/suppression';
  } else if (view === 'outage') {
    if (!isExporter) {
      return NextResponse.json({ errorCode: 'FORBIDDEN', detail: 'Not authorized' }, { status: 403 });
    }
    path = '/api/v1/iadm/06/dev/outage-flags';
  } else if (!isExporter && view === 'export') {
    return NextResponse.json({ errorCode: 'FORBIDDEN', detail: 'Not authorized' }, { status: 403 });
  }

  try {
    const payload = await req.json().catch(() => ({}));
    const res = await fetchFromGateway(path, {
      method: 'POST',
      requireAuth: true,
      body: JSON.stringify(payload ?? {}),
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
