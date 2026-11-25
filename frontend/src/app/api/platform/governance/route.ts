import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

const PLATFORM_ROLES = new Set(['platform_admin', 'system_admin', 'auditor']);

/**
 * IADM-05 BFF — platform governance desk.
 * UI → BFF → Gateway → IADM-05 (:8103). No direct DB.
 */
export async function GET(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || !PLATFORM_ROLES.has(resolution.session.role)) {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' },
      { status: 403 }
    );
  }
  const { searchParams } = new URL(req.url);
  const view = searchParams.get('view') || 'classifications';
  const type = searchParams.get('type') || '';
  const status = searchParams.get('status') || '';
  const id = searchParams.get('id') || '';
  let path = '/api/v1/iadm/05/classifications';
  if (view === 'retention') {
    path = '/api/v1/iadm/05/retention-rules';
  } else if (view === 'policies') {
    const q = new URLSearchParams();
    if (type) q.set('type', type);
    if (status) q.set('status', status);
    const qs = q.toString();
    path = `/api/v1/iadm/05/policies${qs ? `?${qs}` : ''}`;
  } else if (view === 'history' && id) {
    path = `/api/v1/iadm/05/policies/${encodeURIComponent(id)}/history`;
  } else if (view === 'requests') {
    const q = new URLSearchParams();
    if (type) q.set('type', type);
    if (status) q.set('status', status);
    const qs = q.toString();
    path = `/api/v1/iadm/05/requests${qs ? `?${qs}` : ''}`;
  }
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
  if (!resolution.success) {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' },
      { status: 403 }
    );
  }
  const role = resolution.session.role;
  const isR01 = role === 'platform_admin';
  const isR02 = role === 'system_admin';
  if (!isR01 && !isR02) {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' },
      { status: 403 }
    );
  }
  const { searchParams } = new URL(req.url);
  const view = searchParams.get('view') || '';
  const id = searchParams.get('id') || '';
  // Approve/publish/decide are R01 only at BFF (G-15); Java also enforces
  if (
    (view === 'decide' || view === 'publish' || view === 'request-decide' || view === 'erasure-close') &&
    !isR01
  ) {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'Only Super Admin (R01) may approve or publish.' },
      { status: 403 }
    );
  }
  const raw = await req.text();
  let path = '/api/v1/iadm/05/classifications';
  if (view === 'classifications') path = '/api/v1/iadm/05/classifications';
  else if (view === 'retention') path = '/api/v1/iadm/05/retention-rules';
  else if (view === 'privacy') path = '/api/v1/iadm/05/privacy-policies';
  else if (view === 'submit-review' && id)
    path = `/api/v1/iadm/05/policies/${encodeURIComponent(id)}/submit-review`;
  else if (view === 'decide' && id)
    path = `/api/v1/iadm/05/policies/${encodeURIComponent(id)}/decide`;
  else if (view === 'publish' && id)
    path = `/api/v1/iadm/05/policies/${encodeURIComponent(id)}/publish`;
  else if (view === 'exports') path = '/api/v1/iadm/05/exports';
  else if (view === 'erasures') path = '/api/v1/iadm/05/erasures';
  else if (view === 'sharing') path = '/api/v1/iadm/05/sharing';
  else if (view === 'request-decide' && id)
    path = `/api/v1/iadm/05/requests/${encodeURIComponent(id)}/decide`;
  else if (view === 'dispatch' && id)
    path = `/api/v1/iadm/05/requests/${encodeURIComponent(id)}/dispatch`;
  else if (view === 'erasure-close' && id)
    path = `/api/v1/iadm/05/requests/${encodeURIComponent(id)}/close`;
  try {
    const res = await fetchFromGateway(path, {
      method: 'POST',
      requireAuth: true,
      body: raw || '{}',
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
