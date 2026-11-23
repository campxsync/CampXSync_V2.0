import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

/** PATCH unpublished draft (IADM-02-US-012). */
export async function PATCH(
  req: NextRequest,
  context: { params: Promise<{ id: string }> }
) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' },
      { status: 403 }
    );
  }
  const { id } = await context.params;
  const draftId = decodeURIComponent(id || '');
  if (!draftId) {
    return NextResponse.json(
      { errorCode: 'MALFORMED', detail: 'Draft id is required' },
      { status: 400 }
    );
  }
  const payload = await req.json().catch(() => ({}));
  try {
    const res = await fetchFromGateway(`/api/v1/iadm/02/drafts/${encodeURIComponent(draftId)}`, {
      method: 'PATCH',
      requireAuth: true,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        proposedValue: payload.proposedValue,
        reason: payload.reason,
      }),
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
