import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { canReadIadm03Access } from '@/lib/auth/iadm03-access';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

/**
 * IADM-03 BFF — platform identity & access reads.
 * UI → BFF → Gateway → IADM-03 (:8101). No direct DB.
 */
export async function GET(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || !canReadIadm03Access(resolution.session.role)) {
    return NextResponse.json(
      { errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' },
      { status: 403 }
    );
  }
  const { searchParams } = new URL(req.url);
  const view = searchParams.get('view') || 'overview';
  const q = searchParams.get('q') || '';
  const status = searchParams.get('status') || 'ALL';
  let path = '/api/v1/iadm/03/overview';
  if (view === 'roles') {
    path = '/api/v1/iadm/03/roles';
  } else if (view === 'permissions') {
    path = `/api/v1/iadm/03/permissions?q=${encodeURIComponent(q)}`;
  } else if (view === 'profiles') {
    path = `/api/v1/iadm/03/profiles?status=${encodeURIComponent(status)}`;
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
