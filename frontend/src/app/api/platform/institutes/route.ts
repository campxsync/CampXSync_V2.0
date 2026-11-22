import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

/**
 * Platform Operator — list institutes (US-016) and register institute (US-001).
 * Plain language: BFF only; no direct database access.
 */
export async function GET(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' },
      { status: 403 }
    );
  }
  const { searchParams } = new URL(req.url);
  const status = searchParams.get('status') || '';
  const search = searchParams.get('search') || '';
  const qs = new URLSearchParams();
  if (status) qs.set('status', status);
  if (search) qs.set('search', search);
  const suffix = qs.toString() ? `?${qs.toString()}` : '';
  try {
    const res = await fetchFromGateway(`/api/v1/iadm/01/institutes${suffix}`, {
      method: 'GET',
      requireAuth: true,
    });
    const text = await res.text();
    let body: unknown = text;
    try {
      body = JSON.parse(text);
    } catch {
      /* keep */
    }
    if (!res.ok) {
      return NextResponse.json(
        typeof body === 'object' && body
          ? body
          : { errorCode: 'UNAVAILABLE', detail: 'unavailable' },
        { status: res.status }
      );
    }
    return NextResponse.json(body, { status: res.status });
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json(
      { errorCode: 'UNAVAILABLE', detail: 'unavailable' },
      { status: 503 }
    );
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
  const payload = await req.json().catch(() => ({}));
  try {
    const res = await fetchFromGateway('/api/v1/iadm/01/institutes', {
      method: 'POST',
      requireAuth: true,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        code: payload.code,
        name: payload.name,
        // US-001: contact (email/phone). Accept legacy "region" for older clients.
        contact: payload.contact || payload.region,
        type: payload.type,
        planReference: payload.planReference || payload.plan,
      }),
    });
    const text = await res.text();
    let body: unknown = text;
    try {
      body = JSON.parse(text);
    } catch {
      /* keep */
    }
    return NextResponse.json(body, { status: res.status });
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json(
      { errorCode: 'INSTITUTE_CREATE_ERROR', detail: String(err) },
      { status: 500 }
    );
  }
}
