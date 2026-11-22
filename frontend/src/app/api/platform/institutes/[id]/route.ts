import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

async function asPlatform() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    return { ok: false as const, response: NextResponse.json({ errorCode: 'FORBIDDEN', detail: 'Platform admin required' }, { status: 403 }) };
  }
  return { ok: true as const };
}

async function proxyJson(res: Response) {
  const text = await res.text();
  let body: unknown = text;
  try {
    body = JSON.parse(text);
  } catch {
    /* keep */
  }
  return NextResponse.json(body, { status: res.status });
}

export async function GET(_req: NextRequest, context: { params: Promise<{ id: string }> }) {
  const gate = await asPlatform();
  if (!gate.ok) return gate.response;
  const { id } = await context.params;
  try {
    const res = await fetchFromGateway(`/api/v1/iadm/01/institutes/${id}`, { method: 'GET', requireAuth: true });
    return proxyJson(res);
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json({ errorCode: 'INSTITUTE_GET_ERROR', detail: String(err) }, { status: 500 });
  }
}

/** US-018 — Platform Operator may update any institute identity. */
export async function PATCH(req: NextRequest, context: { params: Promise<{ id: string }> }) {
  const gate = await asPlatform();
  if (!gate.ok) return gate.response;
  const { id } = await context.params;
  const payload = await req.json().catch(() => ({}));
  try {
    const res = await fetchFromGateway(`/api/v1/iadm/01/institutes/${id}`, {
      method: 'PATCH',
      requireAuth: true,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: payload.name,
        contact: payload.contact,
        location: payload.location,
      }),
    });
    return proxyJson(res);
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json({ errorCode: 'INSTITUTE_PATCH_ERROR', detail: String(err) }, { status: 500 });
  }
}
