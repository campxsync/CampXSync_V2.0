import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

/** Create configuration draft (IADM-02-US-011). */
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
  const key = String(payload.key || payload.settingKey || '');
  if (!key) {
    return NextResponse.json(
      { errorCode: 'MALFORMED', detail: 'Configuration key is required' },
      { status: 400 }
    );
  }
  try {
    const res = await fetchFromGateway(
      `/api/v1/iadm/02/configurations/${encodeURIComponent(key)}/drafts`,
      {
        method: 'POST',
        requireAuth: true,
        headers: {
          'Content-Type': 'application/json',
          ...(payload.idempotencyKey
            ? { 'Idempotency-Key': String(payload.idempotencyKey) }
            : {}),
        },
        body: JSON.stringify({
          proposedValue: payload.proposedValue,
          reason: payload.reason,
          idempotencyKey: payload.idempotencyKey,
          correlationId: payload.correlationId,
        }),
      }
    );
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
