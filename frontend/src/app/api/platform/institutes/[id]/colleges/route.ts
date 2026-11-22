import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

/** Platform list/create colleges under an institute (support path; primary actor is R03). */
export async function GET(req: NextRequest, context: { params: Promise<{ id: string }> }) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    return NextResponse.json({ errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' }, { status: 403 });
  }
  const { id } = await context.params;
  const status = new URL(req.url).searchParams.get('status');
  const qs = status ? `?status=${encodeURIComponent(status)}` : '';
  try {
    const res = await fetchFromGateway(`/api/v1/iadm/01/institutes/${id}/colleges${qs}`, {
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
    return NextResponse.json(body, { status: res.status });
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json({ errorCode: 'UNAVAILABLE', detail: 'unavailable' }, { status: 503 });
  }
}

export async function POST(req: NextRequest, context: { params: Promise<{ id: string }> }) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    return NextResponse.json({ errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' }, { status: 403 });
  }
  const { id } = await context.params;
  const payload = await req.json().catch(() => ({}));
  try {
    const res = await fetchFromGateway(`/api/v1/iadm/01/institutes/${id}/colleges`, {
      method: 'POST',
      requireAuth: true,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        code: payload.code,
        name: payload.name,
        campusCode: payload.campusCode,
        location: payload.location,
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
    return NextResponse.json({ errorCode: 'UNAVAILABLE', detail: 'unavailable' }, { status: 503 });
  }
}
