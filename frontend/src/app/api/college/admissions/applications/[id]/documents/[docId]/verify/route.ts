import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

const VerifyDocumentSchema = z.object({
  status: z.enum(['VERIFIED', 'REJECTED']),
  rejectionReason: z.string().optional().nullable(),
});

export async function POST(
  req: NextRequest,
  { params }: { params: Promise<{ id: string; docId: string }> }
) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success || (resolution.session.role !== 'college_admin' && resolution.session.role !== 'platform_admin')) {
      return NextResponse.json({ type: 'https://campxsync.edu/errors/forbidden', title: 'Forbidden', status: 403 }, { status: 403 });
    }

    const { tenantId, userId, collegeId } = resolution.session;
    if (!tenantId) {
      return NextResponse.json({ type: 'https://campxsync.edu/errors/bad-request', title: 'Missing Tenant Context', status: 400 }, { status: 400 });
    }

    const { id: applicationId, docId } = await params;
    const body = await req.json();
    const parsed = VerifyDocumentSchema.safeParse(body);
    if (!parsed.success) {
      return NextResponse.json({ error: 'Validation failed', details: parsed.error.format() }, { status: 422 });
    }

    const headers = await getGatewayHeaders(req, supabase, resolution.session, 'COLLEGE_ADMIN');
    headers['Content-Type'] = 'application/json';

    const gatewayUrl = `${GATEWAY_BASE_URL}/api/v1/admissions/applications/${applicationId}/documents/${docId}/verify`;
    const res = await fetch(gatewayUrl, {
      method: 'POST',
      headers,
      body: JSON.stringify(parsed.data),
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
