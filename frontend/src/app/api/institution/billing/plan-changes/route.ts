import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

/** IADM-04 BFF — Institution Admin plan-change request (US-017). */
export async function GET() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'institution_admin') {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' },
      { status: 403 }
    );
  }
  try {
    const res = await fetchFromGateway('/api/v1/iadm/04/plan-changes', {
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
      { errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' },
      { status: 403 }
    );
  }
  const raw = await req.text();
  try {
    const res = await fetchFromGateway('/api/v1/iadm/04/plan-changes', {
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
