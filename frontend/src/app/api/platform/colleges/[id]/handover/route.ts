import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

/** Platform support path for CADM handover (INTERIM acks). Primary actor is Institution Admin (R03). */
export async function POST(req: NextRequest, context: { params: Promise<{ id: string }> }) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    return NextResponse.json({ errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' }, { status: 403 });
  }
  const { id } = await context.params;
  const payload = await req.json().catch(() => ({}));
  try {
    const res = await fetchFromGateway(`/api/v1/iadm/01/colleges/${id}/handover`, {
      method: 'POST',
      requireAuth: true,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ notes: payload.notes || 'S2 demo handover' }),
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
