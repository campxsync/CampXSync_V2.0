import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

const CloseSchema = z.object({
  reason: z.string().max(2000).optional(),
});

/** Close an active break-glass session (IADM-03). */
export async function POST(req: NextRequest, context: { params: Promise<{ id: string }> }) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    return NextResponse.json({ errorCode: 'FORBIDDEN', detail: 'Platform admin required' }, { status: 403 });
  }

  const { id } = await context.params;
  if (!id || !/^[0-9a-fA-F-]{36}$/.test(id)) {
    return NextResponse.json({ errorCode: 'INVALID_ID', detail: 'sessionId must be a UUID' }, { status: 400 });
  }

  let reason = '';
  try {
    const json = await req.json().catch(() => ({}));
    const result = CloseSchema.safeParse(json ?? {});
    if (result.success) reason = result.data.reason ?? '';
  } catch {
    /* empty body ok */
  }

  try {
    const res = await fetchFromGateway(`/api/v1/iadm/03/break-glass-sessions/${id}/close`, {
      method: 'POST',
      requireAuth: true,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason }),
    });
    const text = await res.text();
    let body: unknown = text;
    try {
      body = JSON.parse(text);
    } catch {
      /* keep text */
    }
    return NextResponse.json(body, { status: res.status });
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json(
      { errorCode: 'IDENTITY_BG_CLOSE_ERROR', detail: err instanceof Error ? err.message : 'Request failed' },
      { status: 500 },
    );
  }
}
