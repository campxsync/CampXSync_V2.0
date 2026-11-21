import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

const RegisterSchema = z.object({
  code: z.string().min(2).max(30).regex(/^[A-Z0-9_-]+$/),
  name: z.string().min(3).max(100),
  region: z.string().max(80).optional(),
  plan: z.enum(['ENTERPRISE', 'GROWTH', 'STANDARD', 'TRIAL']).optional(),
});

/** Register institute via IADM-01 (Platform Operator desk PD-04). */
export async function POST(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    return NextResponse.json({ errorCode: 'FORBIDDEN', detail: 'Platform admin required' }, { status: 403 });
  }
  const parsed = RegisterSchema.safeParse(await req.json().catch(() => ({})));
  if (!parsed.success) {
    return NextResponse.json(
      { errorCode: 'VALIDATION', detail: parsed.error.errors.map((e) => e.message).join('; ') },
      { status: 400 },
    );
  }
  try {
    const res = await fetchFromGateway('/api/v1/iadm/01/institutes', {
      method: 'POST',
      requireAuth: true,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        code: parsed.data.code,
        name: parsed.data.name,
        region: parsed.data.region || parsed.data.plan || 'IN',
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
    return NextResponse.json({ errorCode: 'INSTITUTE_REGISTER_ERROR', detail: String(err) }, { status: 500 });
  }
}
