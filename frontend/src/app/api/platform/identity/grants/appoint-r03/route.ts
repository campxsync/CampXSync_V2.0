import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { canMutateIadm03Profiles } from '@/lib/auth/iadm03-access';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';
import { randomUUID } from 'crypto';

export const dynamic = 'force-dynamic';

/** IADM-03 US-015 — appoint Institution Admin (profile then grant). */
export async function POST(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || !canMutateIadm03Profiles(resolution.session.role)) {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' },
      { status: 403 }
    );
  }
  if (resolution.session.role !== 'platform_admin') {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'Only Platform Super Admin may appoint Institution Admin.' },
      { status: 403 }
    );
  }
  const body = await req.json().catch(() => ({}));
  const idempotencyKey =
    req.headers.get('Idempotency-Key') ||
    (typeof body.idempotencyKey === 'string' ? body.idempotencyKey : null) ||
    randomUUID();
  try {
    const res = await fetchFromGateway('/api/v1/iadm/03/grants/appoint-r03', {
      method: 'POST',
      requireAuth: true,
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify({ ...body, idempotencyKey }),
    });
    const text = await res.text();
    let parsed: unknown = text;
    try {
      parsed = JSON.parse(text);
    } catch {
      /* keep */
    }
    return NextResponse.json(
      typeof parsed === 'object' && parsed ? parsed : { errorCode: 'UNAVAILABLE', detail: 'unavailable' },
      { status: res.status }
    );
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json({ errorCode: 'UNAVAILABLE', detail: 'unavailable' }, { status: 503 });
  }
}
