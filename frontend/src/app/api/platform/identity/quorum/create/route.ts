import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

const Schema = z.object({
  tenantId: z.string().uuid(),
  targetUserId: z.string().uuid(),
  action: z.enum(['APPOINT', 'REMOVE']),
  reason: z.string().min(3).max(2000),
});

/** Start Institution Admin (R03) quorum request — IADM-03-US-009. */
export async function POST(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    return NextResponse.json({ errorCode: 'FORBIDDEN', detail: 'Platform admin required' }, { status: 403 });
  }

  const parsed = Schema.safeParse(await req.json().catch(() => ({})));
  if (!parsed.success) {
    return NextResponse.json(
      { errorCode: 'VALIDATION', detail: parsed.error.errors.map((e) => e.message).join(', ') },
      { status: 400 },
    );
  }

  try {
    const res = await fetchFromGateway('/api/v1/iadm/03/institution-admin-quorum-requests', {
      method: 'POST',
      requireAuth: true,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(parsed.data),
    });
    const text = await res.text();
    let body: unknown = text;
    try {
      body = JSON.parse(text);
    } catch {
      /* keep */
    }
    return NextResponse.json(body, { status: res.status });
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json(
      { errorCode: 'QUORUM_CREATE_ERROR', detail: err instanceof Error ? err.message : 'Failed' },
      { status: 500 },
    );
  }
}
