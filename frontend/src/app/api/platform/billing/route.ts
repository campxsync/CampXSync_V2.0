import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

const PLATFORM_ROLES = new Set(['platform_admin', 'system_admin', 'auditor']);

/**
 * IADM-04 BFF — platform billing reads (plans, subscriptions, trial policy).
 * UI → BFF → Gateway → IADM-04 (:8102). No direct DB.
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
  const view = searchParams.get('view') || 'plans';
  const status = searchParams.get('status') || '';
  const tenantId = searchParams.get('tenantId') || '';
  let path = '/api/v1/iadm/04/plans';
  if (view === 'subscriptions') {
    path = '/api/v1/iadm/04/subscriptions';
    const q = new URLSearchParams();
    if (status) q.set('status', status);
    if (tenantId) q.set('tenantId', tenantId);
    const qs = q.toString();
    if (qs) path += `?${qs}`;
  } else if (view === 'trial-policy') {
    path = '/api/v1/iadm/04/trial-policy';
  } else if (view === 'feature-flags') {
    path = '/api/v1/iadm/04/feature-flags';
  } else if (view === 'plans') {
    path = status
      ? `/api/v1/iadm/04/plans?status=${encodeURIComponent(status)}`
      : '/api/v1/iadm/04/plans';
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
  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' },
      { status: 403 }
    );
  }
  const { searchParams } = new URL(req.url);
  const view = searchParams.get('view') || 'plans';
  const raw = await req.text();
  let path = '/api/v1/iadm/04/plans';
  if (view === 'plans') {
    path = '/api/v1/iadm/04/plans';
  } else if (view === 'entitlements-check') {
    path = '/api/v1/iadm/04/entitlements/check';
  } else {
    return NextResponse.json({ errorCode: 'IADM04_INVALID', detail: 'Unknown view.' }, { status: 400 });
  }
  try {
    const res = await fetchFromGateway(path, {
      method: 'POST',
      requireAuth: true,
      headers: { 'Content-Type': 'application/json' },
      body: raw,
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
