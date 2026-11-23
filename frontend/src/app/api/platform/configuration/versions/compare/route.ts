import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

export async function GET(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    return NextResponse.json({ errorCode: 'FORBIDDEN', detail: 'Forbidden' }, { status: 403 });
  }
  const { searchParams } = new URL(req.url);
  const left = searchParams.get('left') || '';
  const right = searchParams.get('right') || '';
  if (!left || !right) {
    return NextResponse.json({ errorCode: 'MALFORMED', detail: 'left and right required' }, { status: 400 });
  }
  try {
    const res = await fetchFromGateway(
      `/api/v1/iadm/02/versions/${encodeURIComponent(left)}/compare/${encodeURIComponent(right)}`,
      { method: 'GET', requireAuth: true }
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
