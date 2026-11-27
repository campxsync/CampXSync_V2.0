import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

/** IADM-03-US-001 / US-021 — Computed-on-request permission-key register (read-only). */
export async function GET(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success) {
    return NextResponse.json({ errorCode: 'UNAUTHORIZED', detail: 'Authentication required' }, { status: 401 });
  }
  const role = resolution.session.role;
  if (role !== 'platform_admin' && role !== 'system_admin' && role !== 'auditor') {
    return NextResponse.json({ errorCode: 'FORBIDDEN', detail: 'Not authorized to view permission-key register' }, { status: 403 });
  }
  try {
    const qs = req.nextUrl.searchParams.toString();
    const headers: Record<string, string> = {};
    const demoAudit = req.headers.get('x-demo-audit-disabled');
    if (demoAudit) headers['X-Demo-Audit-Disabled'] = demoAudit;
    const res = await fetchFromGateway(`/api/v1/iadm/03/register${qs ? `?${qs}` : ''}`, {
      method: 'GET',
      requireAuth: true,
      headers,
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
