import { NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

/**
 * IADM-05 BFF — College Admin (R04) applicable policy + informational entry points (US-011 / G-02).
 */
export async function GET() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'college_admin') {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' },
      { status: 403 }
    );
  }
  try {
    const res = await fetchFromGateway('/api/v1/iadm/05/college-view', {
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

/** IADM-05-US-011 / G-02 — College Admin (R04) cannot edit or override policy. */
export async function POST() {
  return NextResponse.json(
    {
      errorCode: 'FORBIDDEN',
      detail: 'College Admin (R04) cannot edit or override IADM-05 policy (G-02).',
    },
    { status: 403 }
  );
}
