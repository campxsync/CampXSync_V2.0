import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { canAttemptIadm02Mutation, canReadIadm02Config } from '@/lib/auth/iadm02-access';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

/**
 * IADM-02 BFF — configuration catalogue / dashboard proxy.
 * Plain language: UI → this route → API Gateway → IADM-02 (:8100). No direct DB.
 */
export async function GET(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || !canReadIadm02Config(resolution.session.role)) {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' },
      { status: 403 }
    );
  }
  const { searchParams } = new URL(req.url);
  const view = searchParams.get('view') || 'catalogue';
  const path =
    view === 'dashboard'
      ? '/api/v1/iadm/02/dashboard'
      : view === 'drafts'
        ? '/api/v1/iadm/02/drafts'
        : view === 'policies'
          ? '/api/v1/iadm/02/policies'
          : view === 'versions'
            ? '/api/v1/iadm/02/versions'
            : '/api/v1/iadm/02/configurations';
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
