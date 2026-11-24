import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { canAttemptIadm02Mutation, canReadIadm02Config } from '@/lib/auth/iadm02-access';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

export async function POST(
  req: NextRequest,
  context: { params: Promise<{ id: string }> }
) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || !canAttemptIadm02Mutation(resolution.session.role)) {
    return NextResponse.json({ errorCode: 'FORBIDDEN', detail: 'Forbidden' }, { status: 403 });
  }
  const { id } = await context.params;
  const payload = await req.json().catch(() => ({}));
  try {
    const res = await fetchFromGateway(
      `/api/v1/iadm/02/drafts/${encodeURIComponent(id)}/publish`,
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
          releaseNotes: payload.releaseNotes,
          idempotencyKey: payload.idempotencyKey,
        }),
      }
    );
    const body = await res.json().catch(() => ({}));
    return NextResponse.json(body, { status: res.status });
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json({ errorCode: 'UNAVAILABLE', detail: 'unavailable' }, { status: 503 });
  }
}
