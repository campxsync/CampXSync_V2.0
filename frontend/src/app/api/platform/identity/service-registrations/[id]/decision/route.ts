import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

export async function POST(
  req: NextRequest,
  context: { params: Promise<{ id: string }> }
) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success) return NextResponse.json({ errorCode: 'UNAUTHORIZED' }, { status: 401 });
  try {
    const { id } = await context.params;
    const body = await req.text();
    const res = await fetchFromGateway(`/api/v1/iadm/03/service-registrations/${encodeURIComponent(id)}/decision`, {
      method: 'POST',
      requireAuth: true,
      headers: { 'Content-Type': 'application/json' },
      body,
    });
    const text = await res.text();
    let parsed: unknown = text;
    try { parsed = JSON.parse(text); } catch { /* keep */ }
    return NextResponse.json(typeof parsed === 'object' && parsed ? parsed : { detail: text }, { status: res.status });
  } catch (err) {
    if (err instanceof GatewayAuthError) return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    return NextResponse.json({ errorCode: 'UNAVAILABLE' }, { status: 503 });
  }
}
