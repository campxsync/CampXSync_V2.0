import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

const Schema = z.object({
  tenantId: z.string().uuid(),
  reason: z.string().min(3).max(2000),
  requestedMinutes: z.number().int().min(15).max(240).optional(),
});

/** Start break-glass request — IADM-03-US-008. */
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
    const res = await fetchFromGateway('/api/v1/iadm/03/break-glass-requests', {
      method: 'POST',
      requireAuth: true,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        tenantId: parsed.data.tenantId,
        reason: parsed.data.reason,
        requestedMinutes: parsed.data.requestedMinutes ?? 60,
      }),
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
      { errorCode: 'BG_CREATE_ERROR', detail: err instanceof Error ? err.message : 'Failed' },
      { status: 500 },
    );
  }
}
