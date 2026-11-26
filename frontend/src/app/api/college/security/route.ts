import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

/**
 * IADM-07 BFF — college security view (R04): summary posture + published standards only.
 */
export async function GET(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'college_admin') {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You do not have access to this view' },
      { status: 403 }
    );
  }
  const { searchParams } = new URL(req.url);
  const view = searchParams.get('view') || 'summary';
  let path: string | null = null;
  if (view === 'summary') {
    path = '/api/v1/iadm/07/posture/summary';
  } else if (view === 'standards') {
    path = '/api/v1/iadm/07/standards?status=PUBLISHED';
  } else if (view === 'findings' || view === 'posture-overview') {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'College Admin may view summary counts and published standards only' },
      { status: 403 }
    );
  } else {
    return NextResponse.json({ errorCode: 'BAD_REQUEST', detail: 'Unknown view' }, { status: 400 });
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
      typeof body === 'object' && body !== null ? body : { errorCode: 'UNAVAILABLE', detail: 'unavailable' },
      { status: res.status }
    );
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json({ errorCode: 'UNAVAILABLE', detail: 'unavailable' }, { status: 503 });
  }
}

export async function POST() {
  return NextResponse.json(
    { errorCode: 'FORBIDDEN', detail: 'College Admin cannot change security registers' },
    { status: 403 }
  );
}

export async function PATCH() {
  return POST();
}