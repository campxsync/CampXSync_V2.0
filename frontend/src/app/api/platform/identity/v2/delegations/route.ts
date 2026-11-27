import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

export async function GET() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success) return NextResponse.json({ errorCode: 'UNAUTHORIZED' }, { status: 401 });
  try {
    const res = await fetchFromGateway('/api/v1/iadm/03/v2/delegations', { method: 'GET', requireAuth: true });
    const text = await res.text();
    let parsed: unknown = text;
    try { parsed = JSON.parse(text); } catch { /* keep */ }
    return NextResponse.json(typeof parsed === 'object' && parsed ? parsed : { detail: text }, { status: res.status });
  } catch (err) {
    if (err instanceof GatewayAuthError) return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    return NextResponse.json({ errorCode: 'UNAVAILABLE' }, { status: 503 });
  }
}

export async function POST(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success) return NextResponse.json({ errorCode: 'UNAUTHORIZED' }, { status: 401 });
  try {
    const body = await req.text();
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    const demoAudit = req.headers.get('x-demo-audit-disabled');
    if (demoAudit) headers['X-Demo-Audit-Disabled'] = demoAudit;
    const res = await fetchFromGateway('/api/v1/iadm/03/v2/delegations', { method: 'POST', requireAuth: true, headers, body });
    const text = await res.text();
    let parsed: unknown = text;
    try { parsed = JSON.parse(text); } catch { /* keep */ }
    return NextResponse.json(typeof parsed === 'object' && parsed ? parsed : { detail: text }, { status: res.status });
  } catch (err) {
    if (err instanceof GatewayAuthError) return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    return NextResponse.json({ errorCode: 'UNAVAILABLE' }, { status: 503 });
  }
}
