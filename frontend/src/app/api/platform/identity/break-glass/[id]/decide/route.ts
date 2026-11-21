import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway, GatewayAuthError } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

const DecideSchema = z.object({
  decision: z.enum(['APPROVE', 'APPROVED', 'REJECT', 'REJECTED']),
  note: z.string().max(2000).optional(),
});

/** Decide a pending break-glass request (IADM-03). */
export async function POST(req: NextRequest, context: { params: Promise<{ id: string }> }) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    return NextResponse.json({ errorCode: 'FORBIDDEN', detail: 'Platform admin required' }, { status: 403 });
  }

  const { id } = await context.params;
  if (!id || !/^[0-9a-fA-F-]{36}$/.test(id)) {
    return NextResponse.json({ errorCode: 'INVALID_ID', detail: 'requestId must be a UUID' }, { status: 400 });
  }

  let parsed: z.infer<typeof DecideSchema>;
  try {
    const json = await req.json();
    const result = DecideSchema.safeParse(json);
    if (!result.success) {
      return NextResponse.json(
        { errorCode: 'VALIDATION', detail: result.error.errors.map((e) => e.message).join(', ') },
        { status: 400 },
      );
    }
    parsed = result.data;
  } catch {
    return NextResponse.json({ errorCode: 'VALIDATION', detail: 'JSON body required' }, { status: 400 });
  }

  try {
    const res = await fetchFromGateway(`/api/v1/iadm/03/break-glass-requests/${id}/decide`, {
      method: 'POST',
      requireAuth: true,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ decision: parsed.decision, note: parsed.note ?? '' }),
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
      { errorCode: 'IDENTITY_BG_DECIDE_ERROR', detail: err instanceof Error ? err.message : 'Request failed' },
      { status: 500 },
    );
  }
}
