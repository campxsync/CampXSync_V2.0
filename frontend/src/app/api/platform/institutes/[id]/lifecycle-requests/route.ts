import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

async function asPlatform() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    return {
      ok: false as const,
      response: NextResponse.json({ errorCode: 'FORBIDDEN', detail: 'Platform admin required' }, { status: 403 }),
    };
  }
  return { ok: true as const };
}

async function proxy(res: Response) {
  const text = await res.text();
  let body: unknown = text;
  try {
    body = JSON.parse(text);
  } catch {
    /* keep */
  }
  return NextResponse.json(body, { status: res.status });
}

/** Platform support create lifecycle request for an institute (also used in demos). */
export async function POST(req: NextRequest, context: { params: Promise<{ id: string }> }) {
  const gate = await asPlatform();
  if (!gate.ok) return gate.response;
  const { id } = await context.params;
  const payload = await req.json().catch(() => ({}));
  try {
    const res = await fetchFromGateway(`/api/v1/iadm/01/institutes/${id}/lifecycle-requests`, {
      method: 'POST',
      requireAuth: true,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        requestType: payload.requestType,
        reason: payload.reason,
        collegeId: payload.collegeId,
      }),
    });
    return proxy(res);
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json({ errorCode: 'UNAVAILABLE', detail: 'unavailable' }, { status: 503 });
  }
}

export async function GET(req: NextRequest, context: { params: Promise<{ id: string }> }) {
  const gate = await asPlatform();
  if (!gate.ok) return gate.response;
  const { id } = await context.params;
  const status = new URL(req.url).searchParams.get('status');
  const qs = status ? `?status=${encodeURIComponent(status)}` : '';
  try {
    const res = await fetchFromGateway(`/api/v1/iadm/01/institutes/${id}/lifecycle-requests${qs}`, {
      method: 'GET',
      requireAuth: true,
    });
    return proxy(res);
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json({ errorCode: 'UNAVAILABLE', detail: 'unavailable' }, { status: 503 });
  }
}
