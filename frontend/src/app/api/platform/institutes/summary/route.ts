import { NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

/**
 * Platform institute counters — IADM01-US-024.
 * Plain language: numbers come from IADM-01; on failure return unavailable (never silent 0).
 */
export async function GET() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' },
      { status: 403 }
    );
  }
  try {
    const res = await fetchFromGateway('/api/v1/iadm/01/institutes/summary', {
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
        { errorCode: 'UNAVAILABLE', detail: 'unavailable', unavailable: true },
        { status: res.status >= 500 ? 503 : res.status }
      );
    }
    return NextResponse.json(body, { status: 200 });
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json(
      { errorCode: 'UNAVAILABLE', detail: 'unavailable', unavailable: true },
      { status: 503 }
    );
  }
}
