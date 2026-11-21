import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

const Schema = z
  .object({
    userId: z.string().uuid().optional(),
    email: z.string().email().optional(),
  })
  .refine((v) => !!v.userId || !!v.email, { message: 'userId or email required' });

/** Start Institution Admin (R03) two-person appointment via IADM-01 → quorum. */
export async function POST(req: NextRequest, context: { params: Promise<{ id: string }> }) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    return NextResponse.json({ errorCode: 'FORBIDDEN', detail: 'Platform admin required' }, { status: 403 });
  }
  const { id } = await context.params;
  const parsed = Schema.safeParse(await req.json().catch(() => ({})));
  if (!parsed.success) {
    return NextResponse.json({ errorCode: 'VALIDATION', detail: 'userId or email required' }, { status: 400 });
  }
  try {
    const res = await fetchFromGateway(`/api/v1/iadm/01/institutes/${id}/appoint-admin`, {
      method: 'POST',
      requireAuth: true,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId: parsed.data.userId,
        email: parsed.data.email,
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
    return NextResponse.json({ errorCode: 'APPOINT_ADMIN_ERROR', detail: String(err) }, { status: 500 });
  }
}
