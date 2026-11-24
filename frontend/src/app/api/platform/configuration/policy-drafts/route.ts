import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { canAttemptIadm02Mutation, canReadIadm02Config } from '@/lib/auth/iadm02-access';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

export async function POST(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || !canAttemptIadm02Mutation(resolution.session.role)) {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' },
      { status: 403 }
    );
  }
  const payload = await req.json().catch(() => ({}));
  const code = String(payload.policyCode || '');
  if (!code) {
    return NextResponse.json({ errorCode: 'MALFORMED', detail: 'Policy code required' }, { status: 400 });
  }
  try {
    const res = await fetchFromGateway(
      `/api/v1/iadm/02/policies/${encodeURIComponent(code)}/drafts`,
      {
        method: 'POST',
        requireAuth: true,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          proposedValue: payload.proposedValue,
          reason: payload.reason,
          idempotencyKey: payload.idempotencyKey,
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
