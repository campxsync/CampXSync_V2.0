import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

const AcceptOfferSchema = z.object({
  termsAccepted: z.boolean().default(true),
});

export async function POST(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success) {
      return NextResponse.json({ type: 'https://campxsync.edu/errors/unauthenticated', title: 'Unauthenticated', status: 401 }, { status: 401 });
    }

    const { tenantId, userId, collegeId } = resolution.session;
    if (!tenantId) {
      return NextResponse.json({ type: 'https://campxsync.edu/errors/bad-request', title: 'Missing Tenant Context', status: 400 }, { status: 400 });
    }

    const { id: applicationId } = await params;
    const body = await req.json().catch(() => ({ termsAccepted: true }));
    const parsed = AcceptOfferSchema.safeParse(body);

    const gatewayUrl = `${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}/offers/accept`;
    const res = await fetch(gatewayUrl, {
      method: 'POST',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        'X-Tenant-Id': tenantId,
        'X-User-Id': userId,
        'X-User-Role': 'APPLICANT',
        ...(collegeId ? { 'X-College-Id': collegeId } : {}),
      },
      body: JSON.stringify(parsed.success ? parsed.data : { termsAccepted: true }),
    });

    if (!res.ok) {
      const err = await res.text();
      return NextResponse.json({ error: err }, { status: res.status });
    }

    const data = await res.json();
    return NextResponse.json(data);
  } catch (err: any) {
    return NextResponse.json({ error: err.message || 'Internal Server Error' }, { status: 500 });
  }
}
