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
  if (!resolution.success) {
    return NextResponse.json({ errorCode: 'UNAUTHORIZED', detail: 'Authentication required' }, { status: 401 });
  }
  try {
    const { id } = await context.params;
    const body = await req.text();
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    const demoAudit = req.headers.get('x-demo-audit-disabled');
    if (demoAudit) headers['X-Demo-Audit-Disabled'] = demoAudit;
    const res = await fetchFromGateway(`/api/v1/iadm/03/grant-requests/${encodeURIComponent(id)}/validate`, {
      method: 'POST',
      requireAuth: true,
      headers,
      body: body || '{}',
    });
    const text = await res.text();
    let parsed: unknown = text;
    try { parsed = JSON.parse(text); } catch { /* keep */ }
    return NextResponse.json(typeof parsed === 'object' && parsed ? parsed : { detail: text }, { status: res.status });
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json({ errorCode: 'UNAVAILABLE', detail: 'IADM-03 unavailable' }, { status: 503 });
  }
}
