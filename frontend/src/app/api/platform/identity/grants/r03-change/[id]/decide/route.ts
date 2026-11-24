import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { canMutateIadm03Profiles } from '@/lib/auth/iadm03-access';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

/** IADM-03 US-020 — approve/reject pending R03 grant change. */
export async function POST(req: NextRequest, ctx: { params: Promise<{ id: string }> }) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || !canMutateIadm03Profiles(resolution.session.role)) {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' },
      { status: 403 }
    );
  }
  const { id } = await ctx.params;
  const body = await req.json().catch(() => ({}));
  try {
    const res = await fetchFromGateway(
      `/api/v1/iadm/03/grants/r03-change/${encodeURIComponent(id)}/decide`,
      {
        method: 'POST',
        requireAuth: true,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...body, requestId: id }),
      }
    );
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
